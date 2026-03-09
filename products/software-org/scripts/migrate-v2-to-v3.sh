#!/bin/bash
# migrate-v2-to-v3.sh
# Migration script for transitioning from specs2 (v2) to workflows/v3
# 
# This script helps teams migrate their custom workflow configurations
# from the v2 inline agent definitions to v3 operator-based references.
#
# Usage: ./migrate-v2-to-v3.sh [options]
#   --dry-run     Show what would be migrated without making changes
#   --backup      Create backup before migration (default: true)
#   --input-dir   Source directory with v2 configs (default: devsecops/specs2)
#   --output-dir  Target directory for v3 configs (default: workflows/v3)
#   --help        Show this help message

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DRY_RUN=false
BACKUP=true
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
INPUT_DIR="${PROJECT_ROOT}/libs/java/software-org/src/main/resources/devsecops/specs2"
OUTPUT_DIR="${PROJECT_ROOT}/libs/java/software-org/src/main/resources/workflows/v3"
OPERATOR_INDEX="${PROJECT_ROOT}/libs/java/software-org/src/main/resources/operations/OPERATOR_INDEX.yaml"

# Agent to operator mappings
declare -A AGENT_TO_OPERATOR=(
    # Planning domain
    ["EnhancedProductIdeaClarifier"]="planning/product_discovery_operator:clarify"
    ["EnhancedPlanBestPracticeAgent"]="cross-cutting/best_practice"
    ["EnhancedPlanMilestoneTrackerAgent"]="cross-cutting/milestone_tracker"
    ["BusinessGoalsAgent"]="planning/business_goals_operator:define_goals"
    ["SuccessCriteriaAgent"]="planning/business_goals_operator:define_criteria"
    ["ConstraintsIdentifier"]="planning/business_goals_operator:identify_constraints"
    ["AssumptionsDocumenter"]="planning/business_goals_operator:document_assumptions"
    ["StakeholderIdentifier"]="planning/stakeholder_operator:identify"
    ["StakeholderMapper"]="planning/stakeholder_operator:map"
    ["EngagementPlanner"]="planning/stakeholder_operator:engage"
    ["RequirementsGatherer"]="planning/requirements_operator:gather"
    ["FunctionalRequirementsAnalyst"]="planning/requirements_operator:analyze_functional"
    ["NFRAnalyst"]="planning/requirements_operator:analyze_nfr"
    ["TraceabilityMapper"]="planning/requirements_operator:trace"
    
    # Design domain
    ["SolutionApproachAgent"]="design/architecture_operator:solution_approach"
    ["ArchitectureAgent"]="design/architecture_operator:architecture"
    ["HighLevelDesignAgent"]="design/architecture_operator:high_level_design"
    ["ComponentIdentifier"]="design/component_operator:identify"
    ["ComponentDesigner"]="design/component_operator:design"
    ["DataModelAgent"]="design/data_model_operator:entity_modeling"
    ["SchemaDesigner"]="design/data_model_operator:schema_design"
    ["APIContractDesigner"]="design/api_operator:contract_design"
    ["APIDocumenter"]="design/api_operator:documentation"
    ["ThreatModeler"]="design/security_design_operator:threat_modeling"
    ["SecurityControlDesigner"]="design/security_design_operator:control_design"
    ["DesignBestPracticeAgent"]="cross-cutting/best_practice"
    ["DesignMilestoneTrackerAgent"]="cross-cutting/milestone_tracker"
    
    # Build domain
    ["BuildPipelineAgent"]="build/build_pipeline_operator:configure"
    ["ArtifactBuilder"]="build/build_pipeline_operator:artifacts"
    ["ContainerBuilder"]="build/build_pipeline_operator:container"
    ["StaticAnalyzer"]="build/code_quality_operator:static_analysis"
    ["QualityGateAgent"]="build/code_quality_operator:quality_gate"
    ["UnitTestRunner"]="build/test_execution_operator:unit"
    ["IntegrationTestRunner"]="build/test_execution_operator:integration"
    ["CoverageAnalyzer"]="build/test_execution_operator:coverage"
    ["SASTScanner"]="build/security_scan_operator:sast"
    ["SCAScanner"]="build/security_scan_operator:sca"
    ["SecretsScanner"]="build/security_scan_operator:secrets"
    ["DASTScanner"]="build/security_scan_operator:dast"
    ["SupplyChainScanner"]="build/security_scan_operator:supply_chain"
    ["BuildBestPracticeAgent"]="cross-cutting/best_practice"
    ["BuildMilestoneTrackerAgent"]="cross-cutting/milestone_tracker"
    
    # Release domain
    ["DeploymentValidator"]="release/deploy_operator:validate"
    ["DeploymentPlanner"]="release/deploy_operator:plan"
    ["DeploymentExecutor"]="release/deploy_operator:execute"
    ["RollbackAgent"]="release/deploy_operator:rollback"
    ["ReleaseManager"]="release/release_management_operator:plan"
    ["UATCoordinator"]="release/release_management_operator:uat"
    ["GoNoGoAgent"]="cross-cutting/go_nogo"
    ["ReleaseDocumenter"]="release/release_management_operator:document"
    ["ReleaseCommunicator"]="release/release_management_operator:communicate"
    
    # Operate domain
    ["MetricsCollector"]="operate/monitor_operator:metrics"
    ["LogAggregator"]="operate/monitor_operator:logs"
    ["TraceCollector"]="operate/monitor_operator:traces"
    ["AlertManager"]="operate/monitor_operator:alert"
    ["SLOTracker"]="operate/monitor_operator:slo"
    ["IncidentDetector"]="operate/incident_operator:detect"
    ["IncidentResponder"]="operate/incident_operator:respond"
    ["IncidentResolver"]="operate/incident_operator:resolve"
    ["PostmortemAgent"]="operate/incident_operator:postmortem"
    ["DRPlanner"]="operate/incident_operator:dr"
)

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  v2 to v3 Workflow Migration Script${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

print_usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --dry-run     Show what would be migrated without making changes"
    echo "  --no-backup   Skip backup creation"
    echo "  --input-dir   Source directory with v2 configs"
    echo "  --output-dir  Target directory for v3 configs"
    echo "  --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --dry-run"
    echo "  $0 --input-dir /path/to/specs2 --output-dir /path/to/v3"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if yq is installed (for YAML processing)
    if ! command -v yq &> /dev/null; then
        log_warning "yq not found. Using sed for basic transformations."
        log_warning "For better results, install yq: brew install yq"
        USE_YQ=false
    else
        USE_YQ=true
        log_success "yq found"
    fi
    
    # Check input directory exists
    if [[ ! -d "$INPUT_DIR" ]]; then
        log_error "Input directory not found: $INPUT_DIR"
        exit 1
    fi
    log_success "Input directory found: $INPUT_DIR"
    
    # Check operator index exists
    if [[ ! -f "$OPERATOR_INDEX" ]]; then
        log_warning "Operator index not found: $OPERATOR_INDEX"
    else
        log_success "Operator index found"
    fi
}

