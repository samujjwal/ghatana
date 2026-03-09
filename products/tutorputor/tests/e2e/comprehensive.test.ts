/**
 * Comprehensive Automation Testing Framework
 * 
 * Provides end-to-end testing for the entire Tutorputor platform
 * with security testing, performance testing, and user journey automation.
 */

import { test, expect } from '@playwright/test';
import { APIRequestContext, Page } from '@playwright/test';

// Test configuration
const TEST_CONFIG = {
  baseURL: process.env.TEST_BASE_URL || 'http://localhost:3000',
  apiURL: process.env.TEST_API_URL || 'http://localhost:3000',
  timeout: 30000,
  retries: 2,
};

// Test data
const TEST_USERS = {
  student: {
    email: 'student@test.com',
    password: 'TestPassword123!',
    firstName: 'Test',
    lastName: 'Student',
  },
  instructor: {
    email: 'instructor@test.com',
    password: 'TestPassword123!',
    firstName: 'Test',
    lastName: 'Instructor',
  },
  admin: {
    email: 'admin@test.com',
    password: 'AdminPassword123!',
    firstName: 'Test',
    lastName: 'Admin',
  },
};

/**
 * Authentication Helper
 */
class AuthHelper {
  constructor(private request: APIRequestContext) {}

  async login(user: keyof typeof TEST_USERS) {
    const userData = TEST_USERS[user];
    
    const response = await this.request.post('/api/auth/login', {
      data: {
        email: userData.email,
        password: userData.password,
      },
    });

    expect(response.ok()).toBeTruthy();
    
    const data = await response.json();
    return data.accessToken;
  }

  async logout(token: string) {
    const response = await this.request.post('/api/auth/logout', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    expect(response.ok()).toBeTruthy();
  }

  async register(userData: typeof TEST_USERS.student) {
    const response = await this.request.post('/api/auth/register', {
      data: userData,
    });

    return response;
  }
}

/**
 * Security Testing Helper
 */
class SecurityHelper {
  constructor(private request: APIRequestContext) {}

  async testSQLInjection() {
    const maliciousInputs = [
      "'; DROP TABLE users; --",
      "' OR '1'='1",
      "'; SELECT * FROM users; --",
      "' UNION SELECT password FROM users --",
    ];

    for (const input of maliciousInputs) {
      const response = await this.request.post('/api/auth/login', {
        data: {
          email: input,
          password: 'test',
        },
      });

      // Should not succeed with malicious input
      expect(response.status()).toBe(400);
      
      const data = await response.json();
      expect(data.error).toBeDefined();
    }
  }

  async testXSSPrevention() {
    const xssPayloads = [
      '<script>alert("xss")</script>',
      'javascript:alert("xss")',
      '<img src="x" onerror="alert(\'xss\')">',
      '"><script>alert("xss")</script>',
    ];

    for (const payload of xssPayloads) {
      const response = await this.request.post('/api/modules', {
        data: {
          title: payload,
          description: 'Test module',
        },
        headers: {
          Authorization: `Bearer ${await this.getAuthToken()}`,
        },
      });

      // Should sanitize or reject XSS payloads
      if (response.ok()) {
        const data = await response.json();
        expect(data.title).not.toContain('<script>');
        expect(data.title).not.toContain('javascript:');
      }
    }
  }

  async testAuthenticationBypass() {
    // Test without token
    const response1 = await this.request.get('/api/modules');
    expect(response1.status()).toBe(401);

    // Test with invalid token
    const response2 = await this.request.get('/api/modules', {
      headers: {
        Authorization: 'Bearer invalid-token',
      },
    });
    expect(response2.status()).toBe(401);

    // Test with expired token
    const response3 = await this.request.get('/api/modules', {
      headers: {
        Authorization: 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c',
      },
    });
    expect(response3.status()).toBe(401);
  }

  private async getAuthToken(): Promise<string> {
    const authHelper = new AuthHelper(this.request);
    return await authHelper.login('admin');
  }
}

/**
 * Performance Testing Helper
 */
class PerformanceHelper {
  constructor(private page: Page) {}

  async measurePageLoad(pagePath: string) {
    const startTime = Date.now();
    
    await this.page.goto(`${TEST_CONFIG.baseURL}${pagePath}`);
    await this.page.waitForLoadState('networkidle');
    
    const loadTime = Date.now() - startTime;
    
    // Page should load within acceptable time
    expect(loadTime).toBeLessThan(3000);
    
    return {
      loadTime,
      url: this.page.url(),
    };
  }

  async measureAPIResponse(endpoint: string, request: APIRequestContext) {
    const startTime = Date.now();
    
    const response = await request.get(endpoint);
    
    const responseTime = Date.now() - startTime;
    
    // API should respond within acceptable time
    expect(responseTime).toBeLessThan(1000);
    expect(response.ok()).toBeTruthy();
    
    return {
      responseTime,
      status: response.status(),
    };
  }

