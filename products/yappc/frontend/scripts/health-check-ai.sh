#!/bin/bash

###############################################################################
# AI Features Health Check Script
# 
# Monitors the health and performance of AI features including:
# - Database connectivity
# - AI model availability
# - Embedding pipeline status
# - Cost tracking
# - Performance metrics
###############################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

###############################################################################
# Helper Functions
###############################################################################

log_section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

check_status() {
    local name=$1
    local status=$2
    
    if [ "$status" = "OK" ]; then
        echo -e "  ${GREEN}✓${NC} $name: ${GREEN}$status${NC}"
    elif [ "$status" = "WARNING" ]; then
        echo -e "  ${YELLOW}⚠${NC} $name: ${YELLOW}$status${NC}"
    else
        echo -e "  ${RED}✗${NC} $name: ${RED}$status${NC}"
    fi
}

###############################################################################
# Database Checks
###############################################################################

log_section "Database Health"

cd "$PROJECT_ROOT/apps/api"

# Check database connection
if npx prisma db execute --stdin <<< "SELECT 1" &>/dev/null; then
    check_status "Database Connection" "OK"
else
    check_status "Database Connection" "FAILED"
    exit 1
fi

# Check AI tables exist
AI_TABLES=("AIInsight" "Prediction" "AnomalyAlert" "CopilotSession" "AIGeneratedPlan" "VectorEmbedding" "UserAIPreferences" "AIMetric" "ItemEmbedding")

for table in "${AI_TABLES[@]}"; do
    if npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"$table\"" &>/dev/null; then
        count=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"$table\"" 2>/dev/null | tail -n 1)
        check_status "$table table" "OK ($count records)"
    else
        check_status "$table table" "MISSING"
    fi
done

###############################################################################
# AI Service Checks
###############################################################################

log_section "AI Service Health"

# Check OpenAI API key
if [ -n "${OPENAI_API_KEY:-}" ]; then
    check_status "OpenAI API Key" "CONFIGURED"
else
    check_status "OpenAI API Key" "MISSING"
fi

# Check Anthropic API key
if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
    check_status "Anthropic API Key" "CONFIGURED"
else
    check_status "Anthropic API Key" "NOT CONFIGURED"
fi

# Test OpenAI connection (simple)
if [ -n "${OPENAI_API_KEY:-}" ]; then
    if curl -s -f -H "Authorization: Bearer $OPENAI_API_KEY" https://api.openai.com/v1/models &>/dev/null; then
        check_status "OpenAI API Connection" "OK"
    else
        check_status "OpenAI API Connection" "FAILED"
    fi
fi

###############################################################################
# Embedding Pipeline Checks
###############################################################################

log_section "Embedding Pipeline Status"

# Check if embedding pipeline is running
if pgrep -f "embedding-pipeline" > /dev/null; then
    check_status "Pipeline Process" "RUNNING"
    
    # Get process details
    pid=$(pgrep -f "embedding-pipeline" | head -n 1)
    uptime=$(ps -o etime= -p "$pid" 2>/dev/null || echo "unknown")
    check_status "Pipeline Uptime" "$uptime"
else
    check_status "Pipeline Process" "NOT RUNNING"
fi

# Check recent embeddings
recent_embeddings=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"ItemEmbedding\" WHERE \"createdAt\" > NOW() - INTERVAL '24 hours'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Embeddings (24h)" "$recent_embeddings generated"

###############################################################################
# Performance Metrics
###############################################################################

log_section "Performance Metrics"

# AI Metrics from last 24 hours
if npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"AIMetric\" WHERE timestamp > NOW() - INTERVAL '24 hours'" &>/dev/null; then
    total_operations=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"AIMetric\" WHERE timestamp > NOW() - INTERVAL '24 hours'" 2>/dev/null | tail -n 1)
    check_status "AI Operations (24h)" "$total_operations"
    
    # Average latency
    avg_latency=$(npx prisma db execute --stdin <<< "SELECT ROUND(AVG(\"latencyMs\")) FROM \"AIMetric\" WHERE timestamp > NOW() - INTERVAL '24 hours'" 2>/dev/null | tail -n 1 || echo "N/A")
    check_status "Avg Latency (24h)" "${avg_latency}ms"
    
    # Success rate
    success_count=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"AIMetric\" WHERE success = true AND timestamp > NOW() - INTERVAL '24 hours'" 2>/dev/null | tail -n 1 || echo "0")
    if [ "$total_operations" -gt 0 ]; then
        success_rate=$((success_count * 100 / total_operations))
        check_status "Success Rate (24h)" "${success_rate}%"
    fi
