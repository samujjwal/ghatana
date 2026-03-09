package query

import (
    "context"
    "database/sql"
    "encoding/json"
    "testing"
    "time"

    "github.com/ClickHouse/clickhouse-go/v2"
    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
    "go.uber.org/zap/zaptest"
    "google.golang.org/grpc"
    "google.golang.org/grpc/codes"
    "google.golang.org/protobuf/types/known/timestamppb"

    pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

func setupTestDB(t *testing.T) (*sql.DB, func()) {
    dsn := "clickhouse://localhost:9000/default?dial_timeout=10s&read_timeout=20s"
    conn, err := sql.Open("clickhouse", dsn)
    require.NoError(t, err)

    // Create test tables
    _, err = conn.Exec(`
        CREATE TABLE IF NOT EXISTS events (
            timestamp DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
            event_id String CODEC(ZSTD(1)),
            tenant_id String CODEC(ZSTD(1)),
            device_id String CODEC(ZSTD(1)),
            session_id String CODEC(ZSTD(1)),
            source_type Enum8('extension' = 1, 'desktop' = 2, 'agent' = 3) CODEC(ZSTD(1)),
            event_type String CODEC(ZSTD(1)),
            payload String CODEC(ZSTD(1)),
            labels Map(String, String) CODEC(ZSTD(1))
        ) ENGINE = MergeTree()
        PARTITION BY toStartOfDay(timestamp)
        ORDER BY (tenant_id, device_id, toStartOfDay(timestamp), event_type, source_type);
    `)
    require.NoError(t, err, "failed to create events table")

    // Cleanup function
    cleanup := func() {
        _, _ = conn.Exec("DROP TABLE IF EXISTS events")
        _ = conn.Close()
    }

    return conn, cleanup
}

type RawEventRow struct {
    EventID    string
    TenantID   string
    DeviceID   string
    SessionID  string
    SourceType string
    EventType  string
    Timestamp  time.Time
    Payload    map[string]interface{}
    Labels     map[string]string
}

func insertTestEvents(t *testing.T, db *sql.DB, events []RawEventRow) {
    tx, err := db.Begin()
    require.NoError(t, err)

    stmt, err := tx.Prepare(`
        INSERT INTO events (
            timestamp, event_id, tenant_id, device_id, session_id,
            source_type, event_type, payload, labels
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `)
    require.NoError(t, err)
    defer stmt.Close()

    for _, e := range events {
        payload, err := json.Marshal(e.Payload)
        require.NoError(t, err)

        _, err = stmt.Exec(
            e.Timestamp,
            e.EventID,
            e.TenantID,
            e.DeviceID,
            e.SessionID,
            e.SourceType,
            e.EventType,
            string(payload),
            clickhouse.Map(e.Labels),
        )
        require.NoError(t, err)
    }

    err = tx.Commit()
    require.NoError(t, err)
}

func TestQueryService_QueryEvents(t *testing.T) {
    db, cleanup := setupTestDB(t)
    defer cleanup()

    logger := zaptest.NewLogger(t)
    service := NewService(db, logger)

    now := time.Now()
    testEvents := []RawEventRow{
        {
            EventID:    "event1",
            TenantID:   "tenant1",
            DeviceID:   "device1",
            SessionID:  "session1",
            SourceType: "extension",
            EventType:  "page_view",
            Timestamp:  now.Add(-1 * time.Hour),
            Payload: map[string]interface{}{
                "url":    "https://example.com",
                "title":  "Example",
                "domain": "example.com",
            },
            Labels: map[string]string{
                "browser": "chrome",
                "os":      "linux",
            },
        },
        {
            EventID:    "event2",
            TenantID:   "tenant1",
            DeviceID:   "device1",
            SessionID:  "session1",
            SourceType: "extension",
            EventType:  "click",
            Timestamp:  now.Add(-30 * time.Minute),
            Payload: map[string]interface{}{
                "element": "button",
                "text":    "Submit",
            },
            Labels: map[string]string{
                "browser": "chrome",
                "os":      "linux",
            },
        },
        {
            EventID:    "event3",
            TenantID:   "tenant1",
            DeviceID:   "device2",
            SessionID:  "session2",
            SourceType: "desktop",
            EventType:  "app_start",
            Timestamp:  now.Add(-15 * time.Minute),
            Payload: map[string]interface{}{
                "version": "1.0.0",
            },
            Labels: map[string]string{
                "os": "macos",
            },
        },
    }

    // Insert test data
    insertTestEvents(t, db, testEvents)

    tests := []struct {
        name    string
        req     *pb.QueryEventsRequest
        want    int
        wantErr bool
    }{
		{
			name: "query all events",
			req: &pb.QueryEventsRequest{
				PageSize: 10,
			},
			want: 3,
		},
		{
			name: "filter by event type",
			req: &pb.QueryEventsRequest{
				PageSize: 10,
				Filter: &pb.EventFilter{
					AllOf: []*pb.EventFilterCondition{
						{
							Field:       "event_type",
							StringValue: "page_view",
							Operator:    "=",
						},
					},
				},
			},
			want: 1,
		},
		{
			name: "filter by time range",
			req: &pb.QueryEventsRequest{
				PageSize: 10,
				TimeRange: &pb.TimeRange{
					StartTime: timestamppb.New(now.Add(-45 * time.Minute)),
					EndTime:   timestamppb.New(now.Add(-20 * time.Minute)),
				},
			},
			want: 1, // Only the click event should be in this range
		},
		{
			name: "pagination",
			req: &pb.QueryEventsRequest{
				PageSize: 2,
				Sort: []*pb.QueryEventsRequest_SortOption{
					{
						Field:     pb.EventSortField_EVENT_SORT_FIELD_TIMESTAMP,
						Direction: pb.SortDirection_SORT_DIRECTION_ASC,
					},
				},
			},
			want: 2,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			resp, err := service.QueryEvents(context.Background(), tt.req)
			if tt.wantErr {
				assert.Error(t, err)
				return
			}

			require.NoError(t, err)
			assert.Len(t, resp.Events, tt.want)

			// If we requested a specific page size, verify it was respected
			if tt.req.PageSize > 0 && tt.req.PageSize < int32(tt.want) {
				assert.Len(t, resp.Events, int(tt.req.PageSize))
				assert.NotEmpty(t, resp.NextPageToken)
			}

			// Verify the response status
			assert.NotNil(t, resp.Status)
			assert.Equal(t, int32(codes.OK), resp.Status.Code)
		})
	}
}