  async testConcurrentUsers(endpoint: string, request: APIRequestContext, userCount: number = 10) {
    const promises = Array.from({ length: userCount }, () => 
      this.measureAPIResponse(endpoint, request)
    );

    const results = await Promise.all(promises);
    
    // All requests should succeed
    results.forEach(result => {
      expect(result.status).toBe(200);
      expect(result.responseTime).toBeLessThan(2000);
    });

    const avgResponseTime = results.reduce((sum, r) => sum + r.responseTime, 0) / results.length;
    
    return {
      averageResponseTime: avgResponseTime,
      maxResponseTime: Math.max(...results.map(r => r.responseTime)),
      minResponseTime: Math.min(...results.map(r => r.responseTime)),
    };
  }
}

/**
 * User Journey Testing Helper
 */
class UserJourneyHelper {
  constructor(private page: Page, private request: APIRequestContext) {}

  async completeStudentJourney() {
    const authHelper = new AuthHelper(this.request);
    const token = await authHelper.login('student');
    
    // 1. Browse modules
    await this.page.goto(`${TEST_CONFIG.baseURL}/modules`);
    await this.page.waitForSelector('[data-testid="module-list"]');
    
    // 2. View module details
    await this.page.click('[data-testid="module-card"]:first-child');
    await this.page.waitForSelector('[data-testid="module-details"]');
    
    // 3. Start learning
    await this.page.click('[data-testid="start-learning"]');
    await this.page.waitForSelector('[data-testid="content-viewer"]');
    
    // 4. Complete content
    await this.page.click('[data-testid="mark-complete"]');
    await this.page.waitForSelector('[data-testid="progress-indicator"]');
    
    // 5. Take assessment
    await this.page.click('[data-testid="start-assessment"]');
    await this.page.waitForSelector('[data-testid="assessment-question"]');
    
    // Answer questions
    await this.page.click('[data-testid="answer-option"]:first-child');
    await this.page.click('[data-testid="submit-answer"]');
    
    // 6. View results
    await this.page.waitForSelector('[data-testid="assessment-results"]');
    
    // Verify completion
    const progress = await this.page.locator('[data-testid="progress-percentage"]').textContent();
    expect(progress).toContain('100');
  }

  async completeInstructorJourney() {
    const authHelper = new AuthHelper(this.request);
    const token = await authHelper.login('instructor');
    
    // 1. Go to admin dashboard
    await this.page.goto(`${TEST_CONFIG.baseURL}/admin`);
    await this.page.waitForSelector('[data-testid="admin-dashboard"]');
    
    // 2. Create new module
    await this.page.click('[data-testid="create-module"]');
    await this.page.waitForSelector('[data-testid="module-form"]');
    
    // Fill module details
    await this.page.fill('[data-testid="module-title"]', 'Test Module');
    await this.page.fill('[data-testid="module-description"]', 'Test Description');
    await this.page.selectOption('[data-testid="module-domain"]', 'MATHEMATICS');
    
    // 3. Add content
    await this.page.click('[data-testid="add-content"]');
    await this.page.click('[data-testid="content-type-text"]');
    await this.page.fill('[data-testid="content-body"]', 'Test content');
    
    // 4. Create assessment
    await this.page.click('[data-testid="create-assessment"]');
    await this.page.waitForSelector('[data-testid="assessment-form"]');
    
    await this.page.fill('[data-testid="question-text"]', 'Test question?');
    await this.page.fill('[data-testid="answer-option"]', 'Test answer');
    
    // 5. Publish module
    await this.page.click('[data-testid="publish-module"]');
    await this.page.waitForSelector('[data-testid="publish-success"]');
    
    // Verify module is published
    const status = await this.page.locator('[data-testid="module-status"]').textContent();
    expect(status).toContain('PUBLISHED');
  }

  async completeAdminJourney() {
    const authHelper = new AuthHelper(this.request);
    const token = await authHelper.login('admin');
    
    // 1. System health check
    await this.page.goto(`${TEST_CONFIG.baseURL}/admin/system`);
    await this.page.waitForSelector('[data-testid="system-health"]');
    
    // Verify all systems are healthy
    const healthIndicators = await this.page.locator('[data-testid="health-indicator"]').count();
    expect(healthIndicators).toBeGreaterThan(0);
    
    // 2. User management
    await this.page.goto(`${TEST_CONFIG.baseURL}/admin/users`);
    await this.page.waitForSelector('[data-testid="user-list"]');
    
    // 3. Analytics dashboard
    await this.page.goto(`${TEST_CONFIG.baseURL}/admin/analytics`);
    await this.page.waitForSelector('[data-testid="analytics-dashboard"]');
    
    // Verify analytics data loads
    const charts = await this.page.locator('[data-testid="analytics-chart"]').count();
    expect(charts).toBeGreaterThan(0);
  }
}

// Test Suites

test.describe('Authentication', () => {
  test('should login successfully with valid credentials', async ({ request }) => {
    const authHelper = new AuthHelper(request);
    const token = await authHelper.login('student');
    
    expect(token).toBeDefined();
    expect(token.length).toBeGreaterThan(100);
  });

  test('should reject invalid credentials', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: {
        email: 'invalid@test.com',
        password: 'invalid',
      },
    });

    expect(response.status()).toBe(401);
  });

  test('should logout successfully', async ({ request }) => {
    const authHelper = new AuthHelper(request);
    const token = await authHelper.login('student');
    await authHelper.logout(token);
    
    // Token should be invalidated
    const response = await request.get('/api/modules', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    
    expect(response.status()).toBe(401);
  });
});

