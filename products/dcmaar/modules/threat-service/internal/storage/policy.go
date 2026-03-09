package storage

import (
	"context"
	"database/sql"
	"os"
	"strings"

	"go.uber.org/zap"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// GetPolicy retrieves a policy for the given subject and resources.
// Dev-mode behavior:
// - If DCMAR_POLICY_JSON is set, returns that JSON as the policy data.
// - Else if DCMAR_POLICY_FILE points to a file, reads file contents as policy data.
// - Else returns a default permissive policy.
// Replace with real storage-backed policy retrieval when schema is ready.
func (s *Storage) GetPolicy(ctx context.Context, subject string, resources []string) (*pb.Policy, error) {
	// Environment overrides for dev/test
	if js := strings.TrimSpace(os.Getenv("DCMAR_POLICY_JSON")); js != "" {
		s.logger.Debug("GetPolicy (env json)", zap.String("subject", subject))
		return &pb.Policy{Version: "env", Data: []byte(js), SchemaVersion: 1}, nil
	}
	if path := strings.TrimSpace(os.Getenv("DCMAR_POLICY_FILE")); path != "" {
		if b, err := os.ReadFile(path); err == nil {
			s.logger.Debug("GetPolicy (file)", zap.String("subject", subject), zap.String("file", path))
			return &pb.Policy{Version: "file", Data: b, SchemaVersion: 1}, nil
		} else {
			s.logger.Warn("GetPolicy: failed to read policy file", zap.String("file", path), zap.Error(err))
		}
	}

	// ClickHouse-backed lookup (subject-scoped). Extend to resource scoping as needed.
	if s.db != nil {
		var (
			version       string
			data          string
			schemaVersion uint32
		)
		// Expect a table `policies(subject String, resource String, version String, data String, schema_version UInt32)`
		if len(resources) > 0 {
			r := resources[0]
			row := s.db.QueryRowContext(ctx, `
                SELECT version, data, schema_version
                FROM policies
                WHERE subject = ? AND (resource = ? OR resource = '*')
                ORDER BY resource = '*' ASC
                LIMIT 1
            `, subject, r)
			if err := row.Scan(&version, &data, &schemaVersion); err == nil {
				s.logger.Debug("GetPolicy (db, resource)", zap.String("subject", subject), zap.String("version", version), zap.String("resource", r))
				return &pb.Policy{Version: version, Data: []byte(data), SchemaVersion: uint32(schemaVersion)}, nil
			} else if err != sql.ErrNoRows {
				s.logger.Warn("GetPolicy: db lookup (resource) failed", zap.Error(err))
			}
		}
		row := s.db.QueryRowContext(ctx, `SELECT version, data, schema_version FROM policies WHERE subject = ? AND resource = '*' LIMIT 1`, subject)
		if err := row.Scan(&version, &data, &schemaVersion); err == nil {
			s.logger.Debug("GetPolicy (db)", zap.String("subject", subject), zap.String("version", version))
			return &pb.Policy{Version: version, Data: []byte(data), SchemaVersion: uint32(schemaVersion)}, nil
		} else if err != sql.ErrNoRows {
			s.logger.Warn("GetPolicy: db lookup failed", zap.Error(err))
		}
	}

	// Default
	s.logger.Debug("GetPolicy (default)", zap.String("subject", subject), zap.Strings("resources", resources))
	return &pb.Policy{Version: "1.0", Data: []byte(`{"rules":[{"effect":"allow","resources":["*"]}]}`), SchemaVersion: 1}, nil
}
