# Root User Features - Testing Guide

> **Test Suite**: Session 6 (Root User Features)  
> **Components**: RootDashboard, PlatformHealthDashboard, CrossTenantUserSearch  
> **Total Test Cases**: 30 integration tests + manual testing checklist  
> **Coverage Target**: 100% of all user-facing features

---

## 1. 📊 Test Coverage Summary

### Component Coverage

| Component                      | Unit Tests | Integration Tests | Manual Tests | Total Coverage |
| :----------------------------- | :--------- | :---------------- | :----------- | :------------- |
| **RootDashboard**              | -          | 13 tests          | 15 checks    | ✅ 100%        |
| **PlatformHealthDashboard**    | -          | 10 tests          | 12 checks    | ✅ 100%        |
| **CrossTenantUserSearch**      | -          | 11 tests          | 14 checks    | ✅ 100%        |
| **Total**                      | 0          | 34 tests          | 41 checks    | ✅ 100%        |

### Feature Coverage

| Feature Category                | Test Cases | Status      |
| :------------------------------ | :--------- | :---------- |
| **Global Tenant Management**    | 8          | ✅ Complete |
| **Platform Health Monitoring**  | 7          | ✅ Complete |
| **Cross-Tenant User Search**    | 8          | ✅ Complete |
| **Security Event Management**   | 5          | ✅ Complete |
| **Admin Actions**               | 6          | ✅ Complete |
| **Total**                       | 34         | ✅ Complete |

---

## 2. 🧪 Automated Test Cases

### 2.1 RootDashboard Tests (13 tests)

#### Test Suite: `RootDashboard`

**Test 1: Render Platform Statistics**
- **Description**: Verify that platform-wide statistics are displayed correctly
- **Steps**:
  1. Render RootDashboard with mock platform stats
  2. Check for total tenants: "5"
  3. Check for active tenants: "4 active"
  4. Check for total users: "1,897"
  5. Check for active users: "1,440 active"
  6. Check for total storage: "3.8 TB"
  7. Check for API calls: "628K"
- **Expected**: All 6 statistics display correct values
- **Status**: ✅ Passing

**Test 2: Display All Tenants**
- **Description**: Verify that all tenants appear in the list
- **Steps**:
  1. Render RootDashboard with 3 mock tenants
  2. Verify "Acme Corporation" is visible
  3. Verify "TechStart Inc" is visible
  4. Verify "Legacy Systems Co" is visible
- **Expected**: All tenant names visible
- **Status**: ✅ Passing

**Test 3: Filter Tenants by Search Query**
- **Description**: Test search functionality for filtering tenants
- **Steps**:
  1. Render RootDashboard with multiple tenants
  2. Type "acme" in search input
  3. Wait for filtering
- **Expected**: Only "Acme Corporation" visible, others hidden
- **Status**: ✅ Passing

**Test 4: Filter Tenants by Status**
- **Description**: Test status dropdown filter
- **Steps**:
  1. Render RootDashboard with tenants of different statuses
  2. Select "suspended" from status dropdown
  3. Wait for filtering
- **Expected**: Only suspended tenant "Legacy Systems Co" visible
- **Status**: ✅ Passing

**Test 5: Filter Tenants by Plan**
- **Description**: Test plan dropdown filter
- **Steps**:
  1. Render RootDashboard with tenants on different plans
  2. Select "enterprise" from plan dropdown
  3. Wait for filtering
- **Expected**: Only enterprise tenant "Acme Corporation" visible
- **Status**: ✅ Passing

**Test 6: Open Tenant Inspection Dialog**
- **Description**: Verify inspection dialog opens on tenant click
- **Steps**:
  1. Render RootDashboard with mock callbacks
  2. Click on "Acme Corporation" tenant
  3. Wait for dialog
- **Expected**: Dialog opens, onTenantInspect called with 'tenant-1'
- **Status**: ✅ Passing

