package query

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/redis/go-redis/v9"
	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
)

// Server implements the QueryService API
type Server struct {
	pb.UnimplementedQueryServiceServer
	db          *sql.DB
	logger      *zap.Logger
	redisClient *redis.Client
}

// NewServer creates a new QueryService server
func NewServer(db *sql.DB, logger *zap.Logger, redisClient *redis.Client) *Server {
	return &Server{
		db:          db,
		logger:      logger,
		redisClient: redisClient,
	}
}

// QueryEvents retrieves events based on the provided filter
func (s *Server) QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "query"))
	// Validate request
	if err := validateQueryEventsRequest(req); err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}

	// Check cache if enabled
	cacheKey := ""
	if s.redisClient != nil {
		cacheKey = s.generateCacheKey(req)
		if cached, err := s.getFromCache(ctx, cacheKey); err == nil && cached != nil {
			return cached, nil
		}
	}

	// Build and execute query
	query, args, err := buildEventsQuery(req)
	if err != nil {
		return nil, status.Errorf(codes.InvalidArgument, "invalid query: %v", err)
	}

	// Log query plan for analysis using request context for correlation
	go s.logQueryPlan(ctx, query, args...)

	// Execute query
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		logger.Error("query execution failed", zap.Error(err), zap.String("query", query))
		return nil, status.Errorf(codes.Internal, "query execution failed: %v", err)
	}
	defer rows.Close()

	// Process results
	var events []*pb.EventWithMetadata
	for rows.Next() {
		event, err := s.scanEvent(rows, req.Fields)
		if err != nil {
			logger.Warn("failed to scan event", zap.Error(err))
			continue
		}
		events = append(events, event)
	}

	if err := rows.Err(); err != nil {
		logger.Error("error iterating results", zap.Error(err))
		return nil, status.Errorf(codes.Internal, "error processing results: %v", err)
	}

	// Get total count if requested
	var totalCount int32
	if req.IncludeTotal {
		totalCount, err = s.getTotalCount(ctx, req)
		if err != nil {
			logger.Warn("failed to get total count", zap.Error(err))
		}
	}

	// TODO: Implement pagination token generation
	nextPageToken := ""

	// Cache the results if enabled
	resp := &pb.QueryEventsResponse{
		Events:        events,
		NextPageToken: nextPageToken,
		TotalCount:    totalCount,
		Status:        &pb.Status{Code: pb.StatusCode_STATUS_CODE_SUCCESS, Message: "success"},
	}
	if s.redisClient != nil && cacheKey != "" {
		go func(parent context.Context, log *zap.Logger) {
			cacheCtx, cancel := context.WithTimeout(parent, 5*time.Second)
			defer cancel()
			if err := s.cacheResponse(cacheCtx, cacheKey, resp); err != nil {
				telemetry.LoggerFromContextOr(cacheCtx, log).Warn("failed to cache query results", zap.Error(err))
			}
		}(ctx, logger)
	}

	return resp, nil
}

