package main.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * Generic response for booking operations and system replies.
 * Used for conveying success/failure status along with relevant information
 * back to the client after operations like booking, confirmation, cancellation,
 * or building list requests.
 */
public record BookingReply(
        /**
         * Indicates whether the requested operation was successful.
         */
        boolean success,

        /**
         * The unique reservation identifier when applicable.
         * May be null for operations like building list requests or error responses.
         */
        String reservationNumber,

        /**
         * Human-readable message providing additional context or explanation.
         * Examples: "Provisional hold created; please confirm" or "No availability on requested date"
         */
        String message
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}