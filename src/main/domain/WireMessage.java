package main.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * Message envelope used for all communication via RabbitMQ.
 * Contains message type, sender identification, and payload data.
 * The sender field typically contains the client ID to enable
 * direct replies from buildings to clients.
 */
public class WireMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final String sender;
    private final Object payload; // Must be serializable

    /**
     * Creates a new wire message with the specified components.
     *
     * @param type the type of message being sent
     * @param sender the identifier of the message sender
     * @param payload the message content (must be serializable)
     */
    public WireMessage(MessageType type, String sender, Object payload) {
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    /**
     * Gets the message type.
     *
     * @return the type of this message
     */
    public MessageType type() {
        return type;
    }

    /**
     * Gets the sender identifier.
     *
     * @return the ID of the message sender
     */
    public String sender() {
        return sender;
    }

    /**
     * Gets the message payload.
     *
     * @return the content of this message
     */
    public Object payload() {
        return payload;
    }

    /**
     * Returns a string representation of the wire message.
     *
     * @return formatted string showing message details
     */
    @Override
    public String toString() {
        return "WireMessage{type=" + type + ", sender='" + sender + "', payload=" + payload + '}';
    }
}