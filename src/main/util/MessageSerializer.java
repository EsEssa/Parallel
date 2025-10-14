package main.util;

import main.domain.WireMessage;

import java.io.*;

/**
 * Utility class for serializing and deserializing messages for RabbitMQ communication.
 * Provides methods to convert objects to bytes and back, primarily used for WireMessage objects.
 */
public final class MessageSerializer {

    // Private constructor to prevent instantiation
    private MessageSerializer() {}

    /**
     * Serializes a Serializable object into a byte array.
     * Primarily used for converting WireMessage objects for RabbitMQ transmission.
     *
     * @param obj the object to serialize (must implement Serializable)
     * @return byte array representation of the object
     * @throws RuntimeException if serialization fails
     */
    public static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("serialize failed: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a byte array back into a WireMessage object.
     * Used for converting received RabbitMQ messages back to domain objects.
     *
     * @param bytes the byte array to deserialize
     * @return the deserialized WireMessage object
     * @throws RuntimeException if deserialization fails due to I/O or class issues
     */
    public static WireMessage deserialize(byte[] bytes) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object o = ois.readObject();
            return (WireMessage) o;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("deserialize failed: " + e.getMessage(), e);
        }
    }
}