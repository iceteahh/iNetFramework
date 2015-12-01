/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inetframework.server;

import inetframework.logger.LogHandler;
import inetframework.message.MessageQueue;
import inetframework.message.ReceivedMessage;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

/**
 *
 * @author ICETEA
 */
public class Server {

    private int port;

    public int getPort() {
        return port;
    }

    public interface StateChangeListener {

        void stateChanged(State state, Event event);
    }

    public static final int TOPIC_CLIENT_ACCEPTED = 0;
    public static final int TOPIC_CLIENT_DISCONNECT = 1;

    public interface ConnectionsChangeListener {

        void onConnectionsChange(int topic, Object attach);
    }

    private ConnectionsChangeListener connectionsChangeListener = (topic, attach) -> {
    };

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private boolean isPinging;
    private Map<SelectableChannel, Connection> connections;
    private Map<SelectableChannel, Connection> temp;
    private Thread pingThread;
    private Thread listenThread;
    private ByteBuffer pingmsg;
    private final MessageQueue mq;
    private final LogHandler logHandler;
    private final Object lock;

    public Server() {
        logHandler = new LogHandler();
        logHandler.startHandle();
        mq = new MessageQueue();
        lock = new Object();
        init();
    }

    private void init() {
        connections = Collections.synchronizedMap(new HashMap<>());
        pingmsg = ByteBuffer.allocate(1);
        pingmsg.put((byte) 0);
        pingmsg.flip();
        temp = new HashMap<>();
    }