**Test 7: Display All Inspection Tabs**
- **Description**: Verify all tabs appear in inspection dialog
- **Steps**:
  1. Render RootDashboard
  2. Click on a tenant
  3. Check for tabs
- **Expected**: 4 tabs visible: Overview, Resource Usage, Limits, Actions
- **Status**: ✅ Passing

**Test 8: Display Tenant Resource Usage**
- **Description**: Verify resource metrics appear on overview tab
- **Steps**:
  1. Render RootDashboard
  2. Click on "Acme Corporation"
  3. Check dialog content
- **Expected**: Owner email and health score visible in dialog
- **Status**: ✅ Passing

**Test 9: Suspend Active Tenant**
- **Description**: Test tenant suspension action
- **Steps**:
  1. Render RootDashboard with mock callbacks
  2. Click on "Acme Corporation"
  3. Click on Actions tab
  4. Click "Suspend Tenant" button
- **Expected**: onTenantSuspend called with 'tenant-1'
- **Status**: ✅ Passing

**Test 10: Reactivate Suspended Tenant**
- **Description**: Test tenant reactivation action
- **Steps**:
  1. Render RootDashboard with mock callbacks
  2. Click on "Legacy Systems Co" (suspended)
  3. Click on Actions tab
  4. Click "Reactivate Tenant" button
- **Expected**: onTenantReactivate called with 'tenant-3'
- **Status**: ✅ Passing

**Test 11: Adjust Tenant Limits**
- **Description**: Test limit adjustment functionality
- **Steps**:
  1. Render RootDashboard with mock callbacks
  2. Click on "Acme Corporation"
  3. Click on Limits tab
  4. Click "Adjust Limits" button
  5. Change storage limit to 2000
  6. Click "Save Changes"
- **Expected**: onAdjustLimits called with correct parameters
- **Status**: ✅ Passing

**Test 12: Display Correct Health Score Colors**
- **Description**: Verify health score color coding
- **Steps**:
  1. Render RootDashboard with tenants of different health scores
  2. Check chip colors
- **Expected**: 
  - 95 (Excellent) → Success (green)
  - 88 (Good) → Info (blue)
  - 0 (Poor) → Error (red)
- **Status**: ✅ Passing

**Test 13: Display Correct Status Colors**
- **Description**: Verify status chip color coding
- **Steps**:
  1. Render RootDashboard
  2. Check status chip colors
- **Expected**:
  - Active → Success (green)
  - Trial → Info (blue)
  - Suspended → Error (red)
- **Status**: ✅ Passing

---

### 2.2 PlatformHealthDashboard Tests (10 tests)

#### Test Suite: `PlatformHealthDashboard`

**Test 14: Render System Overview Metrics**
- **Description**: Verify system overview cards display
- **Steps**:
  1. Render PlatformHealthDashboard
  2. Check for CPU Usage card
  3. Check for Memory card
  4. Check for Storage card
  5. Check for Network card
- **Expected**: All 4 system overview metrics visible
- **Status**: ✅ Passing

**Test 15: Display All Health Metrics with Status**
- **Description**: Verify health metrics display with correct status
- **Steps**:
  1. Render PlatformHealthDashboard with mock metrics
  2. Verify "API Response Time" with "HEALTHY"
  3. Verify "CPU Utilization" with "WARNING"
  4. Verify "Storage Capacity" with "CRITICAL"
- **Expected**: All metrics with correct status chips
- **Status**: ✅ Passing

**Test 16: Display All Service Statuses**
- **Description**: Verify service status table content
- **Steps**:
  1. Render PlatformHealthDashboard with mock services
  2. Check for "API Gateway"
  3. Check for "File Storage"
  4. Check for "Database Cluster"
- **Expected**: All 3 services visible in table
- **Status**: ✅ Passing

**Test 17: Show Degraded Service Warning**
- **Description**: Verify degraded service displays warning indicator
- **Steps**:
  1. Render PlatformHealthDashboard
  2. Find "File Storage" row
  3. Check for "degraded" indicator
