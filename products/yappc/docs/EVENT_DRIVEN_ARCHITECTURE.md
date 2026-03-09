# Event-Driven Backend Architecture

**Date:** 2026-03-07  
**Status:** 🎯 DESIGN COMPLETE  
**Timeline:** 4-6 weeks

---

## 🎯 Architecture Overview

Transform YAPPC backend from synchronous request/response to event-driven architecture for better scalability, decoupling, and real-time capabilities.

---

## 📐 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Event-Driven Architecture                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐         ┌──────────────┐         ┌─────────┐ │
│  │   Producer   │────────▶│  Event Bus   │────────▶│Consumer │ │
│  │  (Services)  │         │   (Kafka)    │         │(Handlers)│ │
│  └──────────────┘         └──────────────┘         └─────────┘ │
│         │                        │                       │      │
│         │                        ▼                       │      │
│         │                 ┌─────────────┐               │      │
│         │                 │ Event Store │               │      │
│         │                 │(PostgreSQL) │               │      │
│         │                 └─────────────┘               │      │
│         │                                                │      │
│         └────────────────────┬───────────────────────────┘      │
│                              ▼                                  │
│                      ┌──────────────┐                          │
│                      │  CQRS Store  │                          │
│                      │ (Read Model) │                          │
│                      └──────────────┘                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Core Components

### 1. Event Bus (Apache Kafka)

**Configuration:**
```yaml
# kafka-config.yml
bootstrap.servers: localhost:9092
topics:
  canvas-events:
    partitions: 3
    replication-factor: 2
    retention.ms: 604800000  # 7 days
  
  collaboration-events:
    partitions: 3
    replication-factor: 2
    retention.ms: 604800000
  
  ai-events:
    partitions: 5
    replication-factor: 2
    retention.ms: 2592000000  # 30 days
  
  infrastructure-events:
    partitions: 3
    replication-factor: 2
    retention.ms: 604800000
  
  security-events:
    partitions: 2
    replication-factor: 3
    retention.ms: 7776000000  # 90 days

consumer:
  group.id: yappc-consumers
  auto.offset.reset: earliest
  enable.auto.commit: false
  max.poll.records: 500
```

### 2. Event Schema

**Base Event:**
```java
package com.ghatana.yappc.events;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {
    private final String eventId;
    private final String eventType;
    private final String aggregateId;
    private final String tenantId;
    private final String userId;
    private final Instant timestamp;
    private final int version;
    
    protected DomainEvent(String eventType, String aggregateId, String tenantId, String userId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.tenantId = tenantId;
        this.userId = userId;
        this.timestamp = Instant.now();
        this.version = 1;
    }
    
    // Getters
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public Instant getTimestamp() { return timestamp; }
    public int getVersion() { return version; }
}
```

**Canvas Events:**
```java
package com.ghatana.yappc.events.canvas;

import com.ghatana.yappc.events.DomainEvent;

public class NodeCreatedEvent extends DomainEvent {
    private final String nodeId;
    private final String nodeType;
    private final Position position;
    private final Map<String, Object> data;
    
    public NodeCreatedEvent(String canvasId, String tenantId, String userId,
                           String nodeId, String nodeType, Position position,
                           Map<String, Object> data) {
        super("canvas.node.created", canvasId, tenantId, userId);
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.position = position;
        this.data = data;
    }
    
    // Getters
}

public class EdgeConnectedEvent extends DomainEvent {
    private final String edgeId;
    private final String sourceNodeId;
    private final String targetNodeId;
    private final String edgeType;
    
    public EdgeConnectedEvent(String canvasId, String tenantId, String userId,
                             String edgeId, String sourceNodeId, String targetNodeId,
                             String edgeType) {
        super("canvas.edge.connected", canvasId, tenantId, userId);
        this.edgeId = edgeId;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.edgeType = edgeType;
    }
    
    // Getters
}

public class CanvasSavedEvent extends DomainEvent {
    private final int nodeCount;
    private final int edgeCount;
    private final String snapshot;
    
    public CanvasSavedEvent(String canvasId, String tenantId, String userId,
                           int nodeCount, int edgeCount, String snapshot) {
        super("canvas.saved", canvasId, tenantId, userId);
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.snapshot = snapshot;
    }
    
    // Getters
}
```

