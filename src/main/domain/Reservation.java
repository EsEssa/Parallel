package main.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Reservation entity owned by a BuildingService instance.
 * Reservation IDs are generated here (UUID) and are unique within the system.
 */
public class Reservation implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public final String id;
    public final String building;
    public final int rooms;
    public final LocalDate date;
    public final int hours;
    public final Instant createdAt;

    public Reservation(String building, int rooms, LocalDate date, int hours) {
        this.id = UUID.randomUUID().toString();
        this.building = Objects.requireNonNull(building);
        this.rooms = rooms;
        this.date = Objects.requireNonNull(date);
        this.hours = hours;
        this.createdAt = Instant.now();
    }

    @Override
    public String toString() {
        return "Reservation{id='%s', building='%s', rooms=%d, date=%s, hours=%d}"
                .formatted(id, building, rooms, date, hours);
    }
}