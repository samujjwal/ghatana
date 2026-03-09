# Guardian - Access Status Tracking & Dashboard

**Version**: 2.1 (Enhanced with Allowed/Blocked/Temporarily Blocked)  
**Date**: November 23, 2025  
**Build Status**: ✅ Complete

---

## Overview

Guardian now provides **comprehensive access status tracking** similar to the Web Activity Time Tracker reference project. The dashboard displays detailed information about allowed, blocked, and temporarily blocked websites with precise metrics and visual indicators.

---

## Key Features

### 1. **Access Status Classification**

Every website visit is now tracked with one of three statuses:

#### **✅ Allowed (Green)**

- Websites you can freely access
- No restrictions applied
- Full time tracking enabled
- Default status for all websites

#### **🚫 Blocked (Red)**

- Websites completely blocked from access
- Cannot be visited during active session
- Blocked attempts are tracked
- Shows block count and percentage

#### **⏱️ Temporarily Blocked (Yellow)**

- Websites restricted for a limited time period
- Can be accessed again after time expires
- Temporary block attempts tracked
- Shows temporary block count and percentage

---

## Dashboard Components

### **Status Summary Cards** (Top Row)

```
[Status] [✅ Allowed] [🚫 Blocked] [⏱️ Temp Blocked]
  Active    23 sites    2 sites       1 site
```

Each card shows:

- 🎯 **Icon**: Visual indicator of status
- 📊 **Count**: Number of websites in this category
- 📝 **Label**: Status type
- 📍 **Color-coded**: Green/Red/Yellow for quick recognition

### **Top Visited Domains Section** (Left Panel)

**For each domain, displays:**

```
[Rank] Domain Name
├─ Last visited: [Exact Time]
├─ Status Badge: [✅/🚫/⏱️ + Label]
├─ Metrics:
│  ├─ Total Visits: 23
│  ├─ Avg/Visit: 4m 32s
│  ├─ Total Time: 1h 43m
│  └─ Avg/Day: 3 visits
└─ Block Stats (if applicable):
   ├─ 🚫 2 blocked (8%)
   └─ ⏱️ 1 temp block (4%)
```

**Color indicators:**

- Green status badge → Allowed access
- Red status badge → Fully blocked
- Yellow status badge → Temporarily blocked

### **Access Status Summary** (Right Panel)

Three distinct sections showing:

#### **✅ Allowed Websites (Green Box)**

- Count of allowed sites
- List of top allowed domains
- Visit counts for each
- Freely accessible notation

#### **🚫 Blocked Websites (Red Box)**

- Count of blocked sites
- List of blocked domains
- Block attempt counts
- "Completely blocked from access" notation

#### **⏱️ Temporarily Blocked (Yellow Box)**

- Count of temporarily blocked sites
- List of temp-blocked domains
- Temporary block attempt counts
- "Restricted for a period of time" notation

---

## Data Structure

### **WebUsageData Interface**

```typescript
interface WebUsageData {
  timestamp: number; // When recorded
  url: string; // Full URL
  domain: string; // Domain name
  title: string; // Page title
  sessionDuration: number; // Time spent (ms)
  entryTime: number; // Entry timestamp
  status?: AccessStatus; // 'allowed' | 'blocked' | 'temporarily_blocked'
  blockedReason?: string; // Reason if blocked
}
```

### **WebUsageStats Interface**

```typescript
interface WebUsageStats {
  domain: string; // Domain name
  urls: string[]; // All URLs visited
  visitCount: number; // Total visits (allowed)
  blockedCount: number; // Times blocked
  temporarilyBlockedCount: number; // Times temp blocked
  totalTimeSpent: number; // Total time (ms)
  lastVisited: number; // Last visit timestamp
  averageSessionDuration: number; // Avg time per visit
  status: AccessStatus; // Current status
}
```

---

## Metrics & Calculations

### **Block Percentage**

```
blockedPercentage = (blockedCount / visitCount) × 100
Example: 2 blocked out of 25 visits = 8%
```

### **Temporary Block Percentage**

```
tempBlockedPercentage = (temporarilyBlockedCount / visitCount) × 100
Example: 1 temp block out of 25 visits = 4%
```

### **Status Determination**

```
if (blockedCount > 0)           → Status = 'blocked'
else if (temporarilyBlockedCount > 0) → Status = 'temporarily_blocked'
else                            → Status = 'allowed'
```