func TestQueryService_GetMetrics(t *testing.T) {
	db, cleanup := setupTestDB(t)
	defer cleanup()

	logger := zaptest.NewLogger(t)
	service := NewService(db, logger)

	now := time.Now()

	// Insert test data
	insertTestEvents(t, db, []*pb.EventWithMetadata{
		{
			Event: &pb.Event{
				Id: "metric1",
			},
			TenantId:   "tenant1",
			DeviceId:   "device1",
			EventType:  "metric",
			SourceType: pb.SourceType_SOURCE_TYPE_AGENT,
			Timestamp:  timestamppb.New(now.Add(-1 * time.Hour)),
			Payload: map[string]interface{}{
				"name":  "cpu_usage",
				"value": 42.5,
				"unit":  "percent",
			},
			Labels: map[string]string{
				"hostname": "host1",
			},
		},
		{
			Event: &pb.Event{
				Id: "metric2",
			},
			TenantId:   "tenant1",
			DeviceId:   "device1",
			EventType:  "metric",
			SourceType: pb.SourceType_SOURCE_TYPE_AGENT,
			Timestamp:  timestamppb.New(now.Add(-30 * time.Minute)),
			Payload: map[string]interface{}{
				"name":  "memory_usage",
				"value": 75.2,
				"unit":  "percent",
			},
			Labels: map[string]string{
				"hostname": "host1",
			},
		},
	})

	tests := []struct {
		name    string
		req     *pb.GetMetricsRequest
		want    int
		wantErr bool
	}{
		{
			name: "get all metrics",
			req: &pb.GetMetricsRequest{
				Pagination: &pb.PaginationRequest{
					PageSize: 10,
				},
			},
			want: 2,
		},
		{
			name: "filter by metric name",
			req: &pb.GetMetricsRequest{
				Pagination: &pb.PaginationRequest{
					PageSize: 10,
				},
				Filter: &pb.Filter{
					Field:    "payload.name",
					Operator: "=",
					Value:    "cpu_usage",
				},
			},
			want: 1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			resp, err := service.GetMetrics(context.Background(), tt.req)
			if tt.wantErr {
				assert.Error(t, err)
				return
			}

			require.NoError(t, err)
			assert.Len(t, resp.Metrics, tt.want)

			// Verify the response status
			assert.NotNil(t, resp.Status)
			assert.Equal(t, int32(codes.OK), resp.Status.Code)

			// Verify pagination
			if tt.want > 0 {
				assert.NotNil(t, resp.Pagination)
			}

			// Verify metric values
			for _, metric := range resp.Metrics {
				assert.NotEmpty(t, metric.Name)
				assert.True(t, metric.Value > 0)
			}
		})
	}
}

