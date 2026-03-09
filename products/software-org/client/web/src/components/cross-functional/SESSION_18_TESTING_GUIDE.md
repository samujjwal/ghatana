# Session 18 Testing Guide: Future Cross-Functional Features

> **Date:** 2025-11-25
> **Scope:** KnowledgeBase, InnovationTracker, SkillsMatrix
> **Status:** Ready for QA

## 1. Overview

This document outlines the testing strategy, automated test coverage, and manual validation steps for the "Future Cross-Functional Features" delivered in Session 18. These components enable advanced organizational capabilities including knowledge management, innovation tracking, and skills planning.

## 2. Automated Testing

### 2.1 Running Tests

Execute the integration test suite using Vitest:

```bash
# Run all cross-functional tests
pnpm test products/software-org/apps/web/src/components/cross-functional

# Run only Session 18 tests
pnpm test products/software-org/apps/web/src/components/cross-functional/session18.integration.test.tsx
```

### 2.2 Test Coverage

The suite `session18.integration.test.tsx` covers the following scenarios:

#### KnowledgeBase
- [x] **Rendering**: Verifies header, metrics cards, and initial tab state.
- [x] **Navigation**: Tests switching between Articles, Categories, Contributors, and Activity tabs.
- [x] **Filtering**: Validates category chip interaction (e.g., filtering by 'Engineering').
- [x] **Data Display**: Checks correct rendering of article counts and category names.

#### InnovationTracker
- [x] **Rendering**: Verifies dashboard layout and key metrics (Active Ideas, Success Rate).
- [x] **Progress Tracking**: Validates `LinearProgress` display for active experiments.
- [x] **Tab Switching**: Tests navigation through Ideas, Experiments, Results, and Learnings.
- [x] **Content**: Verifies experiment titles and status indicators.

#### SkillsMatrix
- [x] **Rendering**: Verifies matrix overview and aggregate metrics.
- [x] **Matrix View**: Checks rendering of team skills and proficiency levels.
- [x] **Gap Analysis**: Validates display of skill gaps with impact levels.
- [x] **Development Plans**: Tests rendering of individual development plans.

## 3. Manual Testing Scenarios

### 3.1 KnowledgeBase
1.  **Search & Filter**:
    -   Click on "Engineering" category chip. Verify only engineering articles are shown.
    -   Switch to "Categories" tab. Verify grid layout of categories.
2.  **Contributor View**:
    -   Switch to "Contributors" tab.
    -   Verify top contributors are listed with their article counts.
3.  **Responsiveness**:
    -   Resize window to mobile width. Verify grid adjusts from 3 columns to 1 column.

### 3.2 InnovationTracker
1.  **Idea Submission**:
    -   Click "Submit Idea" (mock action). Verify console log or alert if implemented.
2.  **Experiment Progress**:
    -   Check "Experiments" tab. Verify progress bars reflect the percentage values (e.g., 65%).
    -   Verify status chips (e.g., "In Progress", "Planning") have correct colors.
3.  **Voting**:
    -   In "Ideas" tab, verify vote counts are displayed.

### 3.3 SkillsMatrix
1.  **Proficiency Visualization**:
    -   In "Matrix" tab, verify color coding of proficiency levels (Green for Expert, Red for Novice).
    -   Hover over a skill bar to check exact values.
2.  **Gap Identification**:
    -   Switch to "Skill Gaps" tab.
    -   Verify "Critical" gaps are highlighted in red.
    -   Check "Affected Projects" tags.
3.  **Development Tracking**:
    -   Switch to "Development" tab.
    -   Verify progress bars for training courses.

## 4. Edge Cases & Error Handling

-   **Empty States**: Verify components handle empty lists (e.g., no articles, no gaps) gracefully (should show "No items found" or similar).
-   **Long Text**: Test with long article titles or skill descriptions to ensure no layout breakage.
-   **Zero Values**: Verify metrics cards handle 0 values correctly (e.g., 0 Critical Gaps).

## 5. Accessibility Checklist

-   [ ] **Keyboard Navigation**: All tabs and buttons must be focusable via Tab key.
-   [ ] **Screen Readers**: Metrics cards should have appropriate ARIA labels.
-   [ ] **Contrast**: Text on colored chips (proficiency levels) must meet WCAG AA standards.
-   [ ] **Semantics**: Use proper heading hierarchy (h4, h6) and list structures.

## 6. Performance Benchmarks

-   **Render Time**: Initial render < 100ms.
-   **Tab Switch**: < 50ms latency.
-   **List Virtualization**: Not currently implemented (lists < 100 items). Future optimization needed for > 1000 items.
