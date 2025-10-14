package main.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a room reservation in the booking system.
 * Each reservation is uniquely identified and contains details
 * about the building, room count, date, and duration.
 */
public class Reservation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public final String id;
    public final String building;
    public final int rooms;
    public final LocalDate date;
    public final int hours;
    public final Instant createdAt;

    /**
     * Creates a new reservation with automatically generated unique ID.
     *
     * @param building the building name where reservation is made
     * @param rooms the number of rooms to reserve
     * @param date the date of the reservation
     * @param hours the duration in hours
     * @throws NullPointerException if building or date is null
     */
    public Reservation(String building, int rooms, LocalDate date, int hours) {
        this.id = UUID.randomUUID().toString();
        this.building = Objects.requireNonNull(building);
        this.rooms = rooms;
        this.date = Objects.requireNonNull(date);
        this.hours = hours;
        this.createdAt = Instant.now();
    }

    /**
     * Returns a string representation of the reservation.
     *
     * @return formatted string containing reservation details
     */
    @Override
    public String toString() {
        return "Reservation{id='%s', building='%s', rooms=%d, date=%s, hours=%d}"
                .formatted(id, building, rooms, date, hours);
    }
}