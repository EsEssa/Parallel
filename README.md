# ConferenceRent - Distributed Room Booking System

A fault-tolerant, distributed conference room booking system built with RabbitMQ message passing. This project
demonstrates actor-based concurrency, dynamic service discovery, and fault tolerance patterns for distributed systems.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Running the System](#running-the-system)
- [Testing](#testing)
- [System Design](#system-design)
- [Fault Tolerance](#fault-tolerance)
- [Project Structure](#project-structure)

---

## Overview

ConferenceRent is a distributed booking platform where multiple independent processes communicate entirely through
RabbitMQ messages with no shared memory. The system supports:

- Multiple clients making concurrent booking requests
- Multiple rental agents for load balancing (round-robin)
- Multiple buildings that can join/leave dynamically
- Fault-tolerant message processing with acknowledgments
- Durable queues and persistent messages
- Automatic cleanup of stale reservations

---

## Architecture

### Actor Model

The system consists of three types of independent actors:

```
┌─────────┐         ┌─────────┐         ┌──────────┐
│ Client  │────────▶│  Agent  │────────▶│ Building │
│ Agent   │◀────────│ (Rental)│◀────────│ Service  │
└─────────┘         └─────────┘         └──────────┘
```

| Actor               | Role                                     | Scalability                            |
|---------------------|------------------------------------------|----------------------------------------|
| **ClientAgent**     | Sends booking requests, receives replies | Multiple clients supported             |
| **RentalAgent**     | Routes requests, validates buildings     | Multiple agents (load balanced)        |
| **BuildingService** | Manages capacity, processes reservations | Multiple buildings (dynamic discovery) |

### Message Flow

```
1. Client → AGENT_INBOX_QUEUE → Agent (round-robin)
2. Agent → BUILDING_DIRECT_EXCHANGE → Building (routing key)
3. Building → CLIENT_REPLY_QUEUE → Client (direct)
```

### RabbitMQ Topology

| Exchange/Queue             | Type            | Purpose                                                |
|----------------------------|-----------------|--------------------------------------------------------|
| `cr.agents.inbox`          | Queue           | Shared inbox for all agents (round-robin distribution) |
| `cr.buildings.fanout`      | Fanout Exchange | Building announcements for discovery                   |
| `cr.building.direct`       | Direct Exchange | Routes messages to specific buildings                  |
| `cr.client.<clientId>`     | Queue           | Private reply queue per client                         |
| `cr.building.<name>.inbox` | Queue           | Private inbox per building                             |

---

## Features

### Core Functionality

- List Buildings - Discover all available buildings
- Book Rooms - Create provisional reservations
- Confirm Reservations - Finalize bookings
- Cancel Reservations - Release capacity
- Capacity Management - Atomic concurrency control
- Dynamic Discovery - Buildings announce themselves via fanout

### Fault Tolerance

- Manual Acknowledgments - Messages survive process crashes
- Durable Queues - Topology survives broker restarts
- Persistent Messages - Critical messages written to disk
- Auto-Cleanup - Stale pending reservations timeout after 5 minutes
- Idempotent Operations - Confirm/cancel can be called multiple times safely

### Concurrency

- Thread-Safe State - Uses `ConcurrentHashMap` for reservations
- Atomic Capacity Checks - Race-condition-free booking
- Load Balancing - Multiple agents consume from shared queue

---

## Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- **RabbitMQ 3.x** (running locally or via Docker)

---

## Running the System

### Option 1: Run Tests (Automated)

Run the Class `TestMain`

This runs the integrated test suite:

- Building discovery test
- Book → Confirm → Cancel lifecycle test
- Concurrency capacity test (2 clients, 1 room)

### Option 2: Test Manually

Run the Class `DevConsoleMain`

This starts the three Mains in the right order and gives an interactive
testing playground in the console.

### Option 3: Run Components Manually

**1: Run `BuildingMain`**

**2: Run `Rental Agent`**

**3: Run`ClientMain`**

---

## Testing

### Run `TestMain`

### Expected Output

```
=== ConferenceRent Test Harness ===
[Agent AgentTest] listening on cr.agents.inbox
[Agent AgentTest] up. Known buildings: []
[Building BuildingA] announced on cr.buildings.fanout
[Agent AgentTest] discovered building: BuildingA
[Building BuildingA] listening on cr.building.BuildingA.inbox
[Building BuildingA] up. Capacity/day=1

[Test] List buildings
[Client TestClient1] Ready. Listening on cr.client.TestClient1
[Client TestClient1] -> Sent REQUEST_BUILDINGS
[Agent AgentTest] -> [client TestClient1] RESPONSE_BUILDINGS
[Client TestClient1] <- [RESPONSE_BUILDINGS] BookingReply[success=true, reservationNumber=null, message=[BuildingA]]
[Client TestClient1] Disconnected.
PASS

[Test] Book -> Confirm -> Cancel
[Client TestClient2] Ready. Listening on cr.client.TestClient2
[Client TestClient2] -> Sent BOOK_ROOM
[Agent AgentTest] -> [building.BuildingA] BOOK_ROOM
[Building BuildingA] -> client TestClient2 : BOOK_ROOM(9b0c7dd1-24e4-4488-a304-1b99d386bd6e)
[Building BuildingA] PENDING 9b0c7dd1-24e4-4488-a304-1b99d386bd6e for TestClient2 (rooms=1, date=2025-10-17)
[Client TestClient2] <- [BOOK_ROOM] BookingReply[success=true, reservationNumber=9b0c7dd1-24e4-4488-a304-1b99d386bd6e, message=Provisional hold created; please confirm]
[Client TestClient2] -> Sent CONFIRM_RESERVATION
[Agent AgentTest] -> [building.BuildingA] CONFIRM_RESERVATION
[Building BuildingA] -> client TestClient2 : CONFIRM_RESERVATION(9b0c7dd1-24e4-4488-a304-1b99d386bd6e)
[Building BuildingA] CONFIRMED 9b0c7dd1-24e4-4488-a304-1b99d386bd6e for TestClient2
[Client TestClient2] <- [CONFIRM_RESERVATION] BookingReply[success=true, reservationNumber=9b0c7dd1-24e4-4488-a304-1b99d386bd6e, message=Confirmed]
[Client TestClient2] -> Sent CANCEL_RESERVATION
[Agent AgentTest] -> [building.BuildingA] CANCEL_RESERVATION
[Building BuildingA] -> client TestClient2 : CANCEL_RESERVATION(9b0c7dd1-24e4-4488-a304-1b99d386bd6e)
[Building BuildingA] CANCELED 9b0c7dd1-24e4-4488-a304-1b99d386bd6e for TestClient2
[Client TestClient2] <- [CANCEL_RESERVATION] BookingReply[success=true, reservationNumber=9b0c7dd1-24e4-4488-a304-1b99d386bd6e, message=Canceled]
[Client TestClient2] Disconnected.
PASS

[Test] Concurrency capacity (cap=1, two clients) — expect 1 success, 1 fail
[Client RaceC1] Ready. Listening on cr.client.RaceC1
[Client RaceC2] Ready. Listening on cr.client.RaceC2
[Client RaceC2] -> Sent BOOK_ROOM
[Client RaceC1] -> Sent BOOK_ROOM
[Agent AgentTest] -> [building.BuildingA] BOOK_ROOM
[Agent AgentTest] -> [building.BuildingA] BOOK_ROOM
[Building BuildingA] -> client RaceC2 : BOOK_ROOM(3af79031-4c39-47d3-8c5d-99458a68cfab)
[Building BuildingA] PENDING 3af79031-4c39-47d3-8c5d-99458a68cfab for RaceC2 (rooms=1, date=2025-10-18)
[Client RaceC2] <- [BOOK_ROOM] BookingReply[success=true, reservationNumber=3af79031-4c39-47d3-8c5d-99458a68cfab, message=Provisional hold created; please confirm]
[Building BuildingA] -> client RaceC1 : BOOK_ROOM(null)
[Client RaceC1] <- [BOOK_ROOM] BookingReply[success=false, reservationNumber=null, message=No availability on 2025-10-18 (requested 1, capacity 1)]
[Client RaceC1] Disconnected.
[Client RaceC2] Disconnected.
Observed: successes=1, failures=1 -> PASS

=== RESULT: ALL TESTS PASS  ===
[Building BuildingA] down.
[Agent AgentTest] down.

Process finished with exit code 0
```

### Manual Testing Scenarios

**Test Fault Tolerance:**

1. Start system normally
2. Send a booking request
3. Kill the BuildingService process mid-processing
4. Restart BuildingService
5. **Expected**: Message should be redelivered and processed

**Test Pending Timeout:**

1. Book a room but don't confirm
2. Wait 5+ minutes
3. **Expected**: Reservation auto-canceled, capacity freed

---

## System Design

### Reservation Lifecycle

```
1. BOOK_ROOM → Creates PENDING reservation (holds capacity)
2. CONFIRM_RESERVATION → Changes to CONFIRMED
3. CANCEL_RESERVATION → Changes to CANCELED (frees capacity)
```

### State Diagram

```
        BOOK
          ↓
      [PENDING] ─ CONFIRM ─→ [CONFIRMED]
          ↓                      ↓
       CANCEL                  CANCEL
          ↓                      ↓
      [CANCELED] ←───────────────┘
```

### Concurrency Control

**Atomic Capacity Check:**

```
bookedPerDay.compute(date, (d, used) -> {
    int current = (used == null ? 0 : used);
    if (current + requestedRooms > capacity) {
        throw OverCapacity.INSTANCE;
    }
    return current + requestedRooms;
});
```

This ensures **no race conditions** when multiple clients book simultaneously

---

## Fault Tolerance

I would like to mention that the following improvements were suggestions made
by AI. I definitly wouldn't have come up with them and I won't be pretending
I did, but they were simple enough for me to understand and implement.

### 1. Manual Acknowledgments

Messages are only removed from queues after successful processing:

```
channel.basicAck(deliveryTag, false);  // Success
channel.basicNack(deliveryTag, false, true);  // Failure → requeue
```

### 2. Durable Infrastructure

- **Exchanges**: `durable=true` (survive broker restarts)
- **Queues**: `durable=true` for critical queues
- **Messages**: `MessageProperties.PERSISTENT_BASIC` for important messages

### 3. Automatic Recovery

- **Pending Cleanup**: Auto-cancels reservations after 5 minutes
- **Heartbeat Discovery**: Buildings re-announce every 10 seconds

See [FAULT_TOLERANCE_IMPROVEMENTS.md](FAULT_TOLERANCE_IMPROVEMENTS.md) for detailed documentation.

---

## Project Structure

```
src/main/
├── agent/
│   ├── RentalAgent.java          # Intermediary between clients and buildings
│   └── RentalAgentMain.java      # Entry point for agent process
├── building/
│   ├── BuildingService.java      # Manages capacity and reservations
│   └── BuildingMain.java         # Entry point for building process
├── client/
│   ├── ClientAgent.java          # Client communication logic
│   └── ClientMain.java           # Entry point for client process
├── config/
│   ├── AppConfig.java            # Configuration loader
│   └── Constants.java            # Queue/exchange names
├── domain/
│   ├── BookingReply.java         # Response DTO
│   ├── BookingRequest.java       # Request DTO
│   ├── MessageType.java          # Message type enum
│   ├── Reservation.java          # Reservation entity
│   ├── ReservationStatus.java    # Status enum (PENDING/CONFIRMED/CANCELED)
│   └── WireMessage.java          # Message envelope
├── util/
│   ├── MessageSerializer.java    # Java serialization utilities
│   └── RabbitMQConfig.java       # RabbitMQ setup utilities
├── tests/
│   └── TestMain.java             # Integration test suite
└── DevConsoleMain.java           # Interactive testing console
```

---

## Configuration

### Default Settings

- **RabbitMQ Host**: `localhost`
- **RabbitMQ Port**: `5672`
- **Username**: `guest`
- **Password**: `guest`

---

## Performance Characteristics

- **Throughput**: Limited by RabbitMQ broker capacity (~10k msgs/sec)
- **Latency**: Typically <10ms for local broker
- **Scalability**: Horizontal scaling via multiple agents
- **Availability**: High (with durable queues and persistent messages)

---

## Troubleshooting

(Just problems I ran into, and documented them for future me)

### RabbitMQ Connection Failed

```
Error: Cannot connect to RabbitMQ at localhost
```

**Solution**: Ensure RabbitMQ is running on port 5672

### Queue Already Exists with Different Properties

```
Error: PRECONDITION_FAILED - inequivalent arg 'durable'
```

**Solution**: Delete existing queues via management UI or:

```bash
docker restart rabbitmq
```

---

## Additional Documentation

- [FAULT_TOLERANCE_IMPROVEMENTS.md](FAULT_TOLERANCE_IMPROVEMENTS.md) - Detailed fault tolerance guide (AI improvements)

---

## Learning Outcomes

This project demonstrates:

- Message-passing concurrency (actor model)
- Distributed systems design
- RabbitMQ exchange patterns (fanout, direct)
- Fault tolerance and reliability patterns
- Atomic operations with concurrent data structures
- Service discovery mechanisms
- Idempotent operations

---

## Acknowledgments

- RabbitMQ documentation and tutorials
- Lecture Slides from Saxion and my hometown university
- ChatGPT for javadoc and the already mentioned improvements
