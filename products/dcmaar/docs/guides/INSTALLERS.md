# Guardian Cross-Platform Installers

Complete installer solutions for Guardian on macOS, Windows, and Linux. Choose your platform below.

---

## Quick Start

### All Platforms (Automated)

Build installers for all platforms:

```bash
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian
./scripts/build-installers.sh --platform=all --version=1.0.0
```

Or using Make:

```bash
make installers VERSION=1.0.0
```

Installers will be created in `dist/installers/`.

---

## macOS Installation

### System Requirements

- macOS 10.13 or later
- 500 MB disk space
- Google Chrome or compatible browser

### Install via DMG

1. **Download** `dist/installers/macos/Guardian-1.0.0.dmg`
2. **Open** the DMG file
3. **Drag** Guardian.app to Applications
4. **Launch** Guardian from Applications or Spotlight

#### Build macOS Installer

```bash
./scripts/build-installers.sh --platform=macos --version=1.0.0
```

Generates:

- `Guardian-1.0.0.dmg` — Disk image for drag-and-drop install
- `Guardian.pkg` — Package installer (alternative)

#### Post-Installation

Guardian runs as a LaunchAgent (background service):

```bash
# View status
launchctl list | grep guardian

# Start manually
launchctl load ~/Library/LaunchAgents/com.ghatana.guardian.plist

# Stop
launchctl unload ~/Library/LaunchAgents/com.ghatana.guardian.plist

# View logs
tail -f /var/log/guardian.log

# Uninstall
rm -rf /Applications/Guardian.app
launchctl unload ~/Library/LaunchAgents/com.ghatana.guardian.plist
rm ~/Library/LaunchAgents/com.ghatana.guardian.plist
```

---

## Windows Installation

### System Requirements

- Windows 7 or later (Windows 10+ recommended)
- 500 MB disk space
- Administrator privileges
- Google Chrome or compatible browser

### Install via NSIS Setup

1. **Download** `dist/installers/windows/Guardian-1.0.0-Setup.exe`
2. **Run** the installer (Administrator privileges required)
3. **Follow** the installation wizard
4. **Complete** — Guardian will auto-start on next login

#### Build Windows Installer

```bash
# Requires NSIS (Nullsoft Scriptable Install System)
# Install from: https://nsis.sourceforge.io
# Or via Chocolatey: choco install nsis

./scripts/build-installers.sh --platform=windows --version=1.0.0
```

Generates:

- `Guardian-1.0.0-Setup.exe` — Full NSIS installer (recommended)
- MSI alternative available with WiX Toolset

#### Manual Install (No Setup)

```batch
cd scripts\windows
install.bat
```

#### Post-Installation

Guardian auto-starts via scheduled task:

```powershell
# View status
schtasks /query /tn "Guardian"

# Start manually
schtasks /run /tn "Guardian"

# Stop
taskkill /IM guardian.exe /F

# View logs (if app generates logs)
Get-Content "$env:APPDATA\Guardian\logs\guardian.log" -Tail 20

# Uninstall
# Option 1: Settings → Apps → Guardian → Uninstall
# Option 2: Run setup.exe again, select "Uninstall"
# Option 3: Run scripts\windows\uninstall.bat (as Administrator)

uninstall.bat
```

#### Installation Location

- **Program files:** `C:\Program Files\Ghatana\Guardian\`
- **Config/Data:** `C:\Users\<USERNAME>\AppData\Roaming\Guardian\`
- **Shortcuts:** Start Menu → Ghatana → Guardian

---

## Linux Installation

### System Requirements

- Ubuntu 18.04+, Debian 10+, CentOS 7+, Fedora 30+, or other Linux distribution
- 500 MB disk space
- Root or sudo access
- A web browser (Chrome, Firefox, etc.)

### Build Linux Installers

Choose a build method: direct script, Make targets, or specific flavor targets.

#### Method 1: Build All Linux Flavors

```bash
# Direct script
./scripts/build-installers.sh --platform=linux --flavor=all --version=1.0.0

# Using Make
make installers-linux-all VERSION=1.0.0

# Shorthand (same as -all)
make installers-linux VERSION=1.0.0
```

Generates all three formats:

- `guardian-1.0.0_amd64.deb` — Debian/Ubuntu/Linux Mint
- `guardian-1.0.0-1.x86_64.rpm` — Red Hat/CentOS/Fedora
- `Guardian-1.0.0-x86_64.AppImage` — Universal (any Linux distro)

#### Method 2: Build Specific Linux Flavor

**Debian/Ubuntu (DEB package):**

```bash
# Direct script
./scripts/build-installers.sh --platform=linux --flavor=deb --version=1.0.0