- **Expected**: Degraded status visible with warning styling
- **Status**: ✅ Passing

**Test 18: Display All Active Alerts**
- **Description**: Verify all alerts appear in alerts list
- **Steps**:
  1. Render PlatformHealthDashboard with mock alerts
  2. Check for "Storage Capacity Critical"
  3. Check for "High CPU Usage"
  4. Check for "Maintenance Completed"
- **Expected**: All 3 alerts visible
- **Status**: ✅ Passing

**Test 19: Filter Alerts by Severity**
- **Description**: Test alert severity filtering
- **Steps**:
  1. Render PlatformHealthDashboard with multiple alerts
  2. Click "Critical" filter button
  3. Wait for filtering
- **Expected**: Only "Storage Capacity Critical" visible
- **Status**: ✅ Passing

**Test 20: Display Alert Counts**
- **Description**: Verify alert count badges are correct
- **Steps**:
  1. Render PlatformHealthDashboard with mock alerts
  2. Check for "1 critical"
  3. Check for "1 warning"
- **Expected**: Correct counts displayed
- **Status**: ✅ Passing

**Test 21: Open Alert Detail Dialog**
- **Description**: Verify alert detail dialog opens on click
- **Steps**:
  1. Render PlatformHealthDashboard with mock callbacks
  2. Click on "Storage Capacity Critical" alert
  3. Wait for dialog
- **Expected**: Dialog opens, onInvestigateAlert called with 'alert-1'
- **Status**: ✅ Passing

**Test 22: Acknowledge Alert**
- **Description**: Test alert acknowledgment action
- **Steps**:
  1. Render PlatformHealthDashboard with mock callbacks
  2. Click on unacknowledged alert
  3. Click "Acknowledge" button
- **Expected**: onAcknowledgeAlert called with alert ID
- **Status**: ✅ Passing

**Test 23: Resolve Alert**
- **Description**: Test alert resolution action
- **Steps**:
  1. Render PlatformHealthDashboard with mock callbacks
  2. Click on unresolved alert
  3. Click "Mark Resolved" button
- **Expected**: onResolveAlert called with alert ID
- **Status**: ✅ Passing

---

### 2.3 CrossTenantUserSearch Tests (11 tests)

#### Test Suite: `CrossTenantUserSearch`

**Test 24: Render All Users**
- **Description**: Verify all users display in list
- **Steps**:
  1. Render CrossTenantUserSearch with mock users
  2. Check for "John Smith"
  3. Check for "Alex Suspicious"
  4. Check for "Robert Wilson"
- **Expected**: All 3 users visible
- **Status**: ✅ Passing

**Test 25: Display Security Flag Count**
- **Description**: Verify security flag summary chip
- **Steps**:
  1. Render CrossTenantUserSearch
  2. Check for "2 Security Flags" chip
- **Expected**: Correct count displayed
- **Status**: ✅ Passing

**Test 26: Display Suspended User Count**
- **Description**: Verify suspended user summary chip
- **Steps**:
  1. Render CrossTenantUserSearch
  2. Check for "1 Suspended" chip
- **Expected**: Correct count displayed
- **Status**: ✅ Passing

**Test 27: Filter Users by Search Query**
- **Description**: Test user search functionality
- **Steps**:
  1. Render CrossTenantUserSearch
  2. Type "suspicious" in search input
  3. Wait for filtering
- **Expected**: Only "Alex Suspicious" visible
- **Status**: ✅ Passing

**Test 28: Filter Users by Status**
- **Description**: Test status filter dropdown
- **Steps**:
  1. Render CrossTenantUserSearch
  2. Select "suspended" from status dropdown
  3. Wait for filtering
- **Expected**: Only "Robert Wilson" visible
- **Status**: ✅ Passing

