package policy

import (
    "context"
    "encoding/json"
    "strings"

    pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
    "go.uber.org/zap"
)

// Decision represents a policy evaluation outcome.
type Decision struct {
    Allow  bool
    Reason string
}

// Evaluator defines the interface for policy evaluation engines.
type Evaluator interface {
    EvaluateAction(ctx context.Context, subject string, roles []string, command string, args []string, resources []string) Decision
}

// Loader retrieves a policy for a subject/resources.
type Loader func(ctx context.Context, subject string, resources []string) (*pb.Policy, error)

// Verifier evaluates requests against policy.
// Verifier implements a simple JSON-based policy evaluator.
type Verifier struct {
    logger *zap.Logger
    load   Loader
    denyByDefault bool
}

// NewVerifier creates a new policy verifier.
func NewVerifier(logger *zap.Logger) *Verifier { return NewVerifierWithLoader(logger, nil) }

// NewVerifierWithLoader creates a new verifier with a policy loader.
func NewVerifierWithLoader(logger *zap.Logger, loader Loader) *Verifier {
    if logger == nil {
        logger = zap.NewNop()
    }
    return &Verifier{logger: logger, load: loader}
}

type rule struct {
    Effect    string   `json:"effect"`              // "allow" or "deny"
    Resources []string `json:"resources,omitempty"` // list of resources or ["*"]
    Commands  []string `json:"commands,omitempty"`  // list of commands or ["*"]
    Roles     []string `json:"roles,omitempty"`     // required roles (intersection)
}

type policyDoc struct {
    Rules []rule `json:"rules"`
}

func anyMatch(value string, patterns []string) bool {
    if len(patterns) == 0 { return true }
    for _, p := range patterns {
        if p == "*" || strings.EqualFold(p, value) { return true }
    }
    return false
}

func rolesMatch(subjectRoles, required []string) bool {
    if len(required) == 0 { return true }
    set := map[string]struct{}{}
    for _, r := range subjectRoles { set[strings.ToLower(r)] = struct{}{} }
    for _, rr := range required {
        if _, ok := set[strings.ToLower(rr)]; ok { return true }
    }
    return false
}

// SetDenyByDefault toggles the default decision when no rules match or policy is missing/invalid.
func (v *Verifier) SetDenyByDefault(deny bool) { v.denyByDefault = deny }

// EvaluateAction evaluates whether a subject may execute a command with args on resources.
// Deny rules take precedence over allow rules; default is allow if no rules match.
func (v *Verifier) EvaluateAction(ctx context.Context, subject string, roles []string, command string, args []string, resources []string) Decision {
    v.logger.Debug("policy.EvaluateAction",
        zap.String("subject", subject),
        zap.Strings("roles", roles),
        zap.String("command", command),
        zap.Strings("args", args),
        zap.Strings("resources", resources),
    )

    if v.load == nil {
        return Decision{Allow: !v.denyByDefault, Reason: "no-loader"}
    }
    pol, err := v.load(ctx, subject, resources)
    if err != nil || pol == nil || len(pol.Data) == 0 {
        return Decision{Allow: !v.denyByDefault, Reason: "no-policy"}
    }
    var doc policyDoc
    if err := json.Unmarshal(pol.Data, &doc); err != nil {
        v.logger.Warn("policy parse failed", zap.Error(err))
        return Decision{Allow: !v.denyByDefault, Reason: "invalid-policy"}
    }

    matchedAllow := false
    for _, r := range doc.Rules {
        // Match roles, command, resource
        if !rolesMatch(roles, r.Roles) { continue }
        cmdOK := anyMatch(command, r.Commands)
        resOK := len(resources) == 0 || anyMatch(resources[0], r.Resources)
        if !(cmdOK && resOK) { continue }
        if strings.EqualFold(r.Effect, "deny") {
            return Decision{Allow: false, Reason: "rule-deny"}
        }
        if strings.EqualFold(r.Effect, "allow") {
            matchedAllow = true
        }
    }
    if matchedAllow {
        return Decision{Allow: true, Reason: "rule-allow"}
    }
    // Default decision
    if v.denyByDefault {
        return Decision{Allow: false, Reason: "default-deny"}
    }
    return Decision{Allow: true, Reason: "default-allow"}
}
