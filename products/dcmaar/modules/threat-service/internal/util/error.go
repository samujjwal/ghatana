package util

import (
	"fmt"
	"strings"

	"github.com/pkg/errors"
	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Error represents an error that can be converted to a gRPC status.
type Error struct {
	// The underlying error
	Err error
	// The gRPC status code
	Code codes.Code
	// Additional context
	Context map[string]interface{}
}

// Error implements the error interface.
func (e *Error) Error() string {
	var sb strings.Builder

	if e.Code != codes.Unknown {
		sb.WriteString(fmt.Sprintf("[%s] ", e.Code.String()))
	}

	if e.Err != nil {
		sb.WriteString(e.Err.Error())
	} else {
		sb.WriteString("unknown error")
	}

	if len(e.Context) > 0 {
		sb.WriteString(" [")
		first := true
		for k, v := range e.Context {
			if !first {
				sb.WriteString(", ")
			}
			first = false
			sb.WriteString(fmt.Sprintf("%s=%v", k, v))
		}
		sb.WriteString("]")
	}

	return sb.String()
}

// Unwrap returns the underlying error.
func (e *Error) Unwrap() error {
	return e.Err
}

// GRPCStatus returns the gRPC status.
func (e *Error) GRPCStatus() *status.Status {
	return status.New(e.Code, e.Error())
}

// WithContext adds context to the error.
func (e *Error) WithContext(key string, value interface{}) *Error {
	if e.Context == nil {
		e.Context = make(map[string]interface{})
	}
	e.Context[key] = value
	return e
}

// NewError creates a new error with the given code and message.
func NewError(code codes.Code, msg string) *Error {
	return &Error{
		Err:  errors.New(msg),
		Code: code,
	}
}

// Errorf creates a new error with the given code and formatted message.
func Errorf(code codes.Code, format string, args ...interface{}) *Error {
	return &Error{
		Err:  fmt.Errorf(format, args...),
		Code: code,
	}
}

// WrapError wraps an error with a code.
func WrapError(code codes.Code, err error, msg string) *Error {
	if err == nil {
		return nil
	}
	return &Error{
		Err:  errors.Wrap(err, msg),
		Code: code,
	}
}

// WrapErrorf wraps an error with a code and formatted message.
func WrapErrorf(code codes.Code, err error, format string, args ...interface{}) *Error {
	if err == nil {
		return nil
	}
	return &Error{
		Err:  errors.Wrapf(err, format, args...),
		Code: code,
	}
}

// ToGRPC converts an error to a gRPC status error.
func ToGRPC(err error) error {
	if err == nil {
		return nil
	}

	// If it's already a gRPC status error, return it
	if _, ok := status.FromError(err); ok {
		return err
	}

	// If it's our custom error type, convert it to a gRPC status
	if e, ok := err.(*Error); ok {
		return e.GRPCStatus().Err()
	}

	// Otherwise, wrap it in an internal error
	return status.Error(codes.Internal, err.Error())
}

// LogError logs an error with the given logger and returns a gRPC status error.
func LogError(logger *zap.Logger, err error, msg string, fields ...zap.Field) error {
	if err == nil {
		return nil
	}

	// Add the error to the fields
	fields = append(fields, zap.Error(err))

	// Log the error
	logger.Error(msg, fields...)

	// Convert to gRPC status error
	return ToGRPC(err)
}
