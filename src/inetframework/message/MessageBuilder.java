/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package inetframework.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 *
 * @author ICETEA
 */
public class MessageBuilder {

    private final ArrayList<Byte> bytes;
    private final byte header;

    public MessageBuilder(byte header) {
        bytes = new ArrayList<>();
        this.header = header;
    }


    public MessageBuilder putInt(int i) {
        bytes.add((byte) ((i >>> 24) & 0xFF));
        bytes.add((byte) ((i >>> 16) & 0xFF));
        bytes.add((byte) ((i >>> 8) & 0xFF));
        bytes.add((byte) ((i) & 0xFF));
        return this;
    }

    public MessageBuilder putByte(byte b) {
        bytes.add(b);
        return this;
    }

    public MessageBuilder putBytes(byte[] b) {
        for (byte c : b) {
            bytes.add(c);
        }
        return this;
    }

    public MessageBuilder putFloat(float f) {
        putInt(Float.floatToIntBits(f));
        return this;
    }

    public MessageBuilder putDouble(double d) {
        putLong(Double.doubleToLongBits(d));
        return this;
    }

    public MessageBuilder putLong(long l) {
        bytes.add((byte) ((l >>> 56) & 0xFF));
        bytes.add((byte) ((l >>> 48) & 0xFF));
        bytes.add((byte) ((l >>> 40) & 0xFF));
        bytes.add((byte) ((l >>> 32) & 0xFF));
        bytes.add((byte) ((l >>> 24) & 0xFF));
        bytes.add((byte) ((l >>> 16) & 0xFF));
        bytes.add((byte) ((l >>> 8) & 0xFF));
        bytes.add((byte) ((l) & 0xFF));
        return this;
    }

    public MessageBuilder putShort(short s) {
        bytes.add((byte) ((s >>> 8) & 0xFF));
        bytes.add((byte) ((s) & 0xFF));
        return this;
    }

    public MessageBuilder putChar(char c) {
        bytes.add((byte) ((c >>> 8) & 0xFF));
        bytes.add((byte) ((c) & 0xFF));
        return this;
    }
    
    public ByteBuffer createData(){
        int size = bytes.size() + 5;
        ByteBuffer data = ByteBuffer.allocate(size);
        data.put(header);
        data.putInt(bytes.size());
        bytes.stream().forEach(data::put);
        data.flip();
        return data;
    }
}
