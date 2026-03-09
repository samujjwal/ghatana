#!/usr/bin/env bash
# DCMaar Demo Alerts Setup
# Configures Prometheus alerts for the demo environment

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")/.."
CONFIG_DIR="${CONFIG_DIR:-$ROOT_DIR/configs}"
ALERTS_DIR="${CONFIG_DIR}/prometheus/alerts"

# Alert configurations
ALERT_RULES=(
    "high_error_rate"
    "high_latency"
    "service_down"
    "high_cpu_usage"
    "high_memory_usage"
    "high_disk_usage"
    "high_request_rate"
    "high_error_rate_http"
)

# Check prerequisites
check_prerequisites() {
    log "INFO" "🔍 Checking prerequisites..."
    
    local commands=("docker" "docker-compose")
    for cmd in "${commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            log "ERROR" "❌ Required command '$cmd' is not installed."
            return 1
        fi
    done
    
    if ! docker info &> /dev/null; then
        log "ERROR" "❌ Docker daemon is not running."
        return 1
    fi
    
    log "INFO" "✅ All prerequisites are met"
}

# Create alert rules directory structure
setup_alert_structure() {
    log "INFO" "📂 Setting up alert rules directory structure..."
    
    mkdir -p "$ALERTS_DIR"
    
    # Create main alert rules file
    cat > "${ALERTS_DIR}/demo-alerts.yml" << 'EOF'
groups:
- name: demo-alerts
  rules:
  # Include all alert rule files
  - alert: DummyAlert
    expr: vector(0)
    labels:
      severity: none
    annotations:
      description: "This is a dummy alert to ensure the alerting rules are loaded correctly."
      summary: "Dummy alert for configuration validation"

# Include all alert rule files
rule_files:
  - '*.rules.yml'
EOF

    log "INFO" "✅ Alert rules directory structure created at $ALERTS_DIR"
}

# Generate individual alert rule files
generate_alert_rules() {
    log "INFO" "📝 Generating alert rules..."
    
    # High Error Rate Alert
    cat > "${ALERTS_DIR}/high_error_rate.rules.yml" << 'EOF'
groups:
- name: error-rates
  rules:
  - alert: HighErrorRate
    expr: rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) > 0.05
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High error rate on {{ $labels.instance }}"
      description: "Error rate is {{ $value | humanizePercentage }} for {{ $labels.job }}"
EOF

    # High Latency Alert
    cat > "${ALERTS_DIR}/high_latency.rules.yml" << 'EOF'
groups:
- name: latency
  rules:
  - alert: HighLatency
    expr: histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket[5m])) by (le, job)) > 1
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "High latency on {{ $labels.job }}"
      description: "95th percentile latency is {{ $value | humanize }}s"
EOF

    # Service Down Alert
    cat > "${ALERTS_DIR}/service_down.rules.yml" << 'EOF'
