#!/bin/bash
# Capability 4 Incident Summarizer (LLM) Demo
# This script demonstrates the AI-powered incident summarization system

echo "=== DCMAAR Horizontal Slice - Capability 4 Demo ==="
echo "Incident Summarizer: LLM-Powered Technical Analysis for SRE Teams"
echo

echo "🤖 AI Architecture Overview:"
echo "┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐"
echo "│ Correlated      │────│   LLM Summarizer │────│  Incident Summary   │"
echo "│ Incidents       │    │   (OpenAI API)   │    │  (Technical Report) │"
echo "│ (Raw Data)      │    │                  │    │                     │"
echo "└─────────────────┘    └──────────────────┘    └─────────────────────┘"
echo "         │                        │                        │"
echo "         ├── Statistical Data ────┼── Few-Shot Prompting ──┤"
echo "         │                        │                        │"
echo "         ▼                        ▼                        ▼"
echo "┌─────────────────────────────────────────────────────────────────────┐"
echo "│                    ClickHouse Storage                               │"
echo "│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │"
echo "│  │correlated_      │  │incident_summaries│  │summary_feedback  │  │"
echo "│  │incidents        │  │   (Generated)    │  │  (Quality Data)  │  │"
echo "│  └─────────────────┘  └──────────────────┘  └──────────────────┘  │"
echo "└─────────────────────────────────────────────────────────────────────┘"
echo

echo "🧠 LLM Processing Pipeline:"
echo "1. Data Ingestion: Receives correlated incident with anomaly + web events"
echo "2. Prompt Engineering: Few-shot prompting with SRE examples and JSON structure"
echo "3. AI Analysis: OpenAI GPT processes statistical correlations and generates insights"
echo "4. Structured Parsing: Extracts timeline, root cause, impact, and recommendations"
echo "5. Quality Validation: Word count limits (<400 words), confidence scoring"
echo "6. Storage & Feedback: Persists summaries with versioning and quality tracking"
echo

echo "📝 Example LLM-Generated Summary:"
echo "┌─ Incident Summary ──────────────────────────────────────────────────┐"
echo "│ Incident ID: incident-abc123                                        │"
echo "│ Generated: 2025-09-27 14:35 UTC                                     │"
echo "│ Model: gpt-4 | Confidence: 0.87 | Tokens: 456                      │"
echo "│                                                                     │"
echo "│ SUMMARY:                                                            │"
echo "│ Multi-component performance degradation detected on device-123      │"
echo "│ between 14:30-14:35 UTC. Agent detected CPU anomaly (3.2σ above    │"
echo "│ baseline) followed by memory pressure (2.8σ). Browser extension    │"
echo "│ simultaneously reported severe latency spikes on example.com       │"
echo "│ (4.8s) and api.example.com (7.2s). Strong correlation (r=0.74)     │"
echo "│ suggests resource exhaustion impacting web performance.             │"
echo "│                                                                     │"
echo "│ TIMELINE:                                                           │"
echo "│ • 14:30:00 - CPU usage anomaly detected (3.2σ) [HIGH]              │"
echo "│ • 14:31:30 - Page load spike on example.com (4.8s) [HIGH]          │"
echo "│ • 14:32:15 - Memory pressure anomaly (2.8σ) [MEDIUM]               │"
echo "│ • 14:33:00 - API timeout on api.example.com (7.2s) [CRITICAL]      │"
echo "│                                                                     │"
echo "│ ROOT CAUSE:                                                         │"
echo "│ Resource exhaustion (CPU + memory) causing downstream web           │"
echo "│ performance degradation                                             │"
echo "│                                                                     │"
echo "│ IMPACT:                                                             │"
echo "│ User-facing performance degradation on 2 domains, potential        │"
echo "│ service timeouts affecting customer experience                      │"
echo "│                                                                     │"
echo "│ RECOMMENDATIONS:                                                    │"
echo "│ 1. Investigate CPU-intensive processes on device-123               │"
echo "│ 2. Check for memory leaks in running applications                  │"
echo "│ 3. Scale web service capacity for affected domains                 │"
echo "│ 4. Implement monitoring alerts for similar correlation patterns    │"
echo "└─────────────────────────────────────────────────────────────────────┘"
echo

echo "⚙️  LLM Configuration:"
echo "• Model: GPT-4 / GPT-3.5-turbo (configurable)"
echo "• Temperature: 0.3 (factual, consistent responses)"
echo "• Max Tokens: 800 (concise but comprehensive)"
echo "• Max Summary Words: <400 (per requirements)"
echo "• Response Format: Structured JSON with validation"
echo "• Fallback Strategy: Rule-based summary if LLM parsing fails"
echo

echo "🎯 Few-Shot Prompt Engineering:"
echo "• System Role: 'Expert SRE analyzing performance incidents'"
echo "• Response Format: JSON schema with timeline, root cause, recommendations"
echo "• Example Summaries: High-quality templates for consistent output"
echo "• Technical Language: SRE-focused terminology and analysis patterns"
echo "• Validation Rules: Word limits, confidence scoring, timeline structure"
echo

echo "📊 Quality & Feedback System:"
echo "• Accuracy Score: How factually correct is the analysis?"
echo "• Completeness Score: Did it cover all important aspects?"
echo "• Usefulness Score: How actionable for operations teams?"
echo "• Human Review Flags: SRE validation and corrections"
echo "• Model Performance Tracking: Token usage, response times, success rates"
echo

echo "🔧 Implementation Status:"
echo "✅ LLMSummarizer service with OpenAI API integration"
echo "✅ Few-shot prompt engineering with SRE examples"
echo "✅ Structured JSON response parsing with fallback handling"
echo "✅ ClickHouse schema for summaries, feedback, and quality metrics"
echo "✅ SummarizerService orchestration with auto-batch processing"
echo "✅ REST API endpoints for manual and automated summary generation"
echo "✅ Quality tracking system with human review capabilities"
echo

echo "🚀 API Endpoints:"
echo "• GET  /incidents/{id}/summary          - Retrieve existing summary"
echo "• POST /incidents/{id}/summary          - Generate new summary"
echo "• GET  /summaries?start_time=...&limit= - List summaries by time range"
echo "• POST /summaries/auto-generate         - Trigger batch summarization"
echo

echo "📁 Files Created for Capability 4:"
echo "• services/server/internal/summarizer/llm.go"
echo "• services/server/internal/summarizer/service.go"
echo "• services/server/internal/handlers/summarizer.go"
echo "• services/server/migrations/0005_incident_summaries.sql"
echo "• services/server/internal/summarizer/llm_test.go"
echo

echo "💡 Integration with Previous Capabilities:"
echo "• Capability 1 (Agent Anomalies) → Statistical Input Data"
echo "• Capability 2 (Extension Latency) → Web Performance Context"
echo "• Capability 3 (Correlation) → Incident Data Source"
echo "• Capability 4 (LLM Summarizer) → Human-Readable Analysis ← YOU ARE HERE"
echo

echo "✨ Capability 4 Status: COMPLETE (MVP)"
echo "Next: Capability 5 (Policy Simulation Sandbox) 🎯"
echo
echo "🎭 Demo Commands (when server is running):"
echo "# Generate summary for incident"
echo "curl -X POST 'http://localhost:8080/incidents/inc-123/summary' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"max_tokens\": 600, \"temperature\": 0.3}'"
echo
echo "# Get summaries from last 24 hours"
echo "curl 'http://localhost:8080/summaries?limit=10'"
echo
echo "# Trigger auto-summarization"
echo "curl -X POST 'http://localhost:8080/summaries/auto-generate'"