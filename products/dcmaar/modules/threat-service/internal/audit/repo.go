package audit

import (
    "context"
    "encoding/json"
    "time"

    "go.uber.org/zap"
)

// Event is a minimal audit event structure.
type Event struct {
    Time     time.Time         `json:"time"`
    Subject  string            `json:"subject"`
    Action   string            `json:"action"`
    Target   string            `json:"target,omitempty"`
    Result   string            `json:"result"`
    Details  map[string]string `json:"details,omitempty"`
}

// Repo persists audit events. Default impl logs JSON.
type storageWriter interface {
    AppendAudit(ctx context.Context, ev Event) error
}

type Repo struct {
    logger  *zap.Logger
    storage storageWriter
}

func NewRepo(logger *zap.Logger) *Repo { return NewRepoWithStorage(logger, nil) }

func NewRepoWithStorage(logger *zap.Logger, st storageWriter) *Repo {
    if logger == nil {
        logger = zap.NewNop()
    }
    return &Repo{logger: logger, storage: st}
}

// Append persists the event (to storage if configured, else logs JSON).
func (r *Repo) Append(ctx context.Context, ev Event) error {
    if ev.Time.IsZero() {
        ev.Time = time.Now()
    }
    if r.storage != nil {
        if err := r.storage.AppendAudit(ctx, ev); err == nil {
            return nil
        }
    }
    b, _ := json.Marshal(ev)
    r.logger.Info("audit", zap.ByteString("event", b))
    return nil
}
