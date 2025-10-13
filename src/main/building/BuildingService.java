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

    public BuildingService(String buildingName, int capacityPerDay) {
        this.buildingName = Objects.requireNonNull(buildingName);
        this.capacityPerDay = capacityPerDay;
    }

    /** Boot the building: connect, announce, and start consuming commands. */
    public void start() throws IOException, TimeoutException {
        connection = RabbitMQConfig.createConnection(
                AppConfig.getRabbitHost(), AppConfig.getRabbitUser(), AppConfig.getRabbitPass());
        channel = connection.createChannel();

        declareTopology();
        announce(); // initial
        startPeriodicAnnounce();   // <--- add this
        subscribeInbox();

        System.out.printf("[Building %s] up. Capacity/day=%d%n", buildingName, capacityPerDay);
    }

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


    public void stop() throws IOException, java.util.concurrent.TimeoutException {
        if (announcer != null) announcer.shutdownNow();  // <--- stop scheduler
        if (channel != null) channel.close();
        if (connection != null) connection.close();
        System.out.printf("[Building %s] down.%n", buildingName);
    }



    // ----------------------------------------------------------------
    // Topology
    // ----------------------------------------------------------------

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

    private void announce() throws IOException {
        // Broadcast building name so RentalAgents discover/maintain registry
        channel.basicPublish(Constants.BUILDINGS_FANOUT_EXCHANGE, "", null, buildingName.getBytes());
        System.out.printf("[Building %s] announced on %s%n", buildingName, Constants.BUILDINGS_FANOUT_EXCHANGE);
    }

    private String buildingInboxQueue() {
        return "cr.building." + buildingName + ".inbox";
    }

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

    // ----------------------------------------------------------------
    // Message handling
    // ----------------------------------------------------------------

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

        // === Atomic capacity check + update ===
        try {
            bookedPerDay.compute(req.date(), (d, used) -> {
                int current = (used == null ? 0 : used);
                // If adding these rooms exceeds capacity, abort atomically
                if (current + req.rooms() > capacityPerDay) {
                    throw OverCapacity.INSTANCE;
                }
                return current + req.rooms();
            });
        } catch (RuntimeException e) {
            // Our marker exception means "no availability"
            if (e == OverCapacity.INSTANCE) {
                reply(clientId, MessageType.BOOK_ROOM,
                        new BookingReply(false, null,
                                "No availability on " + req.date() + " (requested " + req.rooms() +
                                        ", capacity " + capacityPerDay + ")"));
                return;
            }
            // Anything else is a real error
            throw e;
        }

        // At this point capacity for the date has been incremented atomically.
        // Record the reservation in authoritative state.
        reservations.put(r.id, r);
        status.put(r.id, ReservationStatus.PENDING);

        reply(clientId, MessageType.BOOK_ROOM,
                new BookingReply(true, r.id, "Provisional hold created; please confirm"));

        System.out.printf("[Building %s] PENDING %s for %s (rooms=%d, date=%s)%n",
                buildingName, r.id, clientId, r.rooms, r.date);
    }

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

    // ----------------------------------------------------------------
    // Reply & helpers
    // ----------------------------------------------------------------

    private void reply(String clientId, MessageType type, BookingReply payload) throws IOException {
        WireMessage out = new WireMessage(type, buildingName, payload);
        String q = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(q, false, false, true, null);
        channel.basicPublish("", q, null, MessageSerializer.serialize(out));
        System.out.printf("[Building %s] -> client %s : %s(%s)%n",
                buildingName, clientId, type, payload.reservationNumber());
    }

    private void replyError(String clientId, String message) throws IOException {
        WireMessage err = new WireMessage(MessageType.ERROR, buildingName, message);
        String q = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(q, false, false, true, null);
        channel.basicPublish("", q, null, MessageSerializer.serialize(err));
        System.out.printf("[Building %s] -> client %s : ERROR(%s)%n", buildingName, clientId, message);
    }

    /**
     * Extracts the clientId. Assumes RentalAgent preserved msg.sender as the original clientId.
     * If your agent wraps/changes this, adapt this method to pull clientId from the wrapper payload.
     */
    private String extractClientId(WireMessage msg) {
        return msg.sender(); // expected to be the original client id
    }

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
     */
    private Optional<String> extractReservationId(Object payload) {
        if (payload instanceof String s && !s.isBlank()) {
            return Optional.of(s);
        }
        try {
            // If you have your own ReservationRequest DTO: adapt package/name here.
            // Example:
            // if (payload instanceof ReservationRequest rr) return Optional.ofNullable(rr.reservationNumber());
            Class<?> cls = payload.getClass();
            var m = cls.getMethod("reservationNumber");
            Object val = m.invoke(payload);
            if (val instanceof String s && !s.isBlank()) return Optional.of(s);
        } catch (Throwable ignored) {}
        return Optional.empty();
    }

    private static final class OverCapacity extends RuntimeException {
        static final OverCapacity INSTANCE = new OverCapacity();
        private OverCapacity() { super(null, null, false, false); } // no stacktrace for speed
    }
}
