package main.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * Envelope used on the wire via RabbitMQ.
 * 'sender' should usually be the clientId so buildings can reply directly.
 */
public class WireMessage implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final String sender;       // e.g., "Client1" (ideally the original client id)
    private final Object payload;      // must be Serializable

    public WireMessage(MessageType type, String sender, Object payload) {
        this.type = type;
        this.sender = sender;
        this.payload = payload;
    }

    public MessageType type() { return type; }
    public String sender() { return sender; }
    public Object payload() { return payload; }

    @Override
    public String toString() {
        return "WireMessage{type=" + type + ", sender='" + sender + "', payload=" + payload + '}';
    }
}