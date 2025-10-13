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
    private boolean verbose = false; // set true if you want periodic "still alive" logs
    private static final long HEARTBEAT_LOG_MS = 60_000; // log at most once per minute per building


    public RentalAgent(String agentName) {
        this.agentName = agentName;
    }

    /** Start the agent: connect, subscribe to discovery and client inbox. */
    public void start() throws IOException, TimeoutException {
        connection = RabbitMQConfig.createConnection(
                AppConfig.getRabbitHost(), AppConfig.getRabbitUser(), AppConfig.getRabbitPass());
        channel = connection.createChannel();

        declareTopology(channel);

        subscribeDiscovery(channel);      // learn buildings via fanout
        subscribeClientInbox(channel);    // handle client requests (round-robin)

        System.out.printf("[Agent %s] up. Known buildings: %s%n", agentName, knownBuildings);
    }

    /** Stop the agent cleanly. */
    public void stop() throws IOException, TimeoutException {
        if (channel != null) channel.close();
        if (connection != null) connection.close();
        System.out.printf("[Agent %s] down.%n", agentName);
    }

    // ---------- Topology ----------

    private void declareTopology(Channel ch) throws IOException {
        // Shared client->agent queue (multiple agents consume round-robin)
        ch.queueDeclare(Constants.AGENT_INBOX_QUEUE, false, false, false, null);

        // Fanout for building announcements
        ch.exchangeDeclare(Constants.BUILDINGS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, false);

        // Direct exchange for building-specific commands
        ch.exchangeDeclare(Constants.BUILDING_DIRECT_EXCHANGE, BuiltinExchangeType.DIRECT, false);
    }

    // ---------- Subscriptions ----------

    /** Subscribe to building announcements (fanout). */
    private void subscribeDiscovery(Channel ch) throws IOException {
        String tmpQueue = ch.queueDeclare().getQueue(); // auto-delete, exclusive
        ch.queueBind(tmpQueue, Constants.BUILDINGS_FANOUT_EXCHANGE, "");

        DeliverCallback cb = (tag, delivery) -> {
            String buildingName = new String(delivery.getBody());
            if (buildingName == null || buildingName.isBlank()) return;

            long now = System.currentTimeMillis();
            Long prev = buildingLastSeen.put(buildingName, now);

            // First time we see this building
            if (knownBuildings.add(buildingName) || prev == null) {
                System.out.printf("[Agent %s] discovered building: %s%n", agentName, buildingName);
                return;
            }

            // Already known: suppress spam. Optionally report a heartbeat only every N ms.
            if (verbose && (now - prev) >= HEARTBEAT_LOG_MS) {
                System.out.printf("[Agent %s] heartbeat from %s%n", agentName, buildingName);
            }
        };

        ch.basicConsume(tmpQueue, true, cb, tag -> {});
    }

    /** Subscribe to the shared client -> agents inbox (round-robin). */
    private void subscribeClientInbox(Channel ch) throws IOException {
        DeliverCallback cb = (tag, delivery) -> {
            WireMessage msg = MessageSerializer.deserialize(delivery.getBody());
            try {
                handleClientMessage(msg);
            } catch (Exception e) {
                System.err.printf("[Agent %s] error handling %s: %s%n", agentName, msg.type(), e.getMessage());
                // best-effort error back to client
                safeErrorReply(msg.sender(), "Internal error at agent");
            }
        };
        ch.basicConsume(Constants.AGENT_INBOX_QUEUE, true, cb, tag -> {});
        System.out.printf("[Agent %s] listening on %s%n", agentName, Constants.AGENT_INBOX_QUEUE);
    }

    // ---------- Message handling ----------

    private void handleClientMessage(WireMessage msg) throws IOException {
        switch (msg.type()) {
            case REQUEST_BUILDINGS -> handleRequestBuildings(msg);
            case BOOK_ROOM -> handleBookRoom(msg);
            case CONFIRM_RESERVATION -> handleConfirm(msg);
            case CANCEL_RESERVATION -> handleCancel(msg);
            default -> replyError(msg.sender(), "Unsupported message type: " + msg.type());
        }
    }

    private void handleRequestBuildings(WireMessage msg) throws IOException {
        var list = knownBuildings.stream().sorted().toList();
        var reply = new BookingReply(true, null, list.toString()); // or define a dedicated payload type
        WireMessage out = new WireMessage(MessageType.RESPONSE_BUILDINGS, agentName, reply);
        replyToClient(msg.sender(), out);
    }

    private void handleBookRoom(WireMessage msg) throws IOException {
        if (!(msg.payload() instanceof BookingRequest req)) {
            replyError(msg.sender(), "Invalid payload for BOOK_ROOM");
            return;
        }
        if (!isKnown(req.building())) {
            replyError(msg.sender(), "Unknown building: " + req.building());
            return;
        }
        forwardToBuilding(req.building(), msg); // forward as-is; building replies directly to client (by sender id)
    }

    private void handleConfirm(WireMessage msg) throws IOException {
        if (msg.payload() instanceof BookingRequest req) {
            if (!isKnown(req.building())) {
                replyError(msg.sender(), "Unknown building: " + req.building());
                return;
            }
            forwardToBuilding(req.building(), msg);
        } else {
            replyError(msg.sender(), "Invalid payload for CONFIRM_RESERVATION");
        }
    }

    private void handleCancel(WireMessage msg) throws IOException {
        if (msg.payload() instanceof BookingRequest r) {
            if (!isKnown(r.building())) {
                replyError(msg.sender(), "Unknown building: " + r.building());
                return;
            }
            forwardToBuilding(r.building(), msg);
        } else if (msg.payload() instanceof String onlyReservationNumber) {
            replyError(msg.sender(),
                    "Cancel needs building + reservationNumber (ReservationRequest), not just the id");
        } else {
            replyError(msg.sender(), "Invalid payload for CANCEL_RESERVATION");
        }
    }

    // ---------- Helpers ----------

    private boolean isKnown(String building) {
        return building != null && knownBuildings.contains(building);
    }

    /** Publish to a building via the direct exchange using rk=building.<name>. */
    private void forwardToBuilding(String buildingName, WireMessage original) throws IOException {
        // keep original sender (clientId) so Building can reply directly to the client
        WireMessage forwarded = original;
        byte[] body = MessageSerializer.serialize(forwarded);
        String rk = Constants.RK_BUILDING_PREFIX + buildingName;
        channel.basicPublish(Constants.BUILDING_DIRECT_EXCHANGE, rk, null, body);
        System.out.printf("[Agent %s] -> [%s] %s%n", agentName, rk, forwarded.type());
    }


    /** Reply to the client's private queue: cr.client.<clientId>. */
    private void replyToClient(String clientId, WireMessage reply) throws IOException {
        String q = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(q, false, false, true, null);
        byte[] body = MessageSerializer.serialize(reply);
        channel.basicPublish("", q, null, body);
        System.out.printf("[Agent %s] -> [client %s] %s%n", agentName, clientId, reply.type());
    }

    private void replyError(String clientId, String message) throws IOException {
        WireMessage err = new WireMessage(MessageType.ERROR, agentName, message);
        replyToClient(clientId, err);
    }

    private void safeErrorReply(String clientId, String message) {
        try {
            replyError(clientId, message);
        } catch (IOException ignored) { }
    }
}
