package main.agent;

public class RentalAgentMain {
    public static void main(String[] args) throws Exception {
        String agentName = args.length > 0 ? args[0] : "Agent1";
        RentalAgent agent = new RentalAgent(agentName);
        agent.start();

        System.out.println("[AgentMain] Running as " + agentName + " (Ctrl+C to stop)");
        Thread.currentThread().join();
    }
}

