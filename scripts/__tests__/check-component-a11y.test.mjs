import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { writeFileSync, mkdirSync, rmSync, existsSync } from 'fs';
import { join } from 'path';
import { execSync } from 'child_process';

const TEST_DIR = join(process.cwd(), '.tmp-a11y-test');

describe('check-component-a11y', () => {
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

  it('should pass when components have proper ARIA labels', () => {
    const component = `
      export function ButtonWithLabel() {
        return <button aria-label="Submit form">Submit</button>;
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'Button.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    const result = execSync(`node ${scriptPath}`, {
      cwd: TEST_DIR,
      env: { ...process.env, REPO_ROOT: TEST_DIR },
      stdio: 'pipe',
    }).toString();

    expect(result).toContain('✅ All components follow accessibility best practices');
  });

  it('should fail when button is missing ARIA label', () => {
    const component = `
      export function ButtonWithoutLabel() {
        return <button>Submit</button>;
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'Button.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should fail when image is missing alt attribute', () => {
    const component = `
      export function ImageWithoutAlt() {
        return <img src="logo.png" />;
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'Image.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should fail when div has button role instead of button element', () => {
    const component = `
      export function DivWithButtonRole() {
        return <div role="button">Click me</div>;
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'DivButton.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should fail when heading hierarchy is violated', () => {
    const component = `
      export function BadHeadingHierarchy() {
        return (
          <div>
            <h1>Title</h1>
            <h3>Subtitle</h3>
          </div>
        );
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'Headings.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should fail when form input has no label', () => {
    const component = `
      export function InputWithoutLabel() {
        return <input type="text" />;
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'Input.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    expect(() => {
      execSync(`node ${scriptPath}`, {
        cwd: TEST_DIR,
        env: { ...process.env, REPO_ROOT: TEST_DIR },
        stdio: 'pipe',
      });
    }).toThrow();
  });

  it('should pass when input has proper label via id', () => {
    const component = `
      export function InputWithLabel() {
        return (
          <div>
            <label htmlFor="username">Username</label>
            <input id="username" type="text" />
          </div>
        );
      }
    `;

    const srcDir = join(TEST_DIR, 'src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'Input.tsx'), component);

    const scriptPath = join(process.cwd(), 'scripts/check-component-a11y.mjs');
    const result = execSync(`node ${scriptPath}`, {
      cwd: TEST_DIR,
      env: { ...process.env, REPO_ROOT: TEST_DIR },
      stdio: 'pipe',
    }).toString();

    expect(result).toContain('✅ All components follow accessibility best practices');
  });
});
