#!/bin/bash
# Capability 3 Cross-Source Correlation Demo
# This script demonstrates the end-to-end correlation between agent anomalies and extension latency events

echo "=== DCMAAR Horizontal Slice - Capability 3 Demo ==="
echo "Cross-Source Correlation: Agent Anomalies ↔ Extension Latency Events"
echo

echo "🏗️  Architecture Overview:"
echo "┌─────────────┐    ┌─────────────────┐    ┌─────────────────────┐"
echo "│   Agent     │────│   Extension     │────│  Correlation Svc    │"
echo "│  (Anomaly   │    │   (Latency      │    │  (Statistical       │"
echo "│   Events)   │    │    Events)      │    │   Analysis)         │"
echo "└─────────────┘    └─────────────────┘    └─────────────────────┘"
echo "       │                     │                        │"
echo "       ├─── EventEnvelope ───┼─── WebEventProto ──────┤"
echo "       │                     │                        │"
echo "       ▼                     ▼                        ▼"
echo "┌───────────────────────────────────────────────────────────────┐"
echo "│                  ClickHouse Storage                           │"
echo "│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐ │"
echo "│  │   events    │  │browser_events│  │correlated_incidents  │ │"
echo "│  │(anomalies)  │  │ (latency)    │  │   (correlations)     │ │"
echo "│  └─────────────┘  └──────────────┘  └──────────────────────┘ │"
echo "└───────────────────────────────────────────────────────────────┘"
echo

echo "📊 Correlation Algorithm:"
echo "1. Time Window Analysis (5-minute windows)"
echo "2. Device-based Joining (host_id correlation)"
echo "3. Statistical Correlation (Pearson coefficient)"
echo "4. Confidence Scoring (sample size + temporal proximity)"
echo "5. Incident Classification (performance/resource/mixed)"
echo "6. Severity Assessment (low/medium/high/critical)"
echo

echo "🔧 Implementation Status:"
echo "✅ CorrelationService Go service with statistical analysis"
echo "✅ ClickHouse schema with optimized indexes and partitioning"
echo "✅ gRPC API endpoints for incident retrieval and analysis triggers"  
echo "✅ Protobuf schema for correlation requests/responses"
echo "✅ Time window correlation logic with configurable thresholds"
echo "✅ Multi-metric correlation (CPU/Memory anomalies vs Web latency)"
echo

echo "🎯 Example Correlation Scenario:"
echo "┌─ Timeline ──────────────────────────────────────────────────┐"
echo "│ T+0:00  │ Agent detects CPU anomaly (score: 3.2σ)           │"
echo "│ T+1:30  │ Extension reports slow page load (4.8s latency)   │"
echo "│ T+2:15  │ Agent detects memory anomaly (score: 2.8σ)        │"
echo "│ T+3:00  │ Extension reports API timeout (7.2s latency)      │"
echo "│ T+5:00  │ Correlation Service Analysis:                     │"
echo "│         │   • Correlation Score: 0.74 (74% correlation)    │"
echo "│         │   • Confidence: 0.82 (high confidence)           │"
echo "│         │   • Incident Type: 'mixed' (resource + perf)     │"
echo "│         │   • Severity: 'high' (multiple high scores)      │"
echo "│         │   • Affected Domains: ['example.com', 'api.x']   │"
echo "└─────────────────────────────────────────────────────────────┘"
echo

echo "📈 Key Metrics & Thresholds:"
echo "• Correlation Score Threshold: ≥60% (configurable)"
echo "• Confidence Level Threshold: ≥70% (configurable)"
echo "• Latency Threshold: ≥3000ms (only analyze slow requests)"
echo "• Anomaly Score Threshold: ≥2.0σ (significant anomalies only)"
echo "• Time Window: 5 minutes (configurable)"
echo "• Lookback Period: 1 hour (configurable)"
echo

echo "🚀 API Endpoints:"
echo "• GetCorrelatedIncidentsByWindow(tenant_id, device_id, time_range)"
echo "• TriggerCorrelationAnalysis(config_overrides)"
echo

echo "📁 Files Created for Capability 3:"
echo "• services/server/migrations/0004_correlation_tables.sql"
echo "• services/server/internal/services/correlation_service.go"
echo "• services/server/internal/handlers/correlation.go"
echo "• proto/dcmaar/v1/correlation.proto"
echo "• services/server/internal/services/correlation_service_test.go"
echo

echo "✨ Capability 3 Status: COMPLETE (MVP)"
echo "Ready for integration with Capability 4 (Incident Summarizer) 🎯"