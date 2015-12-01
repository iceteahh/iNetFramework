/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inetframework.logger;

import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ICETEA
 */
public class LogHandler {

    private ArrayDeque<Exception> logQueue;
    private final Object lock;
    private Thread worker;
    private boolean isRunning;

    public LogHandler() {
        logQueue = new ArrayDeque<>();
        lock = new Object();
    }

    public void startHandle() {
        isRunning = true;
        worker = new Thread(() -> {
            while (isRunning) {
                if (logQueue.isEmpty()) {
                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(LogHandler.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                } else {
                    Object ex = logQueue.remove();
                    Logger.getLogger(LogHandler.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        worker.start();
        worker.setName("log hanle");
    }

    public void stop() {
        isRunning = false;
        if (logQueue.isEmpty()) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }
    
    public void push(Exception ex) {
        logQueue.add(ex);
        if(worker.getState() == Thread.State.WAITING){
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }
}
