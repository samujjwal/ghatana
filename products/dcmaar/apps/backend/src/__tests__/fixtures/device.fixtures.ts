/**
 * Device Test Fixtures
 *
 * Provides realistic test data for devices including mobile, desktop, and browser agents.
 *
 * <p><b>Purpose</b><br>
 * Centralized test data builders for device entities across multiple platforms
 * (Android, iOS, Windows, macOS, Chrome). Enables consistent device testing
 * without duplicating platform-specific mock configurations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { androidDevice, createRandomDevice } from './device.fixtures';
 *
 * const device = await createTestDevice(androidDevice);
 * const randomMobile = createRandomDevice('mobile', childId);
 * const pairingCode = createPairingCode(device.id);
 * }</pre>
 *
 * <p><b>Test Coverage</b><br>
 * Used by:
 * - DeviceService tests (registration, pairing, status tracking)
 * - HeartbeatService tests (device online/offline detection)
 * - PolicyService tests (policy application to devices)
 * - BlockService tests (content blocking on specific devices)
 * - AnalyticsService tests (device-specific usage tracking)
 *
 * <p><b>Supported Platforms</b><br>
 * - Mobile: Android (API 28+), iOS (13+)
 * - Desktop: Windows 10+, macOS 10.15+, Linux
 * - Browser: Chrome, Firefox, Edge, Safari extensions
 *
 * @doc.type fixtures
 * @doc.purpose Test data builders for devices across mobile, desktop, and browser platforms
 * @doc.layer backend
 * @doc.pattern Test Factory
 */

import { randomString } from "../setup";

export interface DeviceFixture {
  id?: string;
  userId?: string;
  childId?: string;
  deviceName: string;
  deviceType: "mobile" | "desktop" | "browser";
  osType: string;
  osVersion: string;
  deviceFingerprint: string;
  pairingCode?: string;
  status?: "online" | "offline";
  createdAt?: Date;
  updatedAt?: Date;
}

/**
 * Mobile device fixture (Android)
 */
export const androidDevice: DeviceFixture = {
  deviceName: "Samsung Galaxy S21",
  deviceType: "mobile",
  osType: "Android",
  osVersion: "13",
  deviceFingerprint: `android-${randomString(16)}`,
  status: "offline",
};

/**
 * Mobile device fixture (iOS)
 */
export const iosDevice: DeviceFixture = {
  deviceName: "iPhone 13 Pro",
  deviceType: "mobile",
  osType: "iOS",
  osVersion: "16.5",
  deviceFingerprint: `ios-${randomString(16)}`,
  status: "offline",
};

/**
 * Desktop device fixture (Windows)
 */
export const windowsDesktop: DeviceFixture = {
  deviceName: "Work Desktop",
  deviceType: "desktop",
  osType: "Windows",
  osVersion: "11",
  deviceFingerprint: `windows-${randomString(16)}`,
  status: "offline",
};

/**
 * Desktop device fixture (macOS)
 */
export const macDesktop: DeviceFixture = {
  deviceName: "MacBook Pro",
  deviceType: "desktop",
  osType: "macOS",
  osVersion: "13.4",
  deviceFingerprint: `macos-${randomString(16)}`,
  status: "offline",
};

/**
 * Browser extension device fixture
 */
export const browserDevice: DeviceFixture = {
  deviceName: "Chrome Browser",
  deviceType: "browser",
  osType: "Chrome",
  osVersion: "114.0.5735.199",
  deviceFingerprint: `browser-${randomString(16)}`,
  status: "offline",
};

/**
 * Generate a random device fixture
 */
export function createRandomDevice(
  type: "mobile" | "desktop" | "browser" = "mobile",
  userId?: string,
  childId?: string
): DeviceFixture {
  const devices = {
    mobile: ["Android", "iOS"],
    desktop: ["Windows", "macOS", "Linux"],
    browser: ["Chrome", "Firefox", "Edge", "Safari"],
  };

  const osType =
    devices[type][Math.floor(Math.random() * devices[type].length)];

  return {
    userId,
    childId,
    deviceName: `${osType} Device ${randomString(5)}`,
    deviceType: type,
    osType,
    osVersion: `${Math.floor(Math.random() * 5) + 10}.${Math.floor(Math.random() * 10)}`,
    deviceFingerprint: `${type}-${randomString(16)}`,
    status: Math.random() > 0.5 ? "online" : "offline",
  };
}

export const deviceFixtures = {
  android: androidDevice,
  ios: iosDevice,
  windows: windowsDesktop,
  mac: macDesktop,
  browser: browserDevice,
  createRandom: createRandomDevice,
};

/**
 * Pairing code fixture
 */
export interface PairingCodeFixture {
  code: string;
  deviceId: string;
  expiresAt: Date;
  used: boolean;
}

export function createPairingCode(deviceId: string): PairingCodeFixture {
  const code = randomString(6).toUpperCase();
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000); // 15 minutes

  return {
    code,
    deviceId,
    expiresAt,
    used: false,
  };
}

export const pairingFixtures = {
  createCode: createPairingCode,
};
