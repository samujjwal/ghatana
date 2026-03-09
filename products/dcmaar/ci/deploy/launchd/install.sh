#!/bin/bash
#
# dcmaar macOS Installer
# Production-grade installation script for macOS with LaunchDaemon integration

set -euo pipefail

# Configuration
readonly SCRIPT_NAME="$(basename "$0")"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DCMAAR_VERSION="${DCMAAR_VERSION:-1.0.0}"
readonly INSTALL_DIR="/usr/local"
readonly CONFIG_DIR="/usr/local/etc/dcmaar"
readonly DATA_DIR="/usr/local/var/lib/dcmaar"
readonly LOG_DIR="/usr/local/var/log/dcmaar"
readonly DAEMON_LABEL="com.dcmaar.agent"
readonly DAEMON_USER="_dcmaar"
readonly DAEMON_GROUP="_dcmaar"
readonly DAEMON_PLIST="/Library/LaunchDaemons/${DAEMON_LABEL}.plist"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

# Check if running as root
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "This script must be run as root (use sudo)"
        exit 1
    fi
}

# Check macOS version
check_macos_version() {
    local version
    version=$(sw_vers -productVersion)
    local major_version
    major_version=$(echo "$version" | cut -d. -f1)
    
    if [[ $major_version -lt 10 ]]; then
        log_error "macOS 10.15 or later is required (found: $version)"
        exit 1
    fi
    
    log_info "macOS version: $version ✓"
}

# Check system requirements
check_requirements() {
    log_info "Checking system requirements..."
    
    check_macos_version
    
    # Check available space (minimum 100MB)
    local available_space
    available_space=$(df -k /usr/local 2>/dev/null | awk 'NR==2 {print $4}' || echo 0)
    if [[ $available_space -lt 102400 ]]; then
        log_warning "Low disk space available. At least 100MB recommended."
    fi
    
    # Check if Homebrew is available (optional)
    if command -v brew &> /dev/null; then
        log_info "Homebrew detected ✓"
    else
        log_info "Homebrew not found (optional)"
    fi
    
    log_success "System requirements check passed"
}

# Create system user and group
create_user() {
    log_info "Creating system user: ${DAEMON_USER}"
    
    # Check if user already exists
    if dscl . -read "/Users/${DAEMON_USER}" &> /dev/null; then
        log_info "User ${DAEMON_USER} already exists"
    else
        # Find next available UID in system range
        local next_uid
        next_uid=$(dscl . -list /Users UniqueID | awk '$2 >= 200 && $2 < 400 {print $2}' | sort -n | tail -1)
        next_uid=$((next_uid + 1))
        
        # Create group first
        if ! dscl . -read "/Groups/${DAEMON_GROUP}" &> /dev/null; then
            dscl . -create "/Groups/${DAEMON_GROUP}"
            dscl . -create "/Groups/${DAEMON_GROUP}" PrimaryGroupID "$next_uid"
            dscl . -create "/Groups/${DAEMON_GROUP}" RealName "dcmaar Agent Service Group"
            log_info "Created group: ${DAEMON_GROUP}"
        fi
        
        # Create user
        dscl . -create "/Users/${DAEMON_USER}"
        dscl . -create "/Users/${DAEMON_USER}" UserShell /usr/bin/false
        dscl . -create "/Users/${DAEMON_USER}" RealName "dcmaar Agent Service User"
        dscl . -create "/Users/${DAEMON_USER}" UniqueID "$next_uid"
        dscl . -create "/Users/${DAEMON_USER}" PrimaryGroupID "$next_uid"
        dscl . -create "/Users/${DAEMON_USER}" NFSHomeDirectory "${DATA_DIR}"
        
        log_success "Created user: ${DAEMON_USER}"
    fi
}

# Create directory structure
create_directories() {
    log_info "Creating directory structure..."
    
    local dirs=("${INSTALL_DIR}/bin" "${CONFIG_DIR}" "${DATA_DIR}" "${LOG_DIR}")
    
    for dir in "${dirs[@]}"; do
        mkdir -p "${dir}"
        log_info "Created directory: ${dir}"
    done
    
    # Set permissions
    chown -R "${DAEMON_USER}:${DAEMON_GROUP}" "${DATA_DIR}" "${LOG_DIR}"
    chown -R root:wheel "${INSTALL_DIR}/bin" "${CONFIG_DIR}"
    chmod 755 "${INSTALL_DIR}/bin" "${CONFIG_DIR}"
    chmod 750 "${DATA_DIR}" "${LOG_DIR}"
    
    log_success "Directory structure created"
}

