# Business Metrics Strategy

**Last Updated:** 2026-04-17  
**Version:** 1.0  
**Status:** DEFERRED - Not required at current scale

---

## Overview

This document outlines the business metrics strategy for TutorPutor. Business metrics implementation is currently deferred as learning metrics provide sufficient coverage. This strategy will be implemented when revenue tracking and executive dashboards become critical.

---

## Current State

### Learning Metrics
**Status:** IMPLEMENTED

- Enrollment tracking
- Progress updates
- Module completions
- At-risk student detection
- Performance metrics

**Location:** `services/tutorputor-platform/src/modules/learning/learning-metrics.ts`

---

## Business Metrics Evaluation

### When to Implement Business Metrics

Business metrics should be implemented when:
1. Revenue tracking becomes critical for business decisions
2. Executive dashboards are required for board reporting
3. Business KPIs need formal tracking and alerting
4. Customer success metrics need optimization

### Current Metrics
- Revenue: Not tracked at business level
- Engagement: Tracked at learning level
- Learning outcomes: Tracked at learning level
- Customer success: Not formally tracked

**Conclusion:** Business metrics not required at current scale.

---

## Business Metric Definitions

### Revenue Metrics

**Metrics:**
- Monthly Recurring Revenue (MRR)
- Annual Recurring Revenue (ARR)
- Average Revenue Per User (ARPU)
- Customer Lifetime Value (CLV)
- Churn Rate

**Collection:**
```typescript
interface RevenueMetrics {
  mrr: number;
  arr: number;
  arpu: number;
  clv: number;
  churnRate: number;
}
```

---

### Engagement Metrics

**Metrics:**
- Daily Active Users (DAU)
- Weekly Active Users (WAU)
- Monthly Active Users (MAU)
- Session Duration
- Pages Per Session
- Bounce Rate

**Collection:**
```typescript
interface EngagementMetrics {
  dau: number;
  wau: number;
  mau: number;
  sessionDuration: number;
  pagesPerSession: number;
  bounceRate: number;
}
```

---

### Learning Outcome Metrics

**Metrics:**
- Course Completion Rate
- Average Quiz Score
- Time to Completion
- Skill Acquisition Rate
- Knowledge Retention

**Collection:**
```typescript
interface LearningOutcomeMetrics {
  completionRate: number;
  averageQuizScore: number;
  timeToCompletion: number;
  skillAcquisitionRate: number;
  knowledgeRetention: number;
}
```

---

## Metrics Collection

### Collection Service

**Implementation:**
```typescript
export class BusinessMetricsService {
  async collectRevenueMetrics(tenantId: string): Promise<RevenueMetrics> {
    // Calculate revenue metrics from payment data
  }

  async collectEngagementMetrics(tenantId: string): Promise<EngagementMetrics> {
    // Calculate engagement metrics from analytics data
  }

  async collectLearningOutcomeMetrics(tenantId: string): Promise<LearningOutcomeMetrics> {
    // Calculate learning outcome metrics from learning data
  }
}
```

---

## Dashboard Design

### Executive Dashboard

**Widgets:**
- Revenue trend chart (MRR, ARR)
- User growth chart (DAU, WAU, MAU)
- Engagement metrics (session duration, bounce rate)
- Learning outcomes (completion rate, quiz scores)
- Churn rate trend
- Top performing courses

**Refresh:** Daily

---

### Business Operations Dashboard

**Widgets:**
- Revenue by tenant
- User acquisition cost
- Customer lifetime value
- Subscription tier distribution
- Feature usage heatmap
- Support ticket volume

**Refresh:** Hourly

---

## Alerting Configuration

### Revenue Alerts

**Alerts:**
- MRR drop >10% week-over-week
- Churn rate >5% month-over-month
- ARPU decline >15% quarter-over-quarter

**Severity:** Critical

---

### Engagement Alerts

**Alerts:**
- DAU drop >20% day-over-day
- Session duration drop >30% week-over-week
- Bounce rate increase >25% week-over-week

**Severity:** Warning

---

### Learning Outcome Alerts

**Alerts:**
- Completion rate drop >15% month-over-month
- Average quiz score drop >20% week-over-week
- Time to completion increase >30% month-over-month

**Severity:** Warning

---

## Implementation Steps

1. **Phase 1: Data Collection**
   - Implement BusinessMetricsService
   - Set up metrics collection jobs
   - Configure data aggregation

2. **Phase 2: Dashboard Creation**
   - Create executive dashboard
   - Create operations dashboard
   - Configure data visualization

3. **Phase 3: Alerting Setup**
   - Configure revenue alerts
   - Configure engagement alerts
   - Configure learning outcome alerts

4. **Phase 4: Documentation**
   - Document metric definitions
   - Document dashboard usage
   - Document alerting procedures

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
