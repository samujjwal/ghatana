/**
 * Native Messaging Host Setup Utility - Platform-Agnostic
 *
 * <p><b>Purpose</b><br>
 * Provides automated setup for native messaging host registration across Windows, macOS, and Linux.
 * Detects platform, generates manifest, and provides step-by-step installation instructions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const setup = new NativeMessagingSetup();
 * const extensionId = 'YOUR_EXTENSION_ID';
 * const binaryPath = '/path/to/agent-desktop';
 *
 * // Get manifest content
 * const manifest = setup.getManifest(extensionId, binaryPath);
 *
 * // Get setup instructions
 * const instructions = setup.getInstructions();
 *
 * // Validate setup is complete
 * const isReady = setup.isReady();
 * }</pre>
 *
 * <p><b>Architecture</b><br>
 * Part of the metrics collection pipeline:
 * - MetricsBridge (TypeScript) ← → Native Messaging Host ← → agent-desktop (Rust)
 * - Uses chrome.runtime.sendNativeMessage() for IPC
 * - Manifest registers host with Chrome/Chromium
 *
 * <p><b>Platform-Specific Details</b><br>
 * Windows:
 *   - Registry: HKEY_LOCAL_MACHINE\Software\Google\Chrome\NativeMessagingHosts\...
 *   - Manifest: C:\ProgramData\Google\Chrome\NativeMessagingHosts\...json
 *   - Requires: Administrator privileges for registry entry
 *
 * macOS:
 *   - Location: ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/
 *   - Permissions: 644 for file, 755 for directory
 *   - Expansion: ~ expands to user home directory
 *
 * Linux:
 *   - Location: ~/.config/google-chrome/NativeMessagingHosts/
 *   - Permissions: 644 for file, 755 for directory
 *   - Expansion: ~ expands to user home directory
 *
 * @doc.type class
 * @doc.purpose Cross-platform native messaging host setup utility
 * @doc.layer product
 * @doc.pattern Utility
 */

import { NativeMessagingConfig, OSPlatform, NativeMessagingManifest } from './native-messaging-config';

/**
 * Setup result with status and details
 */
export interface SetupResult {
  success: boolean;
  platform: OSPlatform;
  manifestPath: string;
  binaryPath: string;
  message: string;
  steps: string[];
  errors: string[];
}

/**
 * Native messaging setup utility
 */
export class NativeMessagingSetup {
  private config: NativeMessagingConfig;

  constructor() {
    this.config = NativeMessagingConfig.getInstance();
  }

  /**
   * Get platform detected
   */
  getPlatform(): OSPlatform {
    return this.config.getPlatform();
  }

  /**
   * Check if platform is supported
   */
  isSupported(): boolean {
    return this.config.isSupported();
  }

  /**
   * Get manifest content for setup
   * Caller is responsible for writing to disk/registry
   */
  getManifest(extensionId: string, binaryPath: string): NativeMessagingManifest {
    this.validateInputs(extensionId, binaryPath);
    return this.config.getManifestContent(extensionId, binaryPath);
  }

  /**
   * Get detailed setup instructions for current platform
   */
  getInstructions(): string {
    if (!this.isSupported()) {
      return `
UNSUPPORTED PLATFORM
====================
Your platform (${this.getPlatform()}) is not supported.

Supported platforms:
${this.config.getSupportedPlatforms().join(', ')}

Please use one of the supported platforms and try again.
      `;
    }

    const platform = this.getPlatform();
    const manifestPath = this.config.getManifestPath();

    if (platform === 'windows') {
      return this.getWindowsInstructions(manifestPath);
    } else if (platform === 'macos') {
      return this.getMacOSInstructions(manifestPath);
    } else if (platform === 'linux') {
      return this.getLinuxInstructions(manifestPath);
    }

    return this.config.getSetupInstructions();
  }

