# 🏗️ Project Structure and File Purposes

## 📂 Overview

This project implements a **distributed conference room booking system** using **RabbitMQ**.  
Each **actor** (Client, Rental Agent, Building) runs as an independent process that communicates through message passing.

```
src/
└── main/
├── java/
│ └── nl/saxion/paracomp/conferencerent/
│ ├── api/
│ │ └── BookingAPI.java
│ ├── agent/
│ │ ├── RentalAgent.java
│ │ └── AgentMain.java
│ ├── building/
│ │ ├── BuildingService.java
│ │ └── BuildingMain.java
│ ├── client/
│ │ ├── ClientAgent.java
│ │ ├── ClientUI.java
│ │ └── ClientMain.java
│ ├── config/
│ │ ├── AppConfig.java
│ │ └── Constants.java
│ ├── domain/
│ │ ├── BookingReply.java
│ │ ├── BookingRequest.java
│ │ ├── MessageType.java
│ │ ├── Reservation.java
│ │ ├── ReservationStatus.java
│ │ └── WireMessage.java
│ ├── messaging/
│ │ ├── RabbitFactory.java
│ │ ├── RabbitChannels.java
│ │ └── Serializer.java
│ ├── service/
│ │ ├── IdGenerator.java
│ │ └── ReservationStore.java
│ ├── support/
│ │ └── DomainException.java
│ └── Utils.java
└── resources/
└── nl/saxion/paracomp/conferencerent/
└── rabbitmq.properties
```

---

## 🎭 Actors (each actor = independent process)

### 🧑‍💼 `client/ClientAgent.java`
- **Purpose:** Implements the **Customer** actor’s logic.
- **Responsibilities:**
    - Sends booking-related requests to rental agents.
    - Listens for responses on its own reply queue.
- **Why needed:** Each client process simulates a customer interacting with the system.

### 🚀 `client/ClientMain.java`
- **Purpose:** Entry point for the **Client** process.
- **Responsibilities:** Creates and starts a `ClientAgent` instance.
- **Why needed:** Allows you to run multiple clients as separate JVM processes.

### 💻 `client/ClientUI.java` *(optional)*
- **Purpose:** Optional GUI or CLI interface for interactive testing.
- **Responsibilities:** Lets you manually trigger requests to the system.
- **Why needed:** Useful for manual testing or demonstrations.

---

### 🏢 `agent/RentalAgent.java`
- **Purpose:** Represents the **Rental Agent** actor (intermediary between clients and buildings).
- **Responsibilities:**
    - Receives client requests via a **shared queue**.
    - Forwards them to the appropriate building.
    - Generates unique reservation IDs.
    - Returns replies or errors to the clients.
- **Why needed:** Enables scaling — multiple agents can handle requests concurrently (round-robin).

### 🚀 `agent/AgentMain.java`
- **Purpose:** Entry point for a **Rental Agent** process.
- **Responsibilities:** Creates and starts one `RentalAgent` instance.
- **Why needed:** Each agent runs independently for load balancing and scalability.

---

### 🏬 `building/BuildingService.java`
- **Purpose:** Implements the **Building** actor’s logic.
- **Responsibilities:**
    - Manages room availability and reservations.
    - Consumes messages addressed specifically to this building.
    - Announces its existence via a **fanout exchange**.
- **Why needed:** Each building runs as an independent process that can join or leave the system dynamically.

### 🚀 `building/BuildingMain.java`
- **Purpose:** Entry point for a **Building** process.
- **Responsibilities:** Starts a `BuildingService` for a specific building.
- **Why needed:** Allows launching multiple buildings as separate JVM processes.

---

## 📦 API & Domain Layer

### `api/BookingAPI.java`
- **Purpose:** Defines the application-level contract for booking operations.
- **Responsibilities:** Lists all use cases (list buildings, book, confirm, cancel).
- **Why needed:** Keeps communication logic decoupled from RabbitMQ details; simplifies testing.

---

### `domain/BookingRequest.java`
- **Purpose:** Represents a booking request from a client.
- **Fields:** Building name, number of rooms, date, duration.
- **Why needed:** Standardized, serializable request object.

### `domain/BookingReply.java`
- **Purpose:** Represents the response to a booking request.
- **Fields:** Success flag, reservation ID, message.
- **Why needed:** Provides a consistent reply format for all operations.

### `domain/Reservation.java`
- **Purpose:** Represents a reservation entity.
- **Fields:** Reservation ID, building, rooms, date, status, etc.
- **Why needed:** The central piece of system state that can be confirmed or canceled.

### `domain/ReservationStatus.java`
- **Purpose:** Defines the possible states of a reservation (`PENDING`, `CONFIRMED`, `CANCELED`).
- **Why needed:** Makes state transitions clear and type-safe.

### `domain/MessageType.java`
- **Purpose:** Enum defining all message types (REQUEST, RESPONSE, BOOK, CONFIRM, CANCEL, ERROR).
- **Why needed:** Ensures message handlers are consistent and maintainable.

### `domain/WireMessage.java`
- **Purpose:** Serializable wrapper for all messages sent over RabbitMQ.
- **Fields:** Message type, sender ID, payload.
- **Why needed:** Provides a consistent envelope format for inter-process communication.

---

## 📨 Messaging (RabbitMQ)

### `messaging/RabbitFactory.java`
- **Purpose:** Central factory for RabbitMQ connections and channels.
- **Responsibilities:** Reads connection info from `rabbitmq.properties`.
- **Why needed:** Avoids repeated setup code across different actors.

### `messaging/RabbitChannels.java`
- **Purpose:** Declares exchanges, queues, and routing key patterns.
- **Responsibilities:**
    - Sets up the **fanout exchange** for building announcements.
    - Sets up **direct exchanges** for building-specific messages.
    - Declares the **shared queue** for client requests.
- **Why needed:** Defines the system’s messaging topology in one place.

### `messaging/Serializer.java`
- **Purpose:** Handles serialization and deserialization of messages.
- **Why needed:** Ensures consistent data format (e.g., Java objects or JSON) for RabbitMQ transport.

---

## ⚙️ Services (Logic helpers)

### `service/ReservationStore.java`
- **Purpose:** Manages reservations in memory (could later connect to a database).
- **Responsibilities:** Add, confirm, cancel, and fetch reservations.
- **Why needed:** Separates reservation management from message handling.

### `service/IdGenerator.java`
- **Purpose:** Generates unique reservation numbers.
- **Why needed:** Central place for unique ID logic; ensures consistency across processes.

---

## 🧩 Config & Utilities

### `config/Constants.java`
- **Purpose:** Stores constants for queue names, exchange names, and routing keys.
- **Why needed:** Keeps naming consistent across all actors and avoids hardcoded strings.

### `config/AppConfig.java`
- **Purpose:** Loads configuration values (RabbitMQ credentials, IDs, etc.).
- **Why needed:** Centralized configuration for all components.

### `Utils.java`
- **Purpose:** Small utility methods shared across modules (e.g., date/time formatting, logging).
- **Why needed:** Keeps helper code out of domain classes.

### `support/DomainException.java`
- **Purpose:** Custom exception for business logic errors (e.g., confirming a nonexistent reservation).
- **Why needed:** Standardizes how errors are thrown and converted into error messages.

---

## 🗂️ Resources

### `resources/nl/saxion/paracomp/conferencerent/rabbitmq.properties`
- **Purpose:** Configuration file for RabbitMQ connection details.
- **Contains:**
  ```properties
  rabbitmq.host=localhost
  rabbitmq.user=guest
  rabbitmq.pass=guest
