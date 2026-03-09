package main

import (
    "context"
    "testing"

    pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
    ipolicy "github.com/samujjwal/dcmaar/apps/server/internal/policy"
    stelemetry "github.com/samujjwal/dcmaar/apps/server/internal/telemetry"
    "go.uber.org/zap"
    "google.golang.org/grpc/codes"
    "google.golang.org/grpc/status"
)

type denyVerifier struct{}

func (d *denyVerifier) EvaluateAction(ctx context.Context, subject string, roles []string, command string, args []string, resources []string) ipolicy.Decision {
    return ipolicy.Decision{Allow: false, Reason: "test-deny"}
}

func TestAction_Submit_Denied_IncrementsMetric(t *testing.T) {
    logger := zap.NewNop()
    metrics, _ := stelemetry.NewMetrics(stelemetry.DefaultMetricsConfig(), logger)
    s := &actionServer{logger: logger, verifier: &denyVerifier{}, metrics: metrics}
    _, err := s.SubmitAction(context.Background(), &pb.ActionSubmitRequest{Command: "echo", Args: []string{"x"}})
    if err == nil {
        t.Fatalf("expected PermissionDenied, got nil")
    }
    st, _ := status.FromError(err)
    if st.Code() != codes.PermissionDenied {
        t.Fatalf("expected PermissionDenied, got %v", st.Code())
    }
    snap := metrics.Snapshot()
    if snap["policy_denied_total"] < 1 {
        t.Fatalf("expected policy_denied_total >= 1, snapshot=%v", snap)
    }
}
