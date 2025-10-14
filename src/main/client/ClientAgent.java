package main.client;

import com.rabbitmq.client.*;
import main.config.AppConfig;
import main.config.Constants;
import main.domain.BookingRequest;
import main.domain.MessageType;
import main.domain.WireMessage;
import main.util.MessageSerializer;
import main.util.RabbitMQConfig;

import java.io.IOException;

import java.time.LocalDate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Client agent that handles communication with the rental system.
 * Sends booking requests and listens for responses from agents and buildings.
 */
public class ClientAgent {

    private final String clientId;
    private Connection connection;
    private Channel channel;
    private String replyQueue;
    private final BlockingQueue<WireMessage> replyBuffer = new LinkedBlockingQueue<>();

    /**
     * Creates a new client agent with the specified identifier.
     *
     * @param clientId the unique identifier for this client
     */
    public ClientAgent(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Initializes the client by connecting to RabbitMQ and setting up
     * the private reply queue for receiving responses.
     *
     * @throws IOException if RabbitMQ connection fails
     * @throws TimeoutException if connection times out
     */
    public void start() throws IOException, TimeoutException {
        connection = RabbitMQConfig.createConnection(AppConfig.getRabbitHost(), AppConfig.getRabbitUser(), AppConfig.getRabbitPass());
        channel = connection.createChannel();

        // create reply queue for this client
        replyQueue = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(replyQueue, false, false, true, null);

        // listen for replies
        listenForReplies();

        System.out.printf("[Client %s] Ready. Listening on %s%n", clientId, replyQueue);
    }

    /**
     * Requests a list of all available buildings from the rental agents.
     *
     * @throws IOException if the request fails to send
     */
    public void requestBuildingList() throws IOException {
        WireMessage msg = new WireMessage(MessageType.REQUEST_BUILDINGS, clientId, null);
        sendToAgents(msg);
    }

    /**
     * Sends a room booking request for the specified building and date.
     *
     * @param building the name of the building to book in
     * @param rooms the number of rooms to book
     * @param date the date for the booking
     * @param hours the duration of the booking in hours
     * @throws IOException if the request fails to send
     */
    public void bookRoom(String building, int rooms, LocalDate date, int hours) throws IOException {
        BookingRequest request = new BookingRequest(building, rooms, date, hours);
        WireMessage msg = new WireMessage(MessageType.BOOK_ROOM, clientId, request);
        sendToAgents(msg);
    }

    /**
     * Confirms a previously made reservation.
     *
     * @param building the building where the reservation was made
     * @param reservationNumber the unique identifier of the reservation
     * @throws IOException if the request fails to send
     */
    public void confirmReservation(String building, String reservationNumber) throws IOException {
        BookingRequest req = new BookingRequest(building, reservationNumber);
        WireMessage msg = new WireMessage(MessageType.CONFIRM_RESERVATION, clientId, req);
        sendToAgents(msg);
    }

    /**
     * Cancels an existing reservation.
     *
     * @param building the building where the reservation was made
     * @param reservationNumber the unique identifier of the reservation
     * @throws IOException if the request fails to send
     */
    public void cancelReservation(String building, String reservationNumber) throws IOException {
        BookingRequest req = new BookingRequest(building, reservationNumber);
        WireMessage msg = new WireMessage(MessageType.CANCEL_RESERVATION, clientId, req);
        sendToAgents(msg);
    }

    /**
     * Sends a message to the shared agents inbox queue.
     *
     * @param msg the message to send to the rental agents
     * @throws IOException if the message fails to send
     */
    private void sendToAgents(WireMessage msg) throws IOException {
        byte[] body = MessageSerializer.serialize(msg);
        channel.queueDeclare(Constants.AGENT_INBOX_QUEUE, false, false, false, null);
        channel.basicPublish("", Constants.AGENT_INBOX_QUEUE, null, body);
        System.out.printf("[Client %s] -> Sent %s%n", clientId, msg.type());
    }

    /**
     * Starts listening for reply messages on this client's private queue.
     * Incoming messages are added to the reply buffer for processing.
     *
     * @throws IOException if the consumer setup fails
     */
    private void listenForReplies() throws IOException {
        DeliverCallback callback = (consumerTag, delivery) -> {
            WireMessage msg = MessageSerializer.deserialize(delivery.getBody());
            System.out.printf("[Client %s] <- [%s] %s%n", clientId, msg.type(), msg.payload());
            replyBuffer.offer(msg);
        };
        channel.basicConsume(replyQueue, true, callback, consumerTag -> {});
    }

    /**
     * Closes the RabbitMQ connection and cleans up resources.
     *
     * @throws IOException if closing connections fails
     * @throws TimeoutException if closing times out
     */
    public void stop() throws IOException, TimeoutException {
        if (channel != null) channel.close();
        if (connection != null) connection.close();
        System.out.printf("[Client %s] Disconnected.%n", clientId);
    }

    // Helper for testing

    /**
     * Waits for a reply message to arrive within the specified timeout.
     * Useful for testing and synchronous operations.
     *
     * @param timeoutMs the maximum time to wait in milliseconds
     * @return the received message, or null if timeout occurs
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public WireMessage waitForReply(long timeoutMs) throws InterruptedException {
        return replyBuffer.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Clears all pending replies from the buffer.
     * Useful for resetting state between test cases.
     */
    public void clearReplies() { replyBuffer.clear(); }
}
