package policy

import (
    "context"
    "testing"

    pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
    "go.uber.org/zap"
)

func TestVerifier_AllowDeny(t *testing.T) {
    data := []byte(`{"rules":[
        {"effect":"deny","resources":["secret"],"commands":["delete"]},
        {"effect":"allow","resources":["*"],"commands":["echo"],"roles":["dev"]}
    ]}`)
    loader := func(ctx context.Context, subject string, resources []string) (*pb.Policy, error) {
        return &pb.Policy{Version: "test", Data: data, SchemaVersion: 1}, nil
    }
    v := NewVerifierWithLoader(zap.NewNop(), loader)

    // Allowed: role dev, command echo
    d := v.EvaluateAction(context.Background(), "alice", []string{"dev"}, "echo", nil, []string{"any"})
    if !d.Allow {
        t.Fatalf("expected allow, got deny: %+v", d)
    }

    // Denied: resource secret delete
    d2 := v.EvaluateAction(context.Background(), "alice", []string{"dev"}, "delete", nil, []string{"secret"})
    if d2.Allow {
        t.Fatalf("expected deny, got allow: %+v", d2)
    }
}

func TestVerifier_DefaultDecision(t *testing.T) {
    // Loader returns empty policy -> default path
    loader := func(ctx context.Context, subject string, resources []string) (*pb.Policy, error) {
        return &pb.Policy{Version: "test", Data: nil, SchemaVersion: 1}, nil
    }
    v := NewVerifierWithLoader(zap.NewNop(), loader)
    d := v.EvaluateAction(context.Background(), "bob", nil, "noop", nil, nil)
    if !d.Allow {
        t.Fatalf("expected default allow when denyByDefault=false, got: %+v", d)
    }

    v.SetDenyByDefault(true)
    d2 := v.EvaluateAction(context.Background(), "bob", nil, "noop", nil, nil)
    if d2.Allow {
        t.Fatalf("expected default deny when denyByDefault=true, got: %+v", d2)
    }
}

func TestVerifier_RuleMatching_Table(t *testing.T) {
    policyJSON := []byte(`{"rules":[
        {"effect":"allow","resources":["*"],"commands":["echo"]},
        {"effect":"allow","resources":["reports"],"commands":["*"]},
        {"effect":"deny","resources":["secret"],"commands":["*"]},
        {"effect":"allow","resources":["ops"],"commands":["restart"],"roles":["admin"]}
    ]}`)
    loader := func(ctx context.Context, subject string, resources []string) (*pb.Policy, error) {
        return &pb.Policy{Version: "t", Data: policyJSON, SchemaVersion: 1}, nil
    }
    v := NewVerifierWithLoader(zap.NewNop(), loader)
    cases := []struct{
        name string
        roles []string
        cmd string
        res []string
        allow bool
    }{
        {"echo-any", nil, "echo", []string{"any"}, true},
        {"deny-secret-wild", nil, "anything", []string{"secret"}, false},
        {"reports-any-cmd", nil, "delete", []string{"reports"}, true},
        {"ops-restart-admin-allow", []string{"admin"}, "restart", []string{"ops"}, true},
        {"ops-restart-nonadmin-deny", []string{"dev"}, "restart", []string{"ops"}, false},
    }
    for _, tc := range cases {
        t.Run(tc.name, func(t *testing.T) {
            d := v.EvaluateAction(context.Background(), "u", tc.roles, tc.cmd, nil, tc.res)
            if d.Allow != tc.allow {
                t.Fatalf("expected allow=%v got %v (reason=%s)", tc.allow, d.Allow, d.Reason)
            }
        })
    }
}
