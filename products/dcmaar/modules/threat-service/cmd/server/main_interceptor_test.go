package main

import (
	"context"
	"strings"
	"testing"

	imw "github.com/samujjwal/dcmaar/apps/server/internal/middleware"
	telctx "github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
	"go.uber.org/zap"
	"go.uber.org/zap/zaptest/observer"
	"google.golang.org/grpc"
	"google.golang.org/grpc/metadata"
)

func TestCorrIDInterceptor_AttachesCorrID(t *testing.T) {
	s := &Server{logger: zap.NewNop()}
	ic := s.corrIDInterceptor()

	md := metadata.Pairs("x-corr-id", "abc-123")
	ctx := metadata.NewIncomingContext(context.Background(), md)

	gotCorr := ""
	h := func(ctx context.Context, req interface{}) (interface{}, error) {
		gotCorr = telctx.CorrIDFromContext(ctx)
		return nil, nil
	}
	if _, err := ic(ctx, nil, &grpc.UnaryServerInfo{FullMethod: "/test.Service/Op"}, h); err != nil {
		t.Fatalf("interceptor error: %v", err)
	}
	if gotCorr != "abc-123" {
		t.Fatalf("expected corr_id=abc-123, got %q", gotCorr)
	}
}

func TestIdentityInterceptor_AttachesIdentity(t *testing.T) {
	s := &Server{logger: zap.NewNop()}
	ii := s.identityInterceptor()

	// Unsigned dev JWT with subject
	token := "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyLTEyMyIsInJvbGVzIjpbInIiXX0."
	md := metadata.Pairs("authorization", "Bearer "+token)
	ctx := metadata.NewIncomingContext(context.Background(), md)

	var sub string
	var roles []string
	h := func(ctx context.Context, req interface{}) (interface{}, error) {
		if id := imw.IdentityFromContext(ctx); id != nil {
			sub = id.Subject
			roles = append([]string{}, id.Roles...)
		}
		return nil, nil
	}
	if _, err := ii(ctx, nil, &grpc.UnaryServerInfo{FullMethod: "/test.Service/Op"}, h); err != nil {
		t.Fatalf("interceptor error: %v", err)
	}
	if sub != "user-123" {
		t.Fatalf("expected subject=user-123, got %q", sub)
	}
	if len(roles) != 1 || roles[0] != "r" {
		t.Fatalf("expected roles [r], got %+v", roles)
	}
}

func TestIdentityInterceptor_EnrichesLogger(t *testing.T) {
	core, logs := observer.New(zap.InfoLevel)
	zl := zap.New(core)
	s := &Server{logger: zl}
	ii := s.identityInterceptor()

	// Seed context with logger so interceptor derives from it
	base := telctx.ContextWithLogger(context.Background(), zl)
	token := "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJsb2ctdXNlciIsInJvbGVzIjpbInIiXX0."
	ctx := metadata.NewIncomingContext(base, metadata.Pairs("authorization", "Bearer "+token))

	h := func(ctx context.Context, req interface{}) (interface{}, error) {
		telctx.LoggerFromContextOr(ctx, zl).Info("hello")
		return nil, nil
	}
	if _, err := ii(ctx, nil, &grpc.UnaryServerInfo{FullMethod: "/svc/Op"}, h); err != nil {
		t.Fatalf("interceptor error: %v", err)
	}
	found := false
	for _, e := range logs.All() {
		for _, f := range e.Context {
			if f.Key == "subject" && strings.Contains(f.String, "log-user") {
				found = true
			}
		}
	}
	if !found {
		t.Fatalf("expected logger to be enriched with subject")
	}
}

func TestCorrIDInterceptor_EnrichesLogger(t *testing.T) {
	core, logs := observer.New(zap.InfoLevel)
	zl := zap.New(core)
	s := &Server{logger: zl}
	ic := s.corrIDInterceptor()

	base := telctx.ContextWithLogger(context.Background(), zl)
	ctx := metadata.NewIncomingContext(base, metadata.Pairs("x-corr-id", "xyz-789"))
	h := func(ctx context.Context, req interface{}) (interface{}, error) {
		telctx.LoggerFromContextOr(ctx, zl).Info("world")
		return nil, nil
	}
	if _, err := ic(ctx, nil, &grpc.UnaryServerInfo{FullMethod: "/svc/Op"}, h); err != nil {
		t.Fatalf("interceptor error: %v", err)
	}
	found := false
	for _, e := range logs.All() {
		for _, f := range e.Context {
			if f.Key == "corr_id" && strings.Contains(f.String, "xyz-789") {
				found = true
			}
		}
	}
	if !found {
		t.Fatalf("expected logger to be enriched with corr_id")
	}
}
