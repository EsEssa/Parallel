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
        boolean success,
        String reservationNumber,
        String message
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}