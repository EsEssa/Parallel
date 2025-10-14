package main.building;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import main.config.AppConfig;
import main.config.Constants;
import main.domain.*;
import main.util.MessageSerializer;
import main.util.RabbitMQConfig;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * BuildingService is a standalone "Building" actor/process.
 * It owns availability and reservations for a single building instance.
 *
 * Responsibilities:
 *  - Announce itself on a fanout exchange so agents can discover it.
 *  - Consume building-specific commands from cr.building.direct (rk=building.<name>).
 *  - Implement the hold -> confirm/cancel lifecycle.
 *  - Reply to clients on their private queue (cr.client.<clientId>).
 */
public class BuildingService {

    private final String buildingName;
    private final int capacityPerDay; // simplistic capacity model (rooms per day)

    private Connection connection;
    private Channel channel;
    private java.util.concurrent.ScheduledExecutorService announcer;

    // == Authoritative state ==
    // Reservations by id
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, ReservationStatus> status = new ConcurrentHashMap<>();
    // Total rooms booked per date (for availability check)
    private final Map<LocalDate, Integer> bookedPerDay = new ConcurrentHashMap<>();
    private final boolean verbose = false; // disable spam

    /**
     * Creates a new building service with the specified name and capacity.
     *
     * @param buildingName the unique name of this building
     * @param capacityPerDay the maximum number of rooms available per day
     */
    public BuildingService(String buildingName, int capacityPerDay) {
        this.buildingName = Objects.requireNonNull(buildingName);
        this.capacityPerDay = capacityPerDay;
    }

    /**
     * Starts the building service by connecting to RabbitMQ, setting up queues,
     * and beginning to announce availability and handle reservation requests.
     *
     * @throws IOException if RabbitMQ connection fails
     * @throws TimeoutException if connection times out
     */    public void start() throws IOException, TimeoutException {
        connection = RabbitMQConfig.createConnection(
                AppConfig.getRabbitHost(), AppConfig.getRabbitUser(), AppConfig.getRabbitPass());
        channel = connection.createChannel();

        declareTopology();
        announce(); // initial
        startPeriodicAnnounce();
        subscribeInbox();

        System.out.printf("[Building %s] up. Capacity/day=%d%n", buildingName, capacityPerDay);
    }

    /**
     * Periodically announces building availability to rental agents
     */
    private void startPeriodicAnnounce() {
        announcer = Executors.newSingleThreadScheduledExecutor();
        announcer.scheduleAtFixedRate(() -> {
            try {
                channel.basicPublish(Constants.BUILDINGS_FANOUT_EXCHANGE, "", null, buildingName.getBytes());
                if (verbose) {
                    System.out.printf("[Building %s] re-announced%n", buildingName);
                }
            } catch (Exception ignored) {}
        }, 5, 10, TimeUnit.SECONDS);
    }


    /**
     * Stops the building service and cleans up resources.
     *
     * @throws IOException if closing connections fails
     * @throws TimeoutException if closing times out
     */
    public void stop() throws IOException, java.util.concurrent.TimeoutException {
        if (announcer != null) announcer.shutdownNow();  // stop scheduler
        if (channel != null) channel.close();
        if (connection != null) connection.close();
        System.out.printf("[Building %s] down.%n", buildingName);
    }



    // topology

    /**
     * Declares the necessary exchanges and queues for this building.
     *
     * @throws IOException if topology setup fails
     */
    private void declareTopology() throws IOException {
        // Fanout for building announcements
        channel.exchangeDeclare(Constants.BUILDINGS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, false);

        // Direct exchange for targeted building commands
        channel.exchangeDeclare(Constants.BUILDING_DIRECT_EXCHANGE, BuiltinExchangeType.DIRECT, false);

        // Declare this building's inbox and bind with routing key "building.<name>"
        String inbox = buildingInboxQueue();
        channel.queueDeclare(inbox, false, false, false, null);
        String rk = Constants.RK_BUILDING_PREFIX + buildingName;
        channel.queueBind(inbox, Constants.BUILDING_DIRECT_EXCHANGE, rk);
    }

    /**
     * Sends an announcement to make agents aware of this building.
     *
     * @throws IOException if announcement fails
     */
    private void announce() throws IOException {
        // Broadcast building name so RentalAgents discover/maintain registry
        channel.basicPublish(Constants.BUILDINGS_FANOUT_EXCHANGE, "", null, buildingName.getBytes());
        System.out.printf("[Building %s] announced on %s%n", buildingName, Constants.BUILDINGS_FANOUT_EXCHANGE);
    }

    /**
     * Gets the name of this building's inbox queue.
     *
     * @return the building-specific queue name
     */
    private String buildingInboxQueue() {
        return "cr.building." + buildingName + ".inbox";
    }

