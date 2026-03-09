# Guardian Linux Installers - Quick Reference

Build Guardian installers for different Linux distributions (flavors).

---

## Overview

Guardian supports three Linux installer formats:

| Format       | Distros                              | Filename                         | Command                          |
| ------------ | ------------------------------------ | -------------------------------- | -------------------------------- |
| **DEB**      | Debian, Ubuntu, Linux Mint, Pop!\_OS | `guardian-1.0.0_amd64.deb`       | `make installers-linux-deb`      |
| **RPM**      | RHEL, CentOS, Fedora, openSUSE       | `guardian-1.0.0-1.x86_64.rpm`    | `make installers-linux-rpm`      |
| **AppImage** | Any Linux distro                     | `Guardian-1.0.0-x86_64.AppImage` | `make installers-linux-appimage` |

---

## Build Commands

### Build All Linux Flavors

```bash
# Using Make (recommended)
make installers-linux-all VERSION=1.0.0

# Or direct script
./scripts/build-installers.sh --platform=linux --flavor=all --version=1.0.0

# Shorthand (Make default)
make installers-linux VERSION=1.0.0
```

**Output:** All three installers (DEB, RPM, AppImage) in `dist/installers/linux/`

### Build Specific Flavor

**Debian/Ubuntu (.deb):**

```bash
make installers-linux-deb VERSION=1.0.0
```

**Red Hat/CentOS/Fedora (.rpm):**

```bash
make installers-linux-rpm VERSION=1.0.0
```

**Universal AppImage:**

```bash
make installers-linux-appimage VERSION=1.0.0
```

### Custom Version

```bash
# Set VERSION when calling make
make installers-linux-all VERSION=2.1.0
make installers-linux-deb VERSION=2.1.0 FLAVOR=deb
```

---

## Installation Instructions

### Debian/Ubuntu (DEB)

```bash
# Install from .deb file
sudo apt install ./guardian-1.0.0_amd64.deb

# Or if added to package repository
sudo apt update
sudo apt install guardian

# Verify
dpkg -l | grep guardian
```

### Red Hat/CentOS/Fedora (RPM)

```bash
# Install from .rpm file
sudo rpm -i ./guardian-1.0.0-1.x86_64.rpm

# Or using DNF (Fedora/RHEL 8+)
sudo dnf install ./guardian-1.0.0-1.x86_64.rpm

# Verify
rpm -qa | grep guardian
```

### Any Linux Distribution (AppImage)

```bash
# Make executable
chmod +x Guardian-1.0.0-x86_64.AppImage

# Run directly
./Guardian-1.0.0-x86_64.AppImage

# Or install to /opt
sudo cp Guardian-1.0.0-x86_64.AppImage /opt/guardian
sudo chmod +x /opt/guardian
# Then run: /opt/guardian
```

---

## Prerequisites by Flavor

### For DEB Builder (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install dpkg-dev build-essential
```

### For RPM Builder (CentOS/RHEL/Fedora)

```bash
# CentOS/RHEL 7
sudo yum install rpm-build

# CentOS/RHEL 8+ or Fedora
sudo dnf install rpm-build
```

### For AppImage Builder (Any Linux)

```bash
# Clone and build
git clone https://github.com/AppImage/AppImageKit
cd AppImageKit
mkdir build && cd build
cmake .. && make
sudo make install
```

Or use pre-built:

```bash
wget https://github.com/AppImage/AppImageKit/releases/download/continuous/AppImageKit-x86_64.AppImage
chmod +x AppImageKit-x86_64.AppImage
sudo mv AppImageKit-x86_64.AppImage /usr/local/bin/appimagetool
```

---

## Workflow Examples

### Example 1: Build for Ubuntu Only

```bash
make installers-linux-deb VERSION=1.0.0
# Produces: dist/installers/linux/guardian-1.0.0_amd64.deb
```

### Example 2: Build for Multiple Distributions

```bash
# Build DEB for Ubuntu
make installers-linux-deb VERSION=1.0.0

# Build RPM for CentOS/Fedora
make installers-linux-rpm VERSION=1.0.0

# Build AppImage as universal fallback
make installers-linux-appimage VERSION=1.0.0
```

### Example 3: Multi-Distro Release

```bash
# Build all in one go
make installers-linux-all VERSION=1.0.0

# All three files ready in dist/installers/linux/
ls -lh dist/installers/linux/
# guardian-1.0.0_amd64.deb
# guardian-1.0.0-1.x86_64.rpm
# Guardian-1.0.0-x86_64.AppImage
```

### Example 4: CI/CD Pipeline Integration

```bash
#!/bin/bash
VERSION="1.0.0"
cd /home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian

# Build for all distros
make installers-linux-all VERSION="${VERSION}"

# Upload artifacts to repository or release server
# Example: scp to package repository
scp -r dist/installers/linux/ packages@repo.example.com:/packages/linux/

echo "Linux installers built and uploaded"
```

---

## Troubleshooting

### Issue: `make installers-linux-deb` fails with "dpkg-deb not found"

**Solution:** Install dpkg-dev

```bash
sudo apt install dpkg-dev
```

### Issue: `make installers-linux-rpm` fails with "rpmbuild not found"

**Solution:** Install rpm-build

```bash
# On CentOS/RHEL
sudo yum install rpm-build

# On Fedora/RHEL 8+
sudo dnf install rpm-build
```

### Issue: `make installers-linux-appimage` fails with "appimagetool not found"

**Solution:** Install AppImageKit

```bash
# Build from source
git clone https://github.com/AppImage/AppImageKit.git
cd AppImageKit
mkdir build && cd build
cmake .. && make && sudo make install

# Or download pre-built binary
wget https://github.com/AppImage/AppImageKit/releases/download/continuous/AppImageKit-x86_64.AppImage
sudo install -m 755 AppImageKit-x86_64.AppImage /usr/local/bin/appimagetool
```

### Issue: Need to build on a different distro

**Ubuntu/Debian:**

```bash
# Can build DEB easily, AppImage always works
make installers-linux-deb VERSION=1.0.0
make installers-linux-appimage VERSION=1.0.0
```

**CentOS/RHEL/Fedora:**

```bash
# Can build RPM easily, AppImage always works
make installers-linux-rpm VERSION=1.0.0
make installers-linux-appimage VERSION=1.0.0
```

**Any distro:**

```bash
# AppImage always available on any Linux
make installers-linux-appimage VERSION=1.0.0
```

---

## Post-Installation

After installation, Guardian runs as a systemd service:

```bash
# Start Guardian
sudo systemctl start guardian

# Enable auto-start on boot
sudo systemctl enable guardian

# Check status
sudo systemctl status guardian

# View logs
sudo journalctl -u guardian -f

# Stop Guardian
sudo systemctl stop guardian

# Uninstall
sudo apt remove guardian      # DEB
# or
sudo rpm -e guardian          # RPM
# or
sudo rm -f /opt/guardian      # AppImage
```

---

## Advanced: Custom Build Directory

To build in a custom directory:

```bash
# Set before running make
export VERSION=1.0.0
export FLAVOR=deb

cd /path/to/guardian
make installers-linux-deb

# Outputs to: dist/installers/linux/guardian-1.0.0_amd64.deb
```

---

## See Also

- `INSTALLERS.md` — Complete platform installer guide
- `Makefile` — All available Make targets
- `scripts/build-installers.sh` — Build script source
- GitHub Actions — CI/CD examples in `.github/workflows/`
