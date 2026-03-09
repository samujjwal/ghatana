/**
 * Unit tests for AccessibilityServiceModule
 *
 * Tests validate:
 * - Service initialization and configuration
 * - App monitoring capabilities
 * - Event filtering and detection
 * - Error handling and recovery
 * - Permission checking
 * - Service lifecycle management
 *
 * @see AccessibilityServiceModule.ts
 * @see ../src/native/AccessibilityServiceModule.ts
 */

describe('AccessibilityServiceModule', () => {
  let module: any;

  beforeEach(() => {
    // Import the module
    module = require('../../src/native/AccessibilityServiceModule').default;
    jest.clearAllMocks();
    // Reset module state between tests
    if (module.reset) {
      module.reset();
    }
  });

  describe('initialization', () => {
    /**
     * Verifies that module initializes with required configuration.
     *
     * GIVEN: Fresh module import
     * WHEN: Module initializes
     * THEN: Required properties are set and accessible
     */
    it('should initialize with required configuration', () => {
      expect(module).toBeDefined();
      expect(module.name).toBe('AccessibilityServiceModule');
      expect(typeof module.startMonitoring).toBe('function');
      expect(typeof module.stopMonitoring).toBe('function');
    });

    /**
     * Verifies that module can be configured before monitoring starts.
     *
     * GIVEN: Module instance
     * WHEN: setConfiguration is called with valid config
     * THEN: Configuration is applied without errors
     */
    it('should accept and apply configuration', () => {
      const config = {
        enableAppMonitoring: true,
        detectAnomalies: true,
        logLevel: 'debug',
      };

      expect(() => {
        module.setConfiguration(config);
      }).not.toThrow();
    });
  });

  describe('app monitoring', () => {
    /**
     * Verifies that app monitoring can start successfully.
     *
     * GIVEN: Configured module instance
     * WHEN: startMonitoring is called
     * THEN: Monitoring starts without errors and returns success
     */
    it('should start app monitoring', async () => {
      const result = await module.startMonitoring();
      expect(result).toEqual({ success: true, message: 'Monitoring started' });
    });

    /**
     * Verifies that app monitoring can stop gracefully.
     *
     * GIVEN: Monitoring is running
     * WHEN: stopMonitoring is called
     * THEN: Monitoring stops without errors
     */
    it('should stop app monitoring', async () => {
      await module.startMonitoring();
      const result = await module.stopMonitoring();
      expect(result).toEqual({ success: true, message: 'Monitoring stopped' });
    });

    /**
     * Verifies that monitoring can detect app focus changes.
     *
     * GIVEN: Monitoring is running
     * WHEN: App focus changes to different app
     * THEN: Event is fired with correct app package name
     */
    it('should detect app focus changes', (done) => {
      const appChangeListener = jest.fn((event) => {
        expect(event.packageName).toBeDefined();
        expect(typeof event.packageName).toBe('string');
        appChangeListener.mockRestore();
        done();
      });

      module.onAppFocusChanged(appChangeListener);
      module.startMonitoring();

      // Simulate app change after 100ms
      setTimeout(() => {
        module.simulateAppChange('com.example.app');
      }, 100);
    });

    /**
     * Verifies that monitoring detects unauthorized app access attempts.
     *
     * GIVEN: Restricted app in policy
     * WHEN: User attempts to access restricted app
     * THEN: Violation event is triggered
     */
    it('should detect restricted app access', (done) => {
      module.setPolicy({
        restrictedApps: ['com.facebook.katana'],
      });

      const violationListener = jest.fn((violation) => {
        expect(violation.type).toBe('RESTRICTED_APP_ACCESS');
        expect(violation.packageName).toBe('com.facebook.katana');
        violationListener.mockRestore();
        done();
      });

      module.onViolation(violationListener);
      module.startMonitoring();

      setTimeout(() => {
        module.simulateAppChange('com.facebook.katana');
      }, 100);
    });
  });

  describe('event filtering', () => {
    /**
     * Verifies that module filters system apps correctly.
     *
     * GIVEN: Monitoring enabled with system app filtering
     * WHEN: System app gains focus
     * THEN: No app change event is emitted
     */
    it('should filter system apps', async () => {
      module.setConfiguration({ filterSystemApps: true });
      await module.startMonitoring();

      const appChangeListener = jest.fn();
      module.onAppFocusChanged(appChangeListener);

      // Try to simulate system app focus
      module.simulateAppChange('android.systemui');

      await new Promise((resolve) => setTimeout(resolve, 100));
      expect(appChangeListener).not.toHaveBeenCalled();
    });

    /**
     * Verifies that module filters by app type (game vs productivity).
     *
     * GIVEN: Monitoring with app type filtering
     * WHEN: Different app types gain focus
     * THEN: Only matching apps trigger events
     */
    it('should filter apps by category', async () => {
      module.setConfiguration({
        allowedCategories: ['productivity', 'education'],
      });
      await module.startMonitoring();

      const gameChangeListener = jest.fn();
      module.onAppFocusChanged(gameChangeListener);

      // Simulate gaming app (should be filtered out)
      module.simulateAppChange('com.tencent.tmgp.pubgmobile', 'game');

      await new Promise((resolve) => setTimeout(resolve, 100));
      expect(gameChangeListener).not.toHaveBeenCalled();
    });
  });

  describe('error handling', () => {
    /**
     * Verifies graceful handling of permission denied errors.
     *
     * GIVEN: Module attempts to monitor without permission
     * WHEN: Permission is denied at system level
     * THEN: Module returns error with recovery steps
     */
    it('should handle permission errors gracefully', async () => {
      module.setConfiguration({ throwOnPermissionError: false });

      const result = await module.startMonitoring().catch((err: Error) => ({
        error: err.message,
        code: 'PERMISSION_DENIED',
      }));

      expect(result).toHaveProperty('error');
    });

    /**
     * Verifies recovery from service crash.
     *
     * GIVEN: Service crashes unexpectedly
     * WHEN: Auto-recovery is enabled
     * THEN: Service restarts automatically
     */
    it('should auto-recover from crashes', async () => {
      module.setConfiguration({ autoRecovery: true, recoveryDelayMs: 100 });

      await module.startMonitoring();
      module.simulateCrash();

      await new Promise((resolve) => setTimeout(resolve, 150));
      const status = await module.getServiceStatus();
      expect(status.isRunning).toBe(true);
    });
  });

  describe('permission checking', () => {
    /**
     * Verifies permission check for accessibility service.
     *
     * GIVEN: Module instance
     * WHEN: checkPermissions is called
     * THEN: Returns permission status for accessibility service
     */
    it('should check accessibility service permission', async () => {
      const permissions = await module.checkPermissions();
      expect(permissions).toHaveProperty('accessibilityService');
      expect(typeof permissions.accessibilityService).toBe('boolean');
    });

    /**
     * Verifies permission check for usage stats.
     *
     * GIVEN: Module instance
     * WHEN: checkPermissions is called
     * THEN: Returns permission status for usage stats
     */
    it('should check usage stats permission', async () => {
      const permissions = await module.checkPermissions();
      expect(permissions).toHaveProperty('usageStats');
      expect(typeof permissions.usageStats).toBe('boolean');
    });

    /**
     * Verifies permission request workflow.
     *
     * GIVEN: Missing required permissions
     * WHEN: requestPermissions is called
     * THEN: Permission request is initiated
     */
    it('should request missing permissions', async () => {
      const result = await module.requestPermissions(['accessibilityService']);
      expect(result).toHaveProperty('requested');
      expect(Array.isArray(result.requested)).toBe(true);
    });
  });

  describe('service lifecycle', () => {
    /**
     * Verifies service status reporting.
     *
     * GIVEN: Service running or stopped
     * WHEN: getServiceStatus is called
     * THEN: Returns accurate status with details
     */
    it('should report service status accurately', async () => {
      const statusBefore = await module.getServiceStatus();
      expect(statusBefore).toHaveProperty('isRunning');
      expect(statusBefore).toHaveProperty('uptime');

      await module.startMonitoring();
      const statusAfter = await module.getServiceStatus();
      expect(statusAfter.isRunning).toBe(true);
    });

    /**
     * Verifies cleanup on service shutdown.
     *
     * GIVEN: Service running with active listeners
     * WHEN: stopMonitoring is called
     * THEN: All listeners are cleaned up
     */
    it('should cleanup listeners on shutdown', async () => {
      await module.startMonitoring();

      const listener = jest.fn();
      module.onAppFocusChanged(listener);

      await module.stopMonitoring();

      module.simulateAppChange('com.example.app');
      await new Promise((resolve) => setTimeout(resolve, 100));

      // Listener should not be called after service stops
      expect(listener).not.toHaveBeenCalled();
    });

    /**
     * Verifies metrics collection during service operation.
     *
     * GIVEN: Service running for period of time
     * WHEN: getMetrics is called
     * THEN: Returns collected metrics
     */
    it('should collect operation metrics', async () => {
      await module.startMonitoring();

      await new Promise((resolve) => setTimeout(resolve, 200));

      const metrics = await module.getMetrics();
      expect(metrics).toHaveProperty('eventsProcessed');
      expect(metrics).toHaveProperty('errorsEncountered');
      expect(metrics).toHaveProperty('uptime');
      expect(typeof metrics.eventsProcessed).toBe('number');
    });
  });

  describe('performance', () => {
    /**
     * Verifies that monitoring doesn't create memory leaks.
     *
     * GIVEN: Long-running monitoring session
     * WHEN: Multiple app focus changes occur
     * THEN: Memory usage remains stable
     */
    it('should maintain stable memory during long monitoring', async () => {
      await module.startMonitoring();

      const initialMemory = process.memoryUsage().heapUsed;

      // Simulate many app changes
      for (let i = 0; i < 100; i++) {
        module.simulateAppChange(`com.example.app${i}`);
      }

      await new Promise((resolve) => setTimeout(resolve, 500));

      const finalMemory = process.memoryUsage().heapUsed;
      const increase = finalMemory - initialMemory;

      // Allow for reasonable memory increase (< 10MB)
      expect(increase).toBeLessThan(10 * 1024 * 1024);

      await module.stopMonitoring();
    });

    /**
     * Verifies event processing throughput.
     *
     * GIVEN: High volume of app change events
     * WHEN: Events are processed
     * THEN: All events are handled within acceptable time
     */
    it('should process events efficiently', async () => {
      await module.startMonitoring();

      const startTime = Date.now();
      const eventCount = 500;

      for (let i = 0; i < eventCount; i++) {
        module.simulateAppChange(`com.example.app${i}`);
      }

      await new Promise((resolve) => setTimeout(resolve, 100));
      const duration = Date.now() - startTime;

      // Should handle 500 events in < 200ms (processing time)
      expect(duration).toBeLessThan(200);

      await module.stopMonitoring();
    });
  });
});
