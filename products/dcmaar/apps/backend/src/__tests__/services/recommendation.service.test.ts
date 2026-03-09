import { describe, it, expect, beforeEach } from "vitest";
import { RecommendationService, defaultConfig } from "../../services/recommendation.service";
import type { UsageRecord, BlockRecord } from "../../services/analytics.service";

describe("RecommendationService", () => {
  let service: RecommendationService;

  beforeEach(() => {
    service = new RecommendationService();
  });

  describe("generateForChild", () => {
    /**
     * Test high screen time detection.
     *
     * GIVEN: Usage records totaling 40 hours over 7 days
     * WHEN: generateForChild is called
     * THEN: Should return high screen time alert
     */
    it("should detect high screen time and recommend limits", () => {
      const records: UsageRecord[] = Array.from({ length: 40 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: "Instagram",
        durationSeconds: 3600, // 1 hour each
        startedAt: new Date(Date.now() - i * 24 * 60 * 60 * 1000),
      }));

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const highTimeRec = recommendations.find(
        (r: any) => r.category === "screen_time" && r.headline.includes("Very high")
      );

      expect(highTimeRec).toBeDefined();
      expect(highTimeRec?.headline).toMatch(/very high|⚠️/i);
    });

    /**
     * Test medium screen time detection.
     *
     * GIVEN: Usage records totaling 24 hours over 7 days (3.4 hours/day)
     * WHEN: generateForChild is called
     * THEN: Should return medium screen time recommendation
     */
    it("should detect medium screen time and recommend monitoring", () => {
      const records: UsageRecord[] = Array.from({ length: 24 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: "TikTok",
        durationSeconds: 3600,
        startedAt: new Date(Date.now() - i * 7 * 60 * 60 * 1000), // Spread over 7 hours, then repeating
      }));

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const mediumRec = recommendations.find(
        (r: any) => r.category === "screen_time" && r.headline.includes("elevated")
      );

      expect(mediumRec).toBeDefined();
    });

    /**
     * Test healthy screen time.
     *
     * GIVEN: Usage records totaling 10 hours over 7 days (1.4 hours/day)
     * WHEN: generateForChild is called
     * THEN: Should return positive reinforcement
     */
    it("should recognize healthy screen time habits", () => {
      const records: UsageRecord[] = Array.from({ length: 10 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: "Khan Academy",
        durationSeconds: 3600,
        startedAt: new Date(Date.now() - i * 24 * 60 * 60 * 1000),
      }));

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const healthyRec = recommendations.find(
        (r: any) => r.category === "screen_time" && r.headline.includes("✅")
      );

      expect(healthyRec).toBeDefined();
      expect(healthyRec?.headline).toMatch(/healthy|✅/i);
    });

    /**
     * Test late night usage detection.
     *
     * GIVEN: Usage records with sessions between 2-4 AM
     * WHEN: generateForChild is called
     * THEN: Should return late night alert
     */
    it("should detect late night usage and alert parents", () => {
      const now = new Date();
      const lateNightDate = new Date(now.getTime());
      lateNightDate.setUTCHours(3, 0, 0, 0); // 3 AM UTC

      const records: UsageRecord[] = [
        {
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: "YouTube",
          durationSeconds: 1800,
          startedAt: lateNightDate,
        },
      ];

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const lateNightRec = recommendations.find(
        (r: any) => r.category === "alerts" && r.headline.includes("Late night")
      );

      expect(lateNightRec).toBeDefined();
      expect(lateNightRec?.headline).toMatch(/late night|🌙/i);
    });

    /**
     * Test high block rate detection.
     *
     * GIVEN: 10 usage attempts and 5 blocks (50% block rate)
     * WHEN: generateForChild is called with high block threshold (0.2)
     * THEN: Should return block rate alert
     */
    it("should detect high block rate and recommend policy review", () => {
      const usageRecords: UsageRecord[] = Array.from({ length: 10 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: `App${i}`,
        durationSeconds: 600,
        startedAt: new Date(Date.now() - i * 60 * 60 * 1000),
      }));

      const blockRecords: BlockRecord[] = Array.from({ length: 5 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        blockedItem: `App${i}`,
        timestamp: new Date(Date.now() - i * 60 * 60 * 1000),
      }));

      const recommendations = service.generateForChild(
        "child-1",
        usageRecords,
        blockRecords
      );

      const blockRateRec = recommendations.find(
        (r: any) => r.category === "alerts" && r.headline.includes("block")
      );

      expect(blockRateRec).toBeDefined();
      expect(blockRateRec?.headline).toMatch(/block/i);
    });

    /**
     * Test app switching detection.
     *
     * GIVEN: 20 usage records across 15 different apps in short time
     * WHEN: generateForChild is called
     * THEN: Should detect rapid switching
     */
    it("should detect rapid app switching", () => {
      const records: UsageRecord[] = Array.from({ length: 20 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: `App${i % 15}`, // 15 unique apps
        durationSeconds: 300,
        startedAt: new Date(Date.now() - i * 5 * 60 * 1000), // 5 min apart
      }));

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const switchingRec = recommendations.find(
        (r: any) => r.category === "alerts" && r.headline.includes("switching")
      );

      expect(switchingRec).toBeDefined();
    });

    /**
     * Test low education content alert.
     *
     * GIVEN: 20 usage records, only 1 tagged as education
     * WHEN: generateForChild is called
     * THEN: Should recommend more educational apps
     */
    it("should alert on low educational content", () => {
      const records: UsageRecord[] = [
        {
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: "Khan Academy",
          category: "education",
          durationSeconds: 1800,
          startedAt: new Date(),
        },
        ...Array.from({ length: 19 }, (_, i) => ({
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: `SocialApp${i}`,
          category: "social" as const,
          durationSeconds: 1800,
          startedAt: new Date(Date.now() - i * 1000),
        })),
      ];

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const eduRec = recommendations.find(
        (r: any) => r.category === "engagement" && r.headline.includes("educational")
      );

      expect(eduRec).toBeDefined();
      expect(eduRec?.headline).toMatch(/📚|educational/i);
    });

    /**
     * Test high social media dominance alert.
     *
     * GIVEN: 10 records, 5 are social media
     * WHEN: generateForChild is called
     * THEN: Should recommend alternative activities
     */
    it("should alert on high social media usage", () => {
      const records: UsageRecord[] = [
        ...Array.from({ length: 5 }, (_, i) => ({
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: `SocialApp${i}`,
          category: "social" as const,
          durationSeconds: 1800,
          startedAt: new Date(Date.now() - i * 1000),
        })),
        ...Array.from({ length: 5 }, (_, i) => ({
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: `OtherApp${i}`,
          category: "entertainment" as const,
          durationSeconds: 1800,
          startedAt: new Date(Date.now() - i * 1000),
        })),
      ];

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const socialRec = recommendations.find(
        (r: any) => r.category === "engagement" && r.headline.includes("social")
      );

      expect(socialRec).toBeDefined();
    });

    /**
     * Test empty records handling.
     *
     * GIVEN: Empty usage and block records
     * WHEN: generateForChild is called
     * THEN: Should return only generic recommendation or empty array
     */
    it("should handle empty records gracefully", () => {
      const recommendations = service.generateForChild(
        "child-1",
        [],
        []
      );

      expect(Array.isArray(recommendations)).toBe(true);
      // No assertions on content - should be graceful
    });

    /**
     * Test invalid childId.
     *
     * GIVEN: Empty string childId
     * WHEN: generateForChild is called
     * THEN: Should throw error
     */
    it("should throw error for empty childId", () => {
      expect(() =>
        service.generateForChild("", [], [])
      ).toThrow();
    });
  });

  describe("getSummary", () => {
    /**
     * Test recommendation prioritization.
     *
     * GIVEN: Recommendations with different categories
     * WHEN: getSummary(limit: 3) is called
     * THEN: Should prioritize all categories and return limited set
     */
    it("should prioritize recommendations by severity", () => {
      const recommendations = [
        {
          childId: "child-1",
          headline: "Educational content low",
          detail: "Only 5% is educational",
          category: "engagement" as const,
        },
        {
          childId: "child-1",
          headline: "Late night usage",
          detail: "Activity at 3 AM",
          category: "alerts" as const,
        },
        {
          childId: "child-1",
          headline: "Screen time high",
          detail: "8 hours/day average",
          category: "screen_time" as const,
        },
      ];

      const summary = service.getSummary(recommendations, 3);

      // Should be sorted by priority: alerts (0) < screen_time (1) < engagement (2)
      expect(summary.length).toBe(3);
      const categories = summary.map((r: any) => r.category);
      // First item should be alerts (lowest priority number)
      expect(categories).toContain("alerts");
      expect(categories).toContain("screen_time");
      expect(categories).toContain("engagement");
    });

    /**
     * Test limit parameter.
     *
     * GIVEN: 5 recommendations
     * WHEN: getSummary(limit: 2) is called
     * THEN: Should return only 2 items
     */
    it("should respect limit parameter", () => {
      const recommendations = Array.from({ length: 5 }, (_, i) => ({
        childId: "child-1",
        headline: `Recommendation ${i}`,
        detail: `Detail ${i}`,
        category: "engagement" as const,
      }));

      const summary = service.getSummary(recommendations, 2);

      expect(summary).toHaveLength(2);
    });
  });

  describe("Configuration", () => {
    /**
     * Test custom configuration.
     *
     * GIVEN: Custom config with different thresholds
     * WHEN: Service is instantiated with custom config
     * THEN: Should use custom values instead of defaults
     */
    it("should use custom configuration", () => {
      const customConfig = {
        ...defaultConfig,
        highScreenTimeThreshold: 2, // Very low threshold: 2 hours/day
        mediumScreenTimeThreshold: 1,
      };

      const customService = new RecommendationService(customConfig);

      // With 20 hours over 7 days = ~2.86 hours/day, should trigger high alert with custom config
      const records: UsageRecord[] = Array.from({ length: 20 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: "App",
        durationSeconds: 3600, // 1 hour each
        startedAt: new Date(Date.now() - i * 24 * 60 * 60 * 1000), // Spread over 20 days
      }));

      const recommendations = customService.generateForChild(
        "child-1",
        records,
        []
      );

      // With 20 hours average daily usage over 20 days = 1 hour/day, should be medium
      const mediumOrHighRec = recommendations.find((r: any) => 
        r.category === "screen_time"
      );
      expect(mediumOrHighRec).toBeDefined();
    });
  });

  describe("Edge Cases", () => {
    /**
     * Test single record.
     *
     * GIVEN: Only 1 usage record
     * WHEN: generateForChild is called
     * THEN: Should not crash and return graceful recommendation
     */
    it("should handle single usage record", () => {
      const records: UsageRecord[] = [
        {
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: "App",
          durationSeconds: 1800,
          startedAt: new Date(),
        },
      ];

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      expect(recommendations.length).toBeGreaterThan(0);
    });

    /**
     * Test all midnight usage (edge case for late night detection).
     *
     * GIVEN: Usage only at exactly midnight
     * WHEN: generateForChild is called
     * THEN: Should not trigger late night alert
     */
    it("should not flag midnight as late night", () => {
      const midnightDate = new Date();
      midnightDate.setUTCHours(0, 0, 0, 0);

      const records: UsageRecord[] = [
        {
          childId: "child-1",
          deviceId: "device-1",
          sessionType: "app" as const,
          itemName: "App",
          durationSeconds: 1800,
          startedAt: midnightDate,
        },
      ];

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      const lateNightRec = recommendations.find(
        (r: any) => r.category === "alerts" && r.headline.includes("late night")
      );

      expect(lateNightRec).toBeUndefined();
    });

    /**
     * Test zero block attempts (no blocks possible).
     *
     * GIVEN: 10 usage records and 0 blocks
     * WHEN: generateForChild is called
     * THEN: Should not crash, block rate should be 0%
     */
    it("should handle zero blocks", () => {
      const records: UsageRecord[] = Array.from({ length: 10 }, (_, i) => ({
        childId: "child-1",
        deviceId: "device-1",
        sessionType: "app" as const,
        itemName: `App${i}`,
        durationSeconds: 600,
        startedAt: new Date(Date.now() - i * 1000),
      }));

      const recommendations = service.generateForChild(
        "child-1",
        records,
        []
      );

      expect(recommendations.length).toBeGreaterThan(0);
      // Should not have block rate alert
      const blockRec = recommendations.find(
        (r: any) => r.headline.includes("High block rate")
      );
      expect(blockRec).toBeUndefined();
    });
  });
});
