package main.agent;

import com.rabbitmq.client.*;
import main.config.AppConfig;
import main.config.Constants;
import main.domain.*;
import main.util.MessageSerializer;
import main.util.RabbitMQConfig;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class RentalAgent {

    private final String agentName;
    private Connection connection;
    private Channel channel;

    // learned from building fanout announcements
    private final Set<String> knownBuildings = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> buildingLastSeen = new ConcurrentHashMap<>();
    private final boolean verbose = false; // set true if you want periodic "still alive" logs
    private static final long HEARTBEAT_LOG_MS = 60_000; // log at most once per minute per building


    /**
     * Creates a new rental agent with the specified name.
     *
     * @param agentName the unique identifier for this agent
     */
    public RentalAgent(String agentName) {
        this.agentName = agentName;
    }

    /**
     * Starts the agent by connecting to RabbitMQ, setting up message queues,
     * and beginning to listen for building announcements and client requests.
     *
     * @throws IOException      if there's an issue with RabbitMQ connection
     * @throws TimeoutException if the connection times out
     */
    public void start() throws IOException, TimeoutException {
        connection = RabbitMQConfig.createConnection(AppConfig.getRabbitHost(), AppConfig.getRabbitUser(), AppConfig.getRabbitPass());
        channel = connection.createChannel();

        declareTopology(channel);

        subscribeDiscovery(channel);      // learn buildings via fanout
        subscribeClientInbox(channel);    // handle client requests

        System.out.printf("[Agent %s] up. Known buildings: %s%n", agentName, knownBuildings);
    }

    /**
     * Stops the agent and closes all RabbitMQ connections.
     *
     * @throws IOException      if there's an issue closing connections
     * @throws TimeoutException if closing times out
     */
    public void stop() throws IOException, TimeoutException {
        if (channel != null) channel.close();
        if (connection != null) connection.close();
        System.out.printf("[Agent %s] down.%n", agentName);
    }

    // topology

    // sets up RabbitMQ exchanges and queues
    private void declareTopology(Channel ch) throws IOException {
        // Declare common exchanges
        RabbitMQConfig.declareCommonExchanges(ch);

        // Declare shared client->agent queue (multiple agents consume round-robin)
        RabbitMQConfig.declareAgentInbox(ch);
    }

    // subscriptions

    /**
     * Subscribe to building announcements (fanout)
     *
     * @param ch the RabbitMQ channel to use for subscription
     * @throws IOException if subscription fails
     */
    private void subscribeDiscovery(Channel ch) throws IOException {
        String tmpQueue = ch.queueDeclare().getQueue(); // auto-delete, exclusive
        ch.queueBind(tmpQueue, Constants.BUILDINGS_FANOUT_EXCHANGE, "");

        DeliverCallback cb = (tag, delivery) -> {
            String buildingName = new String(delivery.getBody());
            if (buildingName == null || buildingName.isBlank()) return;

            long now = System.currentTimeMillis();
            Long prev = buildingLastSeen.put(buildingName, now);

            // First time seeing this building
            if (knownBuildings.add(buildingName) || prev == null) {
                System.out.printf("[Agent %s] discovered building: %s%n", agentName, buildingName);
                return;
            }

            // suppress spam. Optionally report a heartbeat only every N ms.
            if (verbose && (now - prev) >= HEARTBEAT_LOG_MS) {
                System.out.printf("[Agent %s] heartbeat from %s%n", agentName, buildingName);
            }
        };

        ch.basicConsume(tmpQueue, true, cb, tag -> {
        });
    }

    /**
     * Subscribe to the shared client -> agents inbox (round-robin)
     *
     * @param ch the RabbitMQ channel to use for subscription
     * @throws IOException if subscription fails
     */
    private void subscribeClientInbox(Channel ch) throws IOException {
        DeliverCallback cb = (tag, delivery) -> {
            try {
                WireMessage msg = MessageSerializer.deserialize(delivery.getBody());
                handleClientMessage(msg);
                // Acknowledge successful processing
                ch.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                System.err.printf("[Agent %s] error handling message: %s%n", agentName, e.getMessage());
                // Reject and requeue for retry (or use false to send to DLQ if configured)
                try {
                    ch.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                } catch (IOException ioEx) {
                    System.err.printf("[Agent %s] Failed to nack message: %s%n", agentName, ioEx.getMessage());
                }
            }
        };
        // Set autoAck to false for manual acknowledgment
        ch.basicConsume(Constants.AGENT_INBOX_QUEUE, false, cb, tag -> {
        });
        System.out.printf("[Agent %s] listening on %s%n", agentName, Constants.AGENT_INBOX_QUEUE);
    }

    // message handling

    private void handleClientMessage(WireMessage msg) throws IOException {
        switch (msg.type()) {
            case REQUEST_BUILDINGS -> handleRequestBuildings(msg);
            case BOOK_ROOM -> handleBookRoom(msg);
            case CONFIRM_RESERVATION -> handleConfirm(msg);
            case CANCEL_RESERVATION -> handleCancel(msg);
            default -> replyError(msg.sender(), "Unsupported message type: " + msg.type());
        }
    }

    /**
     * Handles building list requests by sending the current known buildings.
     *
     * @param msg the incoming request message
     * @throws IOException if reply fails to send
     */
    private void handleRequestBuildings(WireMessage msg) throws IOException {
        var list = knownBuildings.stream().sorted().toList();
        var reply = new BookingReply(true, null, list.toString());
        WireMessage out = new WireMessage(MessageType.RESPONSE_BUILDINGS, agentName, reply);
        replyToClient(msg.sender(), out);
    }

    /**
     * Handles room booking requests by validating the building and forwarding to it.
     *
     * @param msg the booking request message
     * @throws IOException if forwarding fails
     */
    private void handleBookRoom(WireMessage msg) throws IOException {
        if (!(msg.payload() instanceof BookingRequest req)) {
            replyError(msg.sender(), "Invalid payload for BOOK_ROOM");
            return;
        }
        if (isUnknown(req.building())) {
            replyError(msg.sender(), "Unknown building: " + req.building());
            return;
        }
        forwardToBuilding(req.building(), msg); // building replies directly to client (by sender id)
    }

    /**
     * Handles reservation confirmation requests.
     *
     * @param msg the confirmation request message
     * @throws IOException if forwarding fails
     */
    private void handleConfirm(WireMessage msg) throws IOException {
        if (msg.payload() instanceof BookingRequest req) {
            if (isUnknown(req.building())) {
                replyError(msg.sender(), "Unknown building: " + req.building());
                return;
            }
            forwardToBuilding(req.building(), msg);
        } else {
            replyError(msg.sender(), "Invalid payload for CONFIRM_RESERVATION");
        }
    }

    /**
     * Handles reservation cancellation requests.
     *
     * @param msg the cancellation request message
     * @throws IOException if forwarding fails
     */

    private void handleCancel(WireMessage msg) throws IOException {
        if (msg.payload() instanceof BookingRequest r) {
            if (isUnknown(r.building())) {
                replyError(msg.sender(), "Unknown building: " + r.building());
                return;
            }
            forwardToBuilding(r.building(), msg);
        } else if (msg.payload() instanceof String) {
            replyError(msg.sender(), "Cancel needs building + reservationNumber (BookingRequest), not just the id");
        } else {
            replyError(msg.sender(), "Invalid payload for CANCEL_RESERVATION");
        }
    }

    // helpers

    /**
     * Checks if a building is unknown to the agent.
     *
     * @param building the building name to check
     * @return true if the building is unknown, false otherwise
     */
    private boolean isUnknown(String building) {
        return building == null || !knownBuildings.contains(building);
    }

    /**
     * Forwards a message to a specific building using the direct exchange.
     * Preserves the original sender so the building can reply directly to the client.
     *
     * @param buildingName the target building name
     * @param original     the original message to forward
     * @throws IOException if publishing fails
     */
    private void forwardToBuilding(String buildingName, WireMessage original) throws IOException {
        // keep original sender (clientId) so Building can reply directly to the client
        WireMessage forwarded = original;
        byte[] body = MessageSerializer.serialize(forwarded);
        String rk = Constants.RK_BUILDING_PREFIX + buildingName;
        // Make message persistent for fault tolerance
        channel.basicPublish(Constants.BUILDING_DIRECT_EXCHANGE, rk, MessageProperties.PERSISTENT_BASIC, body);
        System.out.printf("[Agent %s] -> [%s] %s%n", agentName, rk, forwarded.type());
    }


    /**
     * Sends a reply message to a specific client's private queue.
     *
     * @param clientId the ID of the client to reply to
     * @param reply    the reply message to send
     * @throws IOException if publishing fails
     */
    private void replyToClient(String clientId, WireMessage reply) throws IOException {
        String q = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(q, false, false, true, null);
        byte[] body = MessageSerializer.serialize(reply);
        channel.basicPublish("", q, null, body);
        System.out.printf("[Agent %s] -> [client %s] %s%n", agentName, clientId, reply.type());
    }

    /**
     * Sends an error message to a client.
     *
     * @param clientId the ID of the client to notify
     * @param message  the error message to send
     * @throws IOException if publishing fails
     */
    private void replyError(String clientId, String message) throws IOException {
        WireMessage err = new WireMessage(MessageType.ERROR, agentName, message);
        replyToClient(clientId, err);
    }

}
