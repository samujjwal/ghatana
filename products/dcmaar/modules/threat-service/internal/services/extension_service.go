package services

import (
	"context"
	"time"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/internal/models"
)

// ExtensionService provides operations related to extensions and event persistence.
type ExtensionService struct {
	Store models.Store
}

// SaveExtensionEnvelopes flattens browser events and persists them via Store.
func (s *ExtensionService) SaveExtensionEnvelopes(ctx context.Context, extensionID string, batch *pb.EventEnvelopeBatch) (int, int, error) {
	if batch == nil || len(batch.Envelopes) == 0 {
		return 0, 0, nil
	}
	var recs []models.BrowserEventRecord
	for _, env := range batch.Envelopes {
		for _, ewm := range env.Events {
			if ewm.Browser == nil {
				continue
			}
			recs = append(recs, models.BrowserEventRecord{
				EventID:     ewm.Event.Id,
				ExtensionID: extensionID,
				TabID:       ewm.Browser.TabId,
				URL:         ewm.Browser.Url,
				Domain:      ewm.Browser.Domain,
				EventType:   ewm.Browser.EventType,
				LatencyMs:   ewm.Browser.Latency,
				StatusCode:  ewm.Browser.StatusCode,
				CreatedAt:   time.Now(),
			})
		}
	}
	processed, rejected, err := s.Store.SaveBrowserEvents(recs)
	return processed, rejected, err
}

// TODO: Add methods for extension registration, update, deactivate/reactivate, and metadata retrieval.
