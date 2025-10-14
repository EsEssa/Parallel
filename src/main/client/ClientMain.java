package main.client;

/**
 * Main entry point for starting a Client application.
 * This class handles the startup of a client process that can
 * communicate with the rental system through a ClientAgent.
 */
public class ClientMain {

    /**
     * Starts a client application with the specified identifier.
     * The client will automatically request a building list on startup
     * and remain active to receive responses.
     *
     * @param args command line arguments where the first argument
     *             can be used to specify the client identifier
     * @throws Exception if the client fails to start or encounters
     *                   an error during operation
     */
    public static void main(String[] args) throws Exception {
        // Get client ID from command line or use default
        String clientId = args.length > 0 ? args[0] : "Client1";

        // Create and start the client agent
        ClientAgent client = new ClientAgent(clientId);
        client.start();

        System.out.printf("[ClientMain] Running as %s (Ctrl+C to stop)%n", clientId);

        // Automatically request building list on startup
        client.requestBuildingList();

        // Keep the process running to receive replies
        Thread.currentThread().join();
    }
}