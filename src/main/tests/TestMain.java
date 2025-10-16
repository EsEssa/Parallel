package main.tests;

import main.agent.RentalAgent;
import main.building.BuildingService;
import main.client.ClientAgent;
import main.domain.*;

import java.time.LocalDate;
import java.util.concurrent.*;

/**
 * Test harness for verifying the Conference Room Booking system functionality.
 * Runs a series of integration tests to validate core system behavior including
 * building discovery, reservation lifecycle, and concurrency handling.
 */
public class TestMain {

    /**
     * Main test runner that executes all test cases and reports overall results.
     *
     * @param args command line arguments (not used)
     * @throws Exception if any test setup or execution fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== ConferenceRent Test Harness ===");

        // Start core system components
        RentalAgent agent = new RentalAgent("AgentTest");
        agent.start();

        BuildingService building = new BuildingService("BuildingA", 1);
        building.start();

        // Allow time for components to initialize and discover each other
        Thread.sleep(800);

        // Execute test suite
        boolean ok = true;
        ok &= testListBuildings();
        ok &= testBookConfirmCancel();
        ok &= testConcurrencyCapacity();

        System.out.println("\n=== RESULT: " + (ok ? "ALL TESTS PASS " : "SOME TESTS FAILED ") + " ===");

        // Cleanup
        building.stop();
        agent.stop();
    }

    /**
     * Tests building discovery functionality by requesting the building list
     * and verifying that the test building is included in the response.
     *
     * @return true if the test passes, false otherwise
     * @throws Exception if client operations fail
     */
    private static boolean testListBuildings() throws Exception {
        System.out.println("\n[Test] List buildings");
        ClientAgent client = new ClientAgent("TestClient1");
        client.start();

        client.clearReplies();
        client.requestBuildingList();
        WireMessage reply = client.waitForReply(3000);

        client.stop();

        boolean pass = reply != null
                && reply.type() == MessageType.RESPONSE_BUILDINGS
                && reply.payload() instanceof BookingReply br
                && br.message() != null && br.message().contains("BuildingA");

        System.out.println(pass ? "PASS" : "FAIL: got=" + (reply == null ? "null" : reply.payload()));
        return pass;
    }

    /**
     * Tests the complete reservation lifecycle: booking, confirmation, and cancellation.
     * Verifies that each step succeeds and maintains reservation state correctly.
     *
     * @return true if all lifecycle steps complete successfully, false otherwise
     * @throws Exception if client operations fail
     */
    private static boolean testBookConfirmCancel() throws Exception {
        System.out.println("\n[Test] Book -> Confirm -> Cancel");
        ClientAgent client = new ClientAgent("TestClient2");
        client.start();

        client.clearReplies();
        client.bookRoom("BuildingA", 1, LocalDate.now().plusDays(1), 2);
        WireMessage book = client.waitForReply(3000);

        // Extract reservation ID from booking response
        String resId = (book != null && book.payload() instanceof BookingReply br && br.success())
                ? br.reservationNumber() : null;

        if (resId == null) {
            System.out.println("FAIL: booking failed or no reservation id. got=" + (book == null ? "null" : book.payload()));
            client.stop();
            return false;
        }

        // Test confirmation
        client.confirmReservation("BuildingA", resId);
        WireMessage confirm = client.waitForReply(3000);

        // Test cancellation
        client.cancelReservation("BuildingA", resId);
        WireMessage cancel = client.waitForReply(3000);

        client.stop();

        boolean pass = confirm != null && confirm.type() == MessageType.CONFIRM_RESERVATION
                && confirm.payload() instanceof BookingReply cbr && cbr.success()
                && cancel != null && cancel.type() == MessageType.CANCEL_RESERVATION
                && cancel.payload() instanceof BookingReply xbr && xbr.success();

        System.out.println(pass ? "PASS" : "FAIL: confirm=" + payloadStr(confirm) + " cancel=" + payloadStr(cancel));
        return pass;
    }

    /**
     * Tests concurrent booking attempts when capacity is limited.
     * With a capacity of 1 room and 2 concurrent booking requests,
     * exactly one should succeed and one should fail due to capacity constraints.
     *
     * @return true if concurrency control works correctly, false otherwise
     * @throws Exception if client operations or thread execution fails
     */
    private static boolean testConcurrencyCapacity() throws Exception {
        System.out.println("\n[Test] Concurrency capacity (cap=1, two clients) — expect 1 success, 1 fail");

        // Use a future date to avoid conflicts with other tests
        LocalDate date = LocalDate.now().plusDays(2);
        ClientAgent c1 = new ClientAgent("RaceC1");
        c1.start();
        ClientAgent c2 = new ClientAgent("RaceC2");
        c2.start();

        c1.clearReplies();
        c2.clearReplies();

        WireMessage r1;
        WireMessage r2;

        // Execute concurrent booking requests
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> f1 = pool.submit(() -> {
                try {
                    c1.bookRoom("BuildingA", 1, date, 1);
                } catch (Exception ignored) {
                }
            });
            Future<?> f2 = pool.submit(() -> {
                try {
                    c2.bookRoom("BuildingA", 1, date, 1);
                } catch (Exception ignored) {
                }
            });
            f1.get();
            f2.get();

            // Collect responses from both clients
            r1 = c1.waitForReply(3000);
            r2 = c2.waitForReply(3000);
        }

        // Cleanup
        c1.stop();
        c2.stop();

        // Count successes and failures
        int successes = 0, failures = 0;
        for (WireMessage r : new WireMessage[]{r1, r2}) {
            if (r != null && r.type() == MessageType.BOOK_ROOM && r.payload() instanceof BookingReply br) {
                if (br.success()) successes++; else failures++;
            }
        }

        boolean pass = (successes == 1 && failures == 1);
        System.out.printf("Observed: successes=%d, failures=%d -> %s%n",
                successes, failures, pass ? "PASS" : "FAIL");

        if (!pass) {
            System.out.println("Details: r1=" + payloadStr(r1) + ", r2=" + payloadStr(r2));
        }
        return pass;
    }

    /**
     * Helper method to safely extract payload string from a message.
     *
     * @param m the WireMessage to extract from
     * @return string representation of the payload, or "null" if message or payload is null
     */
    private static String payloadStr(WireMessage m) {
        if (m == null) return "null";
        Object p = m.payload();
        return (p == null) ? "null" : p.toString();
    }
}