package main.building;

import main.config.AppConfig;

/**
 * Main entry point for starting a Building Service instance.
 * This class handles the startup of a building process with configurable
 * name and capacity, either from command line arguments or default properties.
 */
public class BuildingMain {

    /**
     * Starts a building service with the specified name and capacity.
     * Command line arguments override default configuration values.
     *
     * @param args command line arguments where:
     *             args[0] = building name (optional)
     *             args[1] = capacity per day (optional)
     * @throws Exception if the building service fails to start or 
     *                   encounters an error during operation
     */
    public static void main(String[] args) throws Exception {
        // Get building name from args or fall back to config default
        String name = args.length > 0 ? args[0] : AppConfig.getDefaultBuildingName();
        // Get capacity from args or fall back to config default
        int capacity = args.length > 1 ? Integer.parseInt(args[1]) : AppConfig.getDefaultBuildingCapacity();

        BuildingService svc = new BuildingService(name, capacity);
        svc.start();

        System.out.printf("[BuildingMain] Running %s (capacity %d) — Ctrl+C to stop%n", name, capacity);
        Thread.currentThread().join();
    }
}