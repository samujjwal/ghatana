package query

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
)

type mockCore struct {
	resp *pb.QueryEventsResponse
	err  error
}

func (m *mockCore) QueryEvents(ctx context.Context, req *pb.QueryEventsRequest) (*pb.QueryEventsResponse, error) {
	return m.resp, m.err
}

func TestValidatedService(t *testing.T) {
	svc := NewValidatedService(&mockCore{resp: &pb.QueryEventsResponse{}})

	t.Run("nil request", func(t *testing.T) {
		_, err := svc.QueryEvents(context.Background(), nil)
		assert.Error(t, err)
		assert.Equal(t, codes.InvalidArgument, status.Code(err))
	})

	t.Run("negative page size", func(t *testing.T) {
		_, err := svc.QueryEvents(context.Background(), &pb.QueryEventsRequest{PageSize: -1})
		assert.Error(t, err)
		assert.Equal(t, codes.InvalidArgument, status.Code(err))
	})

	t.Run("page size too large", func(t *testing.T) {
		_, err := svc.QueryEvents(context.Background(), &pb.QueryEventsRequest{PageSize: 1001})
		assert.Error(t, err)
		assert.Equal(t, codes.InvalidArgument, status.Code(err))
	})

	t.Run("start after end", func(t *testing.T) {
		_, err := svc.QueryEvents(context.Background(), &pb.QueryEventsRequest{
			TimeRange: &pb.TimeRange{
				StartTime: timestamppb.Now(),
				EndTime:   timestamppb.New(time.Now().Add(-time.Hour)),
			},
		})
		assert.Error(t, err)
		assert.Equal(t, codes.InvalidArgument, status.Code(err))
	})

	t.Run("range too long", func(t *testing.T) {
		_, err := svc.QueryEvents(context.Background(), &pb.QueryEventsRequest{
			TimeRange: &pb.TimeRange{
				StartTime: timestamppb.New(time.Now().Add(-31 * 24 * time.Hour)),
				EndTime:   timestamppb.Now(),
			},
		})
		assert.Error(t, err)
		assert.Equal(t, codes.InvalidArgument, status.Code(err))
	})

	t.Run("valid request passes through", func(t *testing.T) {
		_, err := svc.QueryEvents(context.Background(), &pb.QueryEventsRequest{
			PageSize: 10,
			TimeRange: &pb.TimeRange{
				StartTime: timestamppb.New(time.Now().Add(-time.Hour)),
				EndTime:   timestamppb.Now(),
			},
		})
		assert.NoError(t, err)
	})
}
