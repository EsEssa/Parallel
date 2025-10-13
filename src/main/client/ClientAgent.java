package main.client;

import com.rabbitmq.client.*;
import main.api.BookingAPI;
import main.config.AppConfig;
import main.config.Constants;
import main.domain.BookingRequest;
import main.domain.MessageType;
import main.domain.WireMessage;
import main.util.MessageSerializer;
import main.util.RabbitMQConfig;

import java.io.IOException;

import java.time.LocalDate;
import java.util.concurrent.TimeoutException;

public class ClientAgent {

    private final String clientId;
    private Connection connection;
    private Channel channel;
    private String replyQueue;

    public ClientAgent(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Initializes RabbitMQ connection and starts listening for responses.
     */
    public void start() throws IOException, TimeoutException {
        connection = RabbitMQConfig.createConnection(AppConfig.getRabbitHost(), AppConfig.getRabbitUser(), AppConfig.getRabbitPass());
        channel = connection.createChannel();

        // Create reply queue for this client
        replyQueue = Constants.CLIENT_QUEUE_PREFIX + clientId;
        channel.queueDeclare(replyQueue, false, false, true, null);

        // Listen for replies
        listenForReplies();

        System.out.printf("[Client %s] Ready. Listening on %s%n", clientId, replyQueue);
    }

    /**
     * Requests list of available buildings.
     */
    public void requestBuildingList() throws IOException {
        WireMessage msg = new WireMessage(MessageType.REQUEST_BUILDINGS, clientId, null);
        sendToAgents(msg);
    }

    /**
     * Requests to book a conference room.
     */
    public void bookRoom(String building, int rooms, LocalDate date, int hours) throws IOException {
        BookingRequest request = new BookingRequest(building, rooms, date, hours);
        WireMessage msg = new WireMessage(MessageType.BOOK_ROOM, clientId, request);
        sendToAgents(msg);
    }

    public void confirmReservation(String building, String reservationNumber) throws IOException {
        BookingRequest req = new BookingRequest(building, reservationNumber);
        WireMessage msg = new WireMessage(MessageType.CONFIRM_RESERVATION, clientId, req);
        sendToAgents(msg);
    }

    public void cancelReservation(String building, String reservationNumber) throws IOException {
        BookingRequest req = new BookingRequest(building, reservationNumber);
        WireMessage msg = new WireMessage(MessageType.CANCEL_RESERVATION, clientId, req);
        sendToAgents(msg);
    }

    /**
     * Sends a message to the shared Agents inbox.
     */
    private void sendToAgents(WireMessage msg) throws IOException {
        byte[] body = MessageSerializer.serialize(msg);
        channel.queueDeclare(Constants.AGENT_INBOX_QUEUE, false, false, false, null);
        channel.basicPublish("", Constants.AGENT_INBOX_QUEUE, null, body);
        System.out.printf("[Client %s] -> Sent %s%n", clientId, msg.type());
    }

    /**
     * Listens for messages on this client’s private reply queue.
     */
    private void listenForReplies() throws IOException {
        DeliverCallback callback = (consumerTag, delivery) -> {
            WireMessage msg = MessageSerializer.deserialize(delivery.getBody());
            System.out.printf("[Client %s] <- [%s] %s%n", clientId, msg.type(), msg.payload());
        };
        channel.basicConsume(replyQueue, true, callback, consumerTag -> {});
    }

    /**
     * Closes the connection.
     */
    public void stop() throws IOException, TimeoutException {
        if (channel != null) channel.close();
        if (connection != null) connection.close();
        System.out.printf("[Client %s] Disconnected.%n", clientId);
    }
}