**Collaboration Events:**
```java
package com.ghatana.yappc.events.collaboration;

import com.ghatana.yappc.events.DomainEvent;

public class UserJoinedEvent extends DomainEvent {
    private final String userName;
    private final String userColor;
    
    public UserJoinedEvent(String canvasId, String tenantId, String userId,
                          String userName, String userColor) {
        super("collaboration.user.joined", canvasId, tenantId, userId);
        this.userName = userName;
        this.userColor = userColor;
    }
    
    // Getters
}

public class CommentAddedEvent extends DomainEvent {
    private final String commentId;
    private final String nodeId;
    private final String text;
    
    public CommentAddedEvent(String canvasId, String tenantId, String userId,
                            String commentId, String nodeId, String text) {
        super("collaboration.comment.added", canvasId, tenantId, userId);
        this.commentId = commentId;
        this.nodeId = nodeId;
        this.text = text;
    }
    
    // Getters
}

public class CursorMovedEvent extends DomainEvent {
    private final double x;
    private final double y;
    
    public CursorMovedEvent(String canvasId, String tenantId, String userId,
                           double x, double y) {
        super("collaboration.cursor.moved", canvasId, tenantId, userId);
        this.x = x;
        this.y = y;
    }
    
    // Getters
}
```

**AI Events:**
```java
package com.ghatana.yappc.events.ai;

import com.ghatana.yappc.events.DomainEvent;

public class AIGenerationStartedEvent extends DomainEvent {
    private final String requestId;
    private final String feature;
    private final String prompt;
    
    public AIGenerationStartedEvent(String canvasId, String tenantId, String userId,
                                   String requestId, String feature, String prompt) {
        super("ai.generation.started", canvasId, tenantId, userId);
        this.requestId = requestId;
        this.feature = feature;
        this.prompt = prompt;
    }
    
    // Getters
}

public class AIGenerationCompletedEvent extends DomainEvent {
    private final String requestId;
    private final String result;
    private final long durationMs;
    private final double cost;
    
    public AIGenerationCompletedEvent(String canvasId, String tenantId, String userId,
                                     String requestId, String result,
                                     long durationMs, double cost) {
        super("ai.generation.completed", canvasId, tenantId, userId);
        this.requestId = requestId;
        this.result = result;
        this.durationMs = durationMs;
        this.cost = cost;
    }
    
    // Getters
}
```

### 3. Event Publisher

```java
package com.ghatana.yappc.events;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EventPublisher {
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    
    public EventPublisher(KafkaProducer<String, String> producer) {
        this.producer = producer;
        this.objectMapper = new ObjectMapper();
    }
    
    public void publish(DomainEvent event) {
        try {
            String topic = getTopicForEvent(event.getEventType());
            String key = event.getAggregateId();
            String value = objectMapper.writeValueAsString(event);
            
            ProducerRecord<String, String> record = new ProducerRecord<>(
                topic, 
                key, 
                value
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    // Log error
                    System.err.println("Failed to publish event: " + exception.getMessage());
                } else {
                    // Log success
                    System.out.println("Published event to " + metadata.topic() + 
                                     " partition " + metadata.partition());
                }
            });
        } catch (Exception e) {
            throw new EventPublishException("Failed to publish event", e);
        }
    }
    
    private String getTopicForEvent(String eventType) {
        if (eventType.startsWith("canvas.")) return "canvas-events";
        if (eventType.startsWith("collaboration.")) return "collaboration-events";
        if (eventType.startsWith("ai.")) return "ai-events";
        if (eventType.startsWith("infrastructure.")) return "infrastructure-events";
        if (eventType.startsWith("security.")) return "security-events";
        return "default-events";
    }
}
```

### 4. Event Consumer

```java
package com.ghatana.yappc.events;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class EventConsumer<T extends DomainEvent> {
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final Class<T> eventClass;
    
    protected EventConsumer(KafkaConsumer<String, String> consumer, 
                          Class<T> eventClass) {
        this.consumer = consumer;
        this.objectMapper = new ObjectMapper();
        this.eventClass = eventClass;
    }
    
    public void start() {
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            for (ConsumerRecord<String, String> record : records) {
                try {
                    T event = objectMapper.readValue(record.value(), eventClass);
                    handle(event);
                    consumer.commitSync();
                } catch (Exception e) {
                    // Handle error - send to DLQ
                    handleError(record, e);
                }
            }
        }
    }
    
    protected abstract void handle(T event);
    
    protected void handleError(ConsumerRecord<String, String> record, Exception e) {
        // Send to dead letter queue
        System.err.println("Failed to process event: " + e.getMessage());
    }
}
```

