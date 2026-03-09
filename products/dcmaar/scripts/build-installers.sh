#!/usr/bin/env bash
#
# Guardian Product Installer Builder
# Builds cross-platform installers for macOS, Windows, and Linux
#
# Usage:
#   ./scripts/build-installers.sh [--platform=PLATFORM] [--flavor=FLAVOR] [--version=VERSION]
#
# Platforms: macos, windows, linux, all (default)
# Flavors (Linux only): deb, rpm, appimage, all (default)
# Examples:
#   ./scripts/build-installers.sh --platform=linux --flavor=deb --version=1.0.0
#   ./scripts/build-installers.sh --platform=linux --flavor=appimage --version=1.0.0
#   ./scripts/build-installers.sh --platform=all --version=1.0.0
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(dirname "$SCRIPT_DIR")"
VERSION="${VERSION:-1.0.0}"
PLATFORM="${PLATFORM:-all}"
FLAVOR="${FLAVOR:-all}"  # Linux flavor: deb, rpm, appimage, all
DIST_DIR="$PRODUCT_ROOT/dist/installers"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${BLUE}ℹ${NC} $*"; }
log_success() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; }

# Create build directory
mkdir -p "$DIST_DIR"

# ============================================================================
# macOS Installer (.dmg + .pkg)
# ============================================================================
build_macos_installer() {
  log_info "Building macOS installer..."

  local macos_dir="$DIST_DIR/macos"
  mkdir -p "$macos_dir"

  # Check for required tools
  if ! command -v productbuild &>/dev/null; then
    log_warn "productbuild not found. Install Xcode Command Line Tools: xcode-select --install"
    return 1
  fi

  # Build stage 1: Create .pkg (component package)
  local pkg_dir="$macos_dir/build"
  mkdir -p "$pkg_dir/Guardian/Applications/Guardian.app/Contents/MacOS"
  mkdir -p "$pkg_dir/Guardian/Applications/Guardian.app/Contents/Resources"
  mkdir -p "$pkg_dir/Guardian/Library/LaunchAgents"

  # Copy binaries and assets
  cp apps/parent-dashboard/dist/* "$pkg_dir/Guardian/Applications/Guardian.app/Contents/Resources/" 2>/dev/null || true
  cp apps/browser-extension/dist/chrome/* "$pkg_dir/Guardian/Applications/Guardian.app/Contents/Resources/browser-extension/" 2>/dev/null || true

  # Create launcher script
  cat > "$pkg_dir/Guardian/Applications/Guardian.app/Contents/MacOS/guardian" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_DIR="$(dirname "$SCRIPT_DIR")/Resources"
exec open -a "Google Chrome" "file://$APP_DIR/index.html" &
EOF
  chmod +x "$pkg_dir/Guardian/Applications/Guardian.app/Contents/MacOS/guardian"

  # Create Info.plist
  cat > "$pkg_dir/Guardian/Applications/Guardian.app/Contents/Info.plist" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>guardian</string>
    <key>CFBundleIdentifier</key>
    <string>com.ghatana.guardian</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Guardian</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.13</string>
</dict>
</plist>
EOF

  # Create LaunchAgent for background monitoring
  cat > "$pkg_dir/Guardian/Library/LaunchAgents/com.ghatana.guardian.plist" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.ghatana.guardian</string>
    <key>Program</key>
    <string>/Applications/Guardian.app/Contents/MacOS/guardian</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
EOF

  # Create Distribution XML for productbuild
  cat > "$macos_dir/Distribution.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<installer-gui-script minSpecVersion="1">
    <title>Guardian</title>
    <organization>com.ghatana</organization>
    <domains enable_localSystem="true" />
    <installation-check script="beforeInstall()"/>
    <script>
        function beforeInstall() {
            return true;
        }
    </script>
    <pkg-ref id="com.ghatana.guardian" installKBytes="50000" version="1.0"/>
    <choices-outline>
        <line choice="default">
            <line choice="com.ghatana.guardian"/>
        </line>
    </choices-outline>
    <choice id="default"/>
    <choice id="com.ghatana.guardian" visible="false">
        <pkg-ref id="com.ghatana.guardian"/>
    </choice>
    <pkg-ref id="com.ghatana.guardian" onConclusion="none">
        <relocate></relocate>
    </pkg-ref>
</installer-gui-script>
EOF

  # Build the component package
  pkgbuild --root "$pkg_dir/Guardian" \
    --identifier "com.ghatana.guardian" \
    --version "$VERSION" \
    --scripts "$SCRIPT_DIR/macos/scripts" \
    "$macos_dir/Guardian.pkg" 2>/dev/null || log_warn "pkgbuild failed, skipping pkg generation"

  # Create DMG
  local dmg_tmp="$macos_dir/tmp.dmg"
  local dmg_final="$macos_dir/Guardian-$VERSION.dmg"

  hdiutil create -volname "Guardian" -srcfolder "$pkg_dir/Guardian/Applications" \
    -ov -format UDZO "$dmg_final" 2>/dev/null || log_warn "DMG creation failed"

  rm -rf "$pkg_dir" "$dmg_tmp"

  if [[ -f "$dmg_final" ]]; then
    log_success "macOS DMG installer created: $dmg_final"
  else
    log_warn "macOS installer generation incomplete"
  fi
}

# ============================================================================
# Windows Installer (NSIS + MSI)
# ============================================================================
build_windows_installer() {
  log_info "Building Windows installer..."

  local windows_dir="$DIST_DIR/windows"
  mkdir -p "$windows_dir"

  # Check for required tools
  if ! command -v makensis &>/dev/null; then
    log_warn "makensis (NSIS) not found. Install from https://nsis.sourceforge.io"
    log_info "Or on Windows: choco install nsis"
    return 1
  fi

  # Create NSIS installer script
  cat > "$windows_dir/Guardian.nsi" << 'EOF'
; Guardian Windows Installer
; Built with NSIS 3.x

!include "MUI2.nsh"
!include "x64.nsh"

; Name and file
Name "Guardian"
OutFile "Guardian-1.0.0-Setup.exe"
InstallDir "$PROGRAMFILES\Ghatana\Guardian"

; Variables
Var StartMenuFolder

; MUI Settings
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_STARTMENU Application $StartMenuFolder
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "English"

; Installer sections
Section "Install"
  SetOutPath "$INSTDIR"
  
  ; Copy files
  File /r "..\..\apps\parent-dashboard\dist\*.*"
  File /r "..\..\apps\browser-extension\dist\chrome\*.*"
  
  ; Create start menu shortcuts
  !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
  CreateDirectory "$SMPROGRAMS\$StartMenuFolder"
  CreateShortcut "$SMPROGRAMS\$StartMenuFolder\Guardian.lnk" "$INSTDIR\Guardian.exe"
  CreateShortcut "$SMPROGRAMS\$StartMenuFolder\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
  !insertmacro MUI_STARTMENU_WRITE_END
  
  ; Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Uninstall"
  RMDir /r "$INSTDIR"
  !insertmacro MUI_STARTMENU_GETFOLDER Application $StartMenuFolder
  RMDir /r "$SMPROGRAMS\$StartMenuFolder"
SectionEnd
EOF

  # Build NSIS installer
  makensis "$windows_dir/Guardian.nsi" 2>/dev/null && \
    log_success "Windows NSIS installer created: $windows_dir/Guardian-1.0.0-Setup.exe" || \
    log_warn "NSIS build failed"

  # Alternative: WiX MSI (if wix toolset available)
  if command -v candle.exe &>/dev/null; then
    log_info "Building WiX MSI installer..."
    # MSI generation would go here
  fi
}

# ============================================================================
# Linux Installers (DEB + RPM + AppImage) with Flavor Support
# ============================================================================
build_linux_deb() {
  log_info "Building DEB package (Debian/Ubuntu/Linux Mint)..."

  local linux_dir="$DIST_DIR/linux"
  mkdir -p "$linux_dir"

  local deb_dir="$linux_dir/deb-build"
  mkdir -p "$deb_dir/guardian-$VERSION/DEBIAN"
  mkdir -p "$deb_dir/guardian-$VERSION/usr/bin"
  mkdir -p "$deb_dir/guardian-$VERSION/usr/share/guardian"
  mkdir -p "$deb_dir/guardian-$VERSION/usr/share/applications"
  mkdir -p "$deb_dir/guardian-$VERSION/usr/share/icons/hicolor/256x256/apps"
  mkdir -p "$deb_dir/guardian-$VERSION/etc/systemd/user"
  mkdir -p "$deb_dir/guardian-$VERSION/opt/guardian"

  # Copy files
  cp -r "$PRODUCT_ROOT/apps/parent-dashboard/dist"/* \
    "$deb_dir/guardian-$VERSION/usr/share/guardian/" 2>/dev/null || true
  cp -r "$PRODUCT_ROOT/apps/browser-extension/dist/chrome"/* \
    "$deb_dir/guardian-$VERSION/usr/share/guardian/browser-extension/" 2>/dev/null || true

  # Create launcher script
  cat > "$deb_dir/guardian-$VERSION/usr/bin/guardian" << 'EOF'
#!/bin/bash
exec /usr/share/guardian/index.html
EOF
  chmod +x "$deb_dir/guardian-$VERSION/usr/bin/guardian"

  # Control file
  cat > "$deb_dir/guardian-$VERSION/DEBIAN/control" << EOF
Package: guardian
Version: $VERSION
Architecture: amd64
Maintainer: Ghatana Team <team@ghatana.com>
Description: Guardian - Family Safety Dashboard
 Monitor and manage family device usage and safety.
 Provides parental controls, web filtering, and activity monitoring.
Depends: libssl3, ca-certificates
Section: misc
Priority: optional
EOF

  # Postinst script (with GUI support)
  cat > "$deb_dir/guardian-$VERSION/DEBIAN/postinst" << 'EOF'
#!/bin/bash
set -e
if [ "$1" = "configure" ]; then
    # Register systemd service
    systemctl daemon-reload 2>/dev/null || true
    
    # Update desktop database for menu entries
    update-desktop-database /usr/share/applications 2>/dev/null || true
    
    # Update icon cache if icon exists
    if command -v update-icon-caches &>/dev/null; then
        update-icon-caches /usr/share/icons/hicolor 2>/dev/null || true
    fi
    
    # Notify user
    echo "Guardian installed successfully!"
    echo "Start Guardian from Applications menu or run: guardian"
    echo "To enable auto-start: systemctl --user enable guardian"
fi
EOF
  chmod 755 "$deb_dir/guardian-$VERSION/DEBIAN/postinst"

  # Prerm script (before removal)
  cat > "$deb_dir/guardian-$VERSION/DEBIAN/prerm" << 'EOF'
#!/bin/bash
set -e
# Stop service before removal
systemctl --user stop guardian 2>/dev/null || true
EOF
  chmod 755 "$deb_dir/guardian-$VERSION/DEBIAN/prerm"

  # Postrm script (after removal)
  cat > "$deb_dir/guardian-$VERSION/DEBIAN/postrm" << 'EOF'
#!/bin/bash
set -e
# Clean up service files
systemctl --user daemon-reload 2>/dev/null || true
# Update desktop database
update-desktop-database /usr/share/applications 2>/dev/null || true
EOF
  chmod 755 "$deb_dir/guardian-$VERSION/DEBIAN/postrm"

  # Desktop file with enhanced GUI features
  cat > "$deb_dir/guardian-$VERSION/usr/share/applications/guardian.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Version=1.0
Name=Guardian
Comment=Family Safety Dashboard
Description=Monitor and manage family device usage and safety with Guardian
GenericName=Family Safety Dashboard
Exec=guardian
Icon=guardian
Categories=Network;Utility;Monitor;System;
Terminal=false
StartupNotify=true
Keywords=family;safety;parental;control;monitoring;
X-Desktop-File-Install-Version=0.24
EOF

  # Systemd user service file (for auto-start and management)
  cat > "$deb_dir/guardian-$VERSION/etc/systemd/user/guardian.service" << 'EOF'
[Unit]
Description=Guardian - Family Safety Dashboard
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/share/guardian/guardian-service.sh
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
EOF

  # Create icon placeholder (SVG format for scalability)
  mkdir -p "$deb_dir/guardian-$VERSION/usr/share/icons/hicolor/256x256/apps"
  cat > "$deb_dir/guardian-$VERSION/usr/share/icons/hicolor/256x256/apps/guardian.svg" << 'EOF'
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="shield-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#4F46E5;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#7C3AED;stop-opacity:1" />
    </linearGradient>
  </defs>
  <!-- Shield background -->
  <path d="M 128 32 L 96 64 L 96 144 Q 96 192 128 224 Q 160 192 160 144 L 160 64 Z" 
        fill="url(#shield-gradient)" stroke="none"/>
  <!-- Checkmark inside shield -->
  <path d="M 110 140 L 125 155 L 150 110" 
        stroke="white" stroke-width="12" fill="none" 
        stroke-linecap="round" stroke-linejoin="round"/>
</svg>
EOF

  # Build DEB
  if command -v dpkg-deb &>/dev/null; then
    dpkg-deb --build "$deb_dir/guardian-$VERSION" "$linux_dir/guardian-${VERSION}_amd64.deb" 2>/dev/null && \
      log_success "DEB package created: $linux_dir/guardian-${VERSION}_amd64.deb" || \
      log_warn "DEB build failed"
  else
    log_warn "dpkg-deb not found, skipping DEB package"
  fi

  rm -rf "$deb_dir"
}

build_linux_rpm() {
  log_info "Building RPM package (Red Hat/CentOS/Fedora)..."

  local linux_dir="$DIST_DIR/linux"
  mkdir -p "$linux_dir"

  local rpm_dir="$linux_dir/rpm-build"
  mkdir -p "$rpm_dir/SPECS"
  mkdir -p "$rpm_dir/SOURCES"

  # Create spec file
  cat > "$rpm_dir/SPECS/guardian.spec" << 'EOF'
Name:           guardian
Version:        1.0.0
Release:        1%{?dist}
Summary:        Family Safety Dashboard

License:        MIT
URL:            https://github.com/ghatana/guardian

%description
Guardian - Monitor and manage family device usage and safety.

%prep
# No prep needed for binary package

%install
mkdir -p %{buildroot}/usr/share/guardian
mkdir -p %{buildroot}/usr/bin
cp -r SOURCES/* %{buildroot}/usr/share/guardian/
cat > %{buildroot}/usr/bin/guardian << 'SCRIPT'
#!/bin/bash
exec /usr/share/guardian/index.html
SCRIPT
chmod +x %{buildroot}/usr/bin/guardian

%files
/usr/bin/guardian
/usr/share/guardian/*

%changelog
* $(date +"%a %b %d %Y") Ghatana Team <team@ghatana.com> - 1.0.0-1
- Initial release
EOF

  if command -v rpmbuild &>/dev/null; then
    log_info "Building RPM package..."
    # RPM build would be run here
    log_warn "RPM build requires full spec setup, manual build recommended"
  else
    log_warn "rpmbuild not found, skipping RPM package"
  fi

  rm -rf "$rpm_dir"
}

build_linux_appimage() {
  log_info "Building AppImage (universal Linux binary)..."

  local linux_dir="$DIST_DIR/linux"
  mkdir -p "$linux_dir"

  local appimage_dir="$linux_dir/appimage-build"
  mkdir -p "$appimage_dir/AppDir/usr/bin"
  mkdir -p "$appimage_dir/AppDir/usr/share/pixmaps"

  # Copy application files
  cp -r "$PRODUCT_ROOT/apps/parent-dashboard/dist"/* \
    "$appimage_dir/AppDir/" 2>/dev/null || true

  # Create AppRun launcher
  cat > "$appimage_dir/AppDir/AppRun" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
exec "$SCRIPT_DIR/index.html"
EOF
  chmod +x "$appimage_dir/AppDir/AppRun"

  # Create desktop file
  cat > "$appimage_dir/AppDir/guardian.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=Guardian
Comment=Family Safety Dashboard
Exec=AppRun
Icon=guardian
Categories=Network;Utility;
Terminal=false
EOF

  if command -v appimagetool &>/dev/null; then
    appimagetool "$appimage_dir/AppDir" "$linux_dir/Guardian-$VERSION-x86_64.AppImage" 2>/dev/null && \
      log_success "AppImage created: $linux_dir/Guardian-$VERSION-x86_64.AppImage" && \
      chmod +x "$linux_dir/Guardian-$VERSION-x86_64.AppImage" || \
      log_warn "AppImage build failed"
  else
    log_warn "appimagetool not found. Install from https://github.com/AppImage/AppImageKit"
  fi

  rm -rf "$appimage_dir"
}

build_linux_installer() {
  case "$FLAVOR" in
    deb)
      build_linux_deb
      ;;
    rpm)
      build_linux_rpm
      ;;
    appimage)
      build_linux_appimage
      ;;
    all)
      build_linux_deb || true
      echo ""
      build_linux_rpm || true
      echo ""
      build_linux_appimage || true
      ;;
    *)
      log_error "Unknown Linux flavor: $FLAVOR"
      echo "Valid flavors: deb, rpm, appimage, all"
      exit 1
      ;;
  esac
}

# ============================================================================
# Main
# ============================================================================
main() {
  # Build flavor suffix for logging
  flavor_suffix=""
  if [[ "$FLAVOR" != "all" && -n "$FLAVOR" ]]; then
    flavor_suffix=" - flavor: $FLAVOR"
  fi
  
  log_info "Building Guardian installers for $PLATFORM (v$VERSION)$flavor_suffix"
  log_info "Output directory: $DIST_DIR"
  echo ""

  case "$PLATFORM" in
    macos)
      build_macos_installer
      ;;
    windows)
      build_windows_installer
      ;;
    linux)
      build_linux_installer
      ;;
    all)
      build_macos_installer || true
      echo ""
      build_windows_installer || true
      echo ""
      build_linux_installer || true
      ;;
    *)
      log_error "Unknown platform: $PLATFORM"
      echo "Valid platforms: macos, windows, linux, all"
      exit 1
      ;;
  esac

  echo ""
  log_info "Installer build complete!"
  log_info "Artifacts available in: $DIST_DIR"
  ls -lh "$DIST_DIR"
}

main
