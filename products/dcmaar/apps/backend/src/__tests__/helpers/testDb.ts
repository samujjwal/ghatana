/**
 * Test Database Helper Utilities
 *
 * Provides comprehensive utilities for seeding test data while respecting
 * foreign key constraints and entity relationships.
 *
 * <p><b>Purpose</b><br>
 * Centralizes database seeding logic to ensure test data is created in the correct
 * order with proper relationships. Eliminates foreign key constraint errors by
 * guaranteeing parent entities exist before child entities are created.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { seedUser, seedChild, seedDevice } from './helpers/testDb';
 * import { parentUser, childProfile, androidDevice } from './fixtures';
 *
 * // Create parent user first
 * const userId = await seedUser(parentUser);
 *
 * // Create child linked to parent
 * const childId = await seedChild(childProfile, userId);
 *
 * // Create device linked to child
 * const deviceId = await seedDevice(androidDevice, childId);
 * }</pre>
 *
 * <p><b>Entity Relationships</b><br>
 * Guardian uses the following entity hierarchy:
 * <pre>
 * users (parent entity)
 *   └── children (linked via parentId)
 *   └── devices (linked via childId, not directly to user)
 *   └── policies (linked via childId, applied to child's devices)
 *   └── sessions (linked to devices and usage tracking)
 * </pre>
 *
 * <p><b>Fixture Loading Order</b><br>
 * Tests MUST follow this order to avoid foreign key violations:
 * 1. User (parent) - no dependencies
 * 2. Child - depends on user.id
 * 3. Device - depends on user.id (required) and optionally child.id
 * 4. Policy - depends on user.id and child.id
 * 5. Usage Session - depends on device.id
 * 6. Block Event - depends on device.id
 *
 * <p><b>Thread Safety</b><br>
 * All functions are thread-unsafe and intended for single-threaded test execution.
 * Test data is isolated per test case via beforeEach/afterEach cleanup.
 *
 * <p><b>Clean Up</b><br>
 * Database cleanup is handled automatically in setup.ts afterEach hook.
 * Individual tests should not need to manually clean up.
 *
 * @doc.type test-utility
 * @doc.purpose Database seeding helpers for test fixture creation
 * @doc.layer backend
 * @doc.pattern Test Factory
 */

import { Pool } from "pg";
import { randomUUID } from "crypto";
import { initTestDatabase } from "../setup";

/**
 * Retry configuration for handling transient deadlocks
 */
const RETRY_CONFIG = {
  maxRetries: 3,
  baseDelayMs: 50,
  maxDelayMs: 500,
  backoffMultiplier: 2,
};

/**
 * Retry helper with exponential backoff for handling transient deadlocks
 */
async function retryWithBackoff<T>(
  operation: () => Promise<T>,
  operationName: string
): Promise<T> {
  let lastError: any;
  let delay = RETRY_CONFIG.baseDelayMs;

  for (let attempt = 1; attempt <= RETRY_CONFIG.maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error: any) {
      lastError = error;

      // Only retry on deadlock or serialization errors
      if (
        !error.message?.includes("deadlock") &&
        !error.message?.includes("serialization")
      ) {
        throw error;
      }

      if (attempt < RETRY_CONFIG.maxRetries) {
        await new Promise((resolve) => setTimeout(resolve, delay));
        delay = Math.min(
          delay * RETRY_CONFIG.backoffMultiplier,
          RETRY_CONFIG.maxDelayMs
        );
      }
    }
  }

  throw new Error(
    `${operationName} failed after ${RETRY_CONFIG.maxRetries} retries: ${lastError.message}`
  );
}
interface SeededUser {
  id: string;
  email: string;
  password: string;
  displayName: string;
  role: "parent" | "child" | "admin";
}

/**
 * Interface for seeded child data
 */
interface SeededChild {
  id: string;
  userId: string;
  name: string;
  age: number;
  birthDate: string;
  avatarUrl?: string;
}

/**
 * Interface for seeded device data
 */
interface SeededDevice {
  id: string;
  userId: string;
  childId?: string;
  deviceName: string;
  deviceType: "mobile" | "desktop" | "browser";
  osType: string;
  osVersion: string;
  deviceFingerprint: string;
  pairingCode?: string;
  status: "online" | "offline";
}

/**
 * Interface for seeded policy data
 */
interface SeededPolicy {
  id: string;
  userId: string;
  childId: string;
  deviceId?: string;
  name: string;
  policyType: "app" | "website" | "schedule" | "daily-limit" | "category";
  enabled: boolean;
  config: Record<string, any>;
}

/**
 * Seed a user into the test database.
 *
 * Creates a new user record with the provided data. The user ID is generated
 * automatically if not provided in the fixture.
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const userId = await seedUser({ email: 'parent@example.com', password: '...', ... });
 * }</pre>
 *
 * @param fixture User fixture data (email, password, displayName, role)
 * @returns Promise resolving to the created user's ID
 * @throws Error if user creation fails (e.g., duplicate email, database error)
 */
