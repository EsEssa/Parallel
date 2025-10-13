package main.domain;

import java.io.Serial;
import java.io.Serializable;

/** Generic reply for book/confirm/cancel and list buildings (message may contain info). */
public record BookingReply(
        boolean success,
        String reservationNumber,   // may be null for list/errors
        String message              // human-readable, e.g. "Provisional hold created; please confirm"
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}