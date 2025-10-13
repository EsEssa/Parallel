package main.util;


import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import main.config.Constants;

public final class RabbitMQConfig {
    private RabbitMQConfig() {}

    /** Create a new connection using host/user/pass. */
    public static Connection createConnection(String host, String user, String pass) {
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost(host);
            if (user != null && !user.isBlank()) f.setUsername(user);
            if (pass != null && !pass.isBlank()) f.setPassword(pass);
            // adjust timeouts etc. if needed
            return f.newConnection();
        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to RabbitMQ at " + host + ": " + e.getMessage(), e);
        }
    }

    /** Declare exchanges that are common to all actors. Call once per channel. */
    public static void declareCommonExchanges(Channel ch) {
        try {
            ch.exchangeDeclare(Constants.BUILDINGS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, false);
            ch.exchangeDeclare(Constants.BUILDING_DIRECT_EXCHANGE,  BuiltinExchangeType.DIRECT, false);
        } catch (Exception e) {
            throw new RuntimeException("declareCommonExchanges failed: " + e.getMessage(), e);
        }
    }

    /** Ensure the shared client→agent inbox exists (multiple agents consume round-robin). */
    public static void declareAgentInbox(Channel ch) {
        try {
            ch.queueDeclare(Constants.AGENT_INBOX_QUEUE, false, false, false, null);
        } catch (Exception e) {
            throw new RuntimeException("declareAgentInbox failed: " + e.getMessage(), e);
        }
    }

    /** Ensure a per-client reply queue exists (auto-delete is fine here). */
    public static String declareClientReplyQueue(Channel ch, String clientId) {
        try {
            String q = Constants.clientReplyQueue(clientId);
            ch.queueDeclare(q, false, false, true, null);
            return q;
        } catch (Exception e) {
            throw new RuntimeException("declareClientReplyQueue failed: " + e.getMessage(), e);
        }
    }

    /** Declare + bind one building’s inbox to the direct exchange. */
    public static String declareAndBindBuildingInbox(Channel ch, String buildingName) {
        try {
            String q  = Constants.buildingInboxQueue(buildingName);
            String rk = Constants.buildingRoutingKey(buildingName);
            ch.queueDeclare(q, false, false, false, null);
            ch.queueBind(q, Constants.BUILDING_DIRECT_EXCHANGE, rk);
            return q;
        } catch (Exception e) {
            throw new RuntimeException("declareAndBindBuildingInbox failed: " + e.getMessage(), e);
        }
    }
}