# Install binaries
install_binaries() {
    log_info "Installing dcmaar agent binary..."
    
    # Copy agent binary
    if [[ -f "${SCRIPT_DIR}/dcmaar-agent" ]]; then
        cp "${SCRIPT_DIR}/dcmaar-agent" "${INSTALL_DIR}/bin/"
        chmod +x "${INSTALL_DIR}/bin/dcmaar-agent"
        
        # Code sign if developer tools are available
        if command -v codesign &> /dev/null; then
            codesign --force --sign - "${INSTALL_DIR}/bin/dcmaar-agent" 2>/dev/null || true
        fi
        
        log_success "Agent binary installed"
    else
        log_error "Agent binary not found: ${SCRIPT_DIR}/dcmaar-agent"
        exit 1
    fi
}

# Install configuration files
install_config() {
    log_info "Installing configuration files..."
    
    # Create default agent configuration
    cat > "${CONFIG_DIR}/agent.toml" << 'EOF'
# dcmaar Agent Configuration for macOS
# Generated by installer

[agent]
name = "dcmaar-agent"
version = "1.0.0"
log_level = "info"

[queue]
max_size = 10000
watermark_high = 8000
watermark_low = 2000
encryption = true

[admin]
bind_address = "127.0.0.1:8787"
enable_metrics = true
enable_status = true

[exporters]
# Add your exporters configuration here

[plugins]
# Add your plugins configuration here

[policies]
# Add your policies configuration here
EOF

    # Set config file permissions
    chown root:"${DAEMON_GROUP}" "${CONFIG_DIR}/agent.toml"
    chmod 640 "${CONFIG_DIR}/agent.toml"
    
    log_success "Configuration files installed"
}

# Install LaunchDaemon
install_daemon() {
    log_info "Installing LaunchDaemon..."
    
    # Copy plist file
    if [[ -f "${SCRIPT_DIR}/${DAEMON_LABEL}.plist" ]]; then
        cp "${SCRIPT_DIR}/${DAEMON_LABEL}.plist" "${DAEMON_PLIST}"
    else
        # Create plist file inline if not found
        cat > "${DAEMON_PLIST}" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${DAEMON_LABEL}</string>
    
    <key>Program</key>
    <string>${INSTALL_DIR}/bin/dcmaar-agent</string>
    
    <key>ProgramArguments</key>
    <array>
        <string>${INSTALL_DIR}/bin/dcmaar-agent</string>
        <string>--config</string>
        <string>${CONFIG_DIR}/agent.toml</string>
    </array>
    
    <key>UserName</key>
    <string>${DAEMON_USER}</string>
    
    <key>GroupName</key>
    <string>${DAEMON_GROUP}</string>
    
    <key>WorkingDirectory</key>
    <string>${DATA_DIR}</string>
    
    <key>RunAtLoad</key>
    <true/>
    
    <key>KeepAlive</key>
    <dict>
        <key>SuccessfulExit</key>
        <false/>
        <key>Crashed</key>
        <true/>
    </dict>
    
    <key>StandardOutPath</key>
    <string>${LOG_DIR}/agent.log</string>
    
    <key>StandardErrorPath</key>
    <string>${LOG_DIR}/agent-error.log</string>
    
    <key>EnvironmentVariables</key>
    <dict>
        <key>RUST_LOG</key>
        <string>info</string>
        <key>DCMAAR_CONFIG_DIR</key>
        <string>${CONFIG_DIR}</string>
        <key>DCMAAR_DATA_DIR</key>
        <string>${DATA_DIR}</string>
        <key>DCMAAR_LOG_DIR</key>
        <string>${LOG_DIR}</string>
    </dict>
    
    <key>ProcessType</key>
    <string>Background</string>
    
    <key>ThrottleInterval</key>
    <integer>10</integer>
    
    <key>ExitTimeOut</key>
    <integer>30</integer>
</dict>
</plist>
EOF
    fi
    
    # Set permissions
    chown root:wheel "${DAEMON_PLIST}"
    chmod 644 "${DAEMON_PLIST}"
    
    # Load the daemon
    launchctl load "${DAEMON_PLIST}"
    
    log_success "LaunchDaemon installed and loaded"
}

