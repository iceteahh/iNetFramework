/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inetframework.message;

import inetframework.server.Server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

/**
 *
 * @author ICETEA
 */
public class ReceivedMessage {

    private byte type;
    private ByteBuffer content;
    private Server.Connection from;
    
    public ReceivedMessage(Server.Connection from, byte type, ByteBuffer content){
        this.type = type;
        this.content = content;
        this.from = from;
    }
    
    public byte getType(){
        return type;
    }
    
    public ByteBuffer getContent(){
        return content;
    }
    
    public Server.Connection getSource(){
        return from;
    }

}
