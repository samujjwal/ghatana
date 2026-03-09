/**
 * Policy Test Fixtures
 *
 * Provides realistic test data for parental control policies across all policy types.
 *
 * <p><b>Purpose</b><br>
 * Comprehensive test data builders for policy entities covering all Guardian policy types:
 * app blocking, website blocking, schedules, screen time limits, and category-based controls.
 * Enables consistent policy testing without duplicating complex configuration objects.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { appBlockPolicy, schedulePolicy, createRandomPolicy } from './policy.fixtures';
 *
 * const policy = await createTestPolicy(appBlockPolicy, childId);
 * const bedtime = schedulePolicy;
 * const randomPolicy = createRandomPolicy('daily-limit', childId);
 * }</pre>
 *
 * <p><b>Policy Types Supported</b><br>
 * - <b>app</b>: Block/allow specific applications by package name
 * - <b>website</b>: Block/allow websites by URL or pattern matching
 * - <b>schedule</b>: Time-based restrictions (e.g., bedtime, study time)
 * - <b>daily-limit</b>: Screen time quotas with daily reset
 * - <b>category</b>: Block content by category (adult, gambling, violence, etc.)
 *
 * <p><b>Test Coverage</b><br>
 * Used by:
 * - PolicyService tests (CRUD operations, policy enforcement)
 * - BlockService tests (policy evaluation and blocking)
 * - UsageService tests (policy-based usage tracking)
 * - ReportsService tests (policy compliance reporting)
 * - WebSocket tests (real-time policy updates)
 *
 * @doc.type fixtures
 * @doc.purpose Test data builders for all parental control policy types
 * @doc.layer backend
 * @doc.pattern Test Factory
 */

import { randomString } from "../setup";

export interface PolicyFixture {
  id?: string;
  userId?: string;
  childId?: string;
  deviceId?: string;
  name: string;
  policyType: "app" | "website" | "schedule" | "daily-limit" | "category";
  enabled: boolean;
  config: any;
  createdAt?: Date;
  updatedAt?: Date;
}

/**
 * App-based blocking policy
 */
export const appBlockPolicy: PolicyFixture = {
  name: "Block Social Media Apps",
  policyType: "app",
  enabled: true,
  config: {
    blockedApps: [
      "com.instagram.android",
      "com.snapchat.android",
      "com.tiktok",
    ],
    action: "block",
  },
};

/**
 * Website-based blocking policy
 */
export const websiteBlockPolicy: PolicyFixture = {
  name: "Block Gaming Websites",
  policyType: "website",
  enabled: true,
  config: {
    blockedUrls: ["roblox.com", "minecraft.net", "fortnite.com"],
    blockPatterns: ["*://*.game.com/*", "*://games.*.com/*"],
    action: "block",
  },
};

/**
 * Schedule-based policy (bedtime)
 */
export const schedulePolicy: PolicyFixture = {
  name: "Bedtime Schedule",
  policyType: "schedule",
  enabled: true,
  config: {
    schedule: {
      monday: { start: "21:00", end: "07:00" },
      tuesday: { start: "21:00", end: "07:00" },
      wednesday: { start: "21:00", end: "07:00" },
      thursday: { start: "21:00", end: "07:00" },
      friday: { start: "22:00", end: "08:00" },
      saturday: { start: "23:00", end: "09:00" },
      sunday: { start: "22:00", end: "07:00" },
    },
    action: "block",
  },
};

/**
 * Daily limit policy (screen time)
 */
export const dailyLimitPolicy: PolicyFixture = {
  name: "Daily Screen Time Limit",
  policyType: "daily-limit",
  enabled: true,
  config: {
    maxDailyMinutes: 120, // 2 hours
    resetTime: "00:00",
    action: "block",
    warningThresholds: [90, 110], // Warn at 90 and 110 minutes
  },
};

/**
 * Category-based blocking policy
 */
export const categoryBlockPolicy: PolicyFixture = {
  name: "Block Adult Content",
  policyType: "category",
  enabled: true,
  config: {
    blockedCategories: ["adult", "gambling", "violence"],
    action: "block",
  },
};

/**
 * Study time policy (allow educational apps only)
 */
export const studyTimePolicy: PolicyFixture = {
  name: "Study Time",
  policyType: "schedule",
  enabled: true,
  config: {
    schedule: {
      monday: { start: "16:00", end: "18:00" },
      tuesday: { start: "16:00", end: "18:00" },
      wednesday: { start: "16:00", end: "18:00" },
      thursday: { start: "16:00", end: "18:00" },
      friday: { start: "16:00", end: "18:00" },
    },
    allowedApps: ["com.google.classroom", "com.khanacademy.android"],
    allowedCategories: ["education"],
    action: "allow-only",
  },
};

/**
 * Weekend relaxed policy
 */
export const weekendPolicy: PolicyFixture = {
  name: "Weekend Extended Hours",
  policyType: "daily-limit",
  enabled: true,
  config: {
    maxDailyMinutes: 240, // 4 hours on weekends
    resetTime: "00:00",
    daysOfWeek: ["saturday", "sunday"],
    action: "block",
  },
};

/**
 * Generate a random policy
 */
export function createRandomPolicy(
  type: PolicyFixture["policyType"] = "app",
  userId?: string,
  childId?: string
): PolicyFixture {
  const configs = {
    app: {
      blockedApps: [`com.random.app${randomString(5)}`],
      action: "block",
    },
    website: {
      blockedUrls: [`${randomString(8)}.com`],
      action: "block",
    },
    schedule: {
      schedule: {
        monday: { start: "20:00", end: "08:00" },
      },
      action: "block",
    },
    "daily-limit": {
      maxDailyMinutes: 60 + Math.floor(Math.random() * 180),
      resetTime: "00:00",
      action: "block",
    },
    category: {
      blockedCategories: ["social-media"],
      action: "block",
    },
  };

  return {
    userId,
    childId,
    name: `Test Policy ${randomString(5)}`,
    policyType: type,
    enabled: true,
    config: configs[type],
  };
}

export const policyFixtures = {
  appBlock: appBlockPolicy,
  websiteBlock: websiteBlockPolicy,
  schedule: schedulePolicy,
  dailyLimit: dailyLimitPolicy,
  categoryBlock: categoryBlockPolicy,
  studyTime: studyTimePolicy,
  weekend: weekendPolicy,
  createRandom: createRandomPolicy,
};
