package policy

import (
    "context"
    "fmt"
    "sync"

    "go.uber.org/zap"
)

// RegoEvaluator is a placeholder evaluator when built without OPA support.
// Build with -tags=opa to use the OPA-backed implementation.
type RegoEvaluator struct {
    logger *zap.Logger
    policyPath string
    denyByDefault bool
    initOnce sync.Once
}

// NewRegoEvaluator creates a new (stub) Rego evaluator.
func NewRegoEvaluator(logger *zap.Logger, policyPath string, denyByDefault bool) *RegoEvaluator {
    if logger == nil { logger = zap.NewNop() }
    return &RegoEvaluator{logger: logger, policyPath: policyPath, denyByDefault: denyByDefault}
}

func (r *RegoEvaluator) EvaluateAction(ctx context.Context, subject string, roles []string, command string, args []string, resources []string) Decision {
    r.initOnce.Do(func() {
        r.logger.Warn("RegoEvaluator stub in use (build without -tags=opa)", zap.String("policy", r.policyPath))
    })
    // Fallback to default decision when Rego is unavailable
    if r.denyByDefault {
        return Decision{Allow: false, Reason: "rego-disabled-default-deny"}
    }
    return Decision{Allow: true, Reason: "rego-disabled-default-allow"}
}

// ErrNotBuilt indicates OPA support was not compiled in.
var ErrNotBuilt = fmt.Errorf("rego evaluator not built (use -tags=opa)")

