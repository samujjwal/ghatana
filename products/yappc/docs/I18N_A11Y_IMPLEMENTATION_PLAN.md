# i18n and Accessibility Implementation Plan (P1-11)

## Current State

### i18n Issues
- `LivePreviewPanel` uses i18n provider for some labels but contains many hardcoded English UI strings
- `toLocaleTimeString()` calls without explicit locale handling
- Phase cockpit and project shell contain substantial hardcoded user-facing text
- No automated gate to enforce i18n extraction

### a11y Issues
- Release-readiness script only checks accessibility evidence files exist, not that a11y actually passes
- No keyboard alternatives for drag/drop/canvas operations
- No comprehensive keyboard navigation tests for phase navigation, canvas, modal/drawer, preview, and command surfaces

## Target State

### i18n Requirements
- All mounted user-facing text extracted to i18n keys
- Dates/numbers/currency use locale services
- Automated i18n grep gate in CI
- RTL/locale fixture tests

### a11y Requirements
- Keyboard alternatives for drag/drop/canvas operations
- Axe/Playwright keyboard navigation tests
- Focus management tests
- Comprehensive a11y testing for mounted routes

## Implementation Steps

### Phase 1: i18n Extraction Gate

1. **Create i18n coverage checker script** ✅
   - Created `scripts/check-i18n-coverage.mjs`
   - Scans mounted component files for hardcoded English strings
   - Fails CI if hardcoded user-facing strings are found

2. **Integrate i18n gate into CI**
   - Add to package.json scripts
   - Add to verify-release-readiness.mjs
   - Make gate fail for production releases

3. **Extract hardcoded strings**
   - Phase cockpit: Extract all user-facing text
   - Project shell: Extract all user-facing text
   - LivePreviewPanel: Complete i18n migration
   - Canvas/page components: Extract builder UI text

### Phase 2: Locale Services

1. **Create locale utility service**
   - Date formatting with explicit locale
   - Number formatting with explicit locale
   - Currency formatting with explicit locale
   - Time zone handling

2. **Replace direct locale calls**
   - Replace `toLocaleTimeString()` with locale service
   - Replace `toLocaleDateString()` with locale service
   - Replace `toLocaleString()` with locale service

### Phase 3: Keyboard Navigation

1. **Add keyboard alternatives for canvas operations**
   - Keyboard shortcuts for common canvas actions
   - Keyboard navigation for drag/drop operations
   - Keyboard alternatives for mouse-only interactions

2. **Implement focus management**
   - Focus trap for modals/drawers
   - Focus restoration after modal close
   - Focus indicators for keyboard navigation

### Phase 4: a11y Testing

1. **Create keyboard navigation tests**
   - Phase navigation keyboard tests
   - Canvas keyboard operation tests
   - Modal/drawer focus management tests
   - Preview surface keyboard tests
   - Command palette keyboard tests

2. **Integrate axe testing**
   - Add axe-core to Playwright tests
   - Create a11y regression tests
   - Add to CI pipeline

3. **Upgrade a11y gate**
   - Modify verify-release-readiness.mjs to run actual a11y tests
   - Fail gate if a11y tests fail
   - Add --execute mode for production releases

## Status

**Current**: Phase 1 complete - i18n coverage checker script created
**Next**: Integrate i18n gate into CI and begin extracting hardcoded strings