// buildEventsQuery constructs the SQL query and arguments based on the request
func buildEventsQuery(req *pb.QueryEventsRequest) (string, []interface{}, error) {
	var (
		selectClause strings.Builder
		whereClause  strings.Builder
		orderBy      strings.Builder
		args         []interface{}
		argIndex     = 1
	)

	// Build SELECT clause
	if len(req.Fields) == 0 {
		// Default to all fields if none specified
		selectClause.WriteString("SELECT tenant_id, device_id, session_id, event_type, source_type, timestamp, payload, labels")
	} else {
		// Ensure required fields are included
		fields := make(map[string]bool)
		for _, f := range req.Fields {
			fields[f] = true
		}

		// Always include required fields for proper event reconstruction
		requiredFields := []string{"tenant_id", "device_id", "session_id", "event_type", "source_type", "timestamp"}
		for _, f := range requiredFields {
			if !fields[f] {
				req.Fields = append(req.Fields, f)
			}
		}

		selectClause.WriteString("SELECT " + strings.Join(req.Fields, ", "))
	}

	// Add FROM clause
	query := fmt.Sprintf("%s FROM events", selectClause.String())

	// Build WHERE clause
	if req.TimeRange != nil {
		if req.TimeRange.StartTime != nil {
			whereClause.WriteString(fmt.Sprintf(" AND timestamp >= $%d", argIndex))
			args = append(args, req.TimeRange.StartTime.AsTime())
			argIndex++
		}
		if req.TimeRange.EndTime != nil {
			whereClause.WriteString(fmt.Sprintf(" AND timestamp <= $%d", argIndex))
			args = append(args, req.TimeRange.EndTime.AsTime())
			argIndex++
		}
	}

	// Add filter conditions
	if req.Filter != nil {
		filterSQL, filterArgs, err := buildFilter(req.Filter, &argIndex)
		if err != nil {
			return "", nil, err
		}
		if filterSQL != "" {
			whereClause.WriteString(" AND " + filterSQL)
			args = append(args, filterArgs...)
		}
	}

	// Add WHERE clause if needed
	if whereClause.Len() > 0 {
		// Remove leading " AND "
		whereStr := strings.TrimPrefix(whereClause.String(), " AND ")
		query += " WHERE " + whereStr
	}

	// Build ORDER BY clause
	if len(req.Sort) > 0 {
		orderBy.WriteString(" ORDER BY ")
		for i, sort := range req.Sort {
			if i > 0 {
				orderBy.WriteString(", ")
			}
			orderBy.WriteString(getSortField(sort.Field))
			if sort.Direction == pb.SortDirection_SORT_DIRECTION_DESC {
				orderBy.WriteString(" DESC")
			} else {
				orderBy.WriteString(" ASC")
			}
		}
		query += orderBy.String()
	}

	// Add LIMIT and OFFSET for pagination
	if req.PageSize > 0 {
		query += fmt.Sprintf(" LIMIT $%d", argIndex)
		args = append(args, req.PageSize)
		argIndex++

		if req.PageToken != "" {
			offset, err := decodePageToken(req.PageToken)
			if err != nil {
				return "", nil, fmt.Errorf("invalid page token: %v", err)
			}
			query += fmt.Sprintf(" OFFSET $%d", argIndex)
			args = append(args, offset)
		}
	}

	return query, args, nil
}

// buildFilter constructs the WHERE clause for the given filter
func buildFilter(filter *pb.EventFilter, argIndex *int) (string, []interface{}, error) {
	var conditions []string
	var args []interface{}

	// Handle ALL_OF conditions (AND)
	for _, cond := range filter.AllOf {
		sql, condArgs, err := buildCondition(cond, argIndex)
		if err != nil {
			return "", nil, err
		}
		if sql != "" {
			conditions = append(conditions, sql)
			args = append(args, condArgs...)
			*argIndex += len(condArgs)
		}
	}

	// Handle ANY_OF conditions (OR)
	if len(filter.AnyOf) > 0 {
		var orConditions []string
		for _, cond := range filter.AnyOf {
			sql, condArgs, err := buildCondition(cond, argIndex)
			if err != nil {
				return "", nil, err
			}
			if sql != "" {
				orConditions = append(orConditions, sql)
				args = append(args, condArgs...)
				*argIndex += len(condArgs)
			}
		}
		if len(orConditions) > 0 {
			conditions = append(conditions, "("+strings.Join(orConditions, " OR ")+")")
		}
	}

	// Handle NOT
	if filter.Not && len(conditions) > 0 {
		return "NOT (" + strings.Join(conditions, " AND ") + ")", args, nil
	}

	return strings.Join(conditions, " AND "), args, nil
}

