package main;

import main.agent.RentalAgent;
import main.building.BuildingService;
import main.client.ClientAgent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class DevConsoleMain {
    private static final List<RentalAgent> agents = new CopyOnWriteArrayList<>();
    private static final List<BuildingService> buildings = new CopyOnWriteArrayList<>();
    private static ClientAgent client;

    public static void main(String[] args) throws Exception {
        String agentName = argOr(args, 0, "Agent1");
        String building  = argOr(args, 1, "BuildingA");
        int capacity     = Integer.parseInt(argOr(args, 2, "5"));
        String clientId  = argOr(args, 3, "Client1");

        // Start actors in correct order
        RentalAgent a = new RentalAgent(agentName); a.start(); agents.add(a);
        BuildingService b = new BuildingService(building, capacity); b.start(); buildings.add(b);
        client = new ClientAgent(clientId); client.start();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { if (client != null) client.stop(); } catch (Exception ignored) {}
            for (RentalAgent ra : new ArrayList<>(agents)) { try { ra.stop(); } catch (Exception ignored) {} }
            for (BuildingService bs : new ArrayList<>(buildings)) { try { bs.stop(); } catch (Exception ignored) {} }
        }));

        printHelp();

        // Single Scanner for whole app; never close System.in
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) { // EOF (Ctrl+D) or input closed
                sleep(100); // small pause to avoid busy-loop on some consoles
                continue;
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            try {
                switch (cmd) {
                    case "help", "h", "?" -> printHelp();

                    case "list" -> client.requestBuildingList();

                    case "book" -> {
                        // book <building> <rooms> <hours> [yyyy-mm-dd]
                        if (parts.length < 4) { System.out.println("Usage: book <b> <rooms> <hours> [yyyy-mm-dd]"); break; }
                        String bld = parts[1];
                        int rooms = Integer.parseInt(parts[2]);
                        int hours = Integer.parseInt(parts[3]);
                        LocalDate date = (parts.length >= 5) ? LocalDate.parse(parts[4]) : LocalDate.now().plusDays(1);
                        client.bookRoom(bld, rooms, date, hours);
                    }

                    case "confirm" -> {
                        // confirm <building> <reservationId>
                        if (parts.length < 3) { System.out.println("Usage: confirm <b> <reservationId>"); break; }
                        client.confirmReservation(parts[1], parts[2]);
                    }

                    case "cancel" -> {
                        // cancel <building> <reservationId>
                        if (parts.length < 3) { System.out.println("Usage: cancel <b> <reservationId>"); break; }
                        client.cancelReservation(parts[1], parts[2]);
                    }

                    case "add-agent" -> {
                        // add-agent [name]
                        String name = (parts.length >= 2) ? parts[1] : ("Agent" + (agents.size() + 1));
                        RentalAgent ra = new RentalAgent(name); ra.start(); agents.add(ra);
                        System.out.println("[Dev] Added agent: " + name);
                    }

                    case "add-building" -> {
                        // add-building <name> <capacity>
                        if (parts.length < 3) { System.out.println("Usage: add-building <name> <capacity>"); break; }
                        String name = parts[1];
                        int cap = Integer.parseInt(parts[2]);
                        BuildingService bs = new BuildingService(name, cap); bs.start(); buildings.add(bs);
                        System.out.printf("[Dev] Added building: %s (cap %d)%n", name, cap);
                    }

                    case "show" -> {
                        System.out.println("Agents: " + agents.size() + ", Buildings: " + buildings.size()
                                + ", Client: " + (client != null));
                    }

                    case "exit", "quit", "q" -> {
                        System.out.println("[Dev] Bye.");
                        return; // triggers shutdown hook
                    }

                    default -> System.out.println("Unknown command. Type 'help'.");
                }
            } catch (Exception e) {
                System.out.println("[Dev] Error: " + e.getMessage());
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                Commands:
                  help                       - show this help
                  list                       - request list of buildings
                  book <b> <rooms> <hours> [yyyy-mm-dd]  - book rooms at building b
                  confirm <b> <reservationId>            - confirm a reservation
                  cancel  <b> <reservationId>            - cancel a reservation
                  add-agent [name]            - start another RentalAgent (embedded)
                  add-building <name> <cap>   - start another BuildingService (embedded)
                  show                        - show running agents/buildings
                  exit                        - quit (stops everything)
                """);
    }

    private static String argOr(String[] args, int i, String def) { return (i < args.length) ? args[i] : def; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
