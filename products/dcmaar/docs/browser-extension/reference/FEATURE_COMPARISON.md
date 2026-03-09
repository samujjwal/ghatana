# Guardian vs Web Activity Time Tracker - Feature Comparison

**Reference Project**: https://github.com/Stigmatoz/web-activity-time-tracker  
**Guardian Dashboard**: Enhanced with similar information architecture

---

## Information Parity Matrix

### ✅ Implemented (Guardian)

| Feature               | Web Activity Tracker        | Guardian                              | Status      |
| --------------------- | --------------------------- | ------------------------------------- | ----------- |
| **Time Tracking**     | ✅ Hours/Days breakdown     | ✅ 24h/7d/All-time                    | ✅ Complete |
| **Domain Statistics** | ✅ Domain list with metrics | ✅ Top domains with detailed stats    | ✅ Complete |
| **Visit Counting**    | ✅ Visit count per domain   | ✅ Visit count + avg/visit + avg/day  | ✅ Enhanced |
| **Time Spent**        | ✅ Total time per domain    | ✅ Total time + breakdown by period   | ✅ Enhanced |
| **Last Visited**      | ✅ Timestamp                | ✅ Exact datetime                     | ✅ Complete |
| **Blocked Tracking**  | ✅ Block status             | ✅ Blocked + Temporarily Blocked      | ✅ Enhanced |
| **Status Badges**     | ✅ Visual indicators        | ✅ Color-coded (Green/Red/Yellow)     | ✅ Enhanced |
| **Summary View**      | ✅ Overview stats           | ✅ Status categories summary          | ✅ Enhanced |
| **Detailed Activity** | ✅ Activity log             | ✅ Detailed activity with exact times | ✅ Enhanced |

---

## Data Points Shown in Guardian Dashboard

### **Top Card Statistics**

```
✓ Collection Status        (Active/Inactive)
✅ Allowed Count           (How many sites can visit)
🚫 Blocked Count           (How many sites are blocked)
⏱️  Temporarily Blocked    (How many have temp restrictions)
```

### **Time-Based Breakdown**

```
Last 24 Hours:  Visits | Time Spent
Last 7 Days:    Visits | Time Spent
All Time:       Visits | Time Spent
```

### **Per-Domain Information**

```
For each domain:
├─ Rank (1, 2, 3...)
├─ Domain Name
├─ Last Visited Time (exact)
├─ Status Badge (✅/🚫/⏱️)
├─ Total Visits (count)
├─ Avg Per Visit (duration)
├─ Total Time Spent (duration)
├─ Average Per Day (visits/day)
└─ Block Statistics (if applicable):
   ├─ Blocked Count + Percentage
   └─ Temp Blocked Count + Percentage
```

### **Access Status Summary**

```
✅ ALLOWED WEBSITES
├─ Count of allowed sites
├─ List of top 5 with visit counts
└─ "Freely accessible" note

🚫 BLOCKED WEBSITES
├─ Count of blocked sites
├─ List of top 5 with block counts
└─ "Completely blocked from access" note

⏱️  TEMPORARILY BLOCKED
├─ Count of temporarily blocked
├─ List of top 5 with temp block counts
└─ "Restricted for period of time" note
```

### **Detailed Activity Log** (Expandable)

```
For each visit:
├─ Page Title (exact)
├─ Full URL
├─ Duration on Page
├─ Entry Time (exact)
├─ Visit Timestamp (exact)
├─ Relative Time ("5m ago")
└─ Domain
```

---

## Architecture Comparison

### **Web Activity Tracker** (Vue.js)

```
entity/
├─ baseTimeList.ts
├─ deffering.ts
├─ notification.ts
├─ restriction.ts      ← Blocking logic
├─ tab.ts
└─ time-interval.ts

dto/
├─ currentTabItem.ts
├─ daySummary.ts
└─ tabListSummary.ts

components/
├─ Dashboard.vue
├─ WebsiteStats.vue    ← Domain list
├─ TabItem.vue
├─ TabList.vue
├─ WhiteList.vue       ← Access control
└─ Limits.vue          ← Time limits
```

### **Guardian** (TypeScript/React)

```
src/
├─ content/
│  └─ index.ts         ← Tracks all visits
├─ controller/
│  └─ GuardianController.ts
│     └─ Aggregates stats by status
├─ dashboard/
│  └─ Dashboard.tsx    ← Main UI
├─ blocker/
│  └─ WebsiteBlocker.ts ← Blocking logic
└─ popup/
   └─ Popup.tsx
```

---

## Key Data Structures

### **Web Activity Tracker**

```typescript
interface TabItem {
  url: string;
  hostname: string;
  title: string;
  timeSpent: number;
  visitedAt: Date;
  tabId: number;
}

interface Restriction {
  hostname: string;
  type: "blocked" | "limited"; // Only 2 status types
  limitMs?: number;
}
```

### **Guardian** (Enhanced)

```typescript
interface WebUsageData {
  url: string;
  domain: string;
  title: string;
  timestamp: number;
  sessionDuration: number;
  status: "allowed" | "blocked" | "temporarily_blocked"; // 3 status types
  blockedReason?: string;
}

interface WebUsageStats {
  domain: string;
  visitCount: number;
  blockedCount: number; // Counts blocks
  temporarilyBlockedCount: number; // Counts temp blocks
  totalTimeSpent: number;
  status: AccessStatus;
  averageSessionDuration: number;
}
```

---

## Visual Comparison

### **Information Density**

**Guardian Advantages:**

- ✅ Exact page titles (not just domain)
- ✅ Multiple time periods (24h/7d/All-time)
- ✅ Per-visit statistics (avg/visit, avg/day)
- ✅ Block counting and percentages
- ✅ Color-coded status indicators
- ✅ Summary boxes by access status
- ✅ Detailed activity log with timestamps
- ✅ Data freshness indicator

