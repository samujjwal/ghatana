# Notification Plugin

This module provides notification plugin implementations with delivery tracking, retry, and dead-letter queue support.

## Implementations

- `InMemoryNotificationPlugin`
  - Variant: `in-memory`
  - Durability: `non-durable`
  - Intended use: local development, lightweight tests, non-critical runtime paths
- `DurableNotificationPlugin`
  - Variant: `durable-jdbc`
  - Durability: `durable`
  - Intended use: production paths requiring restart-safe notification persistence

## Features

- **Asynchronous Delivery**: Notifications are queued and delivered asynchronously via event bus
- **Delivery State Tracking**: Track notification status (pending, delivered, failed, dead-lettered)
- **Retry Policy**: Configurable retry with exponential backoff (max 3 attempts by default)
- **Dead-Letter Queue**: Permanently failed notifications are moved to DLQ for inspection and reprocessing
- **Template-Based**: Supports template-based notification rendering

## Schema

`DurableNotificationPlugin` requires `ensureSchema()` before start-up. This operation is idempotent and safe to call at each application boot.

### Tables

- `notification_queue`: Stores pending and delivered notifications
- `notification_dead_letter`: Stores permanently failed notifications

### Indexes

- `idx_notification_state`: On state column for efficient status queries
- `idx_notification_recipient`: On recipient_id for user notification lookup
- `idx_dlq_recipient`: On recipient_id in DLQ for failed notification lookup

## Usage Example

```java
// Create durable plugin
DataSource dataSource = ...; // your data source
EventBusPort eventBus = ...; // your event bus
NotificationPlugin plugin = new DurableNotificationPlugin(dataSource, eventBus);

// Ensure schema on first boot
plugin.ensureSchema().await();

// Start the plugin
plugin.start();

// Dispatch a notification
String notificationId = plugin.dispatch(
    "user-123",
    "dmos.campaign.launched",
    Map.of("campaignName", "Summer Sale 2026")
).await();

// Check delivery status
DeliveryStatus status = plugin.getDeliveryStatus(notificationId).await();
if (status.state() == DeliveryState.DELIVERED) {
    // Notification delivered successfully
} else if (status.state() == DeliveryState.DEAD_LETTERED) {
    // Handle failed notification
}

// Retry failed notification
if (status.state() == DeliveryState.FAILED) {
    plugin.retry(notificationId).await();
}
```

## Production Considerations

- Use `DurableNotificationPlugin` for production deployments
- Configure appropriate retry policies based on your notification service SLA
- Monitor dead-letter queue size and alert on threshold breaches
- Implement proper event bus consumers for actual notification delivery (email, SMS, push, etc.)
- Consider implementing notification templates with proper rendering engine