    public void start(final int port) {
        if (currentState != State.STOPPED) {
            return;
        }
        try {
            this.port = port;
            performStateChange(Event.START);
            serverSocketChannel = ServerSocketChannel.open();
            selector = Selector.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            mq.startDequeueMessage();
            startPing();
            performStateChange(Event.START_SUCCESS);
        } catch (Exception ex) {
            logHandler.push(ex);
            if (ex instanceof BindException) {
                try {
                    performStateChange(Event.PORT_ALREADY_IN_USE);
                } catch (Exception ex1) {
                    logHandler.push(ex1);
                }
            } else {
                try {
                    performStateChange(Event.START_FAIL);
                } catch (Exception ex1) {
                    logHandler.push(ex1);
                }
            }
            ex.printStackTrace();
            return;
        }
        listenThread = new Thread(() -> {
            while (currentState == State.STARTED) {
                try {
                    if (selector.select() == 0) {
                        continue;
                    }
                } catch (IOException ex) {
                    logHandler.push(ex);
                    try {
                        performStateChange(Event.STOP);
                    } catch (Exception ex1) {
                        logHandler.push(ex1);
                    }
                    return;
                }
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            try {
                                SocketChannel socketChannel = serverSocketChannel.accept();
                                System.out.println("!Accept");
                                if (socketChannel != null) {
                                    socketChannel.configureBlocking(false);
                                    SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
                                    selectionKey.attach(System.currentTimeMillis());
                                    synchronized (lock) {
                                        Connection connection = new Connection(selectionKey);
                                        connections.put(socketChannel, connection);
                                    }
                                    connectionsChangeListener.onConnectionsChange(TOPIC_CLIENT_ACCEPTED, selectionKey);
                                }
                            } catch (IOException ex) {
                                logHandler.push(ex);
                            }
                        } else if (key.isReadable()) {
                            SocketChannel socket = (SocketChannel) key.channel();
                            try {
                                ByteBuffer header = ByteBuffer.allocate(5);
                                socket.read(header);
                                header.flip();
                                byte type = header.get();
                                if (type != 0) {
                                    System.out.println("!header: " + type);
                                    int lent = header.getInt();
                                    System.out.println("!lent: " + lent);
                                    ByteBuffer content = ByteBuffer.allocate(lent);
                                    socket.read(content);
                                    content.flip();
                                    ReceivedMessage msg = new ReceivedMessage(connections.get(key.channel()), type, content);
                                    mq.push(msg);
                                }
                                key.attach(System.currentTimeMillis());
                            } catch (Exception ex) {
                                //logHandler.push(ex);
                                try {
                                    closeConnecion(key);
                                } catch (IOException ex1) {
                                    logHandler.push(ex1);
                                }
                                connectionsChangeListener.onConnectionsChange(TOPIC_CLIENT_DISCONNECT, key);
                            }
                        }
                    }
                    iterator.remove();
                }
            }
        });
        listenThread.start();
        listenThread.setName("server listen");
    }

    public void stop() {
        try {
            performStateChange(Event.STOP);
            mq.stop();
            stopPing();
            selector.wakeup();
            listenThread.interrupt();
            listenThread.join();
            pingThread.join();
            mq.join();
            try {
                selector.close();
                serverSocketChannel.close();
                synchronized (lock) {
                    connections.clear();
                }
                performStateChange(Event.STOP_SUCCESS);
            } catch (Exception ex1) {
                logHandler.push(ex1);
                try {
                    performStateChange(Event.STOP_FAIL);
                } catch (Exception ex2) {
                    logHandler.push(ex2);
                }
            }
        } catch (Exception ex) {
            logHandler.push(ex);
        }
    }

    private void startPing() {
        pingThread = new Thread(() -> {
            isPinging = true;
            while (isPinging) {
                long l = System.currentTimeMillis();
                synchronized (lock) {
                    for (SelectableChannel channel : connections.keySet()) {
                        Connection connection = connections.get(channel);
                        try {
                            connection.socketChannel.write(pingmsg);
                            pingmsg.flip();
                            Long time = (Long) connection.selectionKey.attachment();
                            if (System.currentTimeMillis() - time > 10000) {
                                try {
                                    connection.selectionKey.cancel();
                                    connection.selectionKey.channel().close();
                                } catch (IOException ex1) {
                                    logHandler.push(ex1);
                                }
                                temp.put(channel, connection);
                            }
                        } catch (IOException ex) {
                            try {
                                connection.selectionKey.cancel();
                                connection.selectionKey.channel().close();
                            } catch (IOException ex1) {
                                logHandler.push(ex1);
                            }
                            logHandler.push(ex);
                            temp.put(channel, connection);
                            connectionsChangeListener.onConnectionsChange(TOPIC_CLIENT_DISCONNECT, connection);
                        }
                    }
                    connections.keySet().removeAll(temp.keySet());
                    temp.clear();
                }
                System.out.println("!ping: " + (System.currentTimeMillis() - l));
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    logHandler.push(ex);
                }
            }
        });
        pingThread.start();
        pingThread.setName("thread ping");
    }

    private void stopPing() {
        isPinging = false;
        pingThread.interrupt();
    }

    public void sendAll(ByteBuffer msg) {
        synchronized (lock) {
            for (SelectableChannel channel : connections.keySet()) {
                Connection connection = connections.get(channel);
                try {
                    connection.socketChannel.write(msg);
                    msg.flip();
                } catch (IOException ex) {
                    try {
                        connection.socketChannel.close();
                    } catch (IOException ex1) {
                        logHandler.push(ex1);
                    }
                    connection.selectionKey.cancel();
                    logHandler.push(ex);
                    temp.put(channel, connection);
                    connectionsChangeListener.onConnectionsChange(TOPIC_CLIENT_DISCONNECT, connection);
                }
            }
            connections.keySet().removeAll(temp.keySet());
        }
    }

    public void closeConnecion(SelectionKey selectionKey) throws IOException {
        connections.get(selectionKey.channel()).close();
        synchronized (lock) {
            connections.remove(selectionKey.channel());
        }
    }

    public MessageQueue getMessageQueue() {
        return mq;
    }

    public void setConnectionsChangeListener(ConnectionsChangeListener listener) {
        connectionsChangeListener = listener;
    }

    public void removeConnectionsChangeListener() {
        connectionsChangeListener = new ConnectionsChangeListener() {
            @Override
            public void onConnectionsChange(int topic, Object attach) {
            }
        };
    }

    public Map getListConnect() {
        return connections;
    }
    /**
     * **************************** State machine
     * ************************************************
     */
    private static final List<StateChangeListener> stateChangeListeners
            = Collections.synchronizedList(new ArrayList<>());
    private static Map<State, EnumSet<State>> stateMap;
    private static State currentState;

    static {
        stateMap = new EnumMap<>(State.class);
        stateMap.put(State.STARTED, EnumSet.of(State.STOPPING, State.STARTED));
        stateMap.put(State.STOPPING, EnumSet.of(State.STOPPED, State.STARTED));
        stateMap.put(State.STOPPED, EnumSet.of(State.STARTING, State.STOPPED));
        stateMap.put(State.STARTING, EnumSet.of(State.STARTED, State.STOPPED));
        currentState = State.STOPPED;
    }

    public enum State {

        STARTED,
        STOPPED,
        STOPPING,
        STARTING
    }

    public enum Event {

        START,
        STOP,
        START_SUCCESS,
        START_FAIL,
        STOP_SUCCESS,
        STOP_FAIL,
        PORT_ALREADY_IN_USE
    }

    public void performStateChange(Event e) throws Exception {
        switch (e) {
            case START:
                init();
                setCurrentState(State.STARTING, e);
                break;
            case STOP:
                setCurrentState(State.STOPPING, e);
                break;
            case START_FAIL:
                setCurrentState(State.STOPPED, e);
                break;
            case START_SUCCESS:
                setCurrentState(State.STARTED, e);
                break;
            case STOP_FAIL:
                setCurrentState(State.STARTED, e);
                break;
            case STOP_SUCCESS:
                setCurrentState(State.STOPPED, e);
                break;
            case PORT_ALREADY_IN_USE:
                setCurrentState(State.STOPPED, e);
        }
    }

    private boolean isReachable(State state) {
        for (State s : stateMap.get(currentState)) {
            if (s.equals(state)) {
                return true;
            }
        }
        return false;
    }

    private void setCurrentState(State state, Event e) throws Exception {
        if (isReachable(state)) {
            currentState = state;
            synchronized (stateChangeListeners) {
                for (StateChangeListener listener : stateChangeListeners) {
                    listener.stateChanged(currentState, e);
                }
            }
        } else {
            throw new Exception("Can't perform this state");
        }

    }

    public void addStateChangeListener(StateChangeListener listener) {
        synchronized (stateChangeListeners) {
            stateChangeListeners.add(listener);
        }
    }

    public void removeStateChangeListener(StateChangeListener listener) {
        synchronized (stateChangeListeners) {
            stateChangeListeners.remove(listener);
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    public class Connection {

        private SelectionKey selectionKey;
        private SocketChannel socketChannel;

        public Connection(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
            socketChannel = (SocketChannel) selectionKey.channel();
        }

        public void write(ByteBuffer msg) {
            try {
                socketChannel.write(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void close() {
            selectionKey.cancel();
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public SelectionKey getSelectionKey() {
            return selectionKey;
        }
    }
}
