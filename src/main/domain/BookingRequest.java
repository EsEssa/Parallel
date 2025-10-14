package main.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Data transfer object for all booking-related operations.
 * Supports both room booking requests and reservation management operations
 * using different constructor signatures for each use case.
 */
public class BookingRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String building;
    private final Integer rooms;
    private final LocalDate date;
    private final Integer hours;
    private final String reservationNumber;

    /**
     * Constructor for room booking requests.
     * Used for BOOK_ROOM message type.
     *
     * @param building the building name where booking is requested
     * @param rooms the number of rooms to book
     * @param date the date of the booking
     * @param hours the duration of the booking in hours
     */
    public BookingRequest(String building, int rooms, LocalDate date, int hours) {
        this.building = building;
        this.rooms = rooms;
        this.date = date;
        this.hours = hours;
        this.reservationNumber = null;
    }

    /**
     * Constructor for reservation management operations.
     * Used for CONFIRM_RESERVATION and CANCEL_RESERVATION message types.
     *
     * @param building the building name where reservation exists
     * @param reservationNumber the unique identifier of the reservation
     */
    public BookingRequest(String building, String reservationNumber) {
        this.building = building;
        this.rooms = null;
        this.date = null;
        this.hours = null;
        this.reservationNumber = reservationNumber;
    }

    /**
     * Gets the building name.
     *
     * @return the building identifier
     */
    public String building() {
        return building;
    }

    /**
     * Gets the number of rooms requested.
     *
     * @return the room count, or null for non-booking operations
     */
    public Integer rooms() {
        return rooms;
    }

    /**
     * Gets the booking date.
     *
     * @return the date of booking, or null for non-booking operations
     */
    public LocalDate date() {
        return date;
    }

    /**
     * Gets the booking duration.
     *
     * @return the duration in hours, or null for non-booking operations
     */
    public Integer hours() {
        return hours;
    }

    /**
     * Gets the reservation identifier.
     *
     * @return the reservation number, or null for booking operations
     */
    public String reservationNumber() {
        return reservationNumber;
    }

    /**
     * Returns a string representation of the booking request.
     * Format varies based on whether it's a booking or management operation.
     *
     * @return formatted string showing relevant fields
     */
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