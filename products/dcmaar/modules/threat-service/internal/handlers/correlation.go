package handlers

import (
	"context"
	"fmt"
	"time"

	"go.uber.org/zap"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb/dcmaar/v1"
	"github.com/samujjwal/dcmaar/apps/server/internal/services"
)

// CorrelationHandler implements gRPC handlers for correlation service
type CorrelationHandler struct {
	pb.UnimplementedCorrelationServiceServer
	logger         *zap.Logger
	correlationSvc *services.CorrelationService
}

// NewCorrelationHandler creates a new correlation handler
func NewCorrelationHandler(logger *zap.Logger, correlationSvc *services.CorrelationService) *CorrelationHandler {
	return &CorrelationHandler{
		logger:         logger.Named("correlation_handler"),
		correlationSvc: correlationSvc,
	}
}

// GetCorrelatedIncidentsByWindow retrieves correlated incidents within a time window
func (h *CorrelationHandler) GetCorrelatedIncidentsByWindow(ctx context.Context, req *pb.GetCorrelatedIncidentsRequest) (*pb.GetCorrelatedIncidentsResponse, error) {
	if req == nil {
		return nil, status.Error(codes.InvalidArgument, "request cannot be nil")
	}

	if req.TenantId == "" {
		return nil, status.Error(codes.InvalidArgument, "tenant_id is required")
	}

	if req.DeviceId == "" {
		return nil, status.Error(codes.InvalidArgument, "device_id is required")
	}

	// Convert protobuf timestamps to Go time
	startTime := time.Now().Add(-24 * time.Hour) // Default to last 24 hours
	if req.StartTime != nil {
		startTime = req.StartTime.AsTime()
	}

	endTime := time.Now()
	if req.EndTime != nil {
		endTime = req.EndTime.AsTime()
	}

	h.logger.Info("Getting correlated incidents",
		zap.String("tenant_id", req.TenantId),
		zap.String("device_id", req.DeviceId),
		zap.Time("start_time", startTime),
		zap.Time("end_time", endTime))

	incidents, err := h.correlationSvc.GetCorrelatedIncidentsByWindow(ctx, req.TenantId, req.DeviceId, startTime, endTime)
	if err != nil {
		h.logger.Error("Failed to get correlated incidents", zap.Error(err))
		return nil, status.Error(codes.Internal, "failed to retrieve incidents")
	}

	// Convert service incidents to protobuf format
	var pbIncidents []*pb.CorrelatedIncident
	for _, incident := range incidents {
		pbIncident := &pb.CorrelatedIncident{
			IncidentId:            incident.IncidentID,
			CreatedAt:             timestamppb.New(incident.CreatedAt),
			UpdatedAt:             timestamppb.New(incident.UpdatedAt),
			TenantId:              incident.TenantID,
			DeviceId:              incident.DeviceID,
			HostId:                incident.HostID,
			WindowStart:           timestamppb.New(incident.WindowStart),
			WindowEnd:             timestamppb.New(incident.WindowEnd),
			CorrelationScore:      incident.CorrelationScore,
			Confidence:            incident.Confidence,
			IncidentType:          incident.IncidentType,
			Severity:              incident.Severity,
			AnomalyEventIds:       incident.AnomalyEventIDs,
			WebEventIds:           incident.WebEventIDs,
			AnomalyCount:          int32(incident.AnomalyCount),
			WebEventCount:         int32(incident.WebEventCount),
			AvgCpuAnomalyScore:    incident.AvgCPUAnomalyScore,
			AvgMemoryAnomalyScore: incident.AvgMemoryAnomalyScore,
			AvgLatencyMs:          incident.AvgLatencyMs,
			AffectedDomains:       incident.AffectedDomains,
			Status:                incident.Status,
			Labels:                incident.Labels,
		}

		if incident.ResolvedAt != nil {
			pbIncident.ResolvedAt = timestamppb.New(*incident.ResolvedAt)
		}

		pbIncidents = append(pbIncidents, pbIncident)
	}

	return &pb.GetCorrelatedIncidentsResponse{
		Incidents: pbIncidents,
	}, nil
}

// TriggerCorrelationAnalysis manually triggers correlation analysis
func (h *CorrelationHandler) TriggerCorrelationAnalysis(ctx context.Context, req *pb.TriggerCorrelationRequest) (*pb.TriggerCorrelationResponse, error) {
	if req == nil {
		return nil, status.Error(codes.InvalidArgument, "request cannot be nil")
	}

	h.logger.Info("Triggering correlation analysis")

	// Use provided config or defaults
	config := services.DefaultCorrelationConfig()
	if req.Config != nil {
		if req.Config.TimeWindowMinutes > 0 {
			config.TimeWindowMinutes = int(req.Config.TimeWindowMinutes)
		}
		if req.Config.MinCorrelationScore > 0 {
			config.MinCorrelationScore = req.Config.MinCorrelationScore
		}
		if req.Config.MinConfidence > 0 {
			config.MinConfidence = req.Config.MinConfidence
		}
		if req.Config.MaxLatencyThresholdMs > 0 {
			config.MaxLatencyThresholdMs = req.Config.MaxLatencyThresholdMs
		}
		if req.Config.MinAnomalyScore > 0 {
			config.MinAnomalyScore = req.Config.MinAnomalyScore
		}
		if req.Config.CorrelationLookbackHrs > 0 {
			config.CorrelationLookbackHrs = int(req.Config.CorrelationLookbackHrs)
		}
	}

	correlations, err := h.correlationSvc.AnalyzeForCorrelations(ctx, config)
	if err != nil {
		h.logger.Error("Failed to analyze correlations", zap.Error(err))
		return nil, status.Error(codes.Internal, "correlation analysis failed")
	}

	// Store the correlations
	var storedCount int32
	for _, correlation := range correlations {
		if err := h.correlationSvc.StoreCorrelatedIncident(ctx, correlation); err != nil {
			h.logger.Warn("Failed to store correlation",
				zap.String("incident_id", correlation.IncidentID),
				zap.Error(err))
		} else {
			storedCount++
		}
	}

	h.logger.Info("Correlation analysis completed",
		zap.Int("correlations_found", len(correlations)),
		zap.Int32("correlations_stored", storedCount))

	return &pb.TriggerCorrelationResponse{
		CorrelationsFound:  int32(len(correlations)),
		CorrelationsStored: storedCount,
		Message:            fmt.Sprintf("Analysis complete: found %d correlations, stored %d", len(correlations), storedCount),
	}, nil
}
