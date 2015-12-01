/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inetframework.message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ICETEA
 */
public class MessageQueue {

    public interface MessageDequeueListener {
        void onMessageDequeue(ReceivedMessage receiveMessage);
    }
    private boolean isRunning;
    private Queue<ReceivedMessage> msgQueue;
    private final List<MessageDequeueListener> listeners;
    private final Object lock;
    private Thread thread;

    public MessageQueue() {
        lock = new Object();
        msgQueue = new ArrayDeque<>();
        listeners = Collections.synchronizedList(new ArrayList<>());
    }

    public void startDequeueMessage() {
        isRunning = true;
        thread = new Thread(() -> {
            while (isRunning) {
                if (msgQueue.isEmpty()) {
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(MessageQueue.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } else {
                    ReceivedMessage msg = msgQueue.remove();
                    synchronized (listeners) {
                        for (MessageDequeueListener mh : listeners) {
                            mh.onMessageDequeue(msg);
                        }
                    }
                }
            }
        });
        thread.setName("message hanle");
        thread.start();
    }

    public void push(ReceivedMessage msg) {
        msgQueue.add(msg);
        if(thread.getState() == Thread.State.WAITING){
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    public void addMessageQueueListener(MessageDequeueListener listener) {
        listeners.add(listener);
    }

    public void removeMessageHanleListener(MessageDequeueListener listener) {
        listeners.remove(listener);
    }

    public void stop() {
        isRunning = false;
        if (msgQueue.isEmpty()) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }
    
    public Thread.State getState(){
        return thread.getState();
    }
    
    public void join() throws InterruptedException{
        thread.join();
    }

}
