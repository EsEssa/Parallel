package main.building;

import main.config.AppConfig;

public class BuildingMain {
    public static void main(String[] args) throws Exception {
        // allow overrides via args, else fall back to properties
        String name = args.length > 0 ? args[0] : AppConfig.getDefaultBuildingName();
        int capacity = args.length > 1 ? Integer.parseInt(args[1]) : AppConfig.getDefaultBuildingCapacity();

        BuildingService svc = new BuildingService(name, capacity);
        svc.start();

        System.out.printf("[BuildingMain] Running %s (capacity %d) — Ctrl+C to stop%n", name, capacity);
        Thread.currentThread().join();
    }
}