# Using Make
make installers-linux-deb VERSION=1.0.0
```

Generates: `guardian-1.0.0_amd64.deb`

**Red Hat/CentOS/Fedora (RPM package):**

```bash
# Direct script
./scripts/build-installers.sh --platform=linux --flavor=rpm --version=1.0.0

# Using Make
make installers-linux-rpm VERSION=1.0.0
```

Generates: `guardian-1.0.0-1.x86_64.rpm`

**Universal AppImage (any Linux):**

```bash
# Direct script
./scripts/build-installers.sh --platform=linux --flavor=appimage --version=1.0.0

# Using Make
make installers-linux-appimage VERSION=1.0.0
```

Generates: `Guardian-1.0.0-x86_64.AppImage`

### Quick Install (All Distributions)

```bash
# Download and run
sudo bash /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/scripts/linux/install.sh

# Verify installation
guardian --version
systemctl status guardian
```

### Debian/Ubuntu

```bash
# Install from DEB
sudo apt install ./dist/installers/linux/guardian-1.0.0_amd64.deb

# Or via APT (if added to repository)
sudo apt update
sudo apt install guardian

# Verify
dpkg -l | grep guardian
```

### Red Hat/CentOS/Fedora

```bash
# Install from RPM
sudo rpm -i dist/installers/linux/guardian-1.0.0-1.x86_64.rpm

# Or via DNF
sudo dnf install ./guardian-1.0.0-1.x86_64.rpm

# Verify
rpm -qa | grep guardian
```

### Universal (Any Linux Distribution)

```bash
# Make AppImage executable
chmod +x dist/installers/linux/Guardian-1.0.0-x86_64.AppImage

# Run directly
./Guardian-1.0.0-x86_64.AppImage

# Or install to /opt
sudo cp Guardian-1.0.0-x86_64.AppImage /opt/guardian
sudo chmod +x /opt/guardian
```

### Post-Installation (Linux)

Guardian runs as a systemd service:

```bash
# Start Guardian
sudo systemctl start guardian

# Auto-start on boot
sudo systemctl enable guardian

# View status
sudo systemctl status guardian

# View logs
sudo journalctl -u guardian -f

# Stop Guardian
sudo systemctl stop guardian

# Disable auto-start
sudo systemctl disable guardian

# Uninstall
sudo bash /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/scripts/linux/install.sh uninstall
```

#### Installation Locations (Linux)

| Component         | Location                               |
| ----------------- | -------------------------------------- |
| Executable        | `/usr/local/bin/guardian`              |
| Application files | `/opt/guardian/`                       |
| Configuration     | `/etc/guardian/guardian.conf`          |
| Logs              | `/var/log/guardian.log`                |
| Systemd service   | `/etc/systemd/system/guardian.service` |

---

## Build System Requirements

### macOS

- Xcode Command Line Tools: `xcode-select --install`
- `pkgbuild` (included with Xcode)

### Windows

- [NSIS 3.x](https://nsis.sourceforge.io) (for NSIS installers)
- [WiX Toolset](https://wixtoolset.org) (optional, for MSI)
- PowerShell 5.0+ (included on Windows 10+)

### Linux (Platform-Specific Tools)

**For Debian/Ubuntu installers (.deb):**

```bash
sudo apt update && sudo apt install dpkg-dev
```

**For Red Hat/CentOS/Fedora installers (.rpm):**

```bash
sudo yum install rpm-build
# Or on newer systems:
sudo dnf install rpm-build
```

**For universal AppImage:**

```bash
# Clone and build from source
git clone https://github.com/AppImage/AppImageKit
cd AppImageKit
mkdir build && cd build
cmake ..
make install
```

Or use a pre-built binary:

```bash
wget https://github.com/AppImage/AppImageKit/releases/download/continuous/AppImageKit-x86_64.AppImage
chmod +x AppImageKit-x86_64.AppImage
sudo mv AppImageKit-x86_64.AppImage /usr/local/bin/appimagetool
```

**To build all Linux flavors, install all tools:**

```bash
# Ubuntu/Debian
sudo apt install dpkg-dev rpm build-essential git

# CentOS/RHEL
sudo yum groupinstall "Development Tools"
sudo yum install rpm-build dpkg-dev

