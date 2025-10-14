package main.agent;

/**
 * Main entry point for starting a Rental Agent instance.
 * This class handles the startup of a rental agent process that
 * connects to RabbitMQ and begins processing client requests.
 */
public class RentalAgentMain {

    /**
     * Starts a rental agent with the specified name or a default name.
     * The agent will run until the process is terminated.
     *
     * @param args command line arguments where the first argument
     *             can be used to specify the agent name
     * @throws Exception if the agent fails to start or encounters
     *                   an error during operation
     */
    public static void main(String[] args) throws Exception {
        String agentName = args.length > 0 ? args[0] : "Agent1";
        RentalAgent agent = new RentalAgent(agentName);
        agent.start();

        System.out.println("[AgentMain] Running as " + agentName + " (Ctrl+C to stop)");
        Thread.currentThread().join();
    }
}