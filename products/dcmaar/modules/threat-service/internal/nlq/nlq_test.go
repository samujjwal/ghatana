package nlq

import (
	"context"
	"strings"
	"testing"
	"time"
)

func TestGrammarParser_Parse(t *testing.T) {
	metadata := &QueryMetadata{
		Tables: []TableInfo{
			{
				Name: "events",
				Columns: []ColumnInfo{
					{Name: "timestamp", Type: "DateTime"},
					{Name: "service", Type: "String"},
					{Name: "error_rate", Type: "Float64"},
				},
			},
		},
	}

	parser := NewGrammarParser(metadata)

	tests := []struct {
		name      string
		query     string
		expected  QueryIntent
		wantError bool
	}{
		{
			name:  "basic metric query",
			query: "show error rate for service-api",
			expected: QueryIntent{
				Metric:       "error_rate",
				OriginalText: "show error rate for service-api",
				ParsedBy:     "grammar",
				Filters:      map[string]string{"service": "service-api"},
			},
		},
		{
			name:  "time range query",
			query: "cpu usage in last 24 hours",
			expected: QueryIntent{
				Metric:       "cpu_usage",
				OriginalText: "cpu usage in last 24 hours",
				ParsedBy:     "grammar",
			},
		},
		{
			name:  "aggregation query",
			query: "average response time for service-web",
			expected: QueryIntent{
				Metric:       "response_time",
				Aggregation:  "average",
				OriginalText: "average response time for service-web",
				ParsedBy:     "grammar",
				Filters:      map[string]string{"service": "service-web"},
			},
		},
		{
			name:  "spike detection query",
			query: "show spike in error rate after deploy-123",
			expected: QueryIntent{
				Metric:       "error_rate",
				EventType:    "deployment",
				OriginalText: "show spike in error rate after deploy-123",
				ParsedBy:     "grammar",
			},
		},
		{
			name:  "group by query",
			query: "memory usage grouped by host",
			expected: QueryIntent{
				Metric:       "memory_usage",
				GroupBy:      []string{"host"},
				OriginalText: "memory usage grouped by host",
				ParsedBy:     "grammar",
			},
		},
		{
			name:      "empty query",
			query:     "",
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			intent, err := parser.Parse(context.Background(), tt.query)

			if tt.wantError {
				if err == nil {
					t.Errorf("Expected error but got none")
				}
				return
			}

			if err != nil {
				t.Errorf("Unexpected error: %v", err)
				return
			}

			if intent.Metric != tt.expected.Metric {
				t.Errorf("Expected metric %s, got %s", tt.expected.Metric, intent.Metric)
			}

			if intent.Aggregation != tt.expected.Aggregation {
				t.Errorf("Expected aggregation %s, got %s", tt.expected.Aggregation, intent.Aggregation)
			}

			if intent.EventType != tt.expected.EventType {
				t.Errorf("Expected event type %s, got %s", tt.expected.EventType, intent.EventType)
			}

			if len(intent.GroupBy) != len(tt.expected.GroupBy) {
				t.Errorf("Expected %d group by fields, got %d", len(tt.expected.GroupBy), len(intent.GroupBy))
			}

			if intent.ParsedBy != tt.expected.ParsedBy {
				t.Errorf("Expected parsed by %s, got %s", tt.expected.ParsedBy, intent.ParsedBy)
			}
		})
	}
}

func TestGrammarParser_Validate(t *testing.T) {
	parser := NewGrammarParser(&QueryMetadata{})

	tests := []struct {
		name      string
		intent    QueryIntent
		wantError bool
	}{
		{
			name: "valid intent",
			intent: QueryIntent{
				Metric: "cpu_usage",
				TimeRange: TimeRange{
					Start: time.Now().Add(-1 * time.Hour),
					End:   time.Now(),
				},
			},
			wantError: false,
		},
		{
			name: "no metric",
			intent: QueryIntent{
				TimeRange: TimeRange{
					Start: time.Now().Add(-1 * time.Hour),
					End:   time.Now(),
				},
			},
			wantError: true,
		},
		{
			name: "invalid time range",
			intent: QueryIntent{
				Metric: "cpu_usage",
				TimeRange: TimeRange{
					Start: time.Now(),
					End:   time.Now().Add(-1 * time.Hour), // End before start
				},
			},
			wantError: true,
		},
		{
			name: "time range too large",
			intent: QueryIntent{
				Metric: "cpu_usage",
				TimeRange: TimeRange{
					Start: time.Now().Add(-60 * 24 * time.Hour), // 60 days
					End:   time.Now(),
				},
			},
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := parser.Validate(context.Background(), &tt.intent)
			if tt.wantError && err == nil {
				t.Errorf("Expected error but got none")
			}
			if !tt.wantError && err != nil {
				t.Errorf("Unexpected error: %v", err)
			}
		})
	}
}