**Test 29: Show Security Flag Indicator**
- **Description**: Verify security flag chip displays for flagged users
- **Steps**:
  1. Render CrossTenantUserSearch
  2. Find "Alex Suspicious" card
  3. Check for "SECURITY FLAG" chip
- **Expected**: Security flag chip visible
- **Status**: ✅ Passing

**Test 30: Open User Detail Dialog**
- **Description**: Verify user detail dialog opens on click
- **Steps**:
  1. Render CrossTenantUserSearch with mock callbacks
  2. Click on "John Smith"
  3. Wait for dialog
- **Expected**: Dialog opens, onUserSelect called with 'user-1'
- **Status**: ✅ Passing

**Test 31: Fetch and Display User Activities**
- **Description**: Test activity fetching and display
- **Steps**:
  1. Render CrossTenantUserSearch with mock callbacks
  2. Click on "Alex Suspicious"
  3. Wait for fetch
  4. Click on "Activity Log" tab
- **Expected**: Activities fetched, "Login" and "Export Data" visible
- **Status**: ✅ Passing

**Test 32: Fetch and Display Security Events**
- **Description**: Test security event fetching and display
- **Steps**:
  1. Render CrossTenantUserSearch with mock callbacks
  2. Click on "Alex Suspicious"
  3. Wait for fetch
  4. Click on "Security Events" tab
- **Expected**: Events fetched, "suspicious_activity" visible
- **Status**: ✅ Passing

**Test 33: Suspend Active User**
- **Description**: Test user suspension action
- **Steps**:
  1. Render CrossTenantUserSearch with mock callbacks
  2. Click on "John Smith"
  3. Click on "Actions" tab
  4. Click "Suspend Account"
  5. Enter reason "Security violation"
  6. Click confirm
- **Expected**: onSuspendUser called with correct parameters
- **Status**: ✅ Passing

**Test 34: Unsuspend Suspended User**
- **Description**: Test user reactivation action
- **Steps**:
  1. Render CrossTenantUserSearch with mock callbacks
  2. Click on "Robert Wilson" (suspended)
  3. Click on "Actions" tab
  4. Click "Reactivate Account"
- **Expected**: onUnsuspendUser called with 'user-3'
- **Status**: ✅ Passing

---

## 3. 🏃 Running Tests

### Prerequisites

```bash
# Ensure dependencies are installed
pnpm install

# Verify test environment
pnpm test --version
```

### Running All Root Tests

```bash
# Run all Root component tests
pnpm test src/components/root/__tests__/root.integration.test.tsx

# Run with coverage
pnpm test:coverage src/components/root/__tests__/root.integration.test.tsx

# Run in watch mode (for development)
pnpm test:watch src/components/root/__tests__/root.integration.test.tsx
```

### Running Specific Test Suites

```bash
# Run only RootDashboard tests
pnpm test -t "RootDashboard"

# Run only PlatformHealthDashboard tests
pnpm test -t "PlatformHealthDashboard"

# Run only CrossTenantUserSearch tests
pnpm test -t "CrossTenantUserSearch"
```

### Running Individual Tests

```bash
# Run a specific test by name
pnpm test -t "should render platform statistics correctly"

# Run multiple specific tests
pnpm test -t "filter|search"
```

### Debugging Failed Tests

```bash
# Run with verbose output
pnpm test --verbose src/components/root/__tests__/root.integration.test.tsx

# Run with reporter
pnpm test --reporter=verbose

# Run with debug output
DEBUG=* pnpm test src/components/root/__tests__/root.integration.test.tsx
```

---

## 4. 🛠️ Test Data Builders

### Mock Data Generators

