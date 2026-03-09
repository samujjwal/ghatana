#!/usr/bin/env bash
#
# Guardian Linux Universal Installer
# Works on Debian/Ubuntu, Red Hat/CentOS/Fedora, and other Linux distributions
#
# Usage: sudo bash install.sh [--uninstall]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTION="${1:-install}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}ℹ${NC} $*"; }
log_success() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; }

# Check if running as root
if [[ $EUID -ne 0 ]]; then
    log_error "This script must be run as root"
    echo "Please run: sudo bash $0"
    exit 1
fi

# Detect Linux distribution
detect_distro() {
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        DISTRO_ID="$ID"
    elif [[ -f /etc/lsb-release ]]; then
        . /etc/lsb-release
        DISTRO_ID="${DISTRIB_ID,,}"
    else
        DISTRO_ID="unknown"
    fi
}

# ============================================================================
# Install Functions
# ============================================================================
install_guardian() {
    log_info "Installing Guardian for Linux..."

    local install_dir="/opt/guardian"
    local bin_dir="/usr/local/bin"
    local config_dir="/etc/guardian"
    local systemd_dir="/etc/systemd/system"

    # Create directories
    mkdir -p "$install_dir"
    mkdir -p "$config_dir"

    # Copy application files
    log_info "Copying application files..."
    if [[ -d "$SCRIPT_DIR/../../../apps/parent-dashboard/dist" ]]; then
        cp -r "$SCRIPT_DIR/../../../apps/parent-dashboard/dist"/* "$install_dir/" 2>/dev/null || true
    fi
    if [[ -d "$SCRIPT_DIR/../../../apps/browser-extension/dist/chrome" ]]; then
        cp -r "$SCRIPT_DIR/../../../apps/browser-extension/dist/chrome" "$install_dir/browser-extension" 2>/dev/null || true
    fi

    # Create launcher script
    cat > "$bin_dir/guardian" << 'EOF'
#!/usr/bin/env bash
# Guardian launcher
INSTALL_DIR="/opt/guardian"
exec xdg-open "file://$INSTALL_DIR/index.html" 2>/dev/null || \
    exec sensible-browser "file://$INSTALL_DIR/index.html" 2>/dev/null || \
    echo "Error: No web browser found"
EOF
    chmod +x "$bin_dir/guardian"

    # Create systemd service for background monitoring
    cat > "$systemd_dir/guardian.service" << 'EOF'
[Unit]
Description=Guardian Family Safety Service
After=network.target

[Service]
Type=simple
User=root
ExecStart=/opt/guardian/guardian-service
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=multi-user.target
EOF

    # Create desktop entry
    cat > "/usr/share/applications/guardian.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=Guardian
Comment=Family Safety Dashboard
Exec=guardian
Icon=guardian
Categories=Network;Utility;
Terminal=false
EOF

    # Create configuration file
    cat > "$config_dir/guardian.conf" << 'EOF'
# Guardian Configuration
# Edit this file to customize Guardian behavior

# Enable auto-start on boot
AUTO_START=true

# Backend API URL
API_URL="http://localhost:3000/api"

# WebSocket URL for real-time updates
WS_URL="ws://localhost:3000/ws"

# Log level (debug, info, warn, error)
LOG_LEVEL="info"

# Log file location
LOG_FILE="/var/log/guardian.log"
EOF

    # Create log file
    touch "/var/log/guardian.log"
    chmod 644 "/var/log/guardian.log"

    # Enable systemd service
    systemctl daemon-reload
    systemctl enable guardian.service 2>/dev/null || log_warn "Failed to enable Guardian service"

    log_success "Installation complete!"
    echo ""
    echo "Guardian has been installed to: $install_dir"
    echo "Configuration: $config_dir/guardian.conf"
    echo "Logs: /var/log/guardian.log"
    echo ""
    echo "Next steps:"
    echo "  1. Start Guardian: sudo systemctl start guardian"
    echo "  2. View status: sudo systemctl status guardian"
    echo "  3. View logs: tail -f /var/log/guardian.log"
    echo "  4. Open dashboard: guardian (or xdg-open file:///opt/guardian/index.html)"
    echo ""
}

# ============================================================================
# Uninstall Function
# ============================================================================
uninstall_guardian() {
    log_info "Uninstalling Guardian..."

    local install_dir="/opt/guardian"
    local bin_dir="/usr/local/bin"
    local config_dir="/etc/guardian"
    local systemd_dir="/etc/systemd/system"

    # Stop service
    log_info "Stopping Guardian service..."
    systemctl stop guardian.service 2>/dev/null || true
    systemctl disable guardian.service 2>/dev/null || true

    # Remove files
    log_info "Removing files..."
    rm -f "$bin_dir/guardian"
    rm -f "$systemd_dir/guardian.service"
    rm -f "/usr/share/applications/guardian.desktop"
    rm -rf "$install_dir"

    log_warn "Configuration and logs preserved in $config_dir and /var/log/guardian.log"
    echo "To remove completely, run: sudo rm -rf $config_dir /var/log/guardian.log"

    log_success "Uninstall complete!"
}

# ============================================================================
# Main
# ============================================================================
main() {
    detect_distro

    log_info "Detected distribution: $DISTRO_ID"

    case "$ACTION" in
        install)
            install_guardian
            ;;
        uninstall)
            read -p "Are you sure you want to uninstall Guardian? (y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                uninstall_guardian
            else
                log_warn "Uninstall cancelled"
            fi
            ;;
        *)
            log_error "Unknown action: $ACTION"
            echo "Usage: sudo bash $0 [install|uninstall]"
            exit 1
            ;;
    esac
}

main