create_backup() {
    if [[ "$BACKUP" == "true" ]]; then
        BACKUP_DIR="${INPUT_DIR}.backup.$(date +%Y%m%d_%H%M%S)"
        
        if [[ "$DRY_RUN" == "true" ]]; then
            log_info "[DRY-RUN] Would create backup at: $BACKUP_DIR"
        else
            log_info "Creating backup at: $BACKUP_DIR"
            cp -r "$INPUT_DIR" "$BACKUP_DIR"
            log_success "Backup created"
        fi
    fi
}

analyze_v2_file() {
    local file="$1"
    log_info "Analyzing: $(basename "$file")"
    
    # Count agents in the file
    local agent_count=$(grep -c "agent:" "$file" 2>/dev/null || echo "0")
    log_info "  Found $agent_count agent definitions"
    
    # Extract unique agent names
    local agents=$(grep "agent:" "$file" | sed 's/.*agent: *//' | sort -u)
    
    for agent in $agents; do
        if [[ -n "${AGENT_TO_OPERATOR[$agent]:-}" ]]; then
            echo "  ✓ $agent -> ${AGENT_TO_OPERATOR[$agent]}"
        else
            echo "  ✗ $agent (no mapping found)"
        fi
    done
}

generate_migration_report() {
    log_info "Generating migration report..."
    
    local report_file="${OUTPUT_DIR}/MIGRATION_REPORT.md"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would create report at: $report_file"
        return
    fi
    
    mkdir -p "$OUTPUT_DIR"
    
    cat > "$report_file" << 'EOF'
# v2 to v3 Migration Report

## Migration Summary

This report documents the migration from specs2 (v2) inline agent definitions
to workflows/v3 operator-based references.

## Key Changes

### Agent → Operator Mappings

| Original Agent | New Operator | Mode |
|---------------|--------------|------|
EOF

    # Add mappings to report
    for agent in "${!AGENT_TO_OPERATOR[@]}"; do
        local mapping="${AGENT_TO_OPERATOR[$agent]}"
        local operator=$(echo "$mapping" | cut -d: -f1)
        local mode=$(echo "$mapping" | cut -d: -f2)
        echo "| $agent | $operator | ${mode:-default} |" >> "$report_file"
    done | sort >> "$report_file"
    
    cat >> "$report_file" << 'EOF'

## Migration Checklist

- [ ] Review operator mappings for correctness
- [ ] Verify custom agent configurations are preserved
- [ ] Test workflow execution with new definitions
- [ ] Update any external references to agent names
- [ ] Validate cross-cutting operator parameters

## Files Migrated

EOF

    # List migrated files
    find "$INPUT_DIR/stages" -name "*.yaml" 2>/dev/null | while read -r file; do
        echo "- $(basename "$file")" >> "$report_file"
    done
    
    log_success "Report created: $report_file"
}

print_summary() {
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Migration Summary${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    
    local v2_files=$(find "$INPUT_DIR/stages" -name "*.yaml" 2>/dev/null | wc -l | tr -d ' ')
    local v3_files=$(find "$OUTPUT_DIR/stages" -name "*.yaml" 2>/dev/null | wc -l | tr -d ' ')
    
    echo "v2 stage files:     $v2_files"
    echo "v3 stage files:     $v3_files"
    echo ""
    echo "Agent mappings:     ${#AGENT_TO_OPERATOR[@]}"
    echo ""
    
    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}This was a dry run. No changes were made.${NC}"
        echo "Run without --dry-run to perform the actual migration."
    else
        echo -e "${GREEN}Migration complete!${NC}"
        echo ""
        echo "Next steps:"
        echo "  1. Review the migration report: ${OUTPUT_DIR}/MIGRATION_REPORT.md"
        echo "  2. Test workflow execution with: ./gradlew test"
        echo "  3. Validate operator references in stage files"
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --no-backup)
            BACKUP=false
            shift
            ;;
        --input-dir)
            INPUT_DIR="$2"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --help)
            print_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Main execution
print_header

if [[ "$DRY_RUN" == "true" ]]; then
    log_warning "DRY RUN MODE - No changes will be made"
    echo ""
fi

check_prerequisites
create_backup

# Analyze v2 files
log_info "Analyzing v2 stage definitions..."
echo ""

for file in "$INPUT_DIR"/stages/*.yaml; do
    if [[ -f "$file" ]]; then
        analyze_v2_file "$file"
        echo ""
    fi
done

# Generate report
generate_migration_report

# Print summary
print_summary