test.describe('Security', () => {
  test('should prevent SQL injection attacks', async ({ request }) => {
    const securityHelper = new SecurityHelper(request);
    await securityHelper.testSQLInjection();
  });

  test('should prevent XSS attacks', async ({ request }) => {
    const securityHelper = new SecurityHelper(request);
    await securityHelper.testXSSPrevention();
  });

  test('should prevent authentication bypass', async ({ request }) => {
    const securityHelper = new SecurityHelper(request);
    await securityHelper.testAuthenticationBypass();
  });
});

test.describe('Performance', () => {
  test('should load pages within acceptable time', async ({ page }) => {
    const perfHelper = new PerformanceHelper(page);
    
    const pages = ['/modules', '/dashboard', '/profile'];
    
    for (const pagePath of pages) {
      const result = await perfHelper.measurePageLoad(pagePath);
      expect(result.loadTime).toBeLessThan(3000);
    }
  });

  test('should respond to API calls within acceptable time', async ({ request }) => {
    const perfHelper = new PerformanceHelper(null as any);
    
    const endpoints = ['/api/modules', '/api/user/profile', '/api/assessments'];
    
    for (const endpoint of endpoints) {
      const result = await perfHelper.measureAPIResponse(endpoint, request);
      expect(result.responseTime).toBeLessThan(1000);
    }
  });

  test('should handle concurrent users', async ({ request }) => {
    const perfHelper = new PerformanceHelper(null as any);
    
    const result = await perfHelper.testConcurrentUsers('/api/modules', request, 10);
    expect(result.averageResponseTime).toBeLessThan(1500);
  });
});

test.describe('User Journeys', () => {
  test('student should complete learning journey', async ({ page, request }) => {
    const journeyHelper = new UserJourneyHelper(page, request);
    await journeyHelper.completeStudentJourney();
  });

  test('instructor should complete content creation journey', async ({ page, request }) => {
    const journeyHelper = new UserJourneyHelper(page, request);
    await journeyHelper.completeInstructorJourney();
  });

  test('admin should complete system management journey', async ({ page, request }) => {
    const journeyHelper = new UserJourneyHelper(page, request);
    await journeyHelper.completeAdminJourney();
  });
});

test.describe('API Integration', () => {
  test('should handle module CRUD operations', async ({ request }) => {
    const authHelper = new AuthHelper(request);
    const token = await authHelper.login('instructor');
    
    // Create
    const createResponse = await request.post('/api/modules', {
      data: {
        title: 'Test Module',
        description: 'Test Description',
        domain: 'MATHEMATICS',
        difficulty: 'BEGINNER',
      },
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    
    expect(createResponse.ok()).toBeTruthy();
    const module = await createResponse.json();
    
    // Read
    const readResponse = await request.get(`/api/modules/${module.id}`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    
    expect(readResponse.ok()).toBeTruthy();
    const readModule = await readResponse.json();
    expect(readModule.title).toBe('Test Module');
    
    // Update
    const updateResponse = await request.put(`/api/modules/${module.id}`, {
      data: {
        title: 'Updated Module',
      },
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    
    expect(updateResponse.ok()).toBeTruthy();
    
    // Delete
    const deleteResponse = await request.delete(`/api/modules/${module.id}`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    
    expect(deleteResponse.ok()).toBeTruthy();
  });

  test('should handle assessment submission', async ({ request }) => {
    const authHelper = new AuthHelper(request);
    const token = await authHelper.login('student');
    
    // Get assessments
    const assessmentsResponse = await request.get('/api/assessments', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    
    expect(assessmentsResponse.ok()).toBeTruthy();
    const assessments = await assessmentsResponse.json();
    
    if (assessments.length > 0) {
      const assessment = assessments[0];
      
      // Submit assessment
      const submitResponse = await request.post(`/api/assessments/${assessment.id}/submit`, {
        data: {
          answers: {
            'q1': 'A',
            'q2': 'B',
          },
        },
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      
      expect(submitResponse.ok()).toBeTruthy();
      
      const result = await submitResponse.json();
      expect(result.score).toBeDefined();
      expect(result.passed).toBeDefined();
    }
  });
});

export {
  AuthHelper,
  SecurityHelper,
  PerformanceHelper,
  UserJourneyHelper,
  TEST_CONFIG,
  TEST_USERS,
};
