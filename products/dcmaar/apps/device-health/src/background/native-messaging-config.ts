/**
 * Native Messaging Configuration - Platform-Agnostic Setup
 *
 * <p><b>Purpose</b><br>
 * Provides cross-platform configuration and utilities for Chrome native messaging host registration.
 * Automatically detects OS (Windows, macOS, Linux) and applies appropriate manifest format and paths.
 *
 * <p><b>Architecture</b><br>
 * This module is part of the metrics collection pipeline:
 * Extension (TypeScript) → Native Messaging Host → agent-desktop (Rust)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const config = NativeMessagingConfig.getInstance();
 * const manifestPath = config.getManifestPath();
 * const manifestContent = config.getManifestContent(extensionId, binaryPath);
 * const setupInstructions = config.getSetupInstructions();
 * }</pre>
 *
 * <p><b>Supported Platforms</b><br>
 * - Windows: Registry-based (via JSON file reference) or direct registry entry
 * - macOS: JSON manifest in ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/
 * - Linux: JSON manifest in ~/.config/google-chrome/NativeMessagingHosts/
 *
 * @doc.type class
 * @doc.purpose Platform-agnostic native messaging host configuration
 * @doc.layer product
 * @doc.pattern Configuration
 */

/**
 * Supported OS platforms for native messaging
 */
export type OSPlatform = 'windows' | 'macos' | 'linux' | 'unknown';

/**
 * Native messaging manifest file content
 */
export interface NativeMessagingManifest {
  name: string;
  description: string;
  path: string;
  type: 'stdio';
  allowed_origins: string[];
}

/**
 * Configuration for a specific platform
 */
interface PlatformConfig {
  os: OSPlatform;
  manifestPath: string;
  binaryPathFormat: (binaryPath: string) => string;
  setupInstructions: string;
  registryPath?: string;
}

/**
 * Platform-agnostic native messaging configuration manager
 */
export class NativeMessagingConfig {
  private static instance: NativeMessagingConfig;
  private platform: OSPlatform;

