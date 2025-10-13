package main.config;


import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final String PROPS_PATH =
            "ressources/rabbitmq.properties";

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(PROPS_PATH)) {
            if (in != null) PROPS.load(in);
            else System.err.println("[AppConfig] Properties not found on classpath: " + PROPS_PATH);
        } catch (Exception e) {
            System.err.println("[AppConfig] Failed to load properties: " + e.getMessage());
        }
    }

    private AppConfig() {}

    public static String getRabbitHost() { return PROPS.getProperty("rabbitmq.host", "localhost"); }
    public static String getRabbitUser() { return PROPS.getProperty("rabbitmq.user", "guest"); }
    public static String getRabbitPass() { return PROPS.getProperty("rabbitmq.pass", "guest"); }

    // Optional: actor ids via properties if you want
    public static String getDefaultBuildingName() { return PROPS.getProperty("building.name", "BuildingA"); }
    public static int getDefaultBuildingCapacity() {
        return Integer.parseInt(PROPS.getProperty("building.capacity", "5"));
    }
}