    /**
     * Subscribes to this building's inbox to handle incoming messages.
     *
     * @throws IOException if subscription fails
     */
    private void subscribeInbox() throws IOException {
        DeliverCallback cb = (tag, delivery) -> {
            WireMessage msg = MessageSerializer.deserialize(delivery.getBody());
            try {
                handle(msg);
            } catch (Exception e) {
                System.err.printf("[Building %s] error: %s%n", buildingName, e.getMessage());
                // Best-effort error back to client
                String clientId = extractClientId(msg);
                if (clientId != null) replyError(clientId, "Internal error at building: " + buildingName);
            }
        };
        channel.basicConsume(buildingInboxQueue(), true, cb, tag -> {});
        System.out.printf("[Building %s] listening on %s%n", buildingName, buildingInboxQueue());
    }

    // message handling

    /**
     * Routes incoming messages to the appropriate handler based on message type.
     *
     * @param msg the incoming message to handle
     * @throws IOException if message processing fails
     */
    private void handle(WireMessage msg) throws IOException {
        switch (msg.type()) {
            case BOOK_ROOM -> onBook(msg);
            case CONFIRM_RESERVATION -> onConfirm(msg);
            case CANCEL_RESERVATION -> onCancel(msg);
            default -> {
                String clientId = extractClientId(msg);
                if (clientId != null) replyError(clientId, "Unsupported at building: " + msg.type());
            }
        }
    }

    /**
     * Handles room booking requests by checking availability and creating reservations.
     *
     * @param msg the booking request message
     * @throws IOException if reply fails to send
     */
    private void onBook(WireMessage msg) throws IOException {
        String clientId = requireClientId(msg);
        if (!(msg.payload() instanceof BookingRequest req)) {
            replyError(clientId, "Invalid payload for BOOK_ROOM");
            return;
        }
        if (!buildingName.equals(req.building())) {
            replyError(clientId, "Wrong building. Expected " + buildingName + " but got " + req.building());
            return;
        }
        if (req.rooms() == null || req.date() == null || req.hours() == null) {
            replyError(clientId, "Missing fields for BOOK_ROOM (need rooms, date, hours)");
            return;
        }
        if (req.rooms() <= 0 || req.hours() <= 0) {
            replyError(clientId, "rooms and hours must be > 0");
            return;
        }

        // Prepare the reservation object (id, timestamps, etc.)
        Reservation r = new Reservation(req.building(), req.rooms(), req.date(), req.hours());

        // atomic capacity check + update
        try {
            bookedPerDay.compute(req.date(), (d, used) -> {
                int current = (used == null ? 0 : used);
                // If adding these rooms exceeds capacity, abort
                if (current + req.rooms() > capacityPerDay) {
                    throw OverCapacity.INSTANCE;
                }
                return current + req.rooms();
            });
        } catch (RuntimeException e) {
            // handle over-capacity "no availability"
            if (e == OverCapacity.INSTANCE) {
                reply(clientId, MessageType.BOOK_ROOM,
                        new BookingReply(false, null,
                                "No availability on " + req.date() + " (requested " + req.rooms() +
                                        ", capacity " + capacityPerDay + ")"));
                return;
            }
            throw e;
        }

        // store reservation
        reservations.put(r.id, r);
        status.put(r.id, ReservationStatus.PENDING);

        reply(clientId, MessageType.BOOK_ROOM,
                new BookingReply(true, r.id, "Provisional hold created; please confirm"));

        System.out.printf("[Building %s] PENDING %s for %s (rooms=%d, date=%s)%n",
                buildingName, r.id, clientId, r.rooms, r.date);
    }

    /**
     * Handles reservation confirmation requests.
     *
     * @param msg the confirmation request message
     * @throws IOException if reply fails to send
     */
    private void onConfirm(WireMessage msg) throws IOException {
        String clientId = requireClientId(msg);

        if (!(msg.payload() instanceof BookingRequest req)) {
            replyError(clientId, "Invalid payload for CONFIRM_RESERVATION");
            return;
        }
        String reservationId = req.reservationNumber();
        if (reservationId == null || reservationId.isBlank()) {
            replyError(clientId, "Missing reservation number for confirm");
            return;
        }

        Reservation r = reservations.get(reservationId);
        if (r == null) {
            replyError(clientId, "Unknown reservation: " + reservationId);
            return;
        }

        // Idempotent confirm
        ReservationStatus s = status.getOrDefault(reservationId, ReservationStatus.PENDING);
        if (s == ReservationStatus.CONFIRMED) {
            reply(clientId, MessageType.CONFIRM_RESERVATION,
                    new BookingReply(true, reservationId, "Already confirmed"));
            return;
        }
        if (s == ReservationStatus.CANCELED) {
            replyError(clientId, "Reservation already canceled: " + reservationId);
            return;
        }

        status.put(reservationId, ReservationStatus.CONFIRMED);
        reply(clientId, MessageType.CONFIRM_RESERVATION,
                new BookingReply(true, reservationId, "Confirmed"));
        System.out.printf("[Building %s] CONFIRMED %s for %s%n", buildingName, reservationId, clientId);
    }