  /**
   * Get Windows-specific setup instructions
   */
  private getWindowsInstructions(manifestPath: string): string {
    const regPath = this.config.getRegistryPath();
    return `
WINDOWS NATIVE MESSAGING HOST SETUP
====================================

STEP 1: Create Manifest File
────────────────────────────
Create a JSON file at:
  ${manifestPath}

Use the manifest content provided by getManifestContent().

Example:
{
  "name": "com.ghatana.guardian.desktop",
  "description": "Guardian Desktop Native Messaging Host",
  "path": "C:\\\\path\\\\to\\\\agent-desktop.exe",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://YOUR_EXTENSION_ID/"]
}

STEP 2: Add Registry Entry (Run as Administrator)
───────────────────────────────────────────────────
Open PowerShell as Administrator and run:

$regPath = "${regPath}"
New-Item -Path $regPath -Force | Out-Null
$manifestPath = "${manifestPath}"
New-ItemProperty -Path $regPath -Name "(Default)" -Value $manifestPath -PropertyType String -Force

STEP 3: Verify Registry Entry
──────────────────────────────
Run in PowerShell (no admin required):

reg query "${regPath}"

Expected output should show your manifest path.

STEP 4: Test Connection
────────────────────────
In Chrome DevTools Console (F12):

chrome.runtime.sendNativeMessage(
  'com.ghatana.guardian.desktop',
  { type: 'PING' },
  (response) => {
    if (chrome.runtime.lastError) {
      console.error('Error:', chrome.runtime.lastError.message);
    } else {
      console.log('Success:', response);
    }
  }
);

TROUBLESHOOTING
───────────────
- Path not found: Check manifest file exists and path is correct (forward slashes converted to backslashes)
- Access denied: Run PowerShell as Administrator for registry commands
- Invalid JSON: Validate manifest with: python3 -m json.tool manifest.json
    `;
  }

  /**
   * Get macOS-specific setup instructions
   */
  private getMacOSInstructions(manifestPath: string): string {
    const dir = manifestPath.substring(0, manifestPath.lastIndexOf('/'));
    return `
macOS NATIVE MESSAGING HOST SETUP
==================================

STEP 1: Create Directory
────────────────────────
Run in Terminal:

mkdir -p "${dir}"

STEP 2: Create Manifest File
──────────────────────────────
Create a JSON file at:
  ${manifestPath}

Use the manifest content provided by getManifestContent().

Example:
{
  "name": "com.ghatana.guardian.desktop",
  "description": "Guardian Desktop Native Messaging Host",
  "path": "/Users/username/Developments/ghatana/products/guardian/target/release/agent-desktop",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://YOUR_EXTENSION_ID/"]
}

STEP 3: Set File Permissions
──────────────────────────────
chmod 644 "${manifestPath}"
chmod 755 "${dir}"

STEP 4: Verify Setup
─────────────────────
ls -la "${dir}/" | grep com.ghatana

Should show the manifest file with proper permissions.

STEP 5: Test Connection
────────────────────────
In Chrome DevTools Console (Cmd+Option+J):

chrome.runtime.sendNativeMessage(
  'com.ghatana.guardian.desktop',
  { type: 'PING' },
  (response) => {
    if (chrome.runtime.lastError) {
      console.error('Error:', chrome.runtime.lastError.message);
    } else {
      console.log('Success:', response);
    }
  }
);

TROUBLESHOOTING
───────────────
- File not found: Verify path is absolute, not relative
- Permission denied: Check directory permissions (755) and file permissions (644)
- Path errors: Ensure no spaces in directory names, use full path
    `;
  }

  /**
   * Get Linux-specific setup instructions
   */
  private getLinuxInstructions(manifestPath: string): string {
    const dir = manifestPath.substring(0, manifestPath.lastIndexOf('/'));
    return `
LINUX NATIVE MESSAGING HOST SETUP
==================================

STEP 1: Create Directory
────────────────────────
Run in Terminal:

mkdir -p "${dir}"

STEP 2: Create Manifest File
──────────────────────────────
Create a JSON file at:
  ${manifestPath}

Use the manifest content provided by getManifestContent().

Example:
{
  "name": "com.ghatana.guardian.desktop",
  "description": "Guardian Desktop Native Messaging Host",
  "path": "/home/username/Developments/ghatana/products/guardian/target/release/agent-desktop",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://YOUR_EXTENSION_ID/"]
}

STEP 3: Set File Permissions
──────────────────────────────
chmod 644 "${manifestPath}"
chmod 755 "${dir}"

STEP 4: Make Binary Executable
────────────────────────────────
chmod +x /path/to/agent-desktop

STEP 5: Verify Setup
─────────────────────
ls -la "${dir}/" | grep com.ghatana

Should show the manifest file with proper permissions.

STEP 6: Test Connection
────────────────────────
In Chrome DevTools Console (Ctrl+Shift+J):

chrome.runtime.sendNativeMessage(
  'com.ghatana.guardian.desktop',
  { type: 'PING' },
  (response) => {
    if (chrome.runtime.lastError) {
      console.error('Error:', chrome.runtime.lastError.message);
    } else {
      console.log('Success:', response);
    }
  }
);

TROUBLESHOOTING
───────────────
- File not found: Verify path is absolute, not relative
- Permission denied: Check directory permissions (755) and file permissions (644)
- Binary not executable: Run chmod +x on the agent-desktop binary
- JSON parsing error: Validate with: python3 -m json.tool manifest.json
    `;
  }