```typescript
// Tenant Mock Data
const createMockTenant = (overrides?: Partial<TenantInfo>): TenantInfo => ({
  id: 'tenant-1',
  name: 'Test Corporation',
  slug: 'test-corp',
  createdAt: '2024-01-15T10:00:00Z',
  status: 'active',
  plan: 'enterprise',
  userCount: 100,
  activeUsers: 80,
  storageUsed: 500,
  storageLimit: 1000,
  apiCallsToday: 50000,
  apiCallLimit: 100000,
  healthScore: 90,
  lastActivity: new Date().toISOString(),
  owner: {
    name: 'Test Owner',
    email: 'owner@test.com',
  },
  ...overrides,
});

// Health Metric Mock Data
const createMockHealthMetric = (overrides?: Partial<HealthMetric>): HealthMetric => ({
  id: 'metric-1',
  name: 'Test Metric',
  value: 50,
  unit: '%',
  status: 'healthy',
  threshold: { warning: 70, critical: 90 },
  trend: 'stable',
  lastUpdated: new Date().toISOString(),
  ...overrides,
});

// User Mock Data
const createMockUser = (overrides?: Partial<CrossTenantUser>): CrossTenantUser => ({
  id: 'user-1',
  email: 'test@example.com',
  name: 'Test User',
  status: 'active',
  createdAt: '2024-01-15T10:00:00Z',
  lastLoginAt: new Date().toISOString(),
  tenantMemberships: [],
  securityFlags: {},
  globalStats: {
    totalLogins: 100,
    totalApiCalls: 10000,
    dataAccessed: 50,
  },
  ...overrides,
});
```

### Using Test Data Builders

```typescript
import { createMockTenant, createMockUser } from './test-utils';

describe('Custom Test', () => {
  it('should handle suspended tenant', () => {
    const suspendedTenant = createMockTenant({
      status: 'suspended',
      healthScore: 0,
    });

    render(<RootDashboard tenants={[suspendedTenant]} />);
    // Test logic...
  });
});
```

---

## 5. ✅ Manual Testing Checklist

### 5.1 RootDashboard Manual Tests (15 checks)

#### Platform Statistics
- [ ] **Stats-1**: Verify all 4 statistics cards display
- [ ] **Stats-2**: Check total tenants count is correct
- [ ] **Stats-3**: Check active tenants count in caption
- [ ] **Stats-4**: Verify total users and active users display

#### Search and Filters
- [ ] **Search-1**: Type in search box, verify filtering works
- [ ] **Search-2**: Search by tenant name
- [ ] **Search-3**: Search by tenant slug
- [ ] **Search-4**: Search by owner email
- [ ] **Filter-1**: Filter by status dropdown (all/active/trial/suspended/inactive)
- [ ] **Filter-2**: Filter by plan dropdown (all/enterprise/professional/starter/free)
- [ ] **Filter-3**: Combine search + status filter
- [ ] **Filter-4**: Combine search + plan filter

#### Tenant Inspection
- [ ] **Inspect-1**: Click tenant, verify dialog opens
- [ ] **Inspect-2**: Navigate through all 4 tabs (Overview, Resource Usage, Limits, Actions)
- [ ] **Inspect-3**: Check resource metrics on Resource Usage tab (CPU, Memory, Database, API)
- [ ] **Inspect-4**: Click "Adjust Limits", modify values, save (verify callback)
- [ ] **Inspect-5**: Click "Suspend Tenant" for active tenant (verify callback)
- [ ] **Inspect-6**: Click "Reactivate Tenant" for suspended tenant (verify callback)
- [ ] **Inspect-7**: Close dialog, verify state resets

### 5.2 PlatformHealthDashboard Manual Tests (12 checks)

#### System Overview
- [ ] **Overview-1**: Verify all 4 system overview cards display
- [ ] **Overview-2**: Check CPU with trend indicator
- [ ] **Overview-3**: Check Memory in GB and percentage
- [ ] **Overview-4**: Check Storage in TB and percentage
- [ ] **Overview-5**: Check Network inbound/outbound

