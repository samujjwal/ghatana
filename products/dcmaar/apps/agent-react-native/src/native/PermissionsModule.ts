/**
 * PermissionsModule - React Native Native Module
 *
 * Bridges native Android/iOS permission handling with React Native.
 * Manages permission requests, grants, and revocations.
 *
 * Platform Support:
 * - Android 6.0+ (API 23): Runtime permissions
 * - iOS 14+: Permission prompts
 *
 * @mock This is a mock implementation for development/testing.
 * Production implementation bridges to native code.
 */

/**
 * Permission Request Configuration
 */
export interface PermissionRequest {
  permission: string;
  reason?: string;
  onGranted?: () => void;
  onDenied?: () => void;
}

/**
 * Permission Grant Result
 */
export interface PermissionGrant {
  permission: string;
  granted: boolean;
  timestamp: number;
  reason?: string;
}

/**
 * Permission Revoke Result
 */
export interface PermissionRevoke {
  permission: string;
  revoked: boolean;
  timestamp: number;
}

/**
 * Permission Status Check Result
 */
export interface PermissionStatusResult {
  usageStats?: {
    granted: boolean;
    timestamp: number;
  };
  accessibility?: {
    granted: boolean;
    timestamp: number;
  };
  devAdmin?: {
    granted: boolean;
    timestamp: number;
  };
}

/**
 * Mock PermissionsModule Implementation
 * In production, this bridges to native permission APIs
 */
class PermissionsModuleImpl {
  private grantedPermissions: Set<string> = new Set();
  private permissionHistory: Map<string, { granted: boolean; timestamp: number }> = new Map();

  /**
   * Initialize permissions module
   */
  async initialize(): Promise<void> {
    console.log('[PermissionsModule] Initializing...');
    // In production: Check device OS, initialize native bridge
    this.grantedPermissions.clear();
    return Promise.resolve();
  }

  /**
   * Request a permission from the user
   *
   * @param permission - Permission string (e.g., 'USAGE_STATS', 'ACCESSIBILITY')
   * @returns Result with granted status
   */
  async requestPermission(permission: string): Promise<PermissionGrant> {
    console.log('[PermissionsModule] Requesting permission:', permission);

    const timestamp = Date.now();

    // Mock: Simulate user granting most permissions (80% success)
    const granted = Math.random() > 0.2;

    if (granted) {
      this.grantedPermissions.add(permission);
    }

    const result: PermissionGrant = {
      permission,
      granted,
      timestamp,
      reason: granted ? 'User granted' : 'User denied',
    };

    // Store in history
    this.permissionHistory.set(permission, {
      granted,
      timestamp,
    });

    console.log('[PermissionsModule] Permission request result:', result);
    return Promise.resolve(result);
  }

  /**
   * Request multiple permissions at once
   *
   * @param permissions - Array of permission strings
   * @returns Array of permission grant results
   */
  async requestPermissions(permissions: string[]): Promise<PermissionGrant[]> {
    console.log('[PermissionsModule] Requesting permissions:', permissions);

    const results = await Promise.all(
      permissions.map((permission) => this.requestPermission(permission))
    );

    return Promise.resolve(results);
  }

  /**
   * Check if a specific permission is granted
   *
   * @param permission - Permission string
   * @returns True if permission is granted
   */
  async checkPermissionStatus(permission?: string): Promise<PermissionStatusResult> {
    console.log('[PermissionsModule] Checking permission status:', permission);

    const status: PermissionStatusResult = {
      usageStats: {
        granted: this.grantedPermissions.has('USAGE_STATS'),
        timestamp: this.permissionHistory.get('USAGE_STATS')?.timestamp || 0,
      },
      accessibility: {
        granted: this.grantedPermissions.has('ACCESSIBILITY'),
        timestamp: this.permissionHistory.get('ACCESSIBILITY')?.timestamp || 0,
      },
      devAdmin: {
        granted: this.grantedPermissions.has('DEVICE_ADMIN'),
        timestamp: this.permissionHistory.get('DEVICE_ADMIN')?.timestamp || 0,
      },
    };

    return Promise.resolve(status);
  }

  /**
   * Check if accessibility permission is granted
   *
   * @returns True if accessibility service is enabled
   */
  async isAccessibilityServiceEnabled(): Promise<boolean> {
    const status = this.grantedPermissions.has('ACCESSIBILITY');
    console.log('[PermissionsModule] Accessibility service enabled:', status);
    return Promise.resolve(status);
  }

  /**
   * Check if usage stats permission is granted
   *
   * @returns True if app can read usage stats
   */
  async hasUsageStatsPermission(): Promise<boolean> {
    const status = this.grantedPermissions.has('USAGE_STATS');
    console.log('[PermissionsModule] Usage stats permission:', status);
    return Promise.resolve(status);
  }

  /**
   * Check if device admin is activated
   *
   * @returns True if device admin is active
   */
  async isDeviceAdminActive(): Promise<boolean> {
    const status = this.grantedPermissions.has('DEVICE_ADMIN');
    console.log('[PermissionsModule] Device admin active:', status);
    return Promise.resolve(status);
  }

  /**
   * Revoke a permission
   *
   * @param permission - Permission string to revoke
   * @returns Result with revoke status
   */
  async revokePermission(permission: string): Promise<PermissionRevoke> {
    console.log('[PermissionsModule] Revoking permission:', permission);

    this.grantedPermissions.delete(permission);
    this.permissionHistory.set(permission, {
      granted: false,
      timestamp: Date.now(),
    });

    const result: PermissionRevoke = {
      permission,
      revoked: true,
      timestamp: Date.now(),
    };

    console.log('[PermissionsModule] Permission revoked:', result);
    return Promise.resolve(result);
  }

  /**
   * Get all granted permissions
   *
   * @returns Array of granted permission strings
   */
  async getGrantedPermissions(): Promise<string[]> {
    const permissions = Array.from(this.grantedPermissions);
    console.log('[PermissionsModule] Granted permissions:', permissions);
    return Promise.resolve(permissions);
  }

  /**
   * Get permission history
   *
   * @returns History of all permission requests
   */
  async getPermissionHistory(): Promise<Map<string, { granted: boolean; timestamp: number }>> {
    console.log('[PermissionsModule] Permission history:', this.permissionHistory);
    return Promise.resolve(this.permissionHistory);
  }

  /**
   * Open system settings for a specific permission
   *
   * @param permission - Permission to open settings for
   */
  async openSettings(permission: string): Promise<void> {
    console.log('[PermissionsModule] Opening settings for:', permission);
    // In production: Open native settings activity/screen
    return Promise.resolve();
  }

  /**
   * Request all required permissions at once
   * Convenience method for onboarding flow
   */
  async requestAllRequired(): Promise<PermissionGrant[]> {
    const requiredPermissions = ['USAGE_STATS', 'ACCESSIBILITY'];
    console.log('[PermissionsModule] Requesting all required permissions...');
    return this.requestPermissions(requiredPermissions);
  }

  /**
   * Cleanup module
   */
  async cleanup(): Promise<void> {
    console.log('[PermissionsModule] Cleaning up...');
    this.grantedPermissions.clear();
    this.permissionHistory.clear();
    return Promise.resolve();
  }
}

// Export singleton instance
export const PermissionsModule = new PermissionsModuleImpl();