// buildCondition builds a single filter condition
func buildCondition(cond *pb.EventFilterCondition, argIndex *int) (string, []interface{}, error) {
	if cond == nil {
		return "", nil, nil
	}

	field := cond.GetField()
	if field == "" {
		return "", nil, errors.New("field is required in filter condition")
	}

	operator := cond.GetOperator()
	if operator == "" {
		operator = "=" // Default to equality
	}

	// Handle different value types
	switch {
	case cond.GetStringValue() != "":
		sql := fmt.Sprintf("%s %s $%d", field, operator, *argIndex)
		return sql, []interface{}{cond.GetStringValue()}, nil

	case cond.GetIntValue() != 0:
		sql := fmt.Sprintf("%s %s $%d", field, operator, *argIndex)
		return sql, []interface{}{cond.GetIntValue()}, nil

	case cond.GetFloatValue() != 0:
		sql := fmt.Sprintf("%s %s $%d", field, operator, *argIndex)
		return sql, []interface{}{cond.GetFloatValue()}, nil

	case cond.GetBoolValue():
		// For boolean, we can use direct comparison
		value := cond.GetBoolValue()
		if operator == "!=" {
			value = !value
		}
		sql := fmt.Sprintf("%s = $%d", field, *argIndex)
		return sql, []interface{}{value}, nil

	case len(cond.GetStringValues()) > 0:
		if operator == "in" || operator == "not_in" {
			placeholders := make([]string, len(cond.GetStringValues()))
			for i := range cond.GetStringValues() {
				placeholders[i] = fmt.Sprintf("$%d", *argIndex+i)
			}
			sql := fmt.Sprintf("%s %s (%s)", field, map[bool]string{true: "NOT IN", false: "IN"}[operator == "not_in"],
				strings.Join(placeholders, ", "))
			args := make([]interface{}, len(cond.GetStringValues()))
			for i, v := range cond.GetStringValues() {
				args[i] = v
			}
			*argIndex += len(args)
			return sql, args, nil
		}

	case len(cond.GetIntValues()) > 0:
		if operator == "in" || operator == "not_in" {
			placeholders := make([]string, len(cond.GetIntValues()))
			for i := range cond.GetIntValues() {
				placeholders[i] = fmt.Sprintf("$%d", *argIndex+i)
			}
			sql := fmt.Sprintf("%s %s (%s)", field, map[bool]string{true: "NOT IN", false: "IN"}[operator == "not_in"],
				strings.Join(placeholders, ", "))
			args := make([]interface{}, len(cond.GetIntValues()))
			for i, v := range cond.GetIntValues() {
				args[i] = v
			}
			*argIndex += len(args)
			return sql, args, nil
		}
	}

	return "", nil, fmt.Errorf("unsupported filter condition for field %s with operator %s", field, operator)
}

// getSortField returns the database column name for a sort field
func getSortField(field pb.EventSortField) string {
	switch field {
	case pb.EventSortField_EVENT_SORT_FIELD_EVENT_TYPE:
		return "event_type"
	case pb.EventSortField_EVENT_SORT_FIELD_SOURCE_TYPE:
		return "source_type"
	case pb.EventSortField_EVENT_SORT_FIELD_DEVICE_ID:
		return "device_id"
	case pb.EventSortField_EVENT_SORT_FIELD_SESSION_ID:
		return "session_id"
	case pb.EventSortField_EVENT_SORT_FIELD_TIMESTAMP:
		fallthrough
	default:
		return "timestamp"
	}
}

// scanEvent scans a database row into an EventWithMetadata
func (s *Server) scanEvent(rows *sql.Rows, fields []string) (*pb.EventWithMetadata, error) {
	event := &pb.EventWithMetadata{
		Event: &pb.Event{},
	}

	// Create local variables for database fields
	var tenantID, deviceID, sessionID, sourceType string
	var timestamp time.Time
	var payload, labels []byte

	// If fields are specified, we need to scan dynamically
	if len(fields) > 0 {
		values := make([]interface{}, len(fields))
		for i, field := range fields {
			switch field {
			case "tenant_id":
				values[i] = &tenantID
			case "device_id":
				values[i] = &deviceID
			case "session_id":
				values[i] = &sessionID
			case "event_type":
				values[i] = &sourceType // Note: using sourceType variable for consistency
			case "source_type":
				values[i] = &sourceType
			case "timestamp":
				values[i] = &timestamp
			case "payload":
				values[i] = &payload
			case "labels":
				values[i] = &labels
			default:
				// For custom fields, we'll need to handle them specially
				var value interface{}
				values[i] = &value
			}
		}

		if err := rows.Scan(values...); err != nil {
			return nil, fmt.Errorf("failed to scan row: %v", err)
		}

		// After scanning, populate the Event struct with the scanned values
		event.Event.Source = sourceType
		event.Event.Timestamp = timestamppb.New(timestamp)
		if len(payload) > 0 {
			event.Event.Data = payload
		}
		return event, nil
	}

	// If no fields specified, scan all columns
	var ts time.Time

	err := rows.Scan(
		&tenantID,
		&deviceID,
		&sessionID,
		&sourceType,
		&sourceType, // event_type is same as source_type in our case
		&ts,
		&payload,
		&labels,
	)

	if err != nil {
		return nil, fmt.Errorf("failed to scan row: %v", err)
	}

	// Populate the Event struct
	event.Event.Source = sourceType
	event.Event.Timestamp = timestamppb.New(ts)
	if len(payload) > 0 {
		event.Event.Data = payload
	}

	event.Event.Timestamp = timestamppb.New(ts)

	// Store payload and labels in the Event.Data and Event.Metadata if needed
	if len(payload) > 0 {
		event.Event.Data = payload
	}
	// Labels would typically go in Event.Metadata, but that requires Struct type

	return event, nil
}