func TestSQLGenerator_GenerateSQL(t *testing.T) {
	metadata := &QueryMetadata{
		Tables: []TableInfo{
			{Name: "events", Columns: []ColumnInfo{{Name: "error_rate", Type: "Float64"}}},
			{Name: "metrics", Columns: []ColumnInfo{{Name: "cpu_usage", Type: "Float64"}}},
		},
	}

	generator := NewSQLGenerator(metadata)

	tests := []struct {
		name     string
		intent   QueryIntent
		contains []string // SQL should contain these strings
	}{
		{
			name: "basic metric query",
			intent: QueryIntent{
				Metric: "error_rate",
				TimeRange: TimeRange{
					Start: time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC),
					End:   time.Date(2024, 1, 2, 0, 0, 0, 0, time.UTC),
				},
			},
			contains: []string{"SELECT", "error_rate", "FROM events", "WHERE", "timestamp >=", "timestamp <=", "LIMIT 1000"},
		},
		{
			name: "aggregated query",
			intent: QueryIntent{
				Metric:      "cpu_usage",
				Aggregation: "avg",
				TimeRange: TimeRange{
					Start: time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC),
					End:   time.Date(2024, 1, 2, 0, 0, 0, 0, time.UTC),
				},
			},
			contains: []string{"SELECT", "toStartOfInterval", "avg(cpu_usage)", "GROUP BY", "ORDER BY"},
		},
		{
			name: "filtered query",
			intent: QueryIntent{
				Metric:  "error_rate",
				Filters: map[string]string{"service": "api"},
				TimeRange: TimeRange{
					Start: time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC),
					End:   time.Date(2024, 1, 2, 0, 0, 0, 0, time.UTC),
				},
			},
			contains: []string{"service = 'api'"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			sql, err := generator.GenerateSQL(context.Background(), &tt.intent)
			if err != nil {
				t.Errorf("Unexpected error: %v", err)
				return
			}

			for _, expected := range tt.contains {
				if !strings.Contains(sql.SQL, expected) {
					t.Errorf("Expected SQL to contain '%s', got: %s", expected, sql.SQL)
				}
			}
		})
	}
}

func TestSQLGenerator_ValidateSQL(t *testing.T) {
	generator := NewSQLGenerator(&QueryMetadata{})

	tests := []struct {
		name      string
		sql       SQLQuery
		wantError bool
	}{
		{
			name: "safe query",
			sql: SQLQuery{
				SQL:       "SELECT timestamp, error_rate FROM events WHERE timestamp >= '2024-01-01'",
				Tables:    []string{"events"},
				Functions: []string{""},
			},
			wantError: false,
		},
		{
			name: "forbidden table",
			sql: SQLQuery{
				SQL:    "SELECT * FROM forbidden_table",
				Tables: []string{"forbidden_table"},
			},
			wantError: true,
		},
		{
			name: "forbidden pattern",
			sql: SQLQuery{
				SQL:    "DROP TABLE events",
				Tables: []string{"events"},
			},
			wantError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := generator.ValidateSQL(context.Background(), &tt.sql)
			if tt.wantError && err == nil {
				t.Errorf("Expected error but got none")
			}
			if !tt.wantError && err != nil {
				t.Errorf("Unexpected error: %v", err)
			}
		})
	}
}

func TestCannedQueries(t *testing.T) {
	if len(CannedQueries) < 20 {
		t.Errorf("Expected at least 20 canned queries, got %d", len(CannedQueries))
	}

	// Ensure all canned queries are non-empty
	for i, query := range CannedQueries {
		if strings.TrimSpace(query) == "" {
			t.Errorf("Canned query %d is empty", i)
		}
	}
}

// Benchmark tests
func BenchmarkGrammarParser_Parse(b *testing.B) {
	metadata := &QueryMetadata{
		Tables: []TableInfo{
			{Name: "events", Columns: []ColumnInfo{{Name: "error_rate", Type: "Float64"}}},
		},
	}
	parser := NewGrammarParser(metadata)
	query := "show error rate for service-api in last 24 hours"

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, _ = parser.Parse(context.Background(), query)
	}
}

func BenchmarkSQLGenerator_GenerateSQL(b *testing.B) {
	metadata := &QueryMetadata{
		Tables: []TableInfo{
			{Name: "events", Columns: []ColumnInfo{{Name: "error_rate", Type: "Float64"}}},
		},
	}
	generator := NewSQLGenerator(metadata)
	intent := QueryIntent{
		Metric:  "error_rate",
		Filters: map[string]string{"service": "api"},
		TimeRange: TimeRange{
			Start: time.Now().Add(-24 * time.Hour),
			End:   time.Now(),
		},
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, _ = generator.GenerateSQL(context.Background(), &intent)
	}
}
