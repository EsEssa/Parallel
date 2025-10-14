package main.domain;

/**
 * Defines the types of messages that can be exchanged between system components.
 * Each message type represents a specific operation or response in the booking system.
 */
public enum MessageType {
    /**
     * Client requests a list of available buildings from agents.
     */
    REQUEST_BUILDINGS,

    /**
     * Agents respond with the list of known available buildings.
     */
    RESPONSE_BUILDINGS,

    /**
     * Client requests to book rooms - creates a provisional hold.
     * Flow: client → agent → building
     */
    BOOK_ROOM,

    /**
     * Client confirms a previously made provisional reservation.
     * Flow: client → agent → building
     * Status change: PENDING → CONFIRMED
     */
    CONFIRM_RESERVATION,

    /**
     * Client cancels an existing reservation (either pending or confirmed).
     * Flow: client → agent → building
     * Status change: PENDING/CONFIRMED → CANCELED
     */
    CANCEL_RESERVATION,

    /**
     * Error response sent from any component to the client.
     * Payload typically contains an error message string or error DTO.
     */
    ERROR
}