#!/usr/bin/env node

import { fileURLToPath } from 'url';
import { dirname } from 'path';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Debug information
console.log('Current working directory:', process.cwd());
console.log('Script directory:', __dirname);

// Configuration
const CONFIG = {
  appName: 'dcmar_desktop',
  manifestVersion: '1.0',
  description: 'DCMAR Desktop App Connection',
  platforms: {
    darwin: {
      manifestDir: process.env.HOME 
        ? path.join(process.env.HOME, 'Library/Application Support/Google/Chrome/NativeMessagingHosts')
        : null,
      appPath: path.resolve(__dirname, '../../desktop/build/dcmar-desktop.app/Contents/MacOS/dcmar-desktop')
    },
    linux: {
      manifestDir: process.env.HOME
        ? path.join(process.env.HOME, '.config/google-chrome/NativeMessagingHosts')
        : null,
      appPath: path.resolve(__dirname, '../../desktop/build/dcmar-desktop')
    },
    win32: {
      manifestDir: process.env.APPDATA
        ? path.join(process.env.APPDATA, '..', 'Local', 'Google', 'Chrome', 'User Data', 'NativeMessagingHosts')
        : null,
      appPath: path.resolve(__dirname, '..', '..', 'desktop', 'build', 'dcmar-desktop.exe')
    }
  }
};

// Debug: Log platform-specific config
console.log('Detected platform:', process.platform);
console.log('Platform config:', JSON.stringify(CONFIG.platforms[process.platform], null, 2));

class NativeHostInstaller {
  constructor() {
    this.platform = process.platform;
    this.config = CONFIG.platforms[this.platform];
    if (!this.config) {
      console.error(`Unsupported platform: ${this.platform}`);
      process.exit(1);
    }
    this.manifestPath = path.join(this.config.manifestDir, `${CONFIG.appName}.json`);
  }

  async install() {
    try {
      await this.verifyDesktopApp();
      this.createManifest();
      this.setPermissions();
      console.log('✅ Native messaging host installed successfully!');
      console.log('\nNext steps:');
      console.log('1. Build the desktop app if you haven\'t already');
      console.log('2. Install the extension in your browser');
      console.log('3. Restart your browser');
    } catch (error) {
      console.error('❌ Installation failed:', error.message);
      process.exit(1);
    }
  }

  verifyDesktopApp() {
    if (!fs.existsSync(this.config.appPath)) {
      throw new Error(`Desktop app not found at: ${this.config.appPath}\nPlease build the desktop app first.`);
    }
    
    // Make the app executable on Unix-like systems
    if (this.platform !== 'win32') {
      try {
        fs.chmodSync(this.config.appPath, '755');
      } catch (_e) {
        console.warn('⚠️  Could not set executable permissions on desktop app');
      }
    }
  }

  createManifest() {
    const manifest = {
      name: CONFIG.appName,
      description: CONFIG.description,
      path: this.config.appPath,
      type: 'stdio',
      allowed_origins: ['chrome-extension://' + this.getExtensionId() + '/']
    };

    // Create directory if it doesn't exist
    if (!fs.existsSync(this.config.manifestDir)) {
      fs.mkdirSync(this.config.manifestDir, { recursive: true });
    }

    // Write the manifest file
    fs.writeFileSync(this.manifestPath, JSON.stringify(manifest, null, 2));
    console.log(`📝 Created manifest at: ${this.manifestPath}`);
  }

  setPermissions() {
    try {
      if (this.platform === 'win32') {
        // Grant read permissions to all users on Windows
        execSync(`icacls "${this.manifestPath}" /grant "*S-1-1-0:(RX)"`);
      } else {
        // Set read permissions for all users on Unix-like systems
        fs.chmodSync(this.manifestPath, 0o644);
      }
    } catch (e) {
      console.warn('⚠️  Could not set file permissions:', e.message);
    }
  }

  getExtensionId() {
    // This is a placeholder - in a real app, you might want to:
    // 1. Read the extension ID from a config file
    // 2. Use a fixed ID from your extension's manifest
    // 3. Generate a deterministic ID based on the extension's public key
    // For now, we'll use a placeholder that matches the one in the manifest
    return 'dcmaar-extension-id';
  }
}

// Run the installer
const installer = new NativeHostInstaller();
installer.install().catch(console.error);
