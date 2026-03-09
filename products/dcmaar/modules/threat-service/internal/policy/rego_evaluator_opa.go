//go:build opa
// +build opa

package policy

import (
    "context"
    "os"
    "sync"

    "github.com/open-policy-agent/opa/rego"
    "go.uber.org/zap"
)

// RegoEvaluator evaluates actions using a Rego policy module.
type RegoEvaluator struct {
    logger *zap.Logger
    policyPath string
    denyByDefault bool

    once sync.Once
    plan *rego.PreparedEvalQuery
    loadErr error
}

// NewRegoEvaluator creates a new Rego evaluator (OPA-backed, requires -tags=opa).
func NewRegoEvaluator(logger *zap.Logger, policyPath string, denyByDefault bool) *RegoEvaluator {
    if logger == nil { logger = zap.NewNop() }
    return &RegoEvaluator{logger: logger, policyPath: policyPath, denyByDefault: denyByDefault}
}

func (r *RegoEvaluator) prepare(ctx context.Context) error {
    r.once.Do(func() {
        b, err := os.ReadFile(r.policyPath)
        if err != nil { r.loadErr = err; return }
        q := rego.New(
            rego.Query("data.dcmaar.allow"),
            rego.Module("policy.rego", string(b)),
        )
        plan, err := q.PrepareForEval(ctx)
        if err != nil { r.loadErr = err; return }
        r.plan = &plan
    })
    return r.loadErr
}

func (r *RegoEvaluator) EvaluateAction(ctx context.Context, subject string, roles []string, command string, args []string, resources []string) Decision {
    if err := r.prepare(ctx); err != nil {
        r.logger.Warn("rego prepare failed", zap.Error(err))
        if r.denyByDefault { return Decision{Allow: false, Reason: "rego-prepare-failed"} }
        return Decision{Allow: true, Reason: "rego-prepare-failed"}
    }
    input := map[string]any{
        "subject": subject,
        "roles": roles,
        "command": command,
        "resources": resources,
        "args": args,
    }
    rs, err := r.plan.Eval(ctx, rego.EvalInput(input))
    if err != nil {
        r.logger.Warn("rego eval failed", zap.Error(err))
        if r.denyByDefault { return Decision{Allow: false, Reason: "rego-eval-failed"} }
        return Decision{Allow: true, Reason: "rego-eval-failed"}
    }
    // Expect a single result with a boolean value
    allow := false
    if len(rs) > 0 && len(rs[0].Expressions) > 0 {
        if v, ok := rs[0].Expressions[0].Value.(bool); ok {
            allow = v
        }
    }
    if allow { return Decision{Allow: true, Reason: "rego-allow"} }
    return Decision{Allow: false, Reason: "rego-deny"}
}