**Guardian Features > Web Activity Tracker:**

1. **Status Levels**: 3 levels (allowed, blocked, temp-blocked) vs 2
2. **Metrics**: More granular (avg/visit, avg/day, percentages)
3. **Time Ranges**: Multiple periods displayed simultaneously
4. **Visual Design**: Modern cards with color-coded status
5. **Activity Detail**: Exact timestamps and expandable details

---

## Information Presentation Comparison

### **Metric Clarity**

| What You Want to Know                | Web Activity     | Guardian                             |
| ------------------------------------ | ---------------- | ------------------------------------ |
| **"Which sites do I visit most?"**   | Top domains list | ✅ Top domains with colored badges   |
| **"How much time on each site?"**    | Time per domain  | ✅ Multiple time periods + avg/visit |
| **"Is a site blocked?"**             | Restriction list | ✅ Status badge + block percentage   |
| **"When was I last there?"**         | Timestamp        | ✅ Last visited + relative time      |
| **"How often do I visit?"**          | Visit count      | ✅ Visit count + avg per day         |
| **"What pages did I view?"**         | Tab history      | ✅ Detailed activity log with titles |
| **"How many times blocked?"**        | Block count      | ✅ Block count + percentage of total |
| **"Temporary vs permanent blocks?"** | No distinction   | ✅ Separate counts and percentages   |

---

## Guardian Enhancements Over Reference

### **1. Status Granularity**

```
Reference: 'blocked' | 'limited'
Guardian:  'allowed' | 'blocked' | 'temporarily_blocked'
```

### **2. Metrics Enhancement**

```
Reference: Visit Count, Time Spent
Guardian:  + Average Per Visit, Average Per Day, Block %, Temp Block %
```

### **3. Visual Indicators**

```
Reference: Text labels
Guardian:  Color badges (Green/Red/Yellow) + Emoji icons + Summary boxes
```

### **4. Time Period Viewing**

```
Reference: Single aggregate view
Guardian:  24h | 7d | All-time simultaneously
```

### **5. Activity Detail**

```
Reference: Tab history
Guardian:  Full URL + Page Title + Entry/Exit times + Duration + Status
```

### **6. Summary Organization**

```
Reference: Single lists
Guardian:  Categorized summary boxes (Allowed/Blocked/Temp) + Statistics
```

---

## Dashboard Sections - Side by Side

### **Top Statistics**

**Web Activity Tracker:**

```
[Status Active] [Today's Stats] [This Week] [Total Time]
```

**Guardian (Enhanced):**

```
[Collection Status] [✅ Allowed] [🚫 Blocked] [⏱️ Temp Blocked]
```

### **Domain List**

**Web Activity Tracker:**

```
github.com         25 min    📊
stackoverflow.com  20 min    📊
medium.com        15 min    📊
```

**Guardian (Enhanced):**

```
1️⃣  github.com ✅
    Last: Nov 23 2:45 PM
    Visits: 10 | Avg: 2m 30s | Total: 25m | Daily: 1.4

2️⃣  stackoverflow.com ✅
    Last: Nov 23 1:20 PM
    Visits: 8 | Avg: 2m 30s | Total: 20m | Daily: 1.1

3️⃣  facebook.com 🚫
    Last: Nov 22 3:00 PM
    Blocked: 3 times (100%)
```

### **Status Summary**

**Web Activity Tracker:**

```
Blocked Sites:
- facebook.com
- reddit.com
```

**Guardian (Enhanced):**

```
✅ ALLOWED (23 sites)
• github.com (10 visits)
• stackoverflow.com (8 visits)
[+20 more]

🚫 BLOCKED (2 sites)
• facebook.com (3 blocks)
• reddit.com (2 blocks)

⏱️  TEMPORARILY BLOCKED (1 site)
• twitter.com (1 temp block)
```

---

## Feature Checklist - Guardian Implementation

### **Core Requirements** ✅

- [x] Track all website visits
- [x] Show visit counts
- [x] Calculate time spent
- [x] Display last visited time
- [x] Show allowed/blocked status
- [x] Categorize by access level
- [x] Visual indicators for status

### **Enhanced Features** ✅

- [x] Exact page titles (not just domain)
- [x] Full URLs preserved
- [x] Multiple time periods (24h/7d/All)
- [x] Average per visit calculated
- [x] Average per day calculated
- [x] Block count tracking
- [x] Temporary block count tracking
- [x] Block percentage calculation
- [x] Color-coded status badges
- [x] Summary boxes by category
- [x] Detailed activity log
- [x] Precise timestamps
- [x] Data freshness indicator
- [x] Expandable details view
- [x] Real-time updates (5s)

---

## Summary

**Guardian dashboard provides:**

1. ✅ **All information from Web Activity Time Tracker**
   - Website visit tracking
   - Time spent metrics
   - Access status (blocked/allowed)
   - Detailed statistics

2. ✅ **Enhanced information beyond reference**
   - 3-level status system (vs 2)
   - Per-visit statistics
   - Block percentages
   - Detailed activity log with titles
   - Multiple time period viewing
   - Color-coded visual design
   - Summary categorization
   - Data precision (exact times, URLs, titles)

3. ✅ **Better information hierarchy**
   - Quick-glance status cards
   - Color-coded domain badges
   - Categorized summary boxes
   - Sortable and filterable data
   - Real-time updates

**Result**: Guardian dashboard now shows **exactly what activities were performed** with complete clarity about **allowed/blocked/temporarily blocked** status, plus comprehensive metrics for understanding web usage patterns.