export async function seedUser(fixture: any): Promise<string> {
  const pool = await initTestDatabase();
  const userId = fixture.id || randomUUID();

  // Skip if using mock pool (database not available)
  if ((pool as any).query?.mock) {
    return userId;
  }

  return retryWithBackoff(async () => {
    await pool.query(
      `INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
         VALUES ($1, $2, $3, $4, NOW(), NOW())`,
      [
        userId,
        fixture.email,
        fixture.password, // fixture already contains the password/hash
        fixture.displayName,
      ]
    );
    return userId;
  }, `seedUser (${fixture.email})`);
}

/**
 * Seed a child profile into the test database.
 *
 * Creates a new child record linked to a parent user. The parent user must
 * already exist in the database (created via seedUser).
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const childId = await seedChild(
 *   { name: 'Test Child', dateOfBirth: '2010-05-15' },
 *   parentUserId
 * );
 * }</pre>
 *
 * @param fixture Child fixture data (name, dateOfBirth)
 * @param parentId ID of the parent user (must exist)
 * @returns Promise resolving to the created child's ID
 * @throws Error if parent doesn't exist or child creation fails
 */
export async function seedChild(fixture: any, userId: string): Promise<string> {
  const pool = await initTestDatabase();
  const childId = fixture.id || randomUUID();

  // Skip if using mock pool (database not available)
  if ((pool as any).query?.mock) {
    return childId;
  }

  return retryWithBackoff(async () => {
    await pool.query(
      `INSERT INTO children (id, user_id, name, age, birth_date, avatar_url, created_at, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, NOW(), NOW())`,
      [
        childId,
        userId,
        fixture.name,
        fixture.age || 0,
        fixture.birthDate,
        fixture.avatarUrl || null,
      ]
    );
    return childId;
  }, `seedChild (${fixture.name})`);
}

/**
 * Seed a device into the test database.
 *
 * Creates a new device record linked to a user and optionally to a child profile.
 * The user must already exist in the database (created via seedUser).
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const deviceId = await seedDevice(androidDevice, userId, childId);
 * }</pre>
 *
 * @param fixture Device fixture data (deviceName, deviceType, osType, osVersion, deviceFingerprint, pairingCode)
 * @param userId ID of the user who owns this device (must exist)
 * @param childId Optional ID of the child this device is linked to
 * @returns Promise resolving to the created device's ID
 * @throws Error if user doesn't exist or device creation fails
 */
export async function seedDevice(
  fixture: any,
  userId: string,
  childId?: string
): Promise<string> {
  const pool = await initTestDatabase();
  const deviceId = fixture.id || randomUUID();

  // Skip if using mock pool (database not available)
  if ((pool as any).query?.mock) {
    return deviceId;
  }

  return retryWithBackoff(async () => {
    await pool.query(
      `INSERT INTO devices (
          id, user_id, child_id, device_name, device_type, os_type, os_version, 
          device_fingerprint, pairing_code, status, created_at, updated_at
         ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NOW(), NOW())`,
      [
        deviceId,
        userId,
        childId || null,
        fixture.deviceName,
        fixture.deviceType,
        fixture.osType,
        fixture.osVersion,
        fixture.deviceFingerprint,
        fixture.pairingCode || null,
        fixture.status || "offline",
      ]
    );
    return deviceId;
  }, `seedDevice (${fixture.deviceName})`);
}

/**
 * Seed a policy into the test database.
 *
 * Creates a new policy record linked to a user and child profile. The user and child
 * must already exist in the database.
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const policyId = await seedPolicy(appBlockPolicy, userId, childId);
 * }</pre>
 *
 * @param fixture Policy fixture data (name, policyType, enabled, config)
 * @param userId ID of the user who owns this policy (must exist)
 * @param childId ID of the child this policy is applied to (must exist)
 * @returns Promise resolving to the created policy's ID
 * @throws Error if user or child doesn't exist or policy creation fails
 */
export async function seedPolicy(
  fixture: any,
  userId: string,
  childId: string
): Promise<string> {
  const pool = await initTestDatabase();
  const policyId = fixture.id || randomUUID();

  // Skip if using mock pool (database not available)
  if ((pool as any).query?.mock) {
    return policyId;
  }

  return retryWithBackoff(async () => {
    await pool.query(
      `INSERT INTO policies (
          id, user_id, child_id, device_id, name, policy_type, enabled, config, created_at, updated_at
         ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW(), NOW())`,
      [
        policyId,
        userId,
        childId,
        fixture.deviceId || null,
        fixture.name,
        fixture.policyType,
        fixture.enabled,
        JSON.stringify(fixture.config || fixture.configuration || {}),
      ]
    );
    return policyId;
  }, `seedPolicy (${fixture.name})`);
}