  private platformConfigs: Map<OSPlatform, PlatformConfig> = new Map([
    [
      'windows',
      {
        os: 'windows',
        manifestPath:
          'C:\\ProgramData\\Google\\Chrome\\NativeMessagingHosts\\com.ghatana.guardian.desktop.json',
        binaryPathFormat: (path) => path.replace(/\//g, '\\'),
        registryPath:
          'HKEY_LOCAL_MACHINE\\Software\\Google\\Chrome\\NativeMessagingHosts\\com.ghatana.guardian.desktop',
        setupInstructions: `
WINDOWS SETUP INSTRUCTIONS
==========================

1. Create manifest file at:
   C:\\ProgramData\\Google\\Chrome\\NativeMessagingHosts\\com.ghatana.guardian.desktop.json

2. Run PowerShell as Administrator and execute:
   $regPath = "HKLM:\\Software\\Google\\Chrome\\NativeMessagingHosts\\com.ghatana.guardian.desktop"
   New-Item -Path $regPath -Force | Out-Null
   New-ItemProperty -Path $regPath -Name "(Default)" -Value "C:\\ProgramData\\Google\\Chrome\\NativeMessagingHosts\\com.ghatana.guardian.desktop.json" -PropertyType String -Force

3. Verify registry entry:
   reg query "HKLM\\Software\\Google\\Chrome\\NativeMessagingHosts\\com.ghatana.guardian.desktop"

4. Test connection in Chrome DevTools Console:
   chrome.runtime.sendNativeMessage('com.ghatana.guardian.desktop', {type:'PING'}, (response) => console.log(response));
        `,
      },
    ],
    [
      'macos',
      {
        os: 'macos',
        manifestPath: '~/Library/Application Support/Google/Chrome/NativeMessagingHosts/com.ghatana.guardian.desktop.json',
        binaryPathFormat: (path) => path,
        setupInstructions: `
macOS SETUP INSTRUCTIONS
=========================

1. Create directory:
   mkdir -p ~/Library/Application\\ Support/Google/Chrome/NativeMessagingHosts/

2. Create manifest file:
   ~/Library/Application\\ Support/Google/Chrome/NativeMessagingHosts/com.ghatana.guardian.desktop.json

3. Set permissions:
   chmod 644 ~/Library/Application\\ Support/Google/Chrome/NativeMessagingHosts/com.ghatana.guardian.desktop.json
   chmod 755 ~/Library/Application\\ Support/Google/Chrome/NativeMessagingHosts/

4. Verify file exists:
   ls -la ~/Library/Application\\ Support/Google/Chrome/NativeMessagingHosts/

5. Test connection in Chrome DevTools Console:
   chrome.runtime.sendNativeMessage('com.ghatana.guardian.desktop', {type:'PING'}, (response) => console.log(response));
        `,
      },
    ],
    [
      'linux',
      {
        os: 'linux',
        manifestPath: '~/.config/google-chrome/NativeMessagingHosts/com.ghatana.guardian.desktop.json',
        binaryPathFormat: (path) => path,
        setupInstructions: `
LINUX SETUP INSTRUCTIONS
=========================

1. Create directory:
   mkdir -p ~/.config/google-chrome/NativeMessagingHosts/

2. Create manifest file:
   ~/.config/google-chrome/NativeMessagingHosts/com.ghatana.guardian.desktop.json

3. Set permissions:
   chmod 644 ~/.config/google-chrome/NativeMessagingHosts/com.ghatana.guardian.desktop.json
   chmod 755 ~/.config/google-chrome/NativeMessagingHosts/

4. Verify file exists:
   ls -la ~/.config/google-chrome/NativeMessagingHosts/

5. Test connection in Chrome DevTools Console:
   chrome.runtime.sendNativeMessage('com.ghatana.guardian.desktop', {type:'PING'}, (response) => console.log(response));
        `,
      },
    ],
  ]);

  private constructor() {
    this.platform = this.detectPlatform();
  }

  /**
   * Get singleton instance
   */
  static getInstance(): NativeMessagingConfig {
    if (!NativeMessagingConfig.instance) {
      NativeMessagingConfig.instance = new NativeMessagingConfig();
    }
    return NativeMessagingConfig.instance;
  }

  /**
   * Detect current platform
   * Returns: 'windows' | 'macos' | 'linux' | 'unknown'
   */
  private detectPlatform(): OSPlatform {
    // Try to detect from window.navigator.platform if available
    if (typeof window !== 'undefined' && window.navigator) {
      const platform = window.navigator.platform.toLowerCase();

      if (platform.includes('win')) {
        return 'windows';
      } else if (platform.includes('mac')) {
        return 'macos';
      } else if (platform.includes('linux')) {
        return 'linux';
      }
    }

    // Fallback to userAgent parsing
    if (typeof navigator !== 'undefined') {
      const ua = navigator.userAgent.toLowerCase();

      if (ua.includes('win')) return 'windows';
      if (ua.includes('mac')) return 'macos';
      if (ua.includes('linux') || ua.includes('x11')) return 'linux';
    }

    return 'unknown';
  }

  /**
   * Get detected platform
   */
  getPlatform(): OSPlatform {
    return this.platform;
  }

  /**
   * Get manifest path for current platform
   */
  getManifestPath(): string {
    const config = this.platformConfigs.get(this.platform);
    if (!config) {
      throw new Error(`Unsupported platform: ${this.platform}`);
    }
    return config.manifestPath;
  }

  /**
   * Get platform-appropriate binary path
   */
  getBinaryPath(binaryPath: string): string {
    const config = this.platformConfigs.get(this.platform);
    if (!config) {
      throw new Error(`Unsupported platform: ${this.platform}`);
    }
    return config.binaryPathFormat(binaryPath);
  }

  /**
   * Generate manifest content for current platform
   *
   * @param extensionId - Chrome extension ID (from chrome://extensions)
   * @param binaryPath - Absolute path to agent-desktop binary
   * @returns Manifest object ready to JSON.stringify()
   */
  getManifestContent(extensionId: string, binaryPath: string): NativeMessagingManifest {
    return {
      name: 'com.ghatana.guardian.desktop',
      description: 'Guardian Desktop Native Messaging Host - Device Metrics Collection',
      path: this.getBinaryPath(binaryPath),
      type: 'stdio',
      allowed_origins: [`chrome-extension://${extensionId}/`],
    };
  }

  /**
   * Get setup instructions for current platform
   */
  getSetupInstructions(): string {
    const config = this.platformConfigs.get(this.platform);
    if (!config) {
      return `Platform "${this.platform}" not supported`;
    }
    return config.setupInstructions;
  }

  /**
   * Get registry path (Windows only)
   */
  getRegistryPath(): string | undefined {
    const config = this.platformConfigs.get(this.platform);
    return config?.registryPath;
  }

  /**
   * Check if current platform is supported
   */
  isSupported(): boolean {
    return this.platform !== 'unknown' && this.platformConfigs.has(this.platform);
  }

  /**
   * Get all supported platforms
   */
  getSupportedPlatforms(): OSPlatform[] {
    return Array.from(this.platformConfigs.keys()).filter((p) => p !== 'unknown');
  }

  /**
   * Get manifest content for specific platform
   * Useful for cross-platform testing
   */
  getManifestContentForPlatform(
    platform: OSPlatform,
    extensionId: string,
    binaryPath: string
  ): NativeMessagingManifest | null {
    const config = this.platformConfigs.get(platform);
    if (!config) return null;

    return {
      name: 'com.ghatana.guardian.desktop',
      description: 'Guardian Desktop Native Messaging Host - Device Metrics Collection',
      path: config.binaryPathFormat(binaryPath),
      type: 'stdio',
      allowed_origins: [`chrome-extension://${extensionId}/`],
    };
  }

  /**
   * Validate manifest content
   * Returns: { valid: boolean, errors: string[] }
   */
  validateManifest(manifest: unknown): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (typeof manifest !== 'object' || manifest === null) {
      return { valid: false, errors: ['Manifest must be an object'] };
    }

    const m = manifest as Record<string, unknown>;

    if (typeof m.name !== 'string' || !m.name.includes('com.ghatana')) {
      errors.push('Invalid name: must include "com.ghatana"');
    }

    if (typeof m.path !== 'string' || m.path.length === 0) {
      errors.push('Invalid path: must be a non-empty string');
    }

    if (m.type !== 'stdio') {
      errors.push('Invalid type: must be "stdio"');
    }

    if (!Array.isArray(m.allowed_origins) || m.allowed_origins.length === 0) {
      errors.push('Invalid allowed_origins: must be a non-empty array');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}

/**
 * Export default instance for convenience
 */
export const nativeMessagingConfig = NativeMessagingConfig.getInstance();
