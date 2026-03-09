package query

import (
	"context"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

// QueryCore is the minimal interface implemented by the core service
// that middleware wrappers can decorate. The existing *Service already
// satisfies this interface via its QueryEvents method.
type QueryCore interface {
	QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error)
}