func TestQueryService_StreamEvents(t *testing.T) {
	db, cleanup := setupTestDB(t)
	defer cleanup()

	logger := zaptest.NewLogger(t)
	service := NewService(db, logger)

	// Mock stream
	mockStream := &mockQueryService_StreamEventsServer{
		ctx: context.Background(),
	}

	// Test with follow=false (single response)
	req := &pb.StreamEventsRequest{
		Follow: false,
	}

	err := service.StreamEvents(req, mockStream)
	require.NoError(t, err)

	// Test with follow=true (streaming)
	req.Follow = true
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	mockStream.ctx = ctx

	go func() {
		err = service.StreamEvents(req, mockStream)
		require.NoError(t, err)
	}()

	// Wait for a short time to ensure the streaming starts
	time.Sleep(100 * time.Millisecond)

	// The test will be terminated by the context timeout
}

// mockQueryService_StreamEventsServer is a mock implementation of QueryService_StreamEventsServer
type mockQueryService_StreamEventsServer struct {
	grpc.ServerStream
	ctx context.Context
}

func (m *mockQueryService_StreamEventsServer) Context() context.Context {
	return m.ctx
}

func (m *mockQueryService_StreamEventsServer) Send(event *pb.EventWithMetadata) error {
	// Just return nil to simulate successful send
	return nil
}

func TestExtractMetricValue(t *testing.T) {
	tests := []struct {
		name  string
		input map[string]interface{}
		want  float64
	}{
		{
			name: "float64 value",
			input: map[string]interface{}{
				"value": 42.5,
			},
			want: 42.5,
		},
		{
			name: "int value",
			input: map[string]interface{}{
				"value": 42,
			},
			want: 42.0,
		},
		{
			name: "string value",
			input: map[string]interface{}{
				"value": "42.5",
			},
			want: 0, // Should return 0 for unsupported types
		},
		{
			name: "no value field",
			input: map[string]interface{}{
				"other": 42.5,
			},
			want: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := extractMetricValue(tt.input)
			assert.Equal(t, tt.want, got)
		})
	}
}

func TestExtractCommandStatus(t *testing.T) {
	tests := []struct {
		name   string
		labels map[string]string
		want   pb.CommandStatus
	}{
		{
			name: "pending status",
			labels: map[string]string{
				"status": "pending",
			},
			want: pb.CommandStatus_COMMAND_STATUS_PENDING,
		},
		{
			name: "in_progress status",
			labels: map[string]string{
				"status": "in_progress",
			},
			want: pb.CommandStatus_COMMAND_STATUS_IN_PROGRESS,
		},
		{
			name: "completed status",
			labels: map[string]string{
				"status": "completed",
			},
			want: pb.CommandStatus_COMMAND_STATUS_COMPLETED,
		},
		{
			name: "failed status",
			labels: map[string]string{
				"status": "failed",
			},
			want: pb.CommandStatus_COMMAND_STATUS_FAILED,
		},
		{
			name:   "no status",
			labels: map[string]string{},
			want:   pb.CommandStatus_COMMAND_STATUS_UNSPECIFIED,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := extractCommandStatus(tt.labels)
			assert.Equal(t, tt.want, got)
		})
	}
}