    /**
     * Handles reservation cancellation requests.
     *
     * @param msg the cancellation request message
     * @throws IOException if reply fails to send
     */
    private void onCancel(WireMessage msg) throws IOException {
        String clientId = requireClientId(msg);

        if (!(msg.payload() instanceof BookingRequest req)) {
            replyError(clientId, "Invalid payload for CANCEL_RESERVATION");
            return;
        }
        String reservationId = req.reservationNumber();
        if (reservationId == null || reservationId.isBlank()) {
            replyError(clientId, "Missing reservation number for cancel");
            return;
        }

        Reservation r = reservations.get(reservationId);
        if (r == null) {
            // Idempotent cancellation: "not found" is treated as safe no-op (or return false)
            reply(clientId, MessageType.CANCEL_RESERVATION,
                    new BookingReply(false, reservationId, "Not found (already canceled or never existed)"));
            return;
        }

        ReservationStatus s = status.getOrDefault(reservationId, ReservationStatus.PENDING);
        if (s == ReservationStatus.CANCELED) {
            reply(clientId, MessageType.CANCEL_RESERVATION,
                    new BookingReply(true, reservationId, "Already canceled"));
            return;
        }

        status.put(reservationId, ReservationStatus.CANCELED);

        // Free capacity only if canceling a pending/confirmed that was counted
        bookedPerDay.compute(r.date, (d, used) -> used == null ? 0 : Math.max(0, used - r.rooms));

        reply(clientId, MessageType.CANCEL_RESERVATION,
                new BookingReply(true, reservationId, "Canceled"));
        System.out.printf("[Building %s] CANCELED %s for %s%n", buildingName, reservationId, clientId);
    }

    // reply & helpers

    /**
     * Sends a reply message to a specific client's private queue.
     *
     * @param clientId the ID of the client to reply to
     * @param type the type of the reply message
     * @param payload the payload of the reply message
     * @throws IOException if publishing fails
     */
    private void reply(String clientId, MessageType type, BookingReply payload) throws IOException {
        WireMessage out = new WireMessage(type, buildingName, payload);
        String q = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(q, false, false, true, null);
        channel.basicPublish("", q, null, MessageSerializer.serialize(out));
        System.out.printf("[Building %s] -> client %s : %s(%s)%n",
                buildingName, clientId, type, payload.reservationNumber());
    }

    /**
     * Sends an error message to a specific client's private queue.
     *
     * @param clientId the ID of the client to reply to
     * @param message the error message to send
     * @throws IOException if publishing fails
     */
    private void replyError(String clientId, String message) throws IOException {
        WireMessage err = new WireMessage(MessageType.ERROR, buildingName, message);
        String q = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(q, false, false, true, null);
        channel.basicPublish("", q, null, MessageSerializer.serialize(err));
        System.out.printf("[Building %s] -> client %s : ERROR(%s)%n", buildingName, clientId, message);
    }

    /**
     * Extracts the client ID from a message.
     * Assumes the sender field contains the original client ID.
     *
     * @param msg the message to extract from
     * @return the client ID, or null if not available
     */
    private String extractClientId(WireMessage msg) {
        return msg.sender(); // expected to be the original client id
    }

    /**
     * Extracts the client ID from a message, ensuring it is not null or blank.
     *
     * @param msg the message to extract from
     * @return the client ID, or throws an exception if not available
     */
    private String requireClientId(WireMessage msg) {
        String id = extractClientId(msg);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Client id missing on message (sender not set to clientId)");
        }
        return id;
    }

    /**
     * Accepts either:
     *  - String reservationId, or
     *  - ReservationRequest (if you have that type), or
     *  - Any wrapper you may use that contains a reservation id.
     *
     * @param payload the payload to extract from
     * @return an Optional containing the reservation ID if found
     */
    private Optional<String> extractReservationId(Object payload) {
        if (payload instanceof String s && !s.isBlank()) {
            return Optional.of(s);
        }
        try {
            Class<?> cls = payload.getClass();
            var m = cls.getMethod("reservationNumber");
            Object val = m.invoke(payload);
            if (val instanceof String s && !s.isBlank()) return Optional.of(s);
        } catch (Throwable ignored) {}
        return Optional.empty();
    }

    /**
     * Internal exception for handling over-capacity scenarios efficiently
     */
    private static final class OverCapacity extends RuntimeException {
        static final OverCapacity INSTANCE = new OverCapacity();
        private OverCapacity() { super(null, null, false, false); } // no stacktrace for speed
    }
}
