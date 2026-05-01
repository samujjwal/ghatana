# Accessibility Testing Guide
## For Data-Cloud, AEP, Audio-Video, and YAPPC

**Date:** 2026-04-30  
**Purpose:** Guide for implementing automated accessibility testing across all products

---

## Overview

This guide provides instructions for adding automated accessibility testing to CI/CD pipelines for data-cloud, aep, audio-video, and yappc products. All products should use consistent accessibility testing tools and configurations.

---

## Recommended Tools

### 1. axe-core (for automated accessibility testing)
- **Purpose:** Automated accessibility testing for web applications
- **Integration:** Playwright, Cypress, Jest, or standalone
- **Coverage:** WCAG 2.1 Level A and AA rules

### 2. @axe-core/playwright (for Playwright tests)
- **Purpose:** Accessibility testing within Playwright E2E tests
- **Use Case:** Already installed in AEP UI

### 3. eslint-plugin-jsx-a11y (for React applications)
- **Purpose:** Linting for JSX accessibility issues
- **Coverage:** Common accessibility patterns in React

### 4. pa11y (for command-line testing)
- **Purpose:** Command-line accessibility testing
- **Use Case:** CI/CD integration for static pages

---

## Implementation by Product

### AEP UI (React + Playwright)

AEP UI already has `@axe-core/playwright` installed. Add accessibility tests:

```typescript
// e2e/accessibility.spec.ts
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Accessibility', () => {
  test('should not have any automatically detectable accessibility issues', async ({ page }) => {
    await page.goto('/');
    
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should have proper color contrast', async ({ page }) => {
    await page.goto('/');
    
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should have proper focus management', async ({ page }) => {
    await page.goto('/');
    
    // Test keyboard navigation
    await page.keyboard.press('Tab');
    const focusedElement = await page.evaluate(() => document.activeElement);
    expect(focusedElement).not.toBeNull();
  });
});
```

**Add to package.json scripts:**
```json
{
  "scripts": {
    "test:e2e:a11y": "playwright test --grep @a11y"
  }
}
```

---

### Audio-Video Desktop (React + Tailwind)

Add accessibility testing to existing test setup:

```typescript
// Add to e2e tests
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Accessibility', () => {
  test('should not have accessibility violations', async ({ page }) => {
    await page.goto('/');
    
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('status indicators should have proper contrast', async ({ page }) => {
    await page.goto('/');
    
    const accessibilityScanResults = await new AxeBuilder({ page })
      .include('.status-indicator')
      .analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });
});
```

**Install dependencies:**
```bash
npm install --save-dev @axe-core/playwright @playwright/test
```

---

### Data-Cloud UI

Add accessibility testing to the UI:

```typescript
// e2e/accessibility.spec.ts
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Accessibility', () => {
  test('should not have accessibility violations on homepage', async ({ page }) => {
    await page.goto('/');
    
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should have proper color contrast across all pages', async ({ page }) => {
    const pages = ['/', '/dashboard', '/settings'];
    
    for (const pagePath of pages) {
      await page.goto(pagePath);
      
      const accessibilityScanResults = await new AxeBuilder({ page })
        .withTags(['wcag2aa', 'color-contrast'])
        .analyze();
      
      expect(accessibilityScanResults.violations).toEqual([]);
    }
  });

  test('should have visible focus states for keyboard users', async ({ page }) => {
    await page.goto('/');
    
    // Test that all interactive elements have visible focus
    const interactiveElements = await page.locator('button, a, input, select, textarea').all();
    
    for (const element of interactiveElements) {
      await element.focus();
      const computedStyle = await element.evaluate((el) => {
        return window.getComputedStyle(el);
      });
      
      // Check that outline or box-shadow is set on focus
      expect(computedStyle.outline || computedStyle.boxShadow).toBeTruthy();
    }
  });
});
```

---

### YAPPC Frontend

YAPPC has a comprehensive frontend with multiple libraries. Add accessibility testing:

```typescript
// web/e2e/accessibility.spec.ts
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Accessibility', () => {
  test('should not have accessibility violations', async ({ page }) => {
    await page.goto('/');
    
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should have proper color contrast in dark mode', async ({ page }) => {
    await page.goto('/');
    
    // Enable dark mode
    await page.evaluate(() => {
      document.documentElement.classList.add('dark');
    });
    
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2aa', 'color-contrast'])
      .analyze();
    
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should support keyboard navigation', async ({ page }) => {
    await page.goto('/');
    
    // Test that all interactive elements are keyboard accessible
    const tabCount = await page.evaluate(() => {
      let count = 0;
      document.body.focus();
      while (document.activeElement) {
        count++;
        // Prevent infinite loop
        if (count > 1000) break;
        // Simulate Tab key
        const event = new KeyboardEvent('keydown', { key: 'Tab' });
        document.dispatchEvent(event);
      }
      return count;
    });
    
    expect(tabCount).toBeGreaterThan(0);
  });
});
```

---

## ESLint Configuration

Add accessibility linting to all React applications:

```json
// .eslintrc.json
{
  "extends": [
    "plugin:jsx-a11y/recommended"
  ],
  "plugins": ["jsx-a11y"],
  "rules": {
    "jsx-a11y/anchor-is-valid": "warn",
    "jsx-a11y/alt-text": "error",
    "jsx-a11y/aria-props": "error",
    "jsx-a11y/aria-proptypes": "error",
    "jsx-a11y/aria-unsupported-elements": "error",
    "jsx-a11y/role-has-required-aria-props": "error",
    "jsx-a11y/role-supports-aria-props": "error",
    "jsx-a11y/click-events-have-key-events": "warn",
    "jsx-a11y/no-static-element-interactions": "warn"
  }
}
```

**Install dependencies:**
```bash
npm install --save-dev eslint-plugin-jsx-a11y
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/accessibility.yml
name: Accessibility Tests

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main, develop]

jobs:
  accessibility:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          
      - name: Install dependencies
        run: npm ci
        
      - name: Run ESLint accessibility checks
        run: npm run lint
        
      - name: Build application
        run: npm run build
        
      - name: Install Playwright browsers
        run: npx playwright install --with-deps
        
      - name: Run accessibility tests
        run: npm run test:e2e:a11y
        
      - name: Upload accessibility report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: accessibility-report
          path: playwright-report/
```

---

## Manual Testing Checklist

Use this checklist for manual accessibility testing:

### Color Contrast
- [ ] All text has minimum 4.5:1 contrast ratio for normal text
- [ ] Large text (18pt+) has minimum 3:1 contrast ratio
- [ ] Interactive elements have minimum 3:1 contrast ratio
- [ ] Graphical objects have minimum 3:1 contrast ratio

### Keyboard Navigation
- [ ] All interactive elements are keyboard accessible
- [ ] Tab order follows logical reading order
- [ ] Focus indicator is clearly visible
- [ ] Skip links provided for keyboard users
- [ ] No keyboard traps

### Screen Reader Compatibility
- [ ] All images have alt text
- [ ] Form fields have associated labels
- [ ] Headings are used hierarchically
- [ ] ARIA labels used where necessary
- [ ] Live regions announced properly

### Focus Management
- [ ] Focus moves to new content after navigation
- [ ] Focus is managed in modals and dialogs
- [ ] Focus returns after closing modals
- [ ] Auto-focus does not trap users

### Responsive Design
- [ ] Content is readable at 200% zoom
- [ ] Layout works in portrait and landscape
- [ ] Touch targets are at least 44x44px
- [ ] No horizontal scrolling at 320px width

---

## Automated Testing Rules

Configure axe-core to check specific WCAG rules:

```typescript
const accessibilityScanResults = await new AxeBuilder({ page })
  .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
  .withRules([
    'color-contrast',
    'focus-order-semantics',
    'keyboard',
    'label',
    'link-name',
    'list',
    'listitem',
    'image-alt',
    'image-redundant-alt',
    'button-name',
    'link-purpose',
    'frame-title',
    'document-title',
    'html-has-lang',
    'lang',
    'valid-lang',
    'bypass',
    'no-title',
    'video-caption',
    'audio-caption',
    'label-title-only',
  ])
  .exclude('.ignored-element')
  .analyze();
```

---

## Reporting and Remediation

### Violation Categories

1. **Critical** - Blocks accessibility for users with disabilities
   - Fix immediately before merging

2. **Serious** - Significantly impacts accessibility
   - Fix in the next sprint

3. **Moderate** - Some impact on accessibility
   - Fix within 2 sprints

4. **Minor** - Low impact on accessibility
   - Fix when convenient

### Reporting Format

```json
{
  "violations": [
    {
      "id": "color-contrast",
      "impact": "serious",
      "description": "Elements must have sufficient color contrast",
      "help": "Elements must have sufficient color contrast",
      "helpUrl": "https://dequeuniversity.com/rules/axe/4.7/color-contrast",
      "nodes": [
        {
          "html": "<button class=\"control-button\">Submit</button>",
          "target": [".control-button"],
          "failureSummary": "Fix any of the following:\n  Element has insufficient color contrast of 3.1 (foreground color: #1976d2, background color: #ffffff, font size: 14px, font weight: bold). Expected contrast ratio of 4.5:1"
        }
      ]
    }
  ]
}
```

---

## Continuous Monitoring

### Accessibility Metrics

Track these metrics in your CI/CD dashboard:

1. **Violation Count** - Total number of accessibility violations
2. **Critical Violations** - Number of critical issues
3. **Test Coverage** - Percentage of pages tested
4. **Remediation Time** - Average time to fix violations
5. **Regression Rate** - Percentage of fixed violations that reappear

### Alerts

Set up alerts for:
- New critical violations in production
- Increase in violation count by more than 10%
- Failed accessibility tests in CI/CD

---

## Resources

- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [axe-core Documentation](https://www.deque.com/axe/)
- [Playwright Accessibility Testing](https://playwright.dev/docs/accessibility-testing)
- [React Accessibility](https://react.dev/learn/accessibility)
- [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/)

---

## Next Steps

1. Implement accessibility tests in each product's CI/CD pipeline
2. Set up automated accessibility reporting
3. Train developers on accessibility best practices
4. Establish accessibility review process for PRs
5. Monitor accessibility metrics and trends