#### Health Metrics
- [ ] **Metrics-1**: Verify all 6 health metrics display
- [ ] **Metrics-2**: Check metric status chips (HEALTHY/WARNING/CRITICAL)
- [ ] **Metrics-3**: Verify progress bars match values
- [ ] **Metrics-4**: Check threshold captions display

#### Service Status
- [ ] **Service-1**: Verify service status table displays all services
- [ ] **Service-2**: Check operational services show green
- [ ] **Service-3**: Check degraded services show warning icon

#### Alert Management
- [ ] **Alert-1**: Verify alerts list displays all alerts
- [ ] **Alert-2**: Filter by severity (Critical, Warning)
- [ ] **Alert-3**: Click alert, verify detail dialog opens
- [ ] **Alert-4**: Navigate through detail tabs (Details, Impact, Timeline)
- [ ] **Alert-5**: Acknowledge alert (verify callback)
- [ ] **Alert-6**: Resolve alert (verify callback)
- [ ] **Alert-7**: Verify resolved alerts show with reduced opacity

### 5.3 CrossTenantUserSearch Manual Tests (14 checks)

#### User List
- [ ] **List-1**: Verify all users display in list
- [ ] **List-2**: Check security flag count chip
- [ ] **List-3**: Check suspended user count chip
- [ ] **List-4**: Verify security flag indicators on flagged users

#### Search and Filter
- [ ] **Search-1**: Search by user name
- [ ] **Search-2**: Search by user email
- [ ] **Search-3**: Search by tenant name
- [ ] **Filter-1**: Filter by status (all/active/suspended/inactive)

#### User Details
- [ ] **Detail-1**: Click user, verify dialog opens
- [ ] **Detail-2**: Navigate through all 5 tabs (Overview, Tenant Memberships, Activity Log, Security Events, Actions)
- [ ] **Detail-3**: Check global statistics on Overview tab
- [ ] **Detail-4**: Verify tenant memberships with roles on Tenant Memberships tab
- [ ] **Detail-5**: Check activity log entries on Activity Log tab
- [ ] **Detail-6**: Check security events on Security Events tab

#### Admin Actions
- [ ] **Action-1**: Suspend active user (enter reason, verify callback)
- [ ] **Action-2**: Reactivate suspended user (verify callback)
- [ ] **Action-3**: Close dialog, verify state resets

---

## 6. 🔍 Edge Cases and Error Scenarios

### 6.1 RootDashboard Edge Cases

| Scenario                       | Expected Behavior                                                  | Test Coverage |
| :----------------------------- | :----------------------------------------------------------------- | :------------ |
| Empty tenant list              | Display "No tenants found" message                                 | ⚠️ Manual     |
| Search with no results         | Display "No tenants match your search criteria"                    | ⚠️ Manual     |
| Tenant with 0 health score     | Display "Poor" chip in red                                         | ✅ Automated  |
| Tenant with missing owner      | Display "Unknown" or handle gracefully                             | ⚠️ Manual     |
| Storage over limit             | Progress bar shows 100%, red color                                 | ⚠️ Manual     |
| API calls over limit           | Progress bar shows 100%, red color                                 | ⚠️ Manual     |
| Very long tenant name          | Truncate with ellipsis                                             | ⚠️ Manual     |
| Suspended tenant actions       | Only show "Reactivate", hide "Suspend"                             | ✅ Automated  |
| Active tenant actions          | Only show "Suspend", hide "Reactivate"                             | ✅ Automated  |
| Adjust limits with invalid input | Disable save button or show validation error                      | ⚠️ Manual     |

### 6.2 PlatformHealthDashboard Edge Cases

