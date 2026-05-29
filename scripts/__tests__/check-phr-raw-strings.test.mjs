#!/usr/bin/env node

/**
 * Tests for check-phr-raw-strings.mjs
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

const TEST_DIR = join(process.cwd(), '.tmp-test-phr-raw-strings');

describe('check-phr-raw-strings', () => {
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

  it('should pass when no raw strings are found', () => {
    const webDir = join(TEST_DIR, 'products', 'phr', 'apps', 'web', 'src');
    mkdirSync(webDir, { recursive: true });
    
    writeFileSync(join(webDir, 'Dashboard.tsx'), `
      import { useTranslation } from 'react-i18next';
      
      function Dashboard() {
        const { t } = useTranslation();
        return <button>{t('dashboard.submit')}</button>;
      }
    `);

    const result = execSync(
      `node scripts/check-phr-raw-strings.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No raw strings found');
  });

  it('should fail when raw button text is found', () => {
    const webDir = join(TEST_DIR, 'products', 'phr', 'apps', 'web', 'src');
    mkdirSync(webDir, { recursive: true });
    
    writeFileSync(join(webDir, 'Dashboard.tsx'), `
      function Dashboard() {
        return <button>Submit Form</button>;
      }
    `);

    try {
      execSync(
        `node scripts/check-phr-raw-strings.mjs`,
        { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
      );
      expect.fail('Should have thrown an error');
    } catch (error) {
      expect(error.stdout).toContain('Found 1 files with potential raw strings');
    }
  });

  it('should skip files that use i18n', () => {
    const webDir = join(TEST_DIR, 'products', 'phr', 'apps', 'web', 'src');
    mkdirSync(webDir, { recursive: true });
    
    writeFileSync(join(webDir, 'Dashboard.tsx'), `
      import { useTranslation } from 'react-i18next';
      
      function Dashboard() {
        const { t } = useTranslation();
        return (
          <div>
            <button>{t('dashboard.submit')}</button>
            <label>{t('dashboard.label')}</label>
          </div>
        );
      }
    `);

    const result = execSync(
      `node scripts/check-phr-raw-strings.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No raw strings found');
  });

  it('should skip test files', () => {
    const webDir = join(TEST_DIR, 'products', 'phr', 'apps', 'web', 'src');
    mkdirSync(webDir, { recursive: true });
    
    writeFileSync(join(webDir, 'Dashboard.test.tsx'), `
      function Dashboard() {
        return <button>Submit Form</button>;
      }
    `);

    const result = execSync(
      `node scripts/check-phr-raw-strings.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No raw strings found');
  });

  it('should skip i18n directories', () => {
    const webDir = join(TEST_DIR, 'products', 'phr', 'apps', 'web', 'src');
    const i18nDir = join(webDir, 'i18n');
    mkdirSync(i18nDir, { recursive: true });
    
    writeFileSync(join(i18nDir, 'en.json'), JSON.stringify({
      dashboard: { submit: 'Submit Form' }
    }, null, 2));

    const result = execSync(
      `node scripts/check-phr-raw-strings.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('No raw strings found');
  });

  it('should handle missing directories gracefully', () => {
    const result = execSync(
      `node scripts/check-phr-raw-strings.mjs`,
      { cwd: TEST_DIR, encoding: 'utf-8', stdio: 'pipe' }
    );
    
    expect(result).toContain('PHR web app directory not found');
  });
});
