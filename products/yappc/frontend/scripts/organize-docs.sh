#!/bin/bash

# Script to move and organize README files from apps/libs/packages to docs folder
# Preserves original README.md files in their locations (as requested)

set -e

BASE_DIR="/Users/samujjwal/Development/yappc/frontend"
DOCS_DIR="$BASE_DIR/docs"

# Create category directories
mkdir -p "$DOCS_DIR/components"
mkdir -p "$DOCS_DIR/canvas"
mkdir -p "$DOCS_DIR/libraries"
mkdir -p "$DOCS_DIR/apps"
mkdir -p "$DOCS_DIR/testing"
mkdir -p "$DOCS_DIR/design-system"
mkdir -p "$DOCS_DIR/ai-ml"

echo "📚 Starting documentation organization..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Function to copy and rename file
copy_doc() {
    local src="$1"
    local dest="$2"
    local name="$3"
    
    if [ -f "$src" ]; then
        cp "$src" "$dest/$name"
        echo "✓ Copied: $name → $dest/"
    fi
}

# ========================================
# APPS DOCUMENTATION
# ========================================
echo ""
echo "📱 Processing Apps Documentation..."

copy_doc "$BASE_DIR/apps/web/src/test-utils/README.md" \
         "$DOCS_DIR/apps" \
         "web-test-utils.md"

copy_doc "$BASE_DIR/apps/web/src/components/canvas/devsecops/README.md" \
         "$DOCS_DIR/apps" \
         "web-canvas-devsecops.md"

# ========================================
# CANVAS LIBRARY DOCUMENTATION
# ========================================
echo ""
echo "🎨 Processing Canvas Documentation..."

copy_doc "$BASE_DIR/libs/canvas/examples/README.md" \
         "$DOCS_DIR/canvas" \
         "examples.md"

copy_doc "$BASE_DIR/libs/canvas/src/renderer/README.md" \
         "$DOCS_DIR/canvas" \
         "renderer.md"

copy_doc "$BASE_DIR/libs/canvas/src/viewport/README.md" \
         "$DOCS_DIR/canvas" \
         "viewport.md"

copy_doc "$BASE_DIR/libs/canvas/src/layers/README.md" \
         "$DOCS_DIR/canvas" \
         "layers.md"

copy_doc "$BASE_DIR/libs/canvas/src/security/README.md" \
         "$DOCS_DIR/canvas" \
         "security.md"

copy_doc "$BASE_DIR/libs/canvas/src/plugins/README.md" \
         "$DOCS_DIR/canvas" \
         "plugins.md"

copy_doc "$BASE_DIR/libs/canvas/src/integration/README.md" \
         "$DOCS_DIR/canvas" \
         "integration.md"

copy_doc "$BASE_DIR/libs/canvas/src/layout/README.md" \
         "$DOCS_DIR/canvas" \
         "layout.md"

copy_doc "$BASE_DIR/libs/canvas/src/interop/README.md" \
         "$DOCS_DIR/canvas" \
         "interop.md"

copy_doc "$BASE_DIR/libs/canvas/src/navigation/README.md" \
         "$DOCS_DIR/canvas" \
         "navigation.md"

copy_doc "$BASE_DIR/libs/canvas/src/state/README.md" \
         "$DOCS_DIR/canvas" \
         "state-management.md"

copy_doc "$BASE_DIR/libs/canvas/src/compliance/README.md" \
         "$DOCS_DIR/canvas" \
         "compliance.md"

copy_doc "$BASE_DIR/libs/canvas/src/testing/README.md" \
         "$DOCS_DIR/canvas" \
         "testing.md"

copy_doc "$BASE_DIR/libs/canvas/src/components/README.md" \
         "$DOCS_DIR/canvas" \
         "components.md"

copy_doc "$BASE_DIR/libs/canvas/src/elements/README.md" \
         "$DOCS_DIR/canvas" \
         "elements.md"

copy_doc "$BASE_DIR/libs/canvas/src/theming/README.md" \
         "$DOCS_DIR/canvas" \
         "theming.md"