// getTotalCount returns the total number of matching events
func (s *Server) getTotalCount(ctx context.Context, req *pb.QueryEventsRequest) (int32, error) {
	logger := telemetry.LoggerFromContextOr(ctx, s.logger).With(zap.String("component", "query"))

	// Build a count query based on the original query
	var whereClause strings.Builder
	var args []interface{}
	argIndex := 1

	// Build WHERE clause from time range
	if req.TimeRange != nil {
		if req.TimeRange.StartTime != nil {
			whereClause.WriteString(fmt.Sprintf(" AND timestamp >= $%d", argIndex))
			args = append(args, req.TimeRange.StartTime.AsTime())
			argIndex++
		}
		if req.TimeRange.EndTime != nil {
			whereClause.WriteString(fmt.Sprintf(" AND timestamp <= $%d", argIndex))
			args = append(args, req.TimeRange.EndTime.AsTime())
			argIndex++
		}
	}

	// Add filter conditions
	if req.Filter != nil {
		filterSQL, filterArgs, err := buildFilter(req.Filter, &argIndex)
		if err != nil {
			return 0, err
		}
		if filterSQL != "" {
			whereClause.WriteString(" AND " + filterSQL)
			args = append(args, filterArgs...)
		}
	}

	// Build and execute the count query
	query := "SELECT COUNT(*) FROM events"
	if whereClause.Len() > 0 {
		whereStr := strings.TrimPrefix(whereClause.String(), " AND ")
		query += " WHERE " + whereStr
	}

	var count int32
	err := s.db.QueryRowContext(ctx, query, args...).Scan(&count)
	if err != nil {
		logger.Error("failed to get total count", zap.Error(err), zap.String("query", query))
		return 0, fmt.Errorf("failed to get total count: %v", err)
	}

	return count, nil
}

// decodePageToken decodes a page token to an offset
func decodePageToken(token string) (int32, error) {
	// TODO: Implement proper token decoding
	// For now, we'll just use the token as the offset
	var offset int32
	_, err := fmt.Sscanf(token, "%d", &offset)
	if err != nil {
		return 0, fmt.Errorf("invalid page token")
	}
	return offset, nil
}

// logQueryPlan logs the query plan for analysis
// generateCacheKey generates a cache key for the given request
func (s *Server) generateCacheKey(req *pb.QueryEventsRequest) string {
	// TODO: Implement cache key generation
	return ""
}

// getFromCache retrieves a cached response for the given key
func (s *Server) getFromCache(ctx context.Context, key string) (*pb.QueryEventsResponse, error) {
	// TODO: Implement cache retrieval
	return nil, nil
}

// cacheResponse caches the given response for the given key
func (s *Server) cacheResponse(ctx context.Context, key string, resp *pb.QueryEventsResponse) error {
	// TODO: Implement cache storage
	return nil
}

// validateQueryEventsRequest validates the query request
func validateQueryEventsRequest(req *pb.QueryEventsRequest) error {
	if req == nil {
		return fmt.Errorf("request cannot be nil")
	}
	// Add more validation as needed
	return nil
}
