#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');
const _crypto = require('crypto');

// Configuration
const CONFIG = {
  appName: 'dcmaar_desktop',
  manifestVersion: '1.0',
  description: 'DCMAR Desktop App Connection',
  // Auto-detect the extension ID from the manifest
  get extensionId() {
    try {
      const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, '../../manifest.json'), 'utf-8'));
      return manifest.browser_specific_settings?.gecko?.id || '';
    } catch (_e) {
      return '';
    }
  },
  // Platform-specific configurations
  platforms: {
    darwin: {
      manifestDir: path.join(os.homedir(), 'Library/Application Support/Google/Chrome/NativeMessagingHosts'),
      appPath: path.join(__dirname, '../../desktop/build/dcmaar-desktop.app/Contents/MacOS/dcmar-desktop')
    },
    linux: {
      manifestDir: path.join(os.homedir(), '.config/google-chrome/NativeMessagingHosts'),
      appPath: path.join(__dirname, '../../desktop/build/dcmaar-desktop')
    },
    win32: {
      manifestDir: path.join(process.env.APPDATA, '..\Local\Google\Chrome\User Data\NativeMessagingHosts'),
      appPath: path.join(__dirname, '..\..\desktop\build\dcmaar-desktop.exe')
    }
  }
};

class NativeHostInstaller {
  constructor() {
    this.platform = process.platform;
    this.config = CONFIG.platforms[this.platform] || {};
    this.manifestPath = path.join(this.config.manifestDir, `${CONFIG.appName}.json`);
  }

  async install() {
    try {
      this.validatePlatform();
      await this.verifyDesktopApp();
      this.createManifest();
      this.setPermissions();
      this.displaySuccess();
    } catch (error) {
      console.error('\x1b[31m%s\x1b[0m', 'Installation failed:', error.message);
      process.exit(1);
    }
  }

  validatePlatform() {
    if (!this.config) {
      throw new Error(`Unsupported platform: ${this.platform}`);
    }
  }

  async verifyDesktopApp() {
    if (!fs.existsSync(this.config.appPath)) {
      throw new Error(`Desktop app not found at: ${this.config.appPath}\n` +
        'Please build the desktop app first.');
    }

    // Make the app executable on Unix-like systems
    if (this.platform !== 'win32') {
      try {
        fs.chmodSync(this.config.appPath, '755');
      } catch (_e) {
        console.warn('Warning: Could not set executable permissions on desktop app');
      }
    }
  }

  createManifest() {
    const manifest = {
      name: CONFIG.appName,
      description: CONFIG.description,
      path: this.config.appPath,
      type: 'stdio',
      allowed_extensions: [CONFIG.extensionId],
      allowed_origins: [`chrome-extension://${CONFIG.extensionId}/`]
    };

    // Create directory if it doesn't exist
    if (!fs.existsSync(this.config.manifestDir)) {
      fs.mkdirSync(this.config.manifestDir, { recursive: true });
    }

    // Write the manifest file
    fs.writeFileSync(this.manifestPath, JSON.stringify(manifest, null, 2));
    console.log(`Created manifest at: ${this.manifestPath}`);
  }

  setPermissions() {
    try {
      if (this.platform === 'win32') {
        // Grant read permissions to all users on Windows
        execSync(`icacls "${this.manifestPath}" /grant "*S-1-1-0:(RX)"`);
      } else {
        // Set read permissions for all users on Unix-like systems
        fs.chmodSync(this.manifestPath, '644');
      }
    } catch (e) {
      console.warn('Warning: Could not set file permissions:', e.message);
    }
  }

  displaySuccess() {
    console.log('\n\x1b[32m%s\x1b[0m', '✓ Native messaging host installed successfully!');
    console.log('\nNext steps:');
    console.log('1. Build the desktop app if you haven\'t already');
    console.log('2. Install the extension in your browser');
    console.log('3. Restart your browser');
    console.log('\nTo uninstall, run this script with --uninstall\n');
  }
}

// Handle command line arguments
const args = process.argv.slice(2);
const installer = new NativeHostInstaller();

if (args.includes('--uninstall')) {
  // Add uninstall logic if needed
  console.log('Uninstall not yet implemented');
} else {
  installer.install();
}
