package storage

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"go.uber.org/zap"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
)

// Storage handles all database operations for the server.
type Storage struct {
	db     *sql.DB
	logger *zap.Logger
}

// New creates a new Storage instance.
func New(dsn string, logger *zap.Logger) (*Storage, error) {
	db, err := sql.Open("clickhouse", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to ClickHouse: %w", err)
	}

	// Test the connection
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping ClickHouse: %w", err)
	}

	s := &Storage{
		db:     db,
		logger: logger.Named("storage"),
	}

	// Best-effort startup hint: check for expected tables and log guidance if missing
	s.hintOnMissingTables()

	return s, nil
}

// Close closes the database connection.
func (s *Storage) Close() error {
	return s.db.Close()
}

// HealthCheck performs a health check on the storage.
func (s *Storage) HealthCheck(ctx context.Context) error {
	return s.db.PingContext(ctx)
}

// hintOnMissingTables checks for expected tables and logs hints to run migrations if absent.
func (s *Storage) hintOnMissingTables() {
	if s.db == nil {
		return
	}
	check := func(name string) bool {
		var cnt int
		// system.tables is available in ClickHouse; filter by current database
		row := s.db.QueryRow(`SELECT count() FROM system.tables WHERE name = ? AND database = currentDatabase()`, name)
		if err := row.Scan(&cnt); err != nil {
			s.logger.Debug("table check failed", zap.String("table", name), zap.Error(err))
			return true // avoid noisy logs on errors
		}
		return cnt > 0
	}
	missing := []string{}
	for _, t := range []string{"events", "browser_events", "audit_events", "policies"} {
		if !check(t) {
			missing = append(missing, t)
		}
	}
	if len(missing) > 0 {
		s.logger.Warn("storage tables missing — consider running migrations", zap.Strings("missing", missing),
			zap.String("docs", "docs/migrations/"))
	}
}

// SaveEvents saves a batch of events to the database.
func (s *Storage) SaveEvents(ctx context.Context, envelopes []*pb.EventEnvelope) error {
	if len(envelopes) == 0 {
		return nil
	}

	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "storage"))

	tx, err := s.db.Begin()
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	stmt, err := tx.PrepareContext(ctx, `
		INSERT INTO events (
			timestamp, timestamp_ns, event_id, tenant_id, device_id, session_id,
			source_type, event_type, payload, labels
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`)
	if err != nil {
		return fmt.Errorf("failed to prepare statement: %w", err)
	}
	defer stmt.Close()

	for _, env := range envelopes {
		if env == nil || env.Meta == nil {
			continue
		}

		ts := time.Now()
		if env.Meta.Timestamp != 0 {
			ts = time.Unix(0, env.Meta.Timestamp*int64(time.Millisecond))
		}

		for _, event := range env.Events {
			if event == nil || event.Event == nil {
				continue
			}

			eventType := ""
			if event.ActivityType != pb.ActivityType_ACTIVITY_UNSPECIFIED {
				eventType = pb.ActivityType_name[int32(event.ActivityType)]
			}

			// Convert event to JSON
			payload, err := json.Marshal(event)
			if err != nil {
				logger.Warn("failed to marshal event",
					zap.Error(err),
					zap.String("event_id", event.Event.Id))
				continue
			}

			// Convert metadata to JSON
			var metadataJSON []byte
			if event.Event.Metadata != nil {
				metadataJSON, _ = json.Marshal(event.Event.Metadata.AsMap())
			} else {
				metadataJSON, _ = json.Marshal(map[string]interface{}{})
			}

			_, err = stmt.ExecContext(
				ctx,
				ts,                   // timestamp
				ts.UnixNano(),        // timestamp_ns
				event.Event.Id,       // event_id
				env.Meta.TenantId,    // tenant_id
				env.Meta.DeviceId,    // device_id
				env.Meta.SessionId,   // session_id
				env.Meta.Source,      // source_type
				eventType,            // event_type
				string(payload),      // payload
				string(metadataJSON), // labels (actually metadata)
			)

			if err != nil {
				logger.Warn("failed to insert event",
					zap.Error(err),
					zap.String("event_id", event.Event.Id))
			}
		}
	}

	return tx.Commit()
}