### 5. Event Handlers

```java
package com.ghatana.yappc.events.handlers;

import com.ghatana.yappc.events.EventConsumer;
import com.ghatana.yappc.events.canvas.NodeCreatedEvent;

public class NodeCreatedEventHandler extends EventConsumer<NodeCreatedEvent> {
    
    public NodeCreatedEventHandler(KafkaConsumer<String, String> consumer) {
        super(consumer, NodeCreatedEvent.class);
    }
    
    @Override
    protected void handle(NodeCreatedEvent event) {
        // Update read model
        // Send notifications
        // Update analytics
        // Trigger workflows
        
        System.out.println("Node created: " + event.getNodeId() + 
                         " on canvas: " + event.getAggregateId());
    }
}
```

### 6. Event Store

```sql
-- Event store schema
CREATE TABLE event_store (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    version INT NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_aggregate ON event_store(aggregate_id, version);
CREATE INDEX idx_event_type ON event_store(event_type);
CREATE INDEX idx_event_tenant ON event_store(tenant_id);
CREATE INDEX idx_event_timestamp ON event_store(timestamp DESC);

-- Event snapshots for performance
CREATE TABLE event_snapshots (
    aggregate_id VARCHAR(255) PRIMARY KEY,
    version INT NOT NULL,
    state JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🔄 CQRS Pattern

### Command Side

```java
package com.ghatana.yappc.commands;

public interface Command {
    String getCommandId();
    String getTenantId();
    String getUserId();
}

public class CreateNodeCommand implements Command {
    private final String commandId;
    private final String canvasId;
    private final String tenantId;
    private final String userId;
    private final String nodeType;
    private final Position position;
    
    // Constructor, getters
}

public class CommandHandler<T extends Command> {
    private final EventPublisher eventPublisher;
    
    public void handle(T command) {
        // Validate command
        // Execute business logic
        // Publish events
    }
}
```

### Query Side

```java
package com.ghatana.yappc.queries;

public interface Query {
    String getQueryId();
    String getTenantId();
}

public class GetCanvasQuery implements Query {
    private final String queryId;
    private final String canvasId;
    private final String tenantId;
    
    // Constructor, getters
}

public class QueryHandler<T extends Query, R> {
    private final ReadModelRepository repository;
    
    public R handle(T query) {
        // Query read model
        // Return result
    }
}
```

---

## 🎯 Implementation Phases

### Phase 1: Infrastructure (Week 1-2)
- [ ] Set up Kafka cluster
- [ ] Create event schema
- [ ] Implement event publisher
- [ ] Implement event consumer
- [ ] Set up event store

### Phase 2: Domain Events (Week 3-4)
- [ ] Define all domain events
- [ ] Implement event handlers
- [ ] Create read models
- [ ] Implement CQRS

### Phase 3: Integration (Week 5)
- [ ] Migrate canvas operations
- [ ] Migrate collaboration features
- [ ] Migrate AI features
- [ ] Add monitoring

### Phase 4: Optimization (Week 6)
- [ ] Performance tuning
- [ ] Error handling
- [ ] Documentation
- [ ] Training

---

## 📊 Benefits

### Scalability
- Horizontal scaling of consumers
- Async processing
- Better resource utilization

### Decoupling
- Services don't need to know about each other
- Easy to add new features
- Better testability

### Reliability
- Event replay capability
- Audit trail
- Disaster recovery

### Real-time
- Instant updates
- Live collaboration
- Better UX

---

## 🔍 Monitoring

### Metrics
- Event throughput
- Consumer lag
- Processing time
- Error rate

### Dashboards
- Kafka metrics (Prometheus + Grafana)
- Event flow visualization
- Consumer health
- Topic statistics

---

**Status:** Ready for Implementation  
**Next Step:** Set up Kafka infrastructure
