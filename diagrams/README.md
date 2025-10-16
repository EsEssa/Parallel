# PlantUML Diagrams

This folder contains PlantUML diagrams documenting the ConferenceRent system architecture and behavior.

## Diagram Descriptions

### 1. architecture.puml

Component diagram showing the overall system architecture:

- Client, Agent, and Building layers
- RabbitMQ exchanges and queues
- Message flow between components

### 2. sequence-booking.puml

Sequence diagram showing the complete booking lifecycle:

- Room booking request
- Reservation confirmation
- Reservation cancellation
- Message flow through all components

### 3. sequence-discovery.puml

Sequence diagram showing building discovery mechanism:

- Building startup and announcement
- Agent discovery process
- Periodic heartbeat mechanism
- Client requesting building list

### 4. sequence-concurrency.puml

Sequence diagram demonstrating concurrent booking handling:

- Two clients booking simultaneously
- Atomic capacity check using ConcurrentHashMap
- Race condition prevention
- Exactly one success, one failure

### 5. state-reservation.puml

State machine diagram showing reservation lifecycle:

- PENDING state (provisional hold)
- CONFIRMED state (finalized)
- CANCELED state (released)
- State transitions and triggers

### 6. class-domain.puml

Class diagram showing the domain model:

- WireMessage envelope
- BookingRequest and BookingReply DTOs
- Reservation entity
- MessageType and ReservationStatus enums

### 7. deployment.puml

Deployment diagram showing process distribution:

- Multiple agent processes (load balancing)
- Multiple building processes (independent services)
- Multiple client processes
- RabbitMQ broker as central hub
