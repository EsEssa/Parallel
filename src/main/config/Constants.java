package main.config;

public final class Constants {
    private Constants() {}

    // Queues
    public static final String AGENT_INBOX_QUEUE     = "cr.agents.inbox";      // clients -> agents (shared)
    public static final String CLIENT_QUEUE_PREFIX   = "cr.client.";           // agent/building -> specific client (reply)
    // Example: cr.client.Client1

    // Exchanges
    public static final String BUILDINGS_FANOUT_EXCHANGE = "cr.buildings.fanout"; // buildings announce themselves
    public static final String BUILDING_DIRECT_EXCHANGE  = "cr.building.direct";  // agent -> specific building

    // Routing keys
    public static final String RK_BUILDING_PREFIX = "building."; // e.g. building.BuildingA

    // Derived name helpers
    public static String clientReplyQueue(String clientId) {
        return CLIENT_QUEUE_PREFIX + clientId;
    }
    public static String buildingInboxQueue(String buildingName) {
        return "cr.building." + buildingName + ".inbox";
    }
    public static String buildingRoutingKey(String buildingName) {
        return RK_BUILDING_PREFIX + buildingName;
    }
}