// SaveBrowserEvents saves browser events to the optimized browser_events table.
func (s *Storage) SaveBrowserEvents(ctx context.Context, events []*pb.EventWithMetadata, envelopes []*pb.EventEnvelope) error {
	if len(events) == 0 {
		return nil
	}

	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "storage"))

	tx, err := s.db.Begin()
	if err != nil {
		return fmt.Errorf("failed to begin transaction: %w", err)
	}
	defer tx.Rollback()

	stmt, err := tx.PrepareContext(ctx, `
		INSERT INTO browser_events (
			timestamp, timestamp_ns, event_id, tenant_id, device_id, session_id,
			source_type, tab_id, url, domain, method, status_code, latency_ms
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`)
	if err != nil {
		return fmt.Errorf("failed to prepare browser events statement: %w", err)
	}
	defer stmt.Close()

	// Create a map from event ID to envelope metadata for lookup
	eventToEnvelope := make(map[string]*pb.EnvelopeMeta)
	for _, envelope := range envelopes {
		if envelope.Meta != nil {
			for _, event := range envelope.Events {
				if event != nil && event.Event != nil {
					eventToEnvelope[event.Event.Id] = envelope.Meta
				}
			}
		}
	}

	for _, event := range events {
		if event == nil || event.Event == nil || event.Browser == nil {
			continue
		}

		// Look up envelope metadata for this event
		envMeta := eventToEnvelope[event.Event.Id]
		if envMeta == nil {
			logger.Warn("no envelope metadata found for event",
				zap.String("event_id", event.Event.Id))
			continue
		}

		ts := time.Now()
		if event.Event.Timestamp != nil {
			ts = event.Event.Timestamp.AsTime()
		}

		// Get source type from event metadata or use default
		sourceType := "extension" // default to extension for backwards compatibility
		if event.Event.Source != "" {
			sourceType = event.Event.Source
		}

		_, err = stmt.ExecContext(
			ctx,
			ts,                       // timestamp
			ts.UnixNano(),            // timestamp_ns
			event.Event.Id,           // event_id
			envMeta.TenantId,         // tenant_id
			envMeta.DeviceId,         // device_id
			envMeta.SessionId,        // session_id
			sourceType,               // source_type
			event.Browser.TabId,      // tab_id
			event.Browser.Url,        // url
			event.Browser.Domain,     // domain
			event.Browser.Method,     // method
			event.Browser.StatusCode, // status_code
			event.Browser.Latency,    // latency_ms
		)

		if err != nil {
			logger.Warn("failed to insert browser event",
				zap.Error(err),
				zap.String("event_id", event.Event.Id))
		}
	}

	return tx.Commit()
}

// QueryEvents retrieves events based on the provided filters.
func (s *Storage) QueryEvents(ctx context.Context, req *pb.GetEventsRequest) ([]*pb.EventWithMetadata, error) {
	// TODO: Implement event querying with proper filtering and pagination
	// This is a placeholder implementation
	return nil, nil
}

// QueryBrowserEvents retrieves browser events based on the provided filters.
func (s *Storage) QueryBrowserEvents(ctx context.Context, req *pb.QueryBrowserEventsRequest) ([]*pb.EventWithMetadata, error) {
	// TODO: Implement optimized browser event querying
	// This is a placeholder implementation
	return nil, nil
}

// GetEvent retrieves a single event by ID.
func (s *Storage) GetEvent(ctx context.Context, id string) (*pb.EventWithMetadata, error) {
	// TODO: Implement event retrieval by ID
	return nil, nil
}

// ExecContext executes a statement with context support for correlation service
func (s *Storage) ExecContext(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	return s.db.ExecContext(ctx, query, args...)
}

// QueryContext executes a query with context support for correlation service
func (s *Storage) QueryContext(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	return s.db.QueryContext(ctx, query, args...)
}

// QueryRowContext executes a query that is expected to return at most one row
func (s *Storage) QueryRowContext(ctx context.Context, query string, args ...interface{}) *sql.Row {
	return s.db.QueryRowContext(ctx, query, args...)
}
