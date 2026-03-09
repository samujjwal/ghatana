//go:build integration
// +build integration

package storage

import (
    "context"
    "database/sql"
    "testing"

    _ "github.com/ClickHouse/clickhouse-go/v2"
)

func ensurePoliciesTable(t *testing.T, db *sql.DB) {
    t.Helper()
    stmt := `
        CREATE TABLE IF NOT EXISTS policies (
            subject String,
            resource String,
            version String,
            data String,
            schema_version UInt32
        )
        ENGINE = MergeTree()
        ORDER BY (subject, resource);
    `
    if _, err := db.Exec(stmt); err != nil {
        t.Fatalf("create policies failed: %v", err)
    }
    if _, err := db.Exec("TRUNCATE TABLE policies"); err != nil {
        t.Fatalf("truncate policies failed: %v", err)
    }
}

func TestStorage_GetPolicy_DBPrecedence(t *testing.T) {
    dsn := "tcp://localhost:9000?username=test&password=test&database=testdb"
    db, err := sql.Open("clickhouse", dsn)
    if err != nil {
        t.Skipf("clickhouse open failed: %v (run: make test-env-up)", err)
    }
    if err := db.Ping(); err != nil {
        t.Skipf("clickhouse not reachable: %v (run: make test-env-up)", err)
    }
    ensurePoliciesTable(t, db)

    // Insert wildcard policy and a specific resource policy
    _, err = db.Exec(`INSERT INTO policies (subject,resource,version,data,schema_version) VALUES
        ('alice','*','v1','{"rules":[{"effect":"allow","resources":["*"]}]}',1),
        ('alice','secret','v2','{"rules":[{"effect":"deny","resources":["secret"]}]}',1)
    `)
    if err != nil {
        t.Fatalf("insert policies failed: %v", err)
    }

    s := &Storage{db: db}
    // Specific resource should pick v2
    pol, err := s.GetPolicy(context.Background(), "alice", []string{"secret"})
    if err != nil { t.Fatalf("GetPolicy error: %v", err) }
    if pol == nil || pol.Version != "v2" {
        t.Fatalf("expected v2 policy for resource 'secret', got: %#v", pol)
    }

    // For other resource, wildcard v1 applies
    pol2, err := s.GetPolicy(context.Background(), "alice", []string{"other"})
    if err != nil { t.Fatalf("GetPolicy error: %v", err) }
    if pol2 == nil || pol2.Version != "v1" {
        t.Fatalf("expected v1 wildcard policy, got: %#v", pol2)
    }
}