# Install desktop application (if available)
install_desktop() {
    log_info "Checking for desktop application..."
    
    if [[ -f "${SCRIPT_DIR}/dcmaar-desktop.app.tar.gz" ]]; then
        log_info "Installing desktop application..."
        
        # Extract to Applications
        tar -xzf "${SCRIPT_DIR}/dcmaar-desktop.app.tar.gz" -C /Applications/
        
        # Code sign if available
        if command -v codesign &> /dev/null; then
            codesign --force --deep --sign - "/Applications/dcmaar Desktop.app" 2>/dev/null || true
        fi
        
        # Create CLI symlink
        ln -sf "/Applications/dcmaar Desktop.app/Contents/MacOS/dcmaar-desktop" "/usr/local/bin/dcmaar-desktop"
        
        log_success "Desktop application installed"
    else
        log_info "Desktop application not found, skipping"
    fi
}

# Start daemon
start_daemon() {
    log_info "Starting dcmaar agent daemon..."
    
    # Start the daemon
    launchctl start "${DAEMON_LABEL}"
    
    # Wait for daemon to start
    sleep 3
    
    # Check if daemon is running
    if launchctl list | grep -q "${DAEMON_LABEL}"; then
        log_success "Daemon started successfully"
        
        # Show daemon status
        launchctl list "${DAEMON_LABEL}"
    else
        log_error "Failed to start daemon"
        log_info "Check logs at: ${LOG_DIR}/agent-error.log"
        exit 1
    fi
}

# Post-installation setup
post_install() {
    log_info "Running post-installation setup..."
    
    # Update launch database
    /System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f /Applications/dcmaar\ Desktop.app 2>/dev/null || true
    
    log_success "Post-installation setup complete"
}

# Print installation summary
print_summary() {
    log_success "dcmaar installation completed successfully!"
    echo
    echo "Installation Summary:"
    echo "  • Agent binary: ${INSTALL_DIR}/bin/dcmaar-agent"
    echo "  • Configuration: ${CONFIG_DIR}/agent.toml"
    echo "  • Data directory: ${DATA_DIR}"
    echo "  • Log directory: ${LOG_DIR}"
    echo "  • LaunchDaemon: ${DAEMON_LABEL}"
    echo
    echo "Daemon Management:"
    echo "  • Start:   sudo launchctl start ${DAEMON_LABEL}"
    echo "  • Stop:    sudo launchctl stop ${DAEMON_LABEL}"
    echo "  • Status:  sudo launchctl list ${DAEMON_LABEL}"
    echo "  • Logs:    tail -f ${LOG_DIR}/agent.log"
    echo
    echo "Admin Interface:"
    echo "  • Status:  curl http://localhost:8787/status"
    echo "  • Metrics: curl http://localhost:8787/metrics"
    echo
    if [[ -f "/usr/local/bin/dcmaar-desktop" ]]; then
        echo "Desktop Application:"
        echo "  • Launch: dcmaar-desktop"
        echo "  • Or find 'dcmaar Desktop' in Applications folder"
        echo
    fi
    echo "Edit configuration: sudo nano ${CONFIG_DIR}/agent.toml"
    echo "After config changes: sudo launchctl stop ${DAEMON_LABEL} && sudo launchctl start ${DAEMON_LABEL}"
}

# Uninstall function
uninstall() {
    log_info "Uninstalling dcmaar..."
    
    # Stop and unload daemon
    launchctl unload "${DAEMON_PLIST}" 2>/dev/null || true
    
    # Remove plist file
    rm -f "${DAEMON_PLIST}"
    
    # Remove files and directories
    rm -rf "${INSTALL_DIR}/bin/dcmaar-agent"
    rm -f "/usr/local/bin/dcmaar-desktop"
    rm -rf "/Applications/dcmaar Desktop.app"
    
    # Optionally remove config and data (ask user)
    read -p "Remove configuration and data directories? [y/N]: " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -rf "${CONFIG_DIR}" "${DATA_DIR}" "${LOG_DIR}"
        log_info "Configuration and data removed"
    fi
    
    # Optionally remove user
    read -p "Remove system user '${DAEMON_USER}'? [y/N]: " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        dscl . -delete "/Users/${DAEMON_USER}" 2>/dev/null || true
        dscl . -delete "/Groups/${DAEMON_GROUP}" 2>/dev/null || true
        log_info "System user removed"
    fi
    
    log_success "dcmaar uninstalled successfully"
}

# Main installation function
main() {
    case "${1:-install}" in
        install)
            log_info "Starting dcmaar installation..."
            check_root
            check_requirements
            create_user
            create_directories
            install_binaries
            install_config
            install_daemon
            install_desktop
            start_daemon
            post_install
            print_summary
            ;;
        uninstall)
            check_root
            uninstall
            ;;
        *)
            echo "Usage: $0 {install|uninstall}"
            exit 1
            ;;
    esac
}

# Run main function
main "$@"