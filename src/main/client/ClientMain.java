package main.client;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        String clientId = args.length > 0 ? args[0] : "Client1";

        // create and start the client agent
        ClientAgent client = new ClientAgent(clientId);
        client.start();

        System.out.printf("[ClientMain] Running as %s (Ctrl+C to stop)%n", clientId);

        // Optionally: send a simple test request on startup
        client.requestBuildingList();

        // Keep the process alive so it can receive replies
        Thread.currentThread().join();
    }
}