---

## Visual Design

### **Status Badge Styling**

```
✅ Allowed
├─ Icon: ✅
├─ Background: Green (#dcfce7)
├─ Text: Dark Green (#166534)
└─ Font: Semibold

🚫 Blocked
├─ Icon: 🚫
├─ Background: Red (#fee2e2)
├─ Text: Dark Red (#991b1b)
└─ Font: Semibold

⏱️ Temporarily Blocked
├─ Icon: ⏱️
├─ Background: Yellow (#fef3c7)
├─ Text: Dark Yellow (#92400e)
└─ Font: Semibold
```

### **Layout Structure**

```
Dashboard
├─ Top: Status Cards (Active | Allowed | Blocked | Temp)
├─ Middle: Time Breakdown (24h | 7d | All Time)
└─ Bottom: Two-Column Layout
   ├─ Left: Top Visited Domains (with status badges)
   └─ Right: Access Status Summary (3 boxes)
      ├─ ✅ Allowed (green)
      ├─ 🚫 Blocked (red)
      └─ ⏱️ Temporarily Blocked (yellow)
```

---

## Filtering & Counts

### **Domain List Calculations**

```
Total Allowed = domains.filter(d => d.status !== 'blocked' && d.status !== 'temporarily_blocked').length
Total Blocked = domains.filter(d => d.status === 'blocked').length
Total Temp Blocked = domains.filter(d => d.status === 'temporarily_blocked').length
```

### **Summary Boxes**

Each status section shows:

- Top 5 domains in that category
- Visit/block counts for each
- Scrollable if more than 5

---

## Real-World Example

**Scenario**: User browses for 1 hour with access policies

**Website Activity:**

```
✅ Allowed:
├─ GitHub: 10 visits, 25 min (allowed)
├─ Stack Overflow: 15 visits, 20 min (allowed)
└─ MDN Docs: 8 visits, 15 min (allowed)

🚫 Blocked:
├─ Facebook: 3 blocked attempts
└─ Reddit: 2 blocked attempts

⏱️ Temporarily Blocked:
└─ Twitter: 1 temp block attempt (expires in 30 min)
```

**Dashboard Shows:**

```
Top Cards:
- ✓ Active | ✅ 3 Allowed | 🚫 2 Blocked | ⏱️ 1 Temp Blocked

Top Visited Domains (with statuses):
1. github.com ✅ - 10 visits, 25 min
2. stackoverflow.com ✅ - 15 visits, 20 min
3. mdn.org ✅ - 8 visits, 15 min
4. facebook.com 🚫 - 3 blocked, 0 min
5. reddit.com 🚫 - 2 blocked, 0 min
6. twitter.com ⏱️ - 1 temp block, 0 min

Access Status Summary:
✅ Allowed (3): github.com, stackoverflow.com, mdn.org
🚫 Blocked (2): facebook.com, reddit.com
⏱️ Temp Blocked (1): twitter.com
```

---

## API Changes

### **Message Types**

All existing messages still work:

- `GET_ANALYTICS` - Returns enhanced AnalyticsSummary with status info
- `PAGE_USAGE_TRACKED` - Now includes optional `status` field
- `GET_EVENTS` - Returns all events
- `CLEAR_DATA` - Clears all including status info

### **Response Format**

```typescript
{
  success: true,
  data: {
    // ... existing fields ...
    topDomains: [
      {
        domain: "github.com",
        visitCount: 23,
        blockedCount: 0,              // NEW
        temporarilyBlockedCount: 0,   // NEW
        status: "allowed",            // NEW
        // ... other fields ...
      }
    ],
    recentUsage: [
      {
        // ... existing fields ...
        status: "allowed",            // NEW
        blockedReason: undefined      // NEW
      }
    ]
  }
}
```

---

## Implementation Notes

### **Data Collection**

1. **Content Script**: Tracks all page visits with `status: 'allowed'` by default
2. **GuardianController**: Updates status based on website policies
3. **Dashboard**: Displays aggregated statistics by status

### **Status Assignment Logic**

```typescript
// In GuardianController.ts - getAnalyticsSummary()
for (const record of usage) {
  if (record.status === "blocked") {
    stats.blockedCount += 1;
    stats.status = "blocked";
  } else if (record.status === "temporarily_blocked") {
    stats.temporarilyBlockedCount += 1;
    if (stats.status !== "blocked") {
      stats.status = "temporarily_blocked";
    }
  }
  stats.visitCount += 1;
  // ... rest of aggregation
}
```

