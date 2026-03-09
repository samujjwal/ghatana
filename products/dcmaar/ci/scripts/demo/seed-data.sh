#!/usr/bin/env bash
# DCMaar Demo Data Seeder
# Generates and loads sample data for demo purposes

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")/.."
DATA_DIR="${DATA_DIR:-$ROOT_DIR/data/demo}"
LOG_FILE="${LOG_FILE:-$ROOT_DIR/logs/demo-seed-$(date +%Y%m%d-%H%M%S).log}"

# Database configuration
DB_HOST=${DB_HOST:-postgres}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-dcmaar}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}
DB_URL="postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

# API configuration
API_URL=${API_URL:-http://server:8080}
API_KEY=${DEMO_API_KEY:-}

# Ensure directories exist
mkdir -p "$(dirname "$LOG_FILE")" "$DATA_DIR"

# Logging function
log() {
    local level=$1
    local message=$2
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "[${timestamp}] [${level}] ${message}" | tee -a "$LOG_FILE"
}

# Check prerequisites
check_prerequisites() {
    log "INFO" "🔍 Checking prerequisites..."
    
    # Check for required commands
    local commands=("curl" "jq" "psql")
    for cmd in "${commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            log "ERROR" "❌ Required command '$cmd' is not installed."
            return 1
        fi
    done
    
    # Check database connection
    if ! PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" &> /dev/null; then
        log "ERROR" "❌ Could not connect to the database at ${DB_HOST}:${DB_PORT}/${DB_NAME}"
        return 1
    fi
    
    log "INFO" "✅ All prerequisites are met"
}

# Generate sample users
generate_users() {
    local count=${1:-10}
    log "INFO" "👥 Generating $count sample users..."
    
    mkdir -p "$DATA_DIR/users"
    
    for ((i=1; i<=count; i++)); do
        local user_id="user_$(uuidgen | tr -d '-' | cut -c1-8)"
        local username="user${i}@example.com"
        local first_name=("John" "Jane" "Michael" "Emily" "David" "Sarah" "Robert" "Lisa" "William" "Emma")
        local last_name=("Smith" "Johnson" "Williams" "Brown" "Jones" "Miller" "Davis" "Garcia" "Rodriguez" "Wilson")
        local fname="${first_name[$((RANDOM % ${#first_name[@]}))]}"
        local lname="${last_name[$((RANDOM % ${#last_name[@]}))]}"
        
        cat > "$DATA_DIR/users/${user_id}.json" << EOF
{
    "id": "${user_id}",
    "username": "${username}",
    "email": "${username}",
    "firstName": "${fname}",
    "lastName": "${lname}",
    "role": "user",
    "createdAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
    "updatedAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
    done
    
    log "INFO" "✅ Generated $count sample users in $DATA_DIR/users/"
}

# Generate sample events
generate_events() {
    local count=${1:-100}
    local users=("$DATA_DIR/users/"*)
    local user_count=${#users[@]}
    
    if [ "$user_count" -eq 0 ]; then
        log "WARN" "No users found. Generating sample users first..."
        generate_users 10
        users=("$DATA_DIR/users/"*)
        user_count=${#users[@]}
    fi
    
    log "INFO" "📝 Generating $count sample events..."
    
    local event_types=("page_view" "click" "form_submit" "login" "logout" "download" "api_call")
    local pages=("/" "/dashboard" "/settings" "/reports" "/analytics" "/users" "/api/docs")
    
    mkdir -p "$DATA_DIR/events"
    
    for ((i=1; i<=count; i++)); do
        local event_id="event_$(uuidgen | tr -d '-' | cut -c1-8)"
        local user_file="${users[$((RANDOM % user_count))]}"
        local user_id
        user_id=$(jq -r '.id' "$user_file")
        local event_type="${event_types[$((RANDOM % ${#event_types[@]}))]}"
        local page="${pages[$((RANDOM % ${#pages[@]}))]}"
        local timestamp
        timestamp=$(date -u -d "-$((RANDOM % 30)) days -$((RANDOM % 24)) hours -$((RANDOM % 60)) minutes" +"%Y-%m-%dT%H:%M:%SZ")
        
        cat > "$DATA_DIR/events/${event_id}.json" << EOF
{
    "id": "${event_id}",
    "userId": "${user_id}",
    "type": "${event_type}",
    "page": "${page}",
    "timestamp": "${timestamp}",
    "metadata": {
        "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36",
        "ip": "192.168.1.$((RANDOM % 255))",
        "referrer": "https://example.com${pages[$((RANDOM % ${#pages[@]}))]}",
        "sessionId": "session_$(uuidgen | tr -d '-' | cut -c1-12)",
        "duration": $((RANDOM % 300))
    }
}
EOF
    done
    
    log "INFO" "✅ Generated $count sample events in $DATA_DIR/events/"
}

# Load data into the database
load_data() {
    log "INFO" "📤 Loading data into the database..."
    
    # Load users
    if [ -d "$DATA_DIR/users" ]; then
        for user_file in "$DATA_DIR/users/"*.json; do
            if [ -f "$user_file" ]; then
                local user_json
                user_json=$(cat "$user_file")
                
                # Insert user into database
                PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "
                    INSERT INTO users (id, username, email, first_name, last_name, role, created_at, updated_at)
                    VALUES (
                        '$(jq -r '.id' "$user_file")',
                        '$(jq -r '.username' "$user_file")',
                        '$(jq -r '.email' "$user_file")',
                        '$(jq -r '.firstName' "$user_file")',
                        '$(jq -r '.lastName' "$user_file")',
                        '$(jq -r '.role' "$user_file")',
                        '$(jq -r '.createdAt' "$user_file")'::timestamptz,
                        '$(jq -r '.updatedAt' "$user_file")'::timestamptz
                    ) ON CONFLICT (id) DO NOTHING;
                "
            fi
        done
        log "INFO" "✅ Loaded users into the database"
    fi
    
    # Load events
    if [ -d "$DATA_DIR/events" ]; then
        for event_file in "$DATA_DIR/events/"*.json; do
            if [ -f "$event_file" ]; then
                local event_json
                event_json=$(cat "$event_file")
                
                # Insert event into database
                PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "
                    INSERT INTO events (id, user_id, type, page, timestamp, metadata)
                    VALUES (
                        '$(jq -r '.id' "$event_file")',
                        '$(jq -r '.userId' "$event_file")',
                        '$(jq -r '.type' "$event_file")',
                        '$(jq -r '.page' "$event_file")',
                        '$(jq -r '.timestamp' "$event_file")'::timestamptz,
                        '$(jq -c '.metadata' "$event_file")'::jsonb
                    ) ON CONFLICT (id) DO NOTHING;
                "
            fi
        done
        log "INFO" "✅ Loaded events into the database"
    fi
}

# Generate sample metrics
generate_metrics() {
    log "INFO" "📊 Generating sample metrics..."
    
    local start_time
    start_time=$(date -u -d "30 days ago" +"%Y-%m-%dT%H:%M:%SZ")
    local end_time
    end_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    mkdir -p "$DATA_DIR/metrics"
    
    # Generate system metrics
    cat > "$DATA_DIR/metrics/system_metrics.json" << EOF
{
    "startTime": "$start_time",
    "endTime": "$end_time",
    "metrics": [
        {
            "name": "cpu_usage",
            "unit": "percent",
            "values": [
                {
                    "timestamp": "$start_time",
                    "value": $((10 + RANDOM % 20))
                },
                {
                    "timestamp": "$(date -u -d "15 days ago" +"%Y-%m-%dT%H:%M:%SZ")",
                    "value": $((15 + RANDOM % 30))
                },
                {
                    "timestamp": "$end_time",
                    "value": $((5 + RANDOM % 15))
                }
            ]
        },
        {
            "name": "memory_usage",
            "unit": "MB",
            "values": [
                {
                    "timestamp": "$start_time",
                    "value": $((500 + RANDOM % 1000))
                },
                {
                    "timestamp": "$(date -u -d "15 days ago" +"%Y-%m-%dT%H:%M:%SZ")",
                    "value": $((700 + RANDOM % 1200))
                },
                {
                    "timestamp": "$end_time",
                    "value": $((300 + RANDOM % 800))
                }
            ]
        }
    ]
}
EOF
    
    log "INFO" "✅ Generated sample metrics in $DATA_DIR/metrics/"
}

# Main function
main() {
    log "INFO" "🚀 Starting DCMaar demo data generation..."
    
    # Check prerequisites
    if ! check_prerequisites; then
        log "ERROR" "❌ Prerequisites check failed. See logs above for details."
        exit 1
    fi
    
    # Generate sample data
    generate_users 10
    generate_events 1000
    generate_metrics
    
    # Load data into the database if requested
    if [ "${SKIP_LOAD:-false}" != "true" ]; then
        load_data
    else
        log "INFO" "ℹ️  Skipping database load (SKIP_LOAD=true)"
    fi
    
    log "INFO" "🎉 Demo data generation completed successfully!"
    log "INFO" "📊 Data directory: $DATA_DIR"
    log "INFO" "📝 Log file: $LOG_FILE"
}

# Run the main function
main

exit 0