copy_doc "$BASE_DIR/libs/canvas/src/devsecops/threat-modeling/README.md" \
         "$DOCS_DIR/canvas" \
         "devsecops-threat-modeling.md"

copy_doc "$BASE_DIR/libs/canvas/src/devsecops/README.md" \
         "$DOCS_DIR/canvas" \
         "devsecops.md"

copy_doc "$BASE_DIR/libs/canvas/src/devtools/README.md" \
         "$DOCS_DIR/canvas" \
         "devtools.md"

copy_doc "$BASE_DIR/libs/canvas/src/hooks/__tests__/helpers/README.md" \
         "$DOCS_DIR/canvas" \
         "hooks-test-helpers.md"

copy_doc "$BASE_DIR/libs/canvas/src/workflow/README.md" \
         "$DOCS_DIR/canvas" \
         "workflow.md"

copy_doc "$BASE_DIR/libs/canvas/src/persistence/README.md" \
         "$DOCS_DIR/canvas" \
         "persistence.md"

copy_doc "$BASE_DIR/libs/canvas/src/history/README.md" \
         "$DOCS_DIR/canvas" \
         "history.md"

copy_doc "$BASE_DIR/libs/canvas/src/api/README.md" \
         "$DOCS_DIR/canvas" \
         "api.md"

copy_doc "$BASE_DIR/libs/canvas/src/templates/README.md" \
         "$DOCS_DIR/canvas" \
         "templates.md"

copy_doc "$BASE_DIR/libs/canvas/src/rendering/README.md" \
         "$DOCS_DIR/canvas" \
         "rendering.md"

copy_doc "$BASE_DIR/libs/canvas/src/monitoring/README.md" \
         "$DOCS_DIR/canvas" \
         "monitoring.md"

copy_doc "$BASE_DIR/libs/canvas/src/accessibility/README.md" \
         "$DOCS_DIR/canvas" \
         "accessibility.md"

copy_doc "$BASE_DIR/libs/canvas/src/presentation/README.md" \
         "$DOCS_DIR/canvas" \
         "presentation.md"

# ========================================
# UI COMPONENTS DOCUMENTATION
# ========================================
echo ""
echo "🧩 Processing UI Components Documentation..."

copy_doc "$BASE_DIR/libs/ui/src/interactions/README.md" \
         "$DOCS_DIR/components" \
         "interactions.md"

copy_doc "$BASE_DIR/libs/ui/src/components/Input/internals/README.md" \
         "$DOCS_DIR/components" \
         "input-internals.md"

copy_doc "$BASE_DIR/libs/ui/src/components/README.md" \
         "$DOCS_DIR/components" \
         "ui-components-overview.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/SearchBar/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-searchbar.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/KPICard/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-kpicard.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-components.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/KanbanBoard/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-kanbanboard.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/ViewModeSwitcher/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-viewmodeswitcher.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/TopNav/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-topnav.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/FilterPanel/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-filterpanel.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/Timeline/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-timeline.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/Breadcrumbs/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-breadcrumbs.md"

copy_doc "$BASE_DIR/libs/ui/src/components/DevSecOps/DataTable/README.md" \
         "$DOCS_DIR/components" \
         "devsecops-datatable.md"

copy_doc "$BASE_DIR/libs/ui/src/components/AI/tabs/README.md" \
         "$DOCS_DIR/components" \
         "ai-tabs.md"

copy_doc "$BASE_DIR/libs/ui/src/components/KeyboardShortcutHelp/README.md" \
         "$DOCS_DIR/components" \
         "keyboard-shortcut-help.md"

copy_doc "$BASE_DIR/libs/ui/src/theme/README.md" \
         "$DOCS_DIR/components" \
         "theme.md"

# ========================================
# DESIGN SYSTEM DOCUMENTATION
# ========================================
echo ""
echo "🎨 Processing Design System Documentation..."

copy_doc "$BASE_DIR/libs/design-system-cli/README.md" \
         "$DOCS_DIR/design-system" \
         "cli.md"

copy_doc "$BASE_DIR/libs/design-system-core/README.md" \
         "$DOCS_DIR/design-system" \
         "core.md"

