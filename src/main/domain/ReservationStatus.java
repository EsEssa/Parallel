package main.domain;

/**
 * Represents the possible states of a reservation in the booking system.
 * Reservations progress through these states during their lifecycle.
 */
public enum ReservationStatus {
    /**
     * Initial state after booking - reservation is held but not finalized.
     * Requires confirmation to become active.
     */
    PENDING,

    /**
     * Reservation has been confirmed and is active.
     * The rooms are officially reserved for the specified date and time.
     */
    CONFIRMED,

    /**
     * Reservation has been canceled, either by the client or due to timeout.
     * Capacity is freed up for new bookings.
     */
    CANCELED
}