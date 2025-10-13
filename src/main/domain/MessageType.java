package main.domain;

public enum MessageType {
    REQUEST_BUILDINGS,
    RESPONSE_BUILDINGS,

    BOOK_ROOM,              // client -> agent -> building (provisional HOLD)
    CONFIRM_RESERVATION,    // client -> agent -> building (PENDING -> CONFIRMED)
    CANCEL_RESERVATION,     // client -> agent -> building (PENDING/CONFIRMED -> CANCELED)

    ERROR                   // any actor -> client (error message payload = String or DTO)
}
