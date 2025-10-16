# Fault Tolerance Improvements

This document summarizes the fault tolerance enhancements made to the ConferenceRent booking system.

## 1. Manual Acknowledgments ✅

**Problem**: All consumers used `autoAck=true`, meaning messages were immediately removed from queues even if processing
failed. If a process crashed mid-processing, the message was lost.

**Solution**: Changed all consumers to use manual acknowledgments (`autoAck=false`):

### Changes Made:

- **ClientAgent.listenForReplies()**: Added `basicAck()` on success, `basicNack()` on failure
- **RentalAgent.subscribeClientInbox()**: Added `basicAck()` on success, `basicNack()` with requeue on failure
- **BuildingService.subscribeInbox()**: Added `basicAck()` on success, `basicNack()` with requeue on failure

### How it works:

```
try {
    WireMessage msg = MessageSerializer.deserialize(delivery.getBody());
    handleMessage(msg);
    // Only acknowledge if processing succeeded
    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
} catch (Exception e) {
    // Reject and requeue for retry
    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
}
```

**Benefit**: If a process crashes during message processing, the message remains in the queue and will be redelivered to
another consumer.

---

## 2. Durable Queues and Exchanges ✅

**Problem**: Queues and exchanges were non-durable (`durable=false`), so they disappeared when RabbitMQ restarted,
losing all messages.

**Solution**: Made critical queues and exchanges durable:

### Durable Exchanges:

- `cr.buildings.fanout` (building announcements)
- `cr.building.direct` (building-specific commands)

### Durable Queues:

- `cr.agents.inbox` (client → agent messages)
- `cr.building.<buildingName>.inbox` (agent → building messages)

### Non-Durable Queues (by design):

- `cr.client.<clientId>` (temporary reply queues, auto-delete)

### Changes Made:

- **RabbitMQConfig.declareCommonExchanges()**: Changed to `durable=true`
- **RabbitMQConfig.declareAgentInbox()**: Changed to `durable=true`
- **RabbitMQConfig.declareAndBindBuildingInbox()**: Changed to `durable=true`
- **RentalAgent.declareTopology()**: Changed to `durable=true`
- **BuildingService.declareTopology()**: Changed to `durable=true`
- **ClientAgent.sendToAgents()**: Changed to `durable=true`

**Benefit**: Queues and their bindings survive RabbitMQ broker restarts.

---

## 3. Persistent Messages ✅

**Problem**: Messages were not marked as persistent, so they were lost if RabbitMQ restarted (even with durable queues).

**Solution**: Added `MessageProperties.PERSISTENT_BASIC` to critical message publishes:

### Changes Made:

- **ClientAgent.sendToAgents()**: Messages to agents are now persistent
- **RentalAgent.forwardToBuilding()**: Messages to buildings are now persistent

### Not Persisted (by design):

- Client reply messages (temporary queues, no need for persistence)
- Building announcements (heartbeat messages, can be resent)

```
// Before
channel.basicPublish("", queue, null, body);

// After
channel.basicPublish("", queue, MessageProperties.PERSISTENT_BASIC, body);
```

**Benefit**: Important booking messages survive RabbitMQ restarts and are written to disk.

---

## 4. Additional Improvements Made

### Pending Reservation Timeout ✅

Added automatic cleanup of stale pending reservations:

- **BuildingService.startPendingCleanup()**: Runs every 60 seconds
- Auto-cancels reservations that have been PENDING for more than 5 minutes
- Frees up capacity that was locked by abandoned bookings

---

## What's Still Missing (Advanced Features)

### 1. Dead Letter Queue (DLQ)

**Current**: Failed messages are requeued indefinitely, which can cause infinite retry loops.

**Improvement**: Configure a DLQ for messages that fail multiple times:

```
Map<String, Object> args = new HashMap<>();
args.put("x-dead-letter-exchange", "cr.dlq.exchange");
args.put("x-max-retries", 3);
ch.queueDeclare(queue, true, false, false, args);
```

### 2. Publisher Confirms

**Current**: No confirmation that messages were successfully received by the broker.

**Improvement**: Enable publisher confirms:

```
channel.confirmSelect();
channel.basicPublish(...);
channel.waitForConfirmsOrDie(5000);
```

### 3. Connection Recovery

**Current**: If connection to RabbitMQ is lost, the process crashes.

**Improvement**: Enable automatic connection recovery:

```
ConnectionFactory f = new ConnectionFactory();
f.setAutomaticRecoveryEnabled(true);
f.setNetworkRecoveryInterval(5000);
```

### 4. Prefetch Limit

**Current**: Consumers might receive too many messages at once.

**Improvement**: Set QoS prefetch:

```
channel.basicQos(1); // Process one message at a time
```

---

## Testing Fault Tolerance

### Test 1: Process Crash During Processing

1. Start system normally
2. Add a `Thread.sleep(10000)` in `BuildingService.onBook()` before ack
3. Send a booking request
4. Kill the BuildingService process during sleep
5. Start a new BuildingService
6. **Expected**: Message should be redelivered and processed

### Test 2: RabbitMQ Restart

1. Start system and send several booking requests
2. Restart RabbitMQ: `docker restart rabbitmq`
3. **Expected**: Queues and exchanges should still exist
4. **Expected**: Unacknowledged messages should still be in queues

### Test 3: Pending Reservation Timeout

1. Book a room but don't confirm
2. Wait 5+ minutes
3. **Expected**: Reservation should be auto-canceled
4. **Expected**: Capacity should be freed

---

## Summary

| Feature             | Before         | After         | Benefit                           |
|---------------------|----------------|---------------|-----------------------------------|
| **Acknowledgments** | Auto           | Manual        | Messages survive process crashes  |
| **Queues**          | Non-durable    | Durable       | Queues survive broker restarts    |
| **Exchanges**       | Non-durable    | Durable       | Topology survives broker restarts |
| **Messages**        | Non-persistent | Persistent    | Messages survive broker restarts  |
| **Pending Cleanup** | None           | 5-min timeout | Prevents capacity locks           |

The system is now significantly more fault-tolerant and suitable for production use cases where reliability is critical.
