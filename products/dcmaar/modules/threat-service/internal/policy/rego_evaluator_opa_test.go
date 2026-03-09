//go:build opa
// +build opa

package policy

import (
    "context"
    "os"
    "testing"
    "time"

    "go.uber.org/zap"
)

const sampleRego = `package dcmaar
default allow = false
allow { input.command == "echo" }
`

func TestRegoEvaluator_AllowEcho(t *testing.T) {
    f, err := os.CreateTemp(t.TempDir(), "policy-*.rego")
    if err != nil { t.Fatal(err) }
    if _, err := f.WriteString(sampleRego); err != nil { t.Fatal(err) }
    _ = f.Close()

    ev := NewRegoEvaluator(zap.NewNop(), f.Name(), false)
    ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
    defer cancel()
    d := ev.EvaluateAction(ctx, "u", nil, "echo", nil, []string{"*"})
    if !d.Allow {
        t.Fatalf("expected allow, got %+v", d)
    }
}

func TestRegoEvaluator_PrepareError_DenyByDefaultToggle(t *testing.T) {
    // Point to a non-existent file to trigger prepare error
    ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
    defer cancel()

    // denyByDefault=false -> allow on prepare failure
    ev1 := NewRegoEvaluator(zap.NewNop(), "/no/such/policy.rego", false)
    d1 := ev1.EvaluateAction(ctx, "u", nil, "any", nil, []string{"*"})
    if !d1.Allow {
        t.Fatalf("expected allow on prepare failure when denyByDefault=false, got %+v", d1)
    }

    // denyByDefault=true -> deny on prepare failure
    ev2 := NewRegoEvaluator(zap.NewNop(), "/no/such/policy.rego", true)
    d2 := ev2.EvaluateAction(ctx, "u", nil, "any", nil, []string{"*"})
    if d2.Allow {
        t.Fatalf("expected deny on prepare failure when denyByDefault=true, got %+v", d2)
    }
}

func TestRegoEvaluator_EvalError_DenyByDefaultToggle(t *testing.T) {
    // Syntax error policy to force compile failure -> treated as prepare error path
    bad, err := os.CreateTemp(t.TempDir(), "bad-*.rego")
    if err != nil { t.Fatal(err) }
    // invalid rego (missing package)
    if _, err := bad.WriteString("allow = input.x == 1"); err != nil { t.Fatal(err) }
    _ = bad.Close()

    ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
    defer cancel()

    ev := NewRegoEvaluator(zap.NewNop(), bad.Name(), true)
    d := ev.EvaluateAction(ctx, "u", nil, "any", nil, []string{"*"})
    if d.Allow {
        t.Fatalf("expected deny on invalid policy with denyByDefault=true, got %+v", d)
    }
}