# Fedora
sudo dnf groupinstall "Development Tools"
sudo dnf install rpm-build dpkg-devel
```

---

## Customization

### Modify Installation Paths

Edit the installer scripts:

**macOS:**

```bash
# Edit scripts/macos/scripts/postinst
INSTALL_DIR="/Applications/Guardian.app"
LAUNCH_AGENT="$HOME/Library/LaunchAgents/com.ghatana.guardian.plist"
```

**Windows:**

```batch
REM Edit scripts/windows/install.bat
set INSTALL_DIR=%ProgramFiles%\Ghatana\Guardian
set DATA_DIR=%APPDATA%\Guardian
```

**Linux:**

```bash
# Edit scripts/linux/install.sh
local install_dir="/opt/guardian"
local config_dir="/etc/guardian"
```

### Add Custom Configuration

**macOS/Linux:**

```bash
# Copy custom config before installation
cp myconfig.conf /etc/guardian/guardian.conf
```

**Windows:**

```batch
REM Copy to AppData before first run
copy config.json "%APPDATA%\Guardian\config.json"
```

---

## Verification & Troubleshooting

### Verify Installation

```bash
# macOS
ls -la /Applications/Guardian.app
launchctl list | grep guardian

# Windows
dir "C:\Program Files\Ghatana\Guardian"
schtasks /query /tn "Guardian"

# Linux
dpkg -l guardian  # or rpm -qa guardian
systemctl status guardian
```

### Common Issues

#### macOS: "Guardian" cannot be opened because it is from an unidentified developer

**Solution:**

```bash
# Allow app to run
sudo spctl --add /Applications/Guardian.app
# Or: Right-click → Open → Open
```

#### Windows: Installer requires administrator privileges

**Solution:**

```batch
# Right-click Guardian-1.0.0-Setup.exe
# Select "Run as Administrator"
```

#### Linux: Command not found

**Solution:**

```bash
# Check if /usr/local/bin is in PATH
echo $PATH

# If missing, add to ~/.bashrc
export PATH="/usr/local/bin:$PATH"
source ~/.bashrc
```

#### Service/daemon won't start

**Solution:**

macOS:

```bash
# Check LaunchAgent is valid
plutil -lint ~/Library/LaunchAgents/com.ghatana.guardian.plist
```

Linux:

```bash
# Check service status
sudo journalctl -u guardian -n 50  # Last 50 lines
sudo systemctl status guardian --full
```

---

## Uninstallation

### macOS

```bash
rm -rf /Applications/Guardian.app
launchctl unload ~/Library/LaunchAgents/com.ghatana.guardian.plist
rm ~/Library/LaunchAgents/com.ghatana.guardian.plist
```

### Windows

- **Via Settings:** Settings → Apps → Guardian → Uninstall
- **Via Control Panel:** Control Panel → Programs → Programs and Features → Guardian → Uninstall
- **Via Batch Script:** Run `scripts/windows/uninstall.bat` as Administrator

### Linux

```bash
# DEB
sudo apt remove guardian
sudo apt purge guardian  # Also removes config

# RPM
sudo rpm -e guardian

# AppImage
sudo rm /opt/guardian

# Manual cleanup
sudo bash /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/scripts/linux/install.sh uninstall
```

---

## Advanced Topics

### Silent Installation (Windows)

```batch
Guardian-1.0.0-Setup.exe /S /D=C:\Program Files\Ghatana\Guardian
```

### Custom Installation (Linux)

```bash
# Install to custom directory
INSTALL_DIR=/home/user/guardian ./scripts/linux/install.sh

# Or edit the script before running
nano scripts/linux/install.sh
# Change install_dir="/opt/guardian" to desired location
```

### Distribution & CI/CD

To automate installer builds in CI/CD pipelines:

```bash
#!/bin/bash
# GitHub Actions / GitLab CI example

VERSION="1.0.0"
PRODUCT_ROOT="/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian"

# Build installers
cd "$PRODUCT_ROOT"
./scripts/build-installers.sh --platform=all --version="$VERSION"

# Upload artifacts
# ... (push to release server, S3, GitHub Releases, etc.)
```

---

## Support

For issues or questions:

- **Documentation:** `/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian/docs/`
- **Logs:**
  - macOS: `/var/log/guardian.log`
  - Windows: `%APPDATA%\Guardian\logs\`
  - Linux: `/var/log/guardian.log` or `journalctl -u guardian`