### **Backwards Compatibility**

- All existing data without `status` field defaults to `'allowed'`
- UI gracefully handles missing `blockedCount`/`temporarilyBlockedCount`
- All dashboard components render correctly with or without status info

---

## Usage Instructions

### **Viewing Status Information**

1. **Open Dashboard**
   - Click Guardian extension icon → Dashboard

2. **See Status Summary** (Top Row)
   - Quick count of allowed/blocked/temp-blocked sites

3. **Check Individual Domains** (Left Panel)
   - Each domain shows its current status badge
   - Green ✅ = Can visit freely
   - Red 🚫 = Cannot access
   - Yellow ⏱️ = Temporarily restricted

4. **Review Status Categories** (Right Panel)
   - ✅ Allowed: Green box, lists freely accessible sites
   - 🚫 Blocked: Red box, lists permanently blocked sites
   - ⏱️ Temp Blocked: Yellow box, lists temporarily restricted sites

### **Understanding Percentages**

- **8% blocked** = 2 out of 25 visit attempts were blocked
- **4% temp** = 1 out of 25 visit attempts were temporarily blocked
- Shows enforcement frequency

### **Tracking Over Time**

- All metrics update every 5 seconds
- Status badges change in real-time as policies apply
- Full history preserved in storage

---

## Testing Scenarios

### **Scenario 1: Only Allowed Websites**

1. Visit 3 different websites
2. Stay on each for 30+ seconds
3. Dashboard shows:
   - ✅ 3 Allowed
   - 🚫 0 Blocked
   - ⏱️ 0 Temp Blocked
   - All domains green badges

### **Scenario 2: Blocked Website**

1. Visit allowed site: GitHub (1 min)
2. Try to visit blocked site: Facebook (rejected)
3. Dashboard shows:
   - ✅ 1 Allowed (github.com)
   - 🚫 1 Blocked (facebook.com with block count)
   - ⏱️ 0 Temp Blocked

### **Scenario 3: Temporarily Blocked**

1. Visit allowed site: Google (1 min)
2. Try to visit temp-blocked site: Twitter (temporarily restricted)
3. Dashboard shows:
   - ✅ 1 Allowed (google.com)
   - 🚫 0 Blocked
   - ⏱️ 1 Temp Blocked (twitter.com with temp block count)

---

## File Changes

### **Modified Files**

1. **src/controller/GuardianController.ts**
   - Added `AccessStatus` type definition
   - Enhanced `WebUsageData` with status tracking
   - Enhanced `WebUsageStats` with block counting
   - Updated `getAnalyticsSummary()` to aggregate by status

2. **src/dashboard/Dashboard.tsx**
   - Added `getStatusBadge()` helper function
   - Enhanced `DomainStats` interface with status fields
   - Added status summary cards (top row)
   - Enhanced domain list with status badges
   - Added access status summary section (right panel)
   - Improved visual hierarchy with color-coding

---

## Performance Considerations

- **Memory**: Minimal - only stores counts, not detailed block records
- **Calculation**: O(n) aggregation during analytics summary
- **Rendering**: Optimized with memoization for large domain lists
- **Updates**: Every 5 seconds, efficient batch updates

---

## Future Enhancements

Possible additions:

1. **Time-series blocking trends**: Graph blocked attempts over time
2. **Block reasons**: Show WHY websites are blocked
3. **Unblock requests**: UI to temporarily allow blocked sites
4. **Policy enforcement stats**: How often policies are applied
5. **Export reports**: CSV with status breakdown
6. **Notifications**: Alert when blocked sites attempted
7. **Whitelist/Blacklist management**: UI to adjust policies

---

## Summary

Guardian now provides **complete, status-aware web activity monitoring** with three distinct access categories:

✅ **Allowed**: Websites you can freely access  
🚫 **Blocked**: Websites completely restricted  
⏱️ **Temporarily Blocked**: Websites with time-limited restrictions

The dashboard displays this information clearly with:

- Color-coded status badges
- Summary counts for quick overview
- Detailed metrics for each website
- Block attempt tracking
- Easy categorization in summary boxes

**Dashboard is production-ready for comprehensive web monitoring with access control tracking!** 🎉