/**
 * Seed a usage session into the test database.
 *
 * Creates a new usage session record linked to a device. The device must
 * already exist in the database (created via seedDevice).
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const sessionId = await seedUsageSession(
 *   {
 *     itemName: 'com.instagram.android',
 *     category: 'social-media',
 *     startTime: new Date(),
 *     durationSeconds: 1200, // 20 minutes in seconds
 *   },
 *   deviceId
 * );
 * }</pre>
 *
 * @param fixture Usage session fixture data (itemName, category, startTime, durationSeconds, sessionType)
 * @param deviceId ID of the device this session occurred on (must exist)
 * @returns Promise resolving to the created session's ID
 * @throws Error if device doesn't exist or session creation fails
 */
export async function seedUsageSession(
  fixture: any,
  deviceId: string
): Promise<string> {
  const pool = await initTestDatabase();
  const sessionId = fixture.id || randomUUID();

  // Skip if using mock pool (database not available)
  if ((pool as any).query?.mock) {
    return sessionId;
  }

  return retryWithBackoff(async () => {
    await pool.query(
      `INSERT INTO usage_sessions (
          id, device_id, session_type, item_name, category, start_time, end_time, duration_seconds, created_at
         ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())`,
      [
        sessionId,
        deviceId,
        fixture.sessionType || "app",
        fixture.itemName || null,
        fixture.category || null,
        fixture.startTime || new Date(),
        fixture.endTime || new Date(),
        fixture.durationSeconds || 0,
      ]
    );
    return sessionId;
  }, `seedUsageSession (${deviceId})`);
}

/**
 * Seed a block event into the test database.
 *
 * Creates a new block event record linked to a device. The device must
 * already exist in the database (created via seedDevice).
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const blockId = await seedBlockEvent(
 *   {
 *     blockedItem: 'com.tiktok',
 *     eventType: 'app_blocked',
 *     category: 'social-media',
 *     policyId: policyId,
 *     timestamp: new Date(),
 *   },
 *   deviceId
 * );
 * }</pre>
 *
 * @param fixture Block event fixture data (blockedItem, eventType, category, policyId, timestamp)
 * @param deviceId ID of the device on which this block occurred (must exist)
 * @returns Promise resolving to the created block event's ID
 * @throws Error if device doesn't exist or block event creation fails
 */
export async function seedBlockEvent(
  fixture: any,
  deviceId: string
): Promise<string> {
  const pool = await initTestDatabase();
  const blockId = fixture.id || randomUUID();

  // Skip if using mock pool (database not available)
  if ((pool as any).query?.mock) {
    return blockId;
  }

  return retryWithBackoff(async () => {
    await pool.query(
      `INSERT INTO block_events (
          id, device_id, policy_id, event_type, blocked_item, category, timestamp
         ) VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [
        blockId,
        deviceId,
        fixture.policyId || null,
        fixture.eventType || "blocked",
        fixture.blockedItem || null,
        fixture.category || null,
        fixture.timestamp || new Date(),
      ]
    );
    return blockId;
  }, `seedBlockEvent (${deviceId})`);
}

/**
 * Get count of seeded entities in the test database.
 *
 * Useful for debugging and verifying test data setup.
 *
 * @returns Promise resolving to object with entity counts
 */
export async function getSeededEntityCounts(): Promise<{
  users: number;
  children: number;
  devices: number;
  policies: number;
  usageSessions: number;
  blockEvents: number;
}> {
  const pool = await initTestDatabase();

  // Skip if using mock pool
  if ((pool as any).query?.mock) {
    return {
      users: 0,
      children: 0,
      devices: 0,
      policies: 0,
      usageSessions: 0,
      blockEvents: 0,
    };
  }

  const queries = [
    { name: "users", query: "SELECT COUNT(*) FROM users" },
    { name: "children", query: "SELECT COUNT(*) FROM children" },
    { name: "devices", query: "SELECT COUNT(*) FROM devices" },
    { name: "policies", query: "SELECT COUNT(*) FROM policies" },
    { name: "usageSessions", query: "SELECT COUNT(*) FROM usage_sessions" },
    { name: "blockEvents", query: "SELECT COUNT(*) FROM block_events" },
  ];

  const result: any = {};

  for (const { name, query } of queries) {
    try {
      const { rows } = await pool.query(query);
      result[name] = parseInt(rows[0].count, 10);
    } catch (error) {
      result[name] = 0;
    }
  }

  return result;
}

/**
 * Test database utilities export
 */
export const testDb = {
  seedUser,
  seedChild,
  seedDevice,
  seedPolicy,
  seedUsageSession,
  seedBlockEvent,
  getSeededEntityCounts,
};
