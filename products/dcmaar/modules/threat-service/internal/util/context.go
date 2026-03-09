package util

import (
	"context"
	"time"

	"go.opentelemetry.io/otel/trace"
	"go.uber.org/zap"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/peer"
)

// ContextKey is a type for context keys.
type ContextKey string

// Context keys
const (
	// RequestIDKey is the context key for the request ID.
	RequestIDKey ContextKey = "x-request-id"
	// UserAgentKey is the context key for the user agent.
	UserAgentKey ContextKey = "user-agent"
	// ClientIPKey is the context key for the client IP.
	ClientIPKey ContextKey = "x-forwarded-for"
	// TenantIDKey is the context key for the tenant ID.
	TenantIDKey ContextKey = "x-tenant-id"
	// UserIDKey is the context key for the user ID.
	UserIDKey ContextKey = "x-user-id"
	// SessionIDKey is the context key for the session ID.
	SessionIDKey ContextKey = "x-session-id"
)

// WithRequestID returns a new context with the given request ID.
func WithRequestID(ctx context.Context, requestID string) context.Context {
	return context.WithValue(ctx, RequestIDKey, requestID)
}

// GetRequestID returns the request ID from the context.
func GetRequestID(ctx context.Context) string {
	if id, ok := ctx.Value(RequestIDKey).(string); ok {
		return id
	}

	// Try to get from gRPC metadata if available
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if ids := md.Get(string(RequestIDKey)); len(ids) > 0 {
			return ids[0]
		}
	}

	return ""
}

// WithLogger returns a new context with the given logger.
func WithLogger(ctx context.Context, logger *zap.Logger, fields ...zap.Field) (context.Context, *zap.Logger) {
	// Add request ID to logger if available
	if id := GetRequestID(ctx); id != "" {
		fields = append(fields, zap.String("request_id", id))
	}

	// Add client IP if available
	if ip := GetClientIP(ctx); ip != "" {
		fields = append(fields, zap.String("client_ip", ip))
	}

	// Add user agent if available
	if ua := GetUserAgent(ctx); ua != "" {
		fields = append(fields, zap.String("user_agent", ua))
	}

	// Add trace ID if available
	spanCtx := trace.SpanContextFromContext(ctx)
	if spanCtx.HasTraceID() {
		fields = append(fields, zap.String("trace_id", spanCtx.TraceID().String()))
	}

	// Add tenant ID if available
	if tenantID := GetTenantID(ctx); tenantID != "" {
		fields = append(fields, zap.String("tenant_id", tenantID))
	}

	// Add user ID if available
	if userID := GetUserID(ctx); userID != "" {
		fields = append(fields, zap.String("user_id", userID))
	}

	// Add session ID if available
	if sessionID := GetSessionID(ctx); sessionID != "" {
		fields = append(fields, zap.String("session_id", sessionID))
	}

	// Create a new logger with the fields
	logger = logger.With(fields...)

	// Return the context with the logger
	return context.WithValue(ctx, "logger", logger), logger
}

// GetLogger returns the logger from the context.
func GetLogger(ctx context.Context) *zap.Logger {
	if logger, ok := ctx.Value("logger").(*zap.Logger); ok {
		return logger
	}

	// If no logger is found, create a new one with context values
	logger := zap.NewNop()
	_, logger = WithLogger(ctx, logger)
	return logger
}

// GetClientIP returns the client IP from the context.
func GetClientIP(ctx context.Context) string {
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if xff := md.Get("x-forwarded-for"); len(xff) > 0 {
			return xff[0]
		}

		if xri := md.Get("x-real-ip"); len(xri) > 0 {
			return xri[0]
		}

		if peerInfo, ok := peer.FromContext(ctx); ok {
			return peerInfo.Addr.String()
		}
	}

	if ip, ok := ctx.Value(ClientIPKey).(string); ok {
		return ip
	}

	return ""
}

// GetUserAgent returns the user agent from the context.
func GetUserAgent(ctx context.Context) string {
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if ua := md.Get("user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("grpcgateway-user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("user_agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-http-user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-requested-with"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-forwarded-user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-http-user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-mobile-user-agent"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-ucbrowser-ua"); len(ua) > 0 {
			return ua[0]
		}

		if ua := md.Get("x-requested-with"); len(ua) > 0 {
			return ua[0]
		}
	}

	if ua, ok := ctx.Value(UserAgentKey).(string); ok {
		return ua
	}

	return ""
}

// WithTenantID returns a new context with the given tenant ID.
func WithTenantID(ctx context.Context, tenantID string) context.Context {
	return context.WithValue(ctx, TenantIDKey, tenantID)
}

// GetTenantID returns the tenant ID from the context.
func GetTenantID(ctx context.Context) string {
	if id, ok := ctx.Value(TenantIDKey).(string); ok {
		return id
	}

	// Try to get from gRPC metadata if available
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if ids := md.Get(string(TenantIDKey)); len(ids) > 0 {
			return ids[0]
		}

		// Try alternative headers
		for _, key := range []string{"x-tenant-id", "x-tenant", "tenant-id", "tenant"} {
			if ids := md.Get(key); len(ids) > 0 {
				return ids[0]
			}
		}
	}

	return ""
}

