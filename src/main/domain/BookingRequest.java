package main.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * A single DTO for all booking-related operations.
 *
 * - For BOOK_ROOM: fill in building, rooms, date, hours.
 * - For CONFIRM_RESERVATION / CANCEL_RESERVATION: fill in building + reservationNumber only.
 */
public class BookingRequest implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final String building;
    private final Integer rooms;
    private final LocalDate date;
    private final Integer hours;
    private final String reservationNumber; // used for confirm/cancel

    /** Constructor for BOOK_ROOM. */
    public BookingRequest(String building, int rooms, LocalDate date, int hours) {
        this.building = building;
        this.rooms = rooms;
        this.date = date;
        this.hours = hours;
        this.reservationNumber = null;
    }

    /** Constructor for CONFIRM_RESERVATION / CANCEL_RESERVATION. */
    public BookingRequest(String building, String reservationNumber) {
        this.building = building;
        this.rooms = null;
        this.date = null;
        this.hours = null;
        this.reservationNumber = reservationNumber;
    }

    public String building() { return building; }
    public Integer rooms() { return rooms; }
    public LocalDate date() { return date; }
    public Integer hours() { return hours; }
    public String reservationNumber() { return reservationNumber; }

    @Override
    public String toString() {
        if (reservationNumber != null) {
            return "BookingRequest{building='%s', reservationNumber='%s'}"
                    .formatted(building, reservationNumber);
        } else {
            return "BookingRequest{building='%s', rooms=%d, date=%s, hours=%d}"
                    .formatted(building, rooms, date, hours);
        }
    }
}