copy_doc "$BASE_DIR/libs/tokens/README.md" \
         "$DOCS_DIR/design-system" \
         "tokens.md"

copy_doc "$BASE_DIR/libs/token-analytics/README.md" \
         "$DOCS_DIR/design-system" \
         "token-analytics.md"

copy_doc "$BASE_DIR/libs/designer/README.md" \
         "$DOCS_DIR/design-system" \
         "designer.md"

copy_doc "$BASE_DIR/libs/designer/src/components/DesignerPage/README.md" \
         "$DOCS_DIR/design-system" \
         "designer-page.md"

# ========================================
# AI/ML DOCUMENTATION
# ========================================
echo ""
echo "🤖 Processing AI/ML Documentation..."

copy_doc "$BASE_DIR/libs/ai/README.md" \
         "$DOCS_DIR/ai-ml" \
         "ai.md"

copy_doc "$BASE_DIR/libs/ml/README.md" \
         "$DOCS_DIR/ai-ml" \
         "ml.md"

copy_doc "$BASE_DIR/libs/agents/README.md" \
         "$DOCS_DIR/ai-ml" \
         "agents.md"

# ========================================
# TESTING DOCUMENTATION
# ========================================
echo ""
echo "🧪 Processing Testing Documentation..."

copy_doc "$BASE_DIR/libs/test/README.md" \
         "$DOCS_DIR/testing" \
         "test-library.md"

copy_doc "$BASE_DIR/libs/test-helpers/README.md" \
         "$DOCS_DIR/testing" \
         "test-helpers.md"

copy_doc "$BASE_DIR/libs/mocks/README.md" \
         "$DOCS_DIR/testing" \
         "mocks.md"

# ========================================
# OTHER LIBRARIES DOCUMENTATION
# ========================================
echo ""
echo "📦 Processing Other Libraries Documentation..."

copy_doc "$BASE_DIR/libs/experimentation/README.md" \
         "$DOCS_DIR/libraries" \
         "experimentation.md"

copy_doc "$BASE_DIR/libs/performance-monitor/README.md" \
         "$DOCS_DIR/libraries" \
         "performance-monitor.md"

copy_doc "$BASE_DIR/libs/store/README.md" \
         "$DOCS_DIR/libraries" \
         "store.md"

# ========================================
# CREATE INDEX FILE
# ========================================
echo ""
echo "📋 Creating documentation index..."

cat > "$DOCS_DIR/components-index.md" << 'EOF'
# Components Documentation Index

This directory contains documentation for all UI components and related modules.

## Categories

### 📱 Apps Documentation
- [Web Test Utils](./apps/web-test-utils.md) - Testing utilities for web app
- [Web Canvas DevSecOps](./apps/web-canvas-devsecops.md) - DevSecOps canvas components

### 🎨 Canvas Library
Core canvas functionality and features:

**Core Systems:**
- [API](./canvas/api.md) - Canvas API documentation
- [Renderer](./canvas/renderer.md) - Canvas rendering engine
- [Viewport](./canvas/viewport.md) - Viewport management
- [State Management](./canvas/state-management.md) - Canvas state handling
- [Elements](./canvas/elements.md) - Canvas elements
- [Components](./canvas/components.md) - Canvas components

**Layout & Display:**
- [Layout](./canvas/layout.md) - Layout system
- [Layers](./canvas/layers.md) - Layer management
- [Theming](./canvas/theming.md) - Theme system
- [Rendering](./canvas/rendering.md) - Rendering pipeline
- [Presentation](./canvas/presentation.md) - Presentation mode

**Features & Functionality:**
- [Workflow](./canvas/workflow.md) - Workflow features
- [Navigation](./canvas/navigation.md) - Canvas navigation
- [History](./canvas/history.md) - Undo/redo history
- [Templates](./canvas/templates.md) - Template system
- [Plugins](./canvas/plugins.md) - Plugin architecture

**Integration & Interop:**
- [Integration](./canvas/integration.md) - Integration capabilities
- [Interop](./canvas/interop.md) - Interoperability features
- [Persistence](./canvas/persistence.md) - Data persistence

