//go:build integration
// +build integration

package storage

import (
    "context"
    "database/sql"
    "testing"
    "time"

    _ "github.com/ClickHouse/clickhouse-go/v2"
    iaudit "github.com/samujjwal/dcmaar/apps/server/internal/audit"
    "go.uber.org/zap"
)

func ensureAuditTable(t *testing.T, db *sql.DB) {
    t.Helper()
    stmt := `
        CREATE TABLE IF NOT EXISTS audit_events (
            time    DateTime,
            subject String,
            action  String,
            target  String,
            result  String,
            details String
        )
        ENGINE = MergeTree()
        ORDER BY (time, subject);
    `
    if _, err := db.Exec(stmt); err != nil {
        t.Fatalf("create table failed: %v", err)
    }
    if _, err := db.Exec("TRUNCATE TABLE audit_events"); err != nil {
        t.Fatalf("truncate failed: %v", err)
    }
}

func TestAppendAudit_PersistsToClickHouse(t *testing.T) {
    // ClickHouse from test env: user=test, password=test, db=testdb
    dsn := "tcp://localhost:9000?username=test&password=test&database=testdb"
    db, err := sql.Open("clickhouse", dsn)
    if err != nil {
        t.Skipf("clickhouse driver open failed: %v (run: make test-env-up)", err)
    }
    if err := db.Ping(); err != nil {
        t.Skipf("clickhouse not reachable: %v (run: make test-env-up)", err)
    }
    ensureAuditTable(t, db)

    s := &Storage{db: db, logger: zap.NewNop()}
    ev := iaudit.Event{Time: time.Now(), Subject: "u1", Action: "cmd", Target: "t", Result: "ok", Details: map[string]string{"k":"v"}}
    if err := s.AppendAudit(context.Background(), ev); err != nil {
        t.Fatalf("AppendAudit failed: %v", err)
    }

    var cnt int
    if err := db.QueryRow("SELECT count() FROM audit_events WHERE subject = 'u1' AND action = 'cmd'").Scan(&cnt); err != nil {
        t.Fatalf("count query failed: %v", err)
    }
    if cnt < 1 {
        t.Fatalf("expected at least 1 row, got %d", cnt)
    }
}

