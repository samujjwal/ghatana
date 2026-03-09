package nlq

import (
	"context"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// GrammarParser implements rule-based natural language query parsing
type GrammarParser struct {
	rules    []GrammarRule
	patterns map[string]*regexp.Regexp
	metadata *QueryMetadata
}

// GrammarRule represents a parsing rule for extracting query components
type GrammarRule struct {
	Name     string                                        // Rule name
	Pattern  string                                        // Regex pattern
	Extract  func(matches []string) map[string]interface{} // Extraction function
	Priority int                                           // Rule priority (higher = first)
	Category string                                        // Rule category
	Examples []string                                      // Example queries this rule matches
}

// NewGrammarParser creates a new grammar-based parser
func NewGrammarParser(metadata *QueryMetadata) *GrammarParser {
	parser := &GrammarParser{
		patterns: make(map[string]*regexp.Regexp),
		metadata: metadata,
	}

	parser.initializeRules()
	return parser
}

// Parse converts natural language query to structured intent using grammar rules
func (p *GrammarParser) Parse(ctx context.Context, query string) (*QueryIntent, error) {
	query = strings.TrimSpace(strings.ToLower(query))
	if query == "" {
		return nil, fmt.Errorf("empty query")
	}

	// Initialize intent with defaults
	intent := &QueryIntent{
		OriginalText: query,
		ParsedBy:     "grammar",
		Confidence:   0.0,
		Filters:      make(map[string]string),
		TimeRange:    TimeRange{Label: "last 1h"},
	}

	// Apply grammar rules in priority order
	for _, rule := range p.rules {
		if pattern, exists := p.patterns[rule.Name]; exists {
			if matches := pattern.FindStringSubmatch(query); matches != nil {
				extracted := rule.Extract(matches)
				p.applyExtracted(intent, extracted)
				intent.Confidence += 0.2 // Increase confidence for each matched rule
			}
		}
	}

	// Set default time range if not specified
	if intent.TimeRange.Start.IsZero() {
		intent.TimeRange = TimeRange{
			Start: time.Now().Add(-1 * time.Hour),
			End:   time.Now(),
			Label: "last 1h",
		}
	}

	// Ensure confidence doesn't exceed 1.0
	if intent.Confidence > 1.0 {
		intent.Confidence = 1.0
	}

	return intent, nil
}

// Validate checks if a query intent is valid and safe
func (p *GrammarParser) Validate(ctx context.Context, intent *QueryIntent) error {
	if intent.Metric == "" {
		return fmt.Errorf("no metric specified")
	}

	if intent.TimeRange.Start.IsZero() || intent.TimeRange.End.IsZero() {
		return fmt.Errorf("invalid time range")
	}

	if intent.TimeRange.End.Before(intent.TimeRange.Start) {
		return fmt.Errorf("end time before start time")
	}

	// Check if time range is too large (prevent resource exhaustion)
	maxRange := 30 * 24 * time.Hour // 30 days
	if intent.TimeRange.End.Sub(intent.TimeRange.Start) > maxRange {
		return fmt.Errorf("time range too large (max 30 days)")
	}

	return nil
}

// GetSuggestions provides query suggestions based on partial input
func (p *GrammarParser) GetSuggestions(ctx context.Context, partial string) ([]string, error) {
	partial = strings.TrimSpace(strings.ToLower(partial))

	suggestions := []string{
		"show error rate for service-name in last 24h",
		"average response time after deploy-123 for team-alpha",
		"count alerts grouped by severity since yesterday",
		"show spike in cpu usage for host-prod-01",
		"memory utilization above 80% in last week",
		"database connections grouped by service",
		"failed requests compared to last week",
		"show deployments for team-beta today",
		"disk usage trending up for cluster-main",
		"network latency between regions",
	}

	// Filter suggestions based on partial input
	var filtered []string
	for _, suggestion := range suggestions {
		if strings.Contains(suggestion, partial) || partial == "" {
			filtered = append(filtered, suggestion)
		}
	}

	return filtered, nil
}

// initializeRules sets up grammar rules for parsing natural language queries
func (p *GrammarParser) initializeRules() {
	p.rules = []GrammarRule{
		{
			Name:     "metric_with_service",
			Pattern:  `(?:show|get|display)?\s*([a-z_]+(?:\s+[a-z_]+)*)\s+(?:for|of)\s+([a-z0-9-_]+)`,
			Priority: 10,
			Category: "metric_extraction",
			Examples: []string{"show error rate for service-api", "cpu usage for host-web-01"},
			Extract: func(matches []string) map[string]interface{} {
				return map[string]interface{}{
					"metric":  strings.ReplaceAll(matches[1], " ", "_"),
					"service": matches[2],
				}
			},
		},
		{
			Name:     "time_range_relative",
			Pattern:  `(?:in|over|during|for)?\s+(?:the\s+)?last\s+(\d+)\s*(minutes?|mins?|hours?|hrs?|days?|weeks?)`,
			Priority: 8,
			Category: "time_extraction",
			Examples: []string{"in last 24 hours", "over the last 7 days"},
			Extract: func(matches []string) map[string]interface{} {
				amount, _ := strconv.Atoi(matches[1])
				unit := matches[2]

				var duration time.Duration
				switch {
				case strings.HasPrefix(unit, "min"):
					duration = time.Duration(amount) * time.Minute
				case strings.HasPrefix(unit, "hour") || strings.HasPrefix(unit, "hr"):
					duration = time.Duration(amount) * time.Hour
				case strings.HasPrefix(unit, "day"):
					duration = time.Duration(amount) * 24 * time.Hour
				case strings.HasPrefix(unit, "week"):
					duration = time.Duration(amount) * 7 * 24 * time.Hour
				}

				return map[string]interface{}{
					"time_range": TimeRange{
						Start: time.Now().Add(-duration),
						End:   time.Now(),
						Label: fmt.Sprintf("last %d %s", amount, unit),
					},
				}
			},
		},
		{
			Name:     "aggregation_function",
			Pattern:  `(average|avg|sum|count|total|max|maximum|min|minimum|median)\s+([a-z_]+(?:\s+[a-z_]+)*)`,
			Priority: 9,
			Category: "aggregation",
			Examples: []string{"average response time", "count alerts", "sum memory usage"},
			Extract: func(matches []string) map[string]interface{} {
				agg := matches[1]
				if agg == "avg" {
					agg = "average"
				}
				return map[string]interface{}{
					"aggregation": agg,
					"metric":      strings.ReplaceAll(matches[2], " ", "_"),
				}
			},
		},
		{
			Name:     "spike_detection",
			Pattern:  `(?:show\s+)?spike(?:s)?\s+(?:in\s+)?([a-z_]+(?:\s+[a-z_]+)*)\s*(?:after|following)\s+([a-z0-9-_]+)`,
			Priority: 7,
			Category: "anomaly",
			Examples: []string{"show spike in error rate after deploy-123", "spikes in cpu usage following release-v2"},
			Extract: func(matches []string) map[string]interface{} {
				return map[string]interface{}{
					"metric":     strings.ReplaceAll(matches[1], " ", "_"),
					"event_type": "deployment",
					"event_id":   matches[2],
					"threshold":  2.0, // 2 standard deviations above normal
				}
			},
		},
		{
			Name:     "comparison_query",
			Pattern:  `([a-z_]+(?:\s+[a-z_]+)*)\s+compared?\s+to\s+(?:last\s+)?(\d+)\s*(hours?|days?|weeks?)`,
			Priority: 6,
			Category: "comparison",
			Examples: []string{"error rate compared to last week", "response time compare to 7 days"},
			Extract: func(matches []string) map[string]interface{} {
				amount, _ := strconv.Atoi(matches[2])
				unit := matches[3]

				var duration time.Duration
				switch {
				case strings.HasPrefix(unit, "hour"):
					duration = time.Duration(amount) * time.Hour
				case strings.HasPrefix(unit, "day"):
					duration = time.Duration(amount) * 24 * time.Hour
				case strings.HasPrefix(unit, "week"):
					duration = time.Duration(amount) * 7 * 24 * time.Hour
				}

				now := time.Now()
				return map[string]interface{}{
					"metric": strings.ReplaceAll(matches[1], " ", "_"),
					"comparison": TimeRange{
						Start: now.Add(-2 * duration),
						End:   now.Add(-duration),
						Label: fmt.Sprintf("%d %s ago", amount, unit),
					},
				}
			},
		},
		{
			Name:     "threshold_filter",
			Pattern:  `([a-z_]+(?:\s+[a-z_]+)*)\s+(above|below|over|under|greater than|less than)\s+(\d+(?:\.\d+)?)([%kmg]*)`,
			Priority: 5,
			Category: "threshold",
			Examples: []string{"cpu usage above 80%", "memory under 4gb", "response time over 500ms"},
			Extract: func(matches []string) map[string]interface{} {
				value, _ := strconv.ParseFloat(matches[3], 64)
				unit := matches[4]

				// Convert units to base values
				switch unit {
				case "%":
					value = value / 100.0
				case "k":
					value = value * 1000
				case "m":
					value = value * 1000000
				case "g":
					value = value * 1000000000
				}

				operator := ">"
				if strings.Contains(matches[2], "below") || strings.Contains(matches[2], "under") || strings.Contains(matches[2], "less") {
					operator = "<"
				}

				return map[string]interface{}{
					"metric":    strings.ReplaceAll(matches[1], " ", "_"),
					"threshold": value,
					"operator":  operator,
				}
			},
		},
		{
			Name:     "group_by_clause",
			Pattern:  `(?:grouped?\s+by|group\s+by|by)\s+([a-z_]+(?:,\s*[a-z_]+)*)`,
			Priority: 4,
			Category: "grouping",
			Examples: []string{"grouped by service", "group by host,region", "by team"},
			Extract: func(matches []string) map[string]interface{} {
				groups := strings.Split(matches[1], ",")
				for i, group := range groups {
					groups[i] = strings.TrimSpace(group)
				}
				return map[string]interface{}{
					"group_by": groups,
				}
			},
		},
		{
			Name:     "team_filter",
			Pattern:  `(?:for\s+)?team[-\s]([a-z0-9-_]+)`,
			Priority: 3,
			Category: "filter",
			Examples: []string{"for team-alpha", "team beta", "team-backend"},
			Extract: func(matches []string) map[string]interface{} {
				return map[string]interface{}{
					"team": matches[1],
				}
			},
		},
	}

	// Compile regex patterns
	for _, rule := range p.rules {
		compiled, err := regexp.Compile(rule.Pattern)
		if err != nil {
			continue // Skip invalid patterns
		}
		p.patterns[rule.Name] = compiled
	}
}

// applyExtracted applies extracted components to the query intent
func (p *GrammarParser) applyExtracted(intent *QueryIntent, extracted map[string]interface{}) {
	for key, value := range extracted {
		switch key {
		case "metric":
			if v, ok := value.(string); ok {
				intent.Metric = v
			}
		case "aggregation":
			if v, ok := value.(string); ok {
				intent.Aggregation = v
			}
		case "time_range":
			if v, ok := value.(TimeRange); ok {
				intent.TimeRange = v
			}
		case "comparison":
			if v, ok := value.(TimeRange); ok {
				intent.Comparison = &v
			}
		case "threshold":
			if v, ok := value.(float64); ok {
				intent.Threshold = &v
			}
		case "event_type":
			if v, ok := value.(string); ok {
				intent.EventType = v
			}
		case "group_by":
			if v, ok := value.([]string); ok {
				intent.GroupBy = v
			}
		case "service", "team", "host", "region":
			if v, ok := value.(string); ok {
				intent.Filters[key] = v
			}
		}
	}
}