// WithUserID returns a new context with the given user ID.
func WithUserID(ctx context.Context, userID string) context.Context {
	return context.WithValue(ctx, UserIDKey, userID)
}

// GetUserID returns the user ID from the context.
func GetUserID(ctx context.Context) string {
	if id, ok := ctx.Value(UserIDKey).(string); ok {
		return id
	}

	// Try to get from gRPC metadata if available
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if ids := md.Get(string(UserIDKey)); len(ids) > 0 {
			return ids[0]
		}

		// Try alternative headers
		for _, key := range []string{"x-user-id", "x-user", "user-id", "user"} {
			if ids := md.Get(key); len(ids) > 0 {
				return ids[0]
			}
		}
	}

	return ""
}

// WithSessionID returns a new context with the given session ID.
func WithSessionID(ctx context.Context, sessionID string) context.Context {
	return context.WithValue(ctx, SessionIDKey, sessionID)
}

// GetSessionID returns the session ID from the context.
func GetSessionID(ctx context.Context) string {
	if id, ok := ctx.Value(SessionIDKey).(string); ok {
		return id
	}

	// Try to get from gRPC metadata if available
	if md, ok := metadata.FromIncomingContext(ctx); ok {
		if ids := md.Get(string(SessionIDKey)); len(ids) > 0 {
			return ids[0]
		}

		// Try alternative headers
		for _, key := range []string{"x-session-id", "x-session", "session-id", "session"} {
			if ids := md.Get(key); len(ids) > 0 {
				return ids[0]
			}
		}

		// Check for JWT token in authorization header
		if auth := md.Get("authorization"); len(auth) > 0 {
			// TODO: Parse JWT token to extract session ID if needed
		}
	}

	return ""
}

// WithTimeout returns a new context with the given timeout.
func WithTimeout(ctx context.Context, timeout time.Duration) (context.Context, context.CancelFunc) {
	return context.WithTimeout(ctx, timeout)
}

// WithDeadline returns a new context with the given deadline.
func WithDeadline(ctx context.Context, deadline time.Time) (context.Context, context.CancelFunc) {
	return context.WithDeadline(ctx, deadline)
}

// WithCancel returns a new context with a cancel function.
func WithCancel(ctx context.Context) (context.Context, context.CancelFunc) {
	return context.WithCancel(ctx)
}

// WithValues returns a new context with the given key-value pairs.
func WithValues(ctx context.Context, values map[interface{}]interface{}) context.Context {
	for k, v := range values {
		ctx = context.WithValue(ctx, k, v)
	}
	return ctx
}

// GetString returns the string value associated with the key in the context.
func GetString(ctx context.Context, key interface{}) string {
	if val, ok := ctx.Value(key).(string); ok {
		return val
	}
	return ""
}

// GetInt returns the int value associated with the key in the context.
func GetInt(ctx context.Context, key interface{}) int {
	if val, ok := ctx.Value(key).(int); ok {
		return val
	}
	return 0
}

// GetInt64 returns the int64 value associated with the key in the context.
func GetInt64(ctx context.Context, key interface{}) int64 {
	if val, ok := ctx.Value(key).(int64); ok {
		return val
	}
	return 0
}

// GetFloat64 returns the float64 value associated with the key in the context.
func GetFloat64(ctx context.Context, key interface{}) float64 {
	if val, ok := ctx.Value(key).(float64); ok {
		return val
	}
	return 0
}

// GetBool returns the bool value associated with the key in the context.
func GetBool(ctx context.Context, key interface{}) bool {
	if val, ok := ctx.Value(key).(bool); ok {
		return val
	}
	return false
}

// GetTime returns the time.Time value associated with the key in the context.
func GetTime(ctx context.Context, key interface{}) time.Time {
	if val, ok := ctx.Value(key).(time.Time); ok {
		return val
	}
	return time.Time{}
}

// GetDuration returns the time.Duration value associated with the key in the context.
func GetDuration(ctx context.Context, key interface{}) time.Duration {
	if val, ok := ctx.Value(key).(time.Duration); ok {
		return val
	}
	return 0
}

// GetStringSlice returns the []string value associated with the key in the context.
func GetStringSlice(ctx context.Context, key interface{}) []string {
	if val, ok := ctx.Value(key).([]string); ok {
		return val
	}
	return nil
}

// GetStringMap returns the map[string]interface{} value associated with the key in the context.
func GetStringMap(ctx context.Context, key interface{}) map[string]interface{} {
	if val, ok := ctx.Value(key).(map[string]interface{}); ok {
		return val
	}
	return nil
}

// GetStringMapString returns the map[string]string value associated with the key in the context.
func GetStringMapString(ctx context.Context, key interface{}) map[string]string {
	if val, ok := ctx.Value(key).(map[string]string); ok {
		return val
	}
	return nil
}

// GetStringMapStringSlice returns the map[string][]string value associated with the key in the context.
func GetStringMapStringSlice(ctx context.Context, key interface{}) map[string][]string {
	if val, ok := ctx.Value(key).(map[string][]string); ok {
		return val
	}
	return nil
}