**Security & Compliance:**
- [Security](./canvas/security.md) - Security features
- [Compliance](./canvas/compliance.md) - Compliance features
- [DevSecOps](./canvas/devsecops.md) - DevSecOps features
- [Threat Modeling](./canvas/devsecops-threat-modeling.md) - Threat modeling

**Development Tools:**
- [DevTools](./canvas/devtools.md) - Development tools
- [Monitoring](./canvas/monitoring.md) - Performance monitoring
- [Testing](./canvas/testing.md) - Testing utilities
- [Accessibility](./canvas/accessibility.md) - Accessibility features
- [Hooks Test Helpers](./canvas/hooks-test-helpers.md) - Testing helpers for hooks
- [Examples](./canvas/examples.md) - Canvas examples

### 🧩 UI Components
Reusable UI components:

**General:**
- [UI Components Overview](./components/ui-components-overview.md) - Overview of all UI components
- [Interactions](./components/interactions.md) - Interaction patterns
- [Input Internals](./components/input-internals.md) - Input component internals
- [Theme](./components/theme.md) - Theming system
- [Keyboard Shortcut Help](./components/keyboard-shortcut-help.md) - Keyboard shortcuts

**AI Components:**
- [AI Tabs](./components/ai-tabs.md) - AI-related tab components

**DevSecOps Components:**
- [DevSecOps Components](./components/devsecops-components.md) - Overview
- [Search Bar](./components/devsecops-searchbar.md)
- [KPI Card](./components/devsecops-kpicard.md)
- [Kanban Board](./components/devsecops-kanbanboard.md)
- [View Mode Switcher](./components/devsecops-viewmodeswitcher.md)
- [Top Navigation](./components/devsecops-topnav.md)
- [Filter Panel](./components/devsecops-filterpanel.md)
- [Timeline](./components/devsecops-timeline.md)
- [Breadcrumbs](./components/devsecops-breadcrumbs.md)
- [Data Table](./components/devsecops-datatable.md)

### 🎨 Design System
Design system and tokens:

- [Design System Core](./design-system/core.md) - Core design system
- [CLI](./design-system/cli.md) - Design system CLI tools
- [Tokens](./design-system/tokens.md) - Design tokens
- [Token Analytics](./design-system/token-analytics.md) - Token usage analytics
- [Designer](./design-system/designer.md) - Visual designer tool
- [Designer Page](./design-system/designer-page.md) - Designer page component

### 🤖 AI/ML
AI and machine learning features:

- [AI](./ai-ml/ai.md) - AI features and integrations
- [ML](./ai-ml/ml.md) - Machine learning capabilities
- [Agents](./ai-ml/agents.md) - AI agent system

### 🧪 Testing
Testing utilities and helpers:

- [Test Library](./testing/test-library.md) - Core testing library
- [Test Helpers](./testing/test-helpers.md) - Testing helper utilities
- [Mocks](./testing/mocks.md) - Mock data and utilities

### 📦 Other Libraries
Additional library documentation:

- [Experimentation](./libraries/experimentation.md) - A/B testing and experimentation
- [Performance Monitor](./libraries/performance-monitor.md) - Performance monitoring
- [Store](./libraries/store.md) - State management store

## Navigation

- [Main Documentation](./README.md)
- [API Reference](./api-reference.md)
- [Project Status](./00_START_HERE_FINAL.md)
EOF

echo "✓ Created: components-index.md"

# ========================================
# SUMMARY
# ========================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Documentation organization complete!"
echo ""
echo "📊 Summary:"
echo "  • Apps documentation: docs/apps/"
echo "  • Canvas documentation: docs/canvas/"
echo "  • UI Components: docs/components/"
echo "  • Design System: docs/design-system/"
echo "  • AI/ML: docs/ai-ml/"
echo "  • Testing: docs/testing/"
echo "  • Libraries: docs/libraries/"
echo ""
echo "📄 Index file created: docs/components-index.md"
echo ""
echo "Note: Original README.md files remain in their original locations."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
