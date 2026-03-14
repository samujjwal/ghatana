-- Flyway V006: Kafka Outbox Cursor — tracks per-event Kafka publish progress
--
-- One row per event_store row. Populated atomically within the same transaction as
-- the event_store INSERT in PostgresAggregateEventStore.appendEvent(). The relay
-- polls PENDING rows and atomically transitions them to PUBLISHED or DLQ_ROUTED.
--
-- Status lifecycle: PENDING → PUBLISHED (success) | DLQ_ROUTED (max retries exceeded)

CREATE TABLE IF NOT EXISTS kafka_outbox_cursor (
    cursor_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    event_id        UUID            NOT NULL,
    aggregate_type  VARCHAR(100)    NOT NULL,
    created_at_utc  TIMESTAMPTZ     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED | DLQ_ROUTED
    attempt_count   INTEGER         NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,

    CONSTRAINT pk_kafka_outbox_cursor  PRIMARY KEY (cursor_id),
    CONSTRAINT uq_kafka_outbox_event   UNIQUE      (event_id),
    CONSTRAINT fk_kafka_outbox_event   FOREIGN KEY (event_id) REFERENCES event_store(event_id),
    CONSTRAINT chk_kafka_cursor_status CHECK (status IN ('PENDING', 'PUBLISHED', 'DLQ_ROUTED'))
);

-- Relay polling index: efficiently fetch oldest pending rows
CREATE INDEX idx_kafka_outbox_pending
    ON kafka_outbox_cursor (created_at_utc ASC)
    WHERE status = 'PENDING';

COMMENT ON TABLE kafka_outbox_cursor IS
    'Kafka outbox relay cursor. One row per event_store event. '
    'Populated atomically with the event INSERT. '
    'Relay transitions PENDING rows to PUBLISHED or DLQ_ROUTED.';

COMMENT ON COLUMN kafka_outbox_cursor.status IS
    'PENDING = awaiting Kafka delivery; PUBLISHED = delivered; DLQ_ROUTED = terminal after max retries.';
COMMENT ON COLUMN kafka_outbox_cursor.attempt_count IS
    'Number of Kafka publish attempts. Relay increments on each transient failure.';