groups:
- name: service-status
  rules:
  - alert: ServiceDown
    expr: up{job=~"demo-.+"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "{{ $labels.job }} is down"
      description: "{{ $labels.instance }} of job {{ $labels.job }} has been down for more than 1 minute."
EOF

    # High CPU Usage Alert
    cat > "${ALERTS_DIR}/high_cpu_usage.rules.yml" << 'EOF'
groups:
- name: cpu-usage
  rules:
  - alert: HighCPUUsage
    expr: 100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100 > 80
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High CPU usage on {{ $labels.instance }}"
      description: "CPU usage is {{ $value | humanizePercentage }}"
EOF

    # High Memory Usage Alert
    cat > "${ALERTS_DIR}/high_memory_usage.rules.yml" << 'EOF'
groups:
- name: memory-usage
  rules:
  - alert: HighMemoryUsage
    expr: (node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / node_memory_MemTotal_bytes * 100 > 85
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "High memory usage on {{ $labels.instance }}"
      description: "Memory usage is {{ $value | humanizePercentage }}"
EOF

    # High Disk Usage Alert
    cat > "${ALERTS_DIR}/high_disk_usage.rules.yml" << 'EOF'
groups:
- name: disk-usage
  rules:
  - alert: HighDiskUsage
    expr: 100 - (node_filesystem_avail_bytes{mountpoint="/",fstype!="tmpfs"} * 100) / node_filesystem_size_bytes{mountpoint="/",fstype!="tmpfs"} > 85
    for: 15m
    labels:
      severity: warning
    annotations:
      summary: "High disk usage on {{ $labels.instance }}"
      description: "Disk usage is {{ $value | humanizePercentage }}"
EOF

    # High Request Rate Alert
    cat > "${ALERTS_DIR}/high_request_rate.rules.yml" << 'EOF'
groups:
- name: request-rates
  rules:
  - alert: HighRequestRate
    expr: rate(http_requests_total[5m]) > 1000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High request rate on {{ $labels.instance }}"
      description: "Request rate is {{ $value | humanize }} req/s"
EOF

    # HTTP Error Rate Alert
    cat > "${ALERTS_DIR}/http_error_rate.rules.yml" << 'EOF'
groups:
- name: http-errors
  rules:
  - alert: HighHTTPErrorRate
    expr: sum(rate(http_requests_total{status=~"(4|5).."}[5m])) by (job, status) / sum(rate(http_requests_total[5m])) by (job) > 0.1
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High HTTP error rate for {{ $labels.job }}"
      description: "{{ $value | humanizePercentage }} of requests are failing with status {{ $labels.status }}"
EOF

    log "INFO" "✅ Generated ${#ALERT_RULES[@]} alert rule files"
}

# Configure Alertmanager
get_alertmanager_config() {
    local config_file="${CONFIG_DIR}/alertmanager/alertmanager.yml"
    
    mkdir -p "$(dirname "$config_file")"
    
    cat > "$config_file" << 'EOF'
global:
  resolve_timeout: 5m
  slack_api_url: '${SLACK_API_URL:-}'

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 12h
  receiver: 'demo-team'
  routes:
  - match:
      severity: 'critical'
    receiver: 'demo-team-pager'
    repeat_interval: 1h

receivers:
- name: 'demo-team'
  slack_configs:
  - channel: '#alerts-demo'
    send_resolved: true
    title: '{{ template "slack.dcmaar.title" . }}'
    text: '{{ template "slack.dcmaar.text" . }}'
    title_link: 'http://localhost:9090/alerts'
    icon_emoji: '🚨'
    color: '{{ if eq .Status "firing" }}danger{{ else }}good{{ end }}'

- name: 'demo-team-pager'
  slack_configs:
  - channel: '#alerts-demo-critical'
    send_resolved: true
    title: '🚨 CRITICAL: {{ .CommonAnnotations.summary }}'
    text: '{{ range .Alerts }}
      *Alert:* {{ .Annotations.summary }}
      *Description:* {{ .Annotations.description }}
      *Details:*
      {{ range .Labels.SortedPairs }} • *{{ .Name }}:* `{{ .Value }}`
      {{ end }}
    {{ end }}'
    title_link: 'http://localhost:9090/alerts'
    icon_emoji: '🚨'
    color: 'danger'

templates:
- '/etc/alertmanager/templates/*.tmpl'
EOF

    log "INFO" "✅ Alertmanager configuration created at $config_file"
}

# Create alert templates
create_alert_templates() {
    local templates_dir="${CONFIG_DIR}/alertmanager/templates"
    mkdir -p "$templates_dir"
    
    # Slack template
    cat > "${templates_dir}/slack.tmpl" << 'EOF'
{{ define "slack.dcmaar.title" }}{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }} alert(s) firing{{ end }}{{ end }}

{{ define "slack.dcmaar.text" }}
{{ if .Alerts -}}
{{ range .Alerts -}}
*Alert:* {{ .Annotations.summary }}
{{- if .Annotations.description }}
*Description:* {{ .Annotations.description }}
{{- end }}
*Details:*
{{- range .Labels.SortedPairs }} • *{{ .Name }}:* `{{ .Value }}`
{{- end }}
{{- if .GeneratorURL }}
*Source:* <{{ .GeneratorURL }}|:chart_with_upwards_trend:>
{{- end }}
{{- end }}
{{- else }}
*No alerts firing* :tada:
{{- end }}
{{- end }}

{{ define "slack.dcmaar.title.link" }}{{ .Status | toUpper }}: {{ .CommonAnnotations.summary }}{{ end }}
{{ define "slack.dcmaar.fallback" }}{{ .CommonAnnotations.summary }}: {{ .CommonAnnotations.description }}{{ end }}
EOF

    # Email template
    cat > "${templates_dir}/email.tmpl" << 'EOF'
{{ define "email.dcmaar.html" }}
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>{{ template "email.dcmaar.title" . }}</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .alert { border-left: 4px solid #e74c3c; padding: 10px 15px; margin: 10px 0; background: #f9f9f9; }
        .alert.resolved { border-left-color: #2ecc71; }
        .label { font-weight: bold; }
        .value { font-family: monospace; }
        .footer { margin-top: 20px; font-size: 0.8em; color: #777; }
    </style>
</head>
<body>
    <h2>{{ template "email.dcmaar.title" . }}</h2>
    
    {{ if .Alerts }}
        {{ range .Alerts }}
            <div class="alert {{ if eq .Status "resolved" }}resolved{{ end }}">
                <h3>{{ .Annotations.summary }}</h3>
                {{ if .Annotations.description }}
                    <p>{{ .Annotations.description }}</p>
                {{ end }}
                
                <table>
                    {{ range .Labels.SortedPairs }}
                        <tr>
                            <td class="label">{{ .Name }}:</td>
                            <td class="value">{{ .Value }}</td>
                        </tr>
                    {{ end }}
                </table>
                
                {{ if .GeneratorURL }}
                    <p><a href="{{ .GeneratorURL }}">View in Prometheus</a></p>
                {{ end }}
            </div>
        {{ end }}
    {{ else }}
        <p>No alerts are currently firing.</p>
    {{ end }}
    
    <div class="footer">
        <p>This is an automated message from DCMaar Monitoring.</p>
    </div>
</body>
</html>
{{ end }}

{{ define "email.dcmaar.title" }}{{ if eq .Status "firing" }}[FIRING] {{ .Alerts.Firing | len }} alerts{{ else }}[RESOLVED] {{ .Alerts.Resolved | len }} alerts{{ end }}{{ end }}
{{ define "email.dcmaar.text" }}{{ if eq .Status "firing" }}Alert: {{ .CommonAnnotations.summary }}

{{ .CommonAnnotations.description }}

Alert details:
{{ range .Alerts.Firing }}{{ range .Labels.SortedPairs }}{{ .Name }}: {{ .Value }}
{{ end }}
{{ end }}{{ else }}{{ range .Alerts.Resolved }}{{ .Annotations.summary }} is now resolved
{{ end }}{{ end }}{{ end }}
EOF

    log "INFO" "✅ Alert templates created in $templates_dir"
}

# Update Prometheus configuration
update_prometheus_config() {
    local prometheus_config="${CONFIG_DIR}/prometheus/prometheus.yml"
    
    if [ ! -f "$prometheus_config" ]; then
        log "WARN" "❌ Prometheus config not found at $prometheus_config"
        return 1
    fi
    
    # Backup original config
    cp "$prometheus_config" "${prometheus_config}.bak"
    
    # Check if alerting config already exists
    if ! grep -q "alerting:" "$prometheus_config"; then
        cat >> "$prometheus_config" << 'EOF'

# Alerting configuration
alerting:
  alertmanagers:
  - static_configs:
    - targets: ['alertmanager:9093']

# Load alert rules
rule_files:
  - '/etc/prometheus/rules/*.yml'
  - '/etc/prometheus/rules/*.rules.yml'
EOF
        log "INFO" "✅ Added alerting configuration to $prometheus_config"
    else
        log "INFO" "ℹ️  Alerting configuration already exists in $prometheus_config"
    fi
    
    # Create rules directory if it doesn't exist
    mkdir -p "${CONFIG_DIR}/prometheus/rules"
    
    log "INFO" "🔁 Restarting Prometheus to apply changes..."
    docker-compose -f docker-compose.demo.yml restart prometheus
}

# Main function
main() {
    local command=${1:-help}
    
    case $command in
        setup)
            check_prerequisites
            setup_alert_structure
            generate_alert_rules
            get_alertmanager_config
            create_alert_templates
            update_prometheus_config
            log "INFO" "🎉 Alerting setup completed successfully!"
            log "INFO" "   - Alert rules: $ALERTS_DIR"
            log "INFO" "   - Alertmanager config: ${CONFIG_DIR}/alertmanager/alertmanager.yml"
            log "INFO" "   - Templates: ${CONFIG_DIR}/alertmanager/templates/"
            ;;
        update-prometheus)
            update_prometheus_config
            ;;
        help|*)
            echo "Usage: $0 {setup|update-prometheus|help}"
            echo "  setup             - Set up complete alerting infrastructure"
            echo "  update-prometheus - Update Prometheus configuration with alerting rules"
            echo "  help              - Show this help message"
            exit 1
            ;;
    esac
}

# Logging function
log() {
    local level=$1
    local message=$2
    local timestamp
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "[${timestamp}] [${level}] ${message}"
}

# Run the main function
main "$@"

exit 0
