package models

import "time"

// Extension represents a registered browser extension.
type Extension struct {
	ID                    string    `db:"id"`
	Name                  string    `db:"name"`
	APIKeyHash            string    `db:"api_key_hash"`
	ClientCertFingerprint string    `db:"client_cert_fingerprint"`
	Active                bool      `db:"active"`
	CreatedAt             time.Time `db:"created_at"`
}

// BrowserEventRecord represents a flattened browser event for storage.
type BrowserEventRecord struct {
	EventID     string    `db:"event_id"`
	ExtensionID string    `db:"extension_id"`
	TabID       string    `db:"tab_id"`
	URL         string    `db:"url"`
	Domain      string    `db:"domain"`
	EventType   string    `db:"event_type"`
	LatencyMs   float64   `db:"latency"`
	StatusCode  int32     `db:"status_code"`
	CreatedAt   time.Time `db:"created_at"`
}

// Store abstracts extension lookups and event persistence.
type Store interface {
	CreateExtension(ext *Extension) error
	GetExtensionByID(id string) (*Extension, error)
	ListExtensions(activeOnly bool) ([]*Extension, error)
	SaveBrowserEvents(records []BrowserEventRecord) (processed int, rejected int, err error)
}