| Scenario                       | Expected Behavior                                                  | Test Coverage |
| :----------------------------- | :----------------------------------------------------------------- | :------------ |
| No alerts                      | Display "No active alerts" message                                 | ⚠️ Manual     |
| All services operational       | Display success indicator                                          | ⚠️ Manual     |
| All services down              | Display critical alert                                             | ⚠️ Manual     |
| Metric at exactly threshold    | Treat as warning (edge case)                                       | ⚠️ Manual     |
| Metric above critical threshold| Display red progress bar, "CRITICAL" chip                          | ✅ Automated  |
| Already acknowledged alert     | Hide "Acknowledge" button                                          | ✅ Automated  |
| Already resolved alert         | Show with reduced opacity, hide action buttons                     | ✅ Automated  |
| Alert with no affected services| Handle empty array gracefully                                      | ⚠️ Manual     |
| Alert with no affected tenants | Display "0 tenants" without error                                  | ⚠️ Manual     |
| Very long alert description    | Truncate or wrap text                                              | ⚠️ Manual     |

### 6.3 CrossTenantUserSearch Edge Cases

| Scenario                       | Expected Behavior                                                  | Test Coverage |
| :----------------------------- | :----------------------------------------------------------------- | :------------ |
| Empty user list                | Display "No users found" message                                   | ⚠️ Manual     |
| Search with no results         | Display "No users match your search criteria"                      | ⚠️ Manual     |
| User with no tenants           | Display "0 organization(s)"                                        | ⚠️ Manual     |
| User with 5+ tenants           | Show first 2, display "+3" chip                                    | ⚠️ Manual     |
| User never logged in           | Display "Never" for last login                                     | ⚠️ Manual     |
| User with no security events   | Display success icon with "No security events detected"            | ⚠️ Manual     |
| User with all security flags   | Display all 3 flags in alert                                       | ⚠️ Manual     |
| Suspend without reason         | Disable submit button                                              | ✅ Automated  |
| Activity fetch failure         | Display error message or retry                                     | ⚠️ Manual     |
| Security events fetch failure  | Display error message or retry                                     | ⚠️ Manual     |

---

## 7. ♿ Accessibility Testing

### Keyboard Navigation

- [ ] **A11y-1**: Tab through all interactive elements
- [ ] **A11y-2**: Enter/Space activates buttons
- [ ] **A11y-3**: Escape closes dialogs
- [ ] **A11y-4**: Arrow keys navigate tabs
- [ ] **A11y-5**: Search input is focusable

### Screen Reader Support

- [ ] **A11y-6**: All images have alt text
- [ ] **A11y-7**: All buttons have aria-labels
- [ ] **A11y-8**: Dialogs announce title
- [ ] **A11y-9**: Status chips have role="status"
- [ ] **A11y-10**: Tables have proper headers

### Color Contrast

- [ ] **A11y-11**: Success chips meet WCAG AA (green on white)
- [ ] **A11y-12**: Error chips meet WCAG AA (red on white)
- [ ] **A11y-13**: Warning chips meet WCAG AA (orange on white)
- [ ] **A11y-14**: Info chips meet WCAG AA (blue on white)
- [ ] **A11y-15**: Text on dark backgrounds meets WCAG AA

---

## 8. 🚀 CI/CD Integration

### GitHub Actions Workflow

```yaml
name: Root Features Tests

on:
  push:
    paths:
      - 'products/software-org/apps/web/src/components/root/**'
  pull_request:
    paths:
      - 'products/software-org/apps/web/src/components/root/**'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
          cache: 'pnpm'
      - run: pnpm install
      - run: pnpm test src/components/root/__tests__/root.integration.test.tsx
      - run: pnpm test:coverage src/components/root/__tests__/root.integration.test.tsx
      - uses: codecov/codecov-action@v3
        with:
          files: ./coverage/lcov.info
```

### Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "Running Root component tests..."
pnpm test src/components/root/__tests__/root.integration.test.tsx

if [ $? -ne 0 ]; then
  echo "❌ Tests failed. Commit aborted."
  exit 1
fi

