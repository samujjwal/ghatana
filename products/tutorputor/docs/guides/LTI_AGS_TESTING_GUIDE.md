# LTI AGS Testing Guide

**Date:** 2026-04-17
**Status:** Test Environment Setup Instructions

## Overview

This document provides instructions for setting up test environments and field-testing LTI 1.3 Advantage Grade Services (AGS) integration against Canvas and Moodle learning management systems.

## Prerequisites

### Canvas Test Environment

1. **Canvas Developer Account**
   - Sign up at https://canvas.instructure.com/register
   - Or use institutional Canvas account with developer permissions
   - Enable developer key in account settings

2. **Test Course Setup**
   - Create a test course in Canvas
   - Enable LTI 1.3 for the course
   - Enable AGS (Advantage Grade Services) for the course
   - Note course ID and LTI registration details

### Moodle Test Environment

1. **Moodle Installation**
   - Install Moodle 3.9+ (LTI 1.3 support)
   - Use local installation or cloud provider
   - Enable LTI 1.3 plugin in Moodle

2. **Test Course Setup**
   - Create a test course in Moodle
   - Configure LTI 1.3 tool registration
   - Enable AGS for the course
   - Note course ID and LTI registration details

## Environment Setup

### Canvas Configuration

**Step 1: Register LTI 1.3 Tool**

1. Navigate to Canvas Admin → Developer Keys
2. Create new LTI 1.3 developer key
3. Configure redirect URIs:
   ```
   https://your-tutorputor-platform.com/lti/launch
   https://your-tutorputor-platform.com/lti/redirect
   ```
4. Save the client ID and deployment ID

**Step 2: Enable AGS**

1. Navigate to Course Settings → Navigation
2. Enable "External Tools" navigation item
3. Configure the LTI tool with AGS enabled
4. Note the line item URL for grade passback

**Environment Variables**

```bash
# Canvas LTI Configuration
CANVAS_CLIENT_ID=your_canvas_client_id
CANVAS_DEPLOYMENT_ID=your_canvas_deployment_id
CANVAS_ISSUER=https://canvas.instructure.com
CANVAS_JWKS_URL=https://canvas.instructure.com/api/lti/security/jwks
CANVAS_AUDIENCE=https://canvas.instructure.com/api/lti/authorize
```

### Moodle Configuration

**Step 1: Install LTI 1.3 Plugin**

```bash
cd moodle/mod/lti
git clone https://github.com/moodle/moodle-mod_lti.git
cd moodle-mod_lti
git checkout MOODLE_39_STABLE
```

**Step 2: Configure LTI 1.3 Tool**

1. Navigate to Site Administration → Plugins → Activity modules → LTI
2. Add new LTI external tool
3. Configure platform settings:
   - Platform ID: your-platform-id
   - Client ID: your-client-id
   - Authorization endpoint: https://your-platform.com/lti/auth
   - Token endpoint: https://your-platform.com/lti/token
   - JWKS endpoint: https://your-platform.com/.well-known/jwks.json

**Step 3: Enable AGS**

1. In LTI tool configuration, enable "Accept grades" (AGS)
2. Configure grade line item settings
3. Note the line item URL

**Environment Variables**

```bash
# Moodle LTI Configuration
MOODLE_PLATFORM_ID=your_moodle_platform_id
MOODLE_CLIENT_ID=your_moodle_client_id
MOODLE_ISSUER=https://your-moodle-instance.com
MOODLE_JWKS_URL=https://your-moodle-instance.com/mod/lti/certs.php
MOODLE_AUDIENCE=https://your-moodle-instance.com/mod/lti/token.php
```

## Test Suite Creation

### Test Structure

```
services/tutorputor-platform/src/modules/integration/lti/__tests__/
├── ags-field-test.ts
├── canvas-ags.test.ts
├── moodle-ags.test.ts
└── fixtures/
    ├── canvas-course.json
    └── moodle-course.json
```

### Test Cases

#### Canvas AGS Tests

```typescript
describe('Canvas AGS Integration', () => {
  it('should create a line item', async () => {
    const lineItem = await createCanvasLineItem({
      label: 'Test Assignment',
      scoreMaximum: 100,
      resourceId: 'assignment-123',
    });

    expect(lineItem.id).toBeDefined();
    expect(lineItem.label).toBe('Test Assignment');
  });

  it('should pass back a score', async () => {
    const score = {
      userId: 'student-123',
      scoreGiven: 85,
      scoreMaximum: 100,
      timestamp: new Date(),
    };

    const result = await passbackCanvasScore(lineItemId, score);

    expect(result.success).toBe(true);
  });

  it('should update a score', async () => {
    const updatedScore = {
      userId: 'student-123',
      scoreGiven: 90,
      scoreMaximum: 100,
      timestamp: new Date(),
    };

    const result = await passbackCanvasScore(lineItemId, updatedScore);

    expect(result.success).toBe(true);
  });
});
```

#### Moodle AGS Tests

```typescript
describe('Moodle AGS Integration', () => {
  it('should create a line item', async () => {
    const lineItem = await createMoodleLineItem({
      label: 'Test Assignment',
      scoreMaximum: 100,
      resourceId: 'assignment-123',
    });

    expect(lineItem.id).toBeDefined();
  });

  it('should pass back a score', async () => {
    const score = {
      userId: 'student-123',
      scoreGiven: 85,
      scoreMaximum: 100,
      timestamp: new Date(),
    };

    const result = await passbackMoodleScore(lineItemId, score);

    expect(result.success).toBe(true);
  });
});
```

## Execution

### Running Canvas Tests

```bash
# Set Canvas environment variables
export CANVAS_CLIENT_ID=your_canvas_client_id
export CANVAS_DEPLOYMENT_ID=your_canvas_deployment_id

# Run tests
pnpm test -- canvas-ags.test.ts
```

### Running Moodle Tests

```bash
# Set Moodle environment variables
export MOODLE_PLATFORM_ID=your_moodle_platform_id
export MOODLE_CLIENT_ID=your_moodle_client_id

# Run tests
pnpm test -- moodle-ags.test.ts
```

## Expected Results

### Success Criteria

**Canvas:**
- Line item creation succeeds
- Score passback succeeds
- Score updates reflect in Canvas gradebook
- No authentication errors
- Grades appear within 5 seconds

**Moodle:**
- Line item creation succeeds
- Score passback succeeds
- Score updates reflect in Moodle gradebook
- No authentication errors
- Grades appear within 5 seconds

### Known Issues

**Canvas:**
- May require additional scopes for AGS
- Line item URLs may differ by Canvas version
- Grade passback may be delayed during peak hours

**Moodle:**
- LTI 1.3 plugin version compatibility
- AGS support varies by Moodle version
- May require additional configuration for multi-tenancy

## Troubleshooting

### Common Issues

**"Invalid client credentials"**
- Check client ID and deployment ID
- Verify issuer and audience URLs
- Ensure developer key is active

**"Line item not found"**
- Verify line item ID is correct
- Check line item exists in LMS
- Ensure AGS is enabled for course

**"Score passback failed"**
- Check user enrollment in course
- Verify line item is active
- Check LTI tool permissions

### Debug Mode

Enable debug logging:
```bash
LOG_LEVEL=debug pnpm test -- ags-field-test.ts
```

## Documentation

After testing, document:

1. Platform-specific quirks and workarounds
2. Required LTI scopes and permissions
3. Grade passback latency
4. Error handling requirements
5. Configuration differences between platforms

## Next Steps

1. Set up Canvas test environment
2. Set up Moodle test environment
3. Create test suite
4. Execute tests
5. Document results
6. Update LTI integration guide with platform-specific notes