fi

###############################################################################
# Cost Tracking
###############################################################################

log_section "Cost Tracking"

# Daily cost
daily_cost=$(npx prisma db execute --stdin <<< "SELECT COALESCE(ROUND(SUM(\"costUSD\")::numeric, 4), 0) FROM \"AIMetric\" WHERE timestamp > NOW() - INTERVAL '24 hours'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Cost (24h)" "\$$daily_cost"

# Weekly cost
weekly_cost=$(npx prisma db execute --stdin <<< "SELECT COALESCE(ROUND(SUM(\"costUSD\")::numeric, 2), 0) FROM \"AIMetric\" WHERE timestamp > NOW() - INTERVAL '7 days'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Cost (7d)" "\$$weekly_cost"

# Cost by model
echo ""
echo "  Cost by Model (24h):"
npx prisma db execute --stdin <<< "SELECT model, ROUND(SUM(\"costUSD\")::numeric, 4) as cost FROM \"AIMetric\" WHERE timestamp > NOW() - INTERVAL '24 hours' GROUP BY model ORDER BY cost DESC LIMIT 5" 2>/dev/null | tail -n +2 | while read -r line; do
    echo "    - $line"
done

###############################################################################
# Active Features
###############################################################################

log_section "Active AI Features"

# Recent insights
recent_insights=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"AIInsight\" WHERE \"createdAt\" > NOW() - INTERVAL '7 days'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Insights (7d)" "$recent_insights"

# Recent predictions
recent_predictions=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"Prediction\" WHERE \"createdAt\" > NOW() - INTERVAL '7 days'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Predictions (7d)" "$recent_predictions"

# Recent anomalies
recent_anomalies=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"AnomalyAlert\" WHERE \"detectedAt\" > NOW() - INTERVAL '7 days'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Anomalies (7d)" "$recent_anomalies"

# Active copilot sessions
active_sessions=$(npx prisma db execute --stdin <<< "SELECT COUNT(*) FROM \"CopilotSession\" WHERE status = 'ACTIVE'" 2>/dev/null | tail -n 1 || echo "0")
check_status "Active Copilot Sessions" "$active_sessions"

###############################################################################
# Recommendations
###############################################################################

log_section "Recommendations"

# Check if costs are high
if (( $(echo "$daily_cost > 10" | bc -l) )); then
    echo -e "  ${YELLOW}⚠${NC} Daily costs are high (\$$daily_cost)"
    echo "    → Consider caching more aggressively"
    echo "    → Review embedding generation frequency"
fi

# Check if pipeline is not running
if ! pgrep -f "embedding-pipeline" > /dev/null; then
    echo -e "  ${YELLOW}⚠${NC} Embedding pipeline is not running"
    echo "    → Start with: npx ts-node apps/api/src/jobs/embedding-pipeline.ts schedule"
    echo "    → Or use PM2: pm2 start ecosystem.ai.config.js"
fi

# Check if error rate is high
if [ "$total_operations" -gt 0 ] && [ "$success_count" -gt 0 ]; then
    error_rate=$((100 - success_rate))
    if [ "$error_rate" -gt 5 ]; then
        echo -e "  ${YELLOW}⚠${NC} Error rate is high (${error_rate}%)"
        echo "    → Check logs for common errors"
        echo "    → Verify API key validity"
    fi
fi

###############################################################################
# Summary
###############################################################################

echo ""
log_section "Health Check Complete"
echo ""
echo "  Generated at: $(date)"
echo "  Project: YAPPC AI Features"
echo ""
echo -e "${YELLOW}Commands:${NC}"
echo "  • View detailed metrics: ${BLUE}npx prisma studio${NC}"
echo "  • Check logs: ${BLUE}pm2 logs embedding-pipeline${NC}"
echo "  • Restart pipeline: ${BLUE}pm2 restart embedding-pipeline${NC}"
echo ""
