package main.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * BookingAPI defines the operations that a ConferenceRent client can perform
 * through the distributed system.
 *
 * Implemented by ClientAgent.
 */
public interface BookingAPI {

    /**
     * Requests the list of all available buildings.
     * The result should come from the RentalAgent, which aggregates building announcements.
     *
     * @return a list of building names known to the system
     * @throws IOException if the request fails
     */
    List<String> requestBuildingList() throws IOException;

    /**
     * Books one or more rooms in a given building for a specific date and duration.
     *
     * @param building name of the building
     * @param rooms    number of rooms requested
     * @param date     booking date
     * @param hours    duration of the booking
     * @throws IOException if message sending fails
     */
    void bookRoom(String building, int rooms, LocalDate date, int hours) throws IOException;

    /**
     * Confirms a previously created reservation using its reservation number.
     *
     * @param building           building where the reservation was made
     * @param reservationNumber  reservation identifier
     * @throws IOException if the confirmation fails
     */
    void confirmReservation(String building, String reservationNumber) throws IOException;

    /**
     * Cancels an existing reservation.
     *
     * @param building           building where the reservation exists
     * @param reservationNumber  reservation identifier
     * @throws IOException if the cancellation fails
     */
    void cancelReservation(String building, String reservationNumber) throws IOException;
}