  /**
   * Generate complete setup summary with all steps
   */
  generateSetupSummary(extensionId: string, binaryPath: string): SetupResult {
    const errors: string[] = [];

    // Validate inputs
    if (!extensionId || extensionId.length === 0) {
      errors.push('Extension ID is required');
    }

    if (!binaryPath || binaryPath.length === 0) {
      errors.push('Binary path is required');
    }

    if (!this.isSupported()) {
      errors.push(`Platform "${this.getPlatform()}" is not supported`);
    }

    const platform = this.getPlatform();
    const manifestPath = this.config.getManifestPath();
    const manifest = this.config.getManifestContent(extensionId, binaryPath);

    // Validate manifest
    const validation = this.config.validateManifest(manifest);
    if (!validation.valid) {
      errors.push(...validation.errors);
    }

    const steps = this.generateSteps(platform, manifestPath, extensionId);

    return {
      success: errors.length === 0,
      platform,
      manifestPath,
      binaryPath: manifest.path,
      message: errors.length === 0 ? 'Setup ready to proceed' : 'Setup validation failed',
      steps,
      errors,
    };
  }

  /**
   * Generate platform-specific setup steps
   */
  private generateSteps(platform: OSPlatform, manifestPath: string, extensionId: string): string[] {
    const steps: string[] = [
      `1. Platform detected: ${platform}`,
      `2. Manifest location: ${manifestPath}`,
      `3. Extension ID: ${extensionId}`,
    ];

    if (platform === 'windows') {
      steps.push('4. Register registry entry (requires admin)');
      steps.push('5. Verify registry: reg query "HKLM\\Software\\Google\\Chrome\\NativeMessagingHosts\\..."');
    } else if (platform === 'macos') {
      steps.push('4. Create directory if needed');
      steps.push('5. Set file permissions: chmod 644');
      steps.push('6. Set directory permissions: chmod 755');
    } else if (platform === 'linux') {
      steps.push('4. Create directory if needed');
      steps.push('5. Set file permissions: chmod 644');
      steps.push('6. Set directory permissions: chmod 755');
      steps.push('7. Make binary executable: chmod +x');
    }

    steps.push('N. Test in Chrome DevTools: chrome.runtime.sendNativeMessage(...)');

    return steps;
  }

  /**
   * Check if native messaging is likely ready
   * Note: This is a client-side check; actual readiness depends on file system state
   */
  isReady(): boolean {
    // Check if platform is supported
    if (!this.isSupported()) {
      return false;
    }

    // Check if we can access chrome API
    if (typeof chrome === 'undefined' || !chrome.runtime) {
      return false;
    }

    return true;
  }

  /**
   * Get diagnostic information for troubleshooting
   */
  getDiagnostics(): Record<string, unknown> {
    return {
      platform: this.getPlatform(),
      isSupported: this.isSupported(),
      manifestPath: this.config.getManifestPath(),
      supportedPlatforms: this.config.getSupportedPlatforms(),
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'N/A',
      chromeAvailable: typeof chrome !== 'undefined',
      nativeMessagingAvailable:
        typeof chrome !== 'undefined' && typeof chrome.runtime !== 'undefined'
          ? typeof chrome.runtime.sendNativeMessage === 'function'
          : false,
    };
  }

  /**
   * Validate inputs
   */
  private validateInputs(extensionId: string, binaryPath: string): void {
    if (!extensionId || extensionId.length === 0) {
      throw new Error('Extension ID cannot be empty');
    }

    if (!binaryPath || binaryPath.length === 0) {
      throw new Error('Binary path cannot be empty');
    }

    // Basic extension ID format check (should be 32 alphanumeric characters)
    if (!/^[a-z]{32}$/.test(extensionId)) {
      console.warn('Warning: Extension ID format looks incorrect. Should be 32 lowercase letters.');
    }
  }
}

/**
 * Export singleton instance for convenience
 */
export const nativeMessagingSetup = new NativeMessagingSetup();
