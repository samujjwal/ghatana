package storage

import (
    "context"
    "encoding/json"

    "go.uber.org/zap"

    iaudit "github.com/samujjwal/dcmaar/apps/server/internal/audit"
)

// AppendAudit writes an audit event to ClickHouse if available; logs on error.
func (s *Storage) AppendAudit(ctx context.Context, ev iaudit.Event) error {
    if s.db == nil {
        s.logger.Info("audit (no-db)")
        return nil
    }
    data, _ := json.Marshal(ev.Details)
    if _, err := s.db.ExecContext(ctx,
        `INSERT INTO audit_events (time, subject, action, target, result, details) VALUES (?, ?, ?, ?, ?, ?)`,
        ev.Time, ev.Subject, ev.Action, ev.Target, ev.Result, string(data)); err != nil {
        s.logger.Warn("append audit failed", zap.Error(err))
        return err
    }
    return nil
}

