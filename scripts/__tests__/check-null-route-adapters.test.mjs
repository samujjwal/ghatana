#!/usr/bin/env node

/**
 * Tests for check-null-route-adapters.mjs
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

const TEST_DIR = join(process.cwd(), '.tmp-test-null-adapters');

describe('check-null-route-adapters', () => {
  beforeEach(() => {
    if (existsSync(TEST_DIR)) {
      rmSync(TEST_DIR, { recursive: true, force: true });
    }
    mkdirSync(TEST_DIR, { recursive: true });
  });

  afterEach(() => {
    if (existsSync(TEST_DIR)) {
      rmSync(TEST_DIR, { recursive: true, force: true });
    }
  });

  it('should pass when no null route adapters are found', () => {
    const productDir = join(TEST_DIR, 'products', 'test-product');
    mkdirSync(productDir, { recursive: true });
    
    writeFileSync(join(productDir, 'routes.ts'), `
      const router = RoutingServlet.builder()
        .with('/dashboard', dashboardHandler)
        .with('/records', recordsHandler)
        .build();
    `);

    const result = execSync(
      `node scripts/check-null-route-adapters.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No null route adapters found');
  });

  it('should fail when null route adapter is found in Java code', () => {
    const productDir = join(TEST_DIR, 'products', 'test-product');
    mkdirSync(productDir, { recursive: true });
    
    writeFileSync(join(productDir, 'Routes.java'), `
      public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(null, this::handleNullRoute)
            .build();
      }
    `);

    try {
      execSync(
        `node scripts/check-null-route-adapters.mjs`,
        { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
      );
      expect.fail('Should have thrown an error');
    } catch (error) {
      expect(error.stdout).toContain('Found 1 files with null route adapter usage');
      expect(error.stdout).toContain('null');
    }
  });

  it('should fail when null route adapter is found in TypeScript code', () => {
    const productDir = join(TEST_DIR, 'products', 'test-product');
    mkdirSync(productDir, { recursive: true });
    
    writeFileSync(join(productDir, 'routes.ts'), `
      const routes = [
        { path: '/dashboard', adapter: null },
        { path: '/records', adapter: recordsAdapter },
      ];
    `);

    try {
      execSync(
        `node scripts/check-null-route-adapters.mjs`,
        { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
      );
      expect.fail('Should have thrown an error');
    } catch (error) {
      expect(error.stdout).toContain('Found 1 files with null route adapter usage');
    }
  });

  it('should skip test files', () => {
    const productDir = join(TEST_DIR, 'products', 'test-product');
    mkdirSync(productDir, { recursive: true });
    
    writeFileSync(join(productDir, 'routes.test.ts'), `
      const routes = [
        { path: '/dashboard', adapter: null },
      ];
    `);

    const result = execSync(
      `node scripts/check-null-route-adapters.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No null route adapters found');
  });

  it('should skip mock files', () => {
    const productDir = join(TEST_DIR, 'products', 'test-product');
    mkdirSync(productDir, { recursive: true });
    
    writeFileSync(join(productDir, 'mock-routes.ts'), `
      const routes = [
        { path: '/dashboard', adapter: null },
      ];
    `);

    const result = execSync(
      `node scripts/check-null-route-adapters.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No null route adapters found');
  });

  it('should handle missing products directory gracefully', () => {
    const result = execSync(
      `node scripts/check-null-route-adapters.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No products directory found');
  });
});
