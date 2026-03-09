package storage

import (
    "os"
    "path/filepath"
    "testing"

    "go.uber.org/zap"
)

func TestGetPolicy_EnvJSON(t *testing.T) {
    s := &Storage{logger: zap.NewNop()}
    os.Setenv("DCMAR_POLICY_JSON", `{"rules":[{"effect":"deny","resources":["secret"]}]}`)
    defer os.Unsetenv("DCMAR_POLICY_JSON")

    pol, err := s.GetPolicy(nil, "user1", nil)
    if err != nil {
        t.Fatalf("expected no error, got %v", err)
    }
    if pol == nil || string(pol.Data) == "" || pol.Version != "env" {
        t.Fatalf("unexpected policy: %+v", pol)
    }
}

func TestGetPolicy_File(t *testing.T) {
    s := &Storage{logger: zap.NewNop()}
    dir := t.TempDir()
    p := filepath.Join(dir, "policy.json")
    content := []byte(`{"rules":[{"effect":"allow","resources":["*"]}]}`)
    if err := os.WriteFile(p, content, 0o644); err != nil {
        t.Fatalf("write file: %v", err)
    }
    os.Setenv("DCMAR_POLICY_FILE", p)
    defer os.Unsetenv("DCMAR_POLICY_FILE")

    pol, err := s.GetPolicy(nil, "user2", nil)
    if err != nil {
        t.Fatalf("expected no error, got %v", err)
    }
    if pol == nil || string(pol.Data) != string(content) || pol.Version != "file" {
        t.Fatalf("unexpected policy: %+v", pol)
    }
}