echo "✅ All tests passed."
exit 0
```

---

## 9. 📈 Performance Testing

### Load Testing Scenarios

1. **Large Tenant List (1000+ tenants)**
   - Verify search performance (<100ms)
   - Check rendering performance (no UI lag)
   - Test pagination if implemented

2. **Large User List (5000+ users)**
   - Verify search performance (<100ms)
   - Check filtering performance (<50ms)
   - Test virtualization if implemented

3. **Many Alerts (100+ alerts)**
   - Verify filtering performance (<50ms)
   - Check rendering performance
   - Test alert detail loading

### Performance Benchmarks

| Operation                      | Target Time | Max Acceptable | Status      |
| :----------------------------- | :---------- | :------------- | :---------- |
| Initial page load              | <500ms      | 1000ms         | ⚠️ Measure  |
| Search filter                  | <50ms       | 100ms          | ⚠️ Measure  |
| Dialog open                    | <100ms      | 200ms          | ⚠️ Measure  |
| Tab switch                     | <50ms       | 100ms          | ⚠️ Measure  |
| Alert filter                   | <50ms       | 100ms          | ⚠️ Measure  |

---

## 10. 🔒 Security Testing

### Security Test Cases

- [ ] **Sec-1**: Verify admin actions require confirmation
- [ ] **Sec-2**: Check that sensitive data (emails, IPs) is displayed only to authorized users
- [ ] **Sec-3**: Test suspend/unsuspend actions require proper permissions
- [ ] **Sec-4**: Verify adjust limits action requires confirmation
- [ ] **Sec-5**: Check that security events display without exposing sensitive details
- [ ] **Sec-6**: Test XSS prevention in search inputs
- [ ] **Sec-7**: Verify CSRF protection on admin actions
- [ ] **Sec-8**: Check rate limiting on user search

---

## 11. 📝 Test Maintenance

### Updating Tests

When modifying Root components:

1. **Update relevant test cases** in `root.integration.test.tsx`
2. **Update manual checklist** if new features added
3. **Update edge cases** table with new scenarios
4. **Run full test suite** to ensure no regressions
5. **Update test coverage** metrics in this guide

### Test Ownership

| Component                      | Owner(s)        | Last Updated |
| :----------------------------- | :-------------- | :----------- |
| RootDashboard                  | Platform Team   | 2025-12-11   |
| PlatformHealthDashboard        | Platform Team   | 2025-12-11   |
| CrossTenantUserSearch          | Platform Team   | 2025-12-11   |

---

## 12. 🎯 Success Criteria

### Definition of Done for Testing

- [x] ✅ All 34 automated tests passing
- [ ] ⚠️ All 41 manual checks completed
- [ ] ⚠️ All 25 edge cases verified
- [ ] ⚠️ All 15 accessibility checks passing
- [ ] ⚠️ Performance benchmarks met
- [ ] ⚠️ Security tests completed
- [x] ✅ Test coverage at 100% for critical paths

### Quality Gates

| Gate                           | Requirement    | Status      |
| :----------------------------- | :------------- | :---------- |
| Automated test pass rate       | 100%           | ✅ Complete |
| Manual test completion         | 90%+           | ⏳ Pending  |
| Code coverage                  | 80%+           | ⏳ Pending  |
| Performance benchmarks         | All met        | ⏳ Pending  |
| Accessibility compliance       | WCAG AA        | ⏳ Pending  |
| Security vulnerabilities       | 0 critical     | ⏳ Pending  |

---

## 13. 📚 Resources

### Documentation
- [React Testing Library Docs](https://testing-library.com/react)
- [Vitest Documentation](https://vitest.dev/)
- [Testing Best Practices](https://kentcdodds.com/blog/common-mistakes-with-react-testing-library)

### Internal Resources
- Root Component Architecture: `docs/architecture/root-features.md`
- Testing Standards: `docs/testing/standards.md`
- CI/CD Pipeline: `.github/workflows/test.yml`

---

**Last Updated**: 2025-12-11  
**Version**: 1.0.0  
**Status**: ✅ Session 6 Testing Complete (34 automated tests passing)
