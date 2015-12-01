/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inetframework.client;

import inetframework.message.MessageQueue;
import inetframework.message.ReceivedMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 *
 * @author ICETEA
 */
public class Client {

    public interface ClientStatus {
        void onConnected();
        void onDisconnected();
        void onConnectFail();
    }

    private SocketChannel socketChannel;
    private Thread pingThread;
    private Thread listenThread;
    private boolean isPinging;
    private boolean isListening;
    private boolean isStopListening;
    private final ByteBuffer pingMsg;
    private long lastUpdate;
    private final MessageQueue mq;

    private ClientStatus clientStatus = new Client.ClientStatus() {
        @Override
        public void onConnected() {
            System.out.println("Connected");
        }

        @Override
        public void onDisconnected() {
            System.out.println("Disconnected");
        }

        @Override
        public void onConnectFail() {
            System.out.println("Connect fail");
        }
    };

    public Client() throws IOException {
        pingMsg = ByteBuffer.allocate(1);
        pingMsg.put((byte) 0);
        mq = new MessageQueue();
    }

    public void connect(String address, int port) {
        if (isListening || isStopListening) {
            return;
        }
        lastUpdate = System.currentTimeMillis();
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(address, port));
        } catch (IOException ex) {
            //Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            clientStatus.onConnectFail();
            return;
        }
        startListening();
        mq.startDequeueMessage();
        pingThread = new Thread(() -> {
            isPinging = true;
            while (isPinging) {
                pingMsg.flip();
                try {
                    socketChannel.write(pingMsg);
                } catch (IOException ex) {
                    try {
                        close();
                    } catch (IOException ex1) {
                        //Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                    clientStatus.onDisconnected();
                    return;
                }
                if (System.currentTimeMillis() - lastUpdate > 10000) {
                    try {
                        close();
                    } catch (IOException ex) {
                        //Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    clientStatus.onDisconnected();
                    return;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    //Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            isStopListening = false;
        });
        pingThread.setName("ping thread");
        pingThread.start();
        clientStatus.onConnected();
    }

    private void startListening() {
        isListening = true;
        listenThread = new Thread(() -> {
            while (isListening) {
                ByteBuffer header = ByteBuffer.allocate(5);
                try {
                    socketChannel.read(header);
                    header.flip();
                    byte type = header.get();
                    if (type != 0) {
                        int lent = header.getInt();
                        ByteBuffer content = ByteBuffer.allocate(lent);
                        socketChannel.read(content);
                        content.flip();
                        ReceivedMessage msg = new ReceivedMessage(null, type, content);
                        mq.push(msg);
                    }
                    lastUpdate = System.currentTimeMillis();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    try {
                        close();
                    } catch (IOException ex1) {
                        //Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                    clientStatus.onDisconnected();
                }
            }
            isStopListening = false;
        });
        listenThread.setName("listen thread");
        listenThread.start();
    }

    public void send(ByteBuffer msg) throws IOException {
        //System.out.println("*" + msg.get());
        socketChannel.write(msg);
    }

    public void close() throws IOException {
        socketChannel.close();
        isPinging = false;
        isListening = false;
        isStopListening = true;
        listenThread.interrupt();
        pingThread.interrupt();
        mq.stop();
        try {
            pingThread.join();
            listenThread.join();
            mq.join();
            isStopListening = false;
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    public SocketChannel getSocket() {
        return socketChannel;
    }

    public void setStatusListener(Client.ClientStatus listener) {
        clientStatus = listener;
    }

    public void removeClientStatus() {
        clientStatus = new Client.ClientStatus() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onDisconnected() {
            }

            @Override
            public void onConnectFail() {
            }
        };
    }

    public MessageQueue getMessageQueue() {
        return mq;
    }

    public boolean isAlive(){
        return isListening;
    }
}
