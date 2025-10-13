package main.util;

import main.domain.WireMessage;

import java.io.*;

public final class MessageSerializer {
    private MessageSerializer() {}

    /** Serialize any Serializable (typically WireMessage) to bytes. */
    public static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("serialize failed: " + e.getMessage(), e);
        }
    }

    /** Deserialize bytes into a WireMessage. */
    public static WireMessage deserialize(byte[] bytes) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object o = ois.readObject();
            return (WireMessage) o;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("deserialize failed: " + e.getMessage(), e);
        }
    }
}
