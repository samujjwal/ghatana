# Release Readiness Execution Mode

**@doc.type documentation**
**@doc.purpose Define mandatory test execution mode for production release gates**
**@doc.layer product**

## Overview

Production release gates must execute tests and verify actual results, not only check for the existence of evidence files. This ensures that release readiness is validated through active testing rather than passive artifact verification.

## Execution Modes

### Evidence-Only Mode (Deprecated)
- **Status**: Deprecated for production releases
- **Behavior**: Checks for existence of evidence files (screenshots, logs, reports)
- **Problem**: Does not verify test execution or results
- **Use Case**: Only for local development or offline scenarios

### Execution Mode (Required)
- **Status**: Required for production releases
- **Behavior**: Executes tests and verifies actual results
- **Validation**: Test passes/fail status, coverage metrics, performance thresholds
- **Use Case**: All production release branches

## Required Test Categories

### Unit Tests
- **Execution**: Must run and pass
- **Coverage**: Minimum 80% for critical paths
- **Failure Action**: Block release

### Integration Tests
- **Execution**: Must run and pass
- **Scope**: API boundaries, database, message flows
- **Failure Action**: Block release

### E2E Tests
- **Execution**: Must run and pass
- **Scope**: Critical user workflows
- **Failure Action**: Block release

### Visual Regression Tests
- **Execution**: Must run and pass
- **Threshold**: Configurable pixel difference tolerance
- **Failure Action**: Block release or require manual review

### Accessibility Tests
- **Execution**: Must run and pass
- **Standards**: WCAG 2.1 AA compliance
- **Failure Action**: Block release or require remediation plan

## Release Gate Configuration

### Environment Variables
```bash
# Enable execution mode (required for production)
RELEASE_READINESS_MODE=execution

# Test categories to execute
RELEASE_TEST_CATEGORIES=unit,integration,e2e,visual,a11y

# Coverage thresholds
RELEASE_COVERAGE_THRESHOLD=80

# Visual regression threshold
RELEASE_VISUAL_THRESHOLD=0.1
```

### CI/CD Integration
- Release branch triggers must set `RELEASE_READINESS_MODE=execution`
- Evidence-only mode is rejected for production releases
- Test results must be uploaded as artifacts
- Test execution logs must be retained for audit

## Validation Checks

### Pre-Release Validation
1. Verify `RELEASE_READINESS_MODE=execution` is set
2. Verify all required test categories are enabled
3. Verify coverage thresholds are configured
4. Verify test execution environment is provisioned

### Post-Execution Validation
1. Verify all tests executed (no skipped categories)
2. Verify all tests passed (no failures)
3. Verify coverage thresholds met
4. Verify visual regression within tolerance
5. Verify accessibility compliance met
6. Verify test artifacts uploaded

## Failure Handling

### Test Failure
- **Action**: Block release
- **Notification**: Alert release team with test results
- **Remediation**: Fix failing tests or add exception with approval

### Coverage Below Threshold
- **Action**: Block release or require approval
- **Notification**: Alert with coverage report
- **Remediation**: Add tests or document exception

### Execution Timeout
- **Action**: Block release
- **Notification**: Alert with timeout details
- **Remediation**: Optimize tests or increase timeout with approval

## Evidence vs Execution

### Evidence-Only Checks (Deprecated)
- ❌ Check for screenshot file existence
- ❌ Check for log file existence
- ❌ Check for report file existence
- ❌ Verify file timestamps

### Execution Checks (Required)
- ✅ Execute unit tests and verify pass/fail
- ✅ Execute integration tests and verify pass/fail
- ✅ Execute E2E tests and verify pass/fail
- ✅ Calculate and verify coverage metrics
- ✅ Run visual regression and verify pixel difference
- ✅ Run accessibility tests and verify compliance

## Migration Path

### Existing Evidence-Only Gates
1. Add test execution to existing gates
2. Configure execution mode variables
3. Validate test results instead of evidence files
4. Remove evidence-only validation after migration

### Example Migration
```bash
# Before (evidence-only)
if [ -f "screenshots/test.png" ]; then
  echo "Evidence found, passing release gate"
fi

# After (execution mode)
npm run test:e2e
if [ $? -eq 0 ]; then
  echo "Tests passed, release gate satisfied"
else
  echo "Tests failed, blocking release"
  exit 1
fi
```

## References

- CI/CD Configuration: `.github/workflows/`
- Test Configuration: `vitest.config.ts`, `playwright.config.ts`
- Coverage Configuration: `vitest.shared.config.ts`
- Release Scripts: `scripts/deployment/`
