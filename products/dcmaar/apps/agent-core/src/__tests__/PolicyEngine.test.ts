/**
 * PolicyEngine Tests
 */

import { PolicyEngine } from '../business/PolicyEngine';
import { Policy, PolicyAction, AppCategory } from '../types';

describe('PolicyEngine', () => {
  let engine: PolicyEngine;

  beforeEach(() => {
    engine = new PolicyEngine();
  });

  describe('Time-based policies', () => {
    it('should block app during restricted hours', () => {
      const policy: Policy = {
        id: 'policy-1',
        name: 'School Hours Block',
        enabled: true,
        targetApps: ['com.youtube'],
        targetCategories: [],
        timeWindows: [
          {
            daysOfWeek: [1, 2, 3, 4, 5], // Monday-Friday
            startMinutes: 480, // 8:00 AM
            endMinutes: 900, // 3:00 PM
            isBlocked: true
          }
        ],
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Test at 10 AM on Monday (should block)
      const monday10am = new Date('2025-11-03T10:00:00');
      const result = engine.evaluatePolicy('com.youtube', monday10am, 0);
      
      expect(result.action).toBe(PolicyAction.BLOCK);
      expect(result.reason).toContain('restricted hours');
    });

    it('should allow app outside restricted hours', () => {
      const policy: Policy = {
        id: 'policy-1',
        name: 'School Hours Block',
        enabled: true,
        targetApps: ['com.youtube'],
        targetCategories: [],
        timeWindows: [
          {
            daysOfWeek: [1, 2, 3, 4, 5],
            startMinutes: 480,
            endMinutes: 900,
            isBlocked: true
          }
        ],
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Test at 4 PM on Monday (should allow)
      const monday4pm = new Date('2025-11-03T16:00:00');
      const result = engine.evaluatePolicy('com.youtube', monday4pm, 0);
      
      expect(result.action).toBe(PolicyAction.ALLOW);
    });

    it('should allow app on weekends when policy is weekday-only', () => {
      const policy: Policy = {
        id: 'policy-1',
        name: 'Weekday Block',
        enabled: true,
        targetApps: ['com.youtube'],
        targetCategories: [],
        timeWindows: [
          {
            daysOfWeek: [1, 2, 3, 4, 5], // Monday-Friday only
            startMinutes: 0,
            endMinutes: 1439,
            isBlocked: true
          }
        ],
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Test on Saturday
      const saturday = new Date('2025-11-01T10:00:00'); // Saturday
      const result = engine.evaluatePolicy('com.youtube', saturday, 0);
      
      expect(result.action).toBe(PolicyAction.ALLOW);
    });
  });

  describe('Daily limit policies', () => {
    it('should block app when daily limit is reached', () => {
      const policy: Policy = {
        id: 'policy-2',
        name: 'Daily Limit',
        enabled: true,
        targetApps: ['com.tiktok'],
        targetCategories: [],
        dailyLimitMs: 60 * 60 * 1000, // 1 hour
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Already used 1 hour today
      const usageToday = 60 * 60 * 1000;
      const result = engine.evaluatePolicy('com.tiktok', new Date(), usageToday);
      
      expect(result.action).toBe(PolicyAction.BLOCK);
      expect(result.reason).toContain('Daily limit reached');
    });

    it('should allow app when under daily limit', () => {
      const policy: Policy = {
        id: 'policy-2',
        name: 'Daily Limit',
        enabled: true,
        targetApps: ['com.tiktok'],
        targetCategories: [],
        dailyLimitMs: 60 * 60 * 1000, // 1 hour
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Used 30 minutes
      const usageToday = 30 * 60 * 1000;
      const result = engine.evaluatePolicy('com.tiktok', new Date(), usageToday);
      
      expect(result.action).toBe(PolicyAction.ALLOW);
    });
  });

  describe('Category-based policies', () => {
    it('should block apps in blocked categories', () => {
      const policy: Policy = {
        id: 'policy-3',
        name: 'Block Social Media',
        enabled: true,
        targetApps: [],
        targetCategories: [AppCategory.SOCIAL],
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Test known social media app
      const result = engine.evaluatePolicy('com.facebook', new Date(), 0);
      
      expect(result.action).toBe(PolicyAction.BLOCK);
      expect(result.reason).toContain('social');
    });

    it('should allow apps not in blocked categories', () => {
      const policy: Policy = {
        id: 'policy-3',
        name: 'Block Social Media',
        enabled: true,
        targetApps: [],
        targetCategories: [AppCategory.SOCIAL],
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      // Test education app
      const result = engine.evaluatePolicy('com.khanacademy', new Date(), 0);
      
      expect(result.action).toBe(PolicyAction.ALLOW);
    });
  });

  describe('No policies', () => {
    it('should allow all apps when no policies exist', () => {
      engine.loadPolicies([]);

      const result = engine.evaluatePolicy('com.any.app', new Date(), 0);
      
      expect(result.action).toBe(PolicyAction.ALLOW);
    });
  });

  describe('Disabled policies', () => {
    it('should not enforce disabled policies', () => {
      const policy: Policy = {
        id: 'policy-4',
        name: 'Disabled Policy',
        enabled: false, // DISABLED
        targetApps: ['com.youtube'],
        targetCategories: [],
        timeWindows: [
          {
            daysOfWeek: [0, 1, 2, 3, 4, 5, 6],
            startMinutes: 0,
            endMinutes: 1439,
            isBlocked: true
          }
        ],
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      engine.loadPolicies([policy]);

      const result = engine.evaluatePolicy('com.youtube', new Date(), 0);
      
      expect(result.action).toBe(PolicyAction.ALLOW);
    });
  });

  describe('Multiple policies', () => {
    it('should block if any policy blocks (most restrictive wins)', () => {
      const policies: Policy[] = [
        {
          id: 'policy-5',
          name: 'Allow with limit',
          enabled: true,
          targetApps: ['com.youtube'],
          targetCategories: [],
          dailyLimitMs: 2 * 60 * 60 * 1000, // 2 hours
          createdAt: Date.now(),
          updatedAt: Date.now()
        },
        {
          id: 'policy-6',
          name: 'Block during school',
          enabled: true,
          targetApps: ['com.youtube'],
          targetCategories: [],
          timeWindows: [
            {
              daysOfWeek: [1, 2, 3, 4, 5],
              startMinutes: 480,
              endMinutes: 900,
              isBlocked: true
            }
          ],
          createdAt: Date.now(),
          updatedAt: Date.now()
        }
      ];

      engine.loadPolicies(policies);

      // At 10 AM Monday, under daily limit but in restricted time
      const monday10am = new Date('2025-11-03T10:00:00');
      const result = engine.evaluatePolicy('com.youtube', monday10am, 1000);
      
      expect(result.action).toBe(PolicyAction.BLOCK);
    });
  });
});
