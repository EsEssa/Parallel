package main.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for application settings.
 * Loads properties from configuration files and provides
 * default values for RabbitMQ connection and system parameters.
 */
public final class AppConfig {
    private static final String PROPS_PATH = "ressources/rabbitmq.properties";
    private static final Properties PROPS = new Properties();

    static {
        loadProperties();
    }

    // Private constructor to prevent instantiation
    private AppConfig() {}

    /**
     * Loads properties from the configuration file on the classpath.
     * If the file is not found, defaults will be used for all properties.
     */
    private static void loadProperties() {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(PROPS_PATH)) {
            if (in != null) {
                PROPS.load(in);
            } else {
                System.err.println("[AppConfig] Properties not found on classpath: " + PROPS_PATH);
            }
        } catch (Exception e) {
            System.err.println("[AppConfig] Failed to load properties: " + e.getMessage());
        }
    }

    /**
     * Gets the RabbitMQ host address from configuration.
     *
     * @return the RabbitMQ host, defaults to "localhost" if not configured
     */
    public static String getRabbitHost() {
        return PROPS.getProperty("rabbitmq.host", "localhost");
    }

    /**
     * Gets the RabbitMQ username from configuration.
     *
     * @return the RabbitMQ username, defaults to "guest" if not configured
     */
    public static String getRabbitUser() {
        return PROPS.getProperty("rabbitmq.user", "guest");
    }

    /**
     * Gets the RabbitMQ password from configuration.
     *
     * @return the RabbitMQ password, defaults to "guest" if not configured
     */
    public static String getRabbitPass() {
        return PROPS.getProperty("rabbitmq.pass", "guest");
    }

    /**
     * Gets the default building name from configuration.
     *
     * @return the default building name, defaults to "BuildingA" if not configured
     */
    public static String getDefaultBuildingName() {
        return PROPS.getProperty("building.name", "BuildingA");
    }

    /**
     * Gets the default building capacity from configuration.
     *
     * @return the default building capacity, defaults to 5 if not configured
     */
    public static int getDefaultBuildingCapacity() {
        return Integer.parseInt(PROPS.getProperty("building.capacity", "5"));
    }
}