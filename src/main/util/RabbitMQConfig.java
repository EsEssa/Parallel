package main.util;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import main.config.Constants;

/**
 * Utility class for RabbitMQ configuration and setup.
 * Provides methods for creating connections and declaring common
 * exchanges and queues used throughout the application.
 */
public final class RabbitMQConfig {

    // Private constructor to prevent instantiation
    private RabbitMQConfig() {}

    /**
     * Creates a new RabbitMQ connection with the specified credentials.
     *
     * @param host the RabbitMQ server hostname
     * @param user the username for authentication
     * @param pass the password for authentication
     * @return a new RabbitMQ connection
     * @throws RuntimeException if connection fails
     */
    public static Connection createConnection(String host, String user, String pass) {
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost(host);
            if (user != null && !user.isBlank()) f.setUsername(user);
            if (pass != null && !pass.isBlank()) f.setPassword(pass);
            // Additional connection settings can be configured here if needed
            return f.newConnection();
        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to RabbitMQ at " + host + ": " + e.getMessage(), e);
        }
    }

    /**
     * Should be called once per channel during initialization.
     *
     * @param ch the channel to use for declaration
     * @throws RuntimeException if exchange declaration fails
     */
    public static void declareCommonExchanges(Channel ch) {
        try {
            // Make exchanges durable (survive broker restarts)
            ch.exchangeDeclare(Constants.BUILDINGS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true, false, false, null);
            ch.exchangeDeclare(Constants.BUILDING_DIRECT_EXCHANGE, BuiltinExchangeType.DIRECT, true, false, false, null);
        } catch (Exception e) {
            throw new RuntimeException("declareCommonExchanges failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures the shared client-to-agent inbox queue exists.
     * Multiple agents can consume from this queue for load distribution.
     * Made durable for fault tolerance.
     * @param ch the channel to use for declaration
     * @throws RuntimeException if queue declaration fails
     */
    public static void declareAgentInbox(Channel ch) {
        try {
            // Make durable (survive broker restarts)
            ch.queueDeclare(Constants.AGENT_INBOX_QUEUE, true, false, false, null);
        } catch (Exception e) {
            throw new RuntimeException("declareAgentInbox failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures a client-specific reply queue exists.
     * Uses auto-delete since these queues are temporary and client-specific.
     *
     * @param ch the channel to use for declaration
     * @param clientId the unique identifier of the client
     * @return the name of the declared queue
     * @throws RuntimeException if queue declaration fails
     */
    public static String declareClientReplyQueue(Channel ch, String clientId) {
        try {
            String q = Constants.clientReplyQueue(clientId);
            ch.queueDeclare(q, false, false, true, null);
            return q;
        } catch (Exception e) {
            throw new RuntimeException("declareClientReplyQueue failed: " + e.getMessage(), e);
        }
    }

    /**
     * Declares and binds a building-specific inbox queue to the direct exchange.
     * Each building has its own queue for receiving commands.
     * Made durable for fault tolerance.
     *
     * @param ch the channel to use for declaration and binding
     * @param buildingName the name of the building
     * @return the name of the declared and bound queue
     * @throws RuntimeException if declaration or binding fails
     */
    public static String declareAndBindBuildingInbox(Channel ch, String buildingName) {
        try {
            String q  = Constants.buildingInboxQueue(buildingName);
            String rk = Constants.buildingRoutingKey(buildingName);
            // Make durable (survive broker restarts)
            ch.queueDeclare(q, true, false, false, null);
            ch.queueBind(q, Constants.BUILDING_DIRECT_EXCHANGE, rk);
            return q;
        } catch (Exception e) {
            throw new RuntimeException("declareAndBindBuildingInbox failed: " + e.getMessage(), e);
        }
    }
}