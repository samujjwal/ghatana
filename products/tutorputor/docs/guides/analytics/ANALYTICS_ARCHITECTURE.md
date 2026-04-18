# Analytics Architecture Documentation

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

TutorPutor implements a comprehensive analytics system that provides insights into learning performance, engagement patterns, and predictive analytics for teachers and administrators. The system supports real-time event streaming, historical analysis, and advanced predictive modeling.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Analytics Layer                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │  Event Recording │  │  Metrics         │                │
│  │  (Postgres+Redis)│  │  (Prometheus)    │                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                     │                             │
│           └──────────┬──────────┘                             │
│                      ▼                                        │
│           ┌──────────────────┐                               │
│           │  Analytics       │                               │
│           │  Service         │                               │
│           └────────┬─────────┘                               │
│                    │                                        │
│           ┌────────┴────────┐                               │
│           ▼                 ▼                                │
│  ┌──────────────────┐ ┌──────────────────┐                 │
│  │  Teacher         │ │  Data Export     │                 │
│  │  Analytics       │ │  Service         │                 │
│  └──────────────────┘ └──────────────────┘                 │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────────┐                               │
│           │  Predictive      │                               │
│           │  Analytics       │                               │
│           └──────────────────┘                               │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Data Model

### Event Storage
**Location:** `services/tutorputor-platform/src/modules/learning/analytics-service.ts`

**Schema:**
- `learningEvent` - Individual learning events (module_viewed, module_completed, assessment_started, etc.)
- `enrollment` - Module enrollment tracking with progress
- `assessmentAttempt` - Assessment attempt tracking with scores
- `learnerProfile` - Learner mastery and knowledge gap data

**Storage:**
- **PostgreSQL:** Persistent storage for all events
- **Redis:** Real-time event streaming (streams + pub/sub)
- **Feature Store:** ML feature vector enrichment

---

## 2. Metrics Calculation

### Performance Metrics
**Location:** `services/tutorputor-platform/src/modules/learning/analytics-service.ts`

**Metrics:**
- Module completion rates
- Assessment scores and failure rates
- Average completion time
- Difficulty heatmaps
- Grade distribution

**Calculation:**
```typescript
completionRate = completedEnrollments / totalEnrollments
averageScore = sum(scores) / count(attempts)
failureRate = failedAttempts / totalAttempts
difficultyScore = failureRate * 0.4 + dropOffRate * 0.3 + (avgAttempts / 5) * 30
```

### Engagement Metrics
**Location:** `services/tutorputor-platform/src/modules/learning/analytics-service.ts`

**Metrics:**
- Active users (unique users in period)
- Event type distribution
- Usage trends (daily/weekly/monthly)
- AI tutor query count
- Assessment attempt count

**Calculation:**
```typescript
activeUsers = count(unique userIds in events)
trendDirection = recentCompletions - previousCompletions
engagementDecline = previousWeekEvents > recentEvents * 2
```

---

## 3. Predictive Analytics

### At-Risk Student Detection
**Location:** `services/tutorputor-platform/src/modules/learning/analytics-service.ts`

**Risk Factors:**
- **Inactivity:** No activity in 5+ days (20-30 points)
- **Low Progress:** <20% progress with >1 hour invested (25 points)
- **Failing Assessments:** 3+ failed assessments (20-30 points)
- **Declining Engagement:** >50% drop in activity (20 points)

**Risk Levels:**
- **Critical:** 80+ points
- **High:** 60-79 points
- **Medium:** 30-59 points
- **Low:** <30 points

### Enhanced Predictive Analytics
**Location:** `services/tutorputor-platform/src/modules/analytics/EnhancedPredictiveAnalyticsService.ts`

**Features:**
- **Learning Path Prediction:** Recommend optimal sequence based on mastery
- **Mastery Prediction:** Predict time to mastery for specific concepts
- **Dropout Prediction:** Comprehensive dropout risk modeling
- **Content Gap Analysis:** Identify concept gaps across cohorts

---

## 4. Dashboard Design

### Admin Dashboard
**Location:** `apps/tutorputor-admin/src/components/content-studio/AnalyticsDashboard.tsx`

**Components:**
- Key metrics (views, completions, completion rate, avg time)
- Simulation performance (starts, aborts, errors)
- 7-day trend visualization
- Drift signal detection
- Recommended actions
- Validation scores (authority, accuracy, usefulness, safety, accessibility)
- Recent authoring activity

### Teacher Dashboard
**Location:** `services/tutorputor-platform/src/modules/analytics/TeacherAnalyticsService.ts`

**Components:**
- Classroom-level performance metrics
- Student performance comparison
- Assignment tracking
- Grade distribution visualization
- Intervention recommendations

---

## 5. Data Export

### Export Service
**Location:** `services/tutorputor-platform/src/modules/analytics/DataExportService.ts`

**Formats:**
- CSV (comma-separated values)
- Excel (multi-sheet workbooks)
- JSON (structured data)

**Scopes:**
- **Tenant:** All data for a tenant
- **Classroom:** Data for a specific classroom
- **Student:** Data for a specific student
- **Assessment:** Data for a specific assessment

**Features:**
- Custom date range filtering
- Data anonymization (hash IDs, mask emails)
- Scheduled report delivery (placeholder)

---

## 6. API Endpoints

### Export Endpoints
```
GET /analytics/export
  Query: tenantId, format, scope, scopeId, startDate, endDate, anonymize
  Response: File download (CSV/Excel/JSON)
```

### Teacher Analytics Endpoints
```
GET /analytics/classroom/:classroomId
  Query: tenantId
  Response: ClassroomAnalytics

GET /analytics/student/:studentId
  Query: tenantId
  Response: StudentAnalytics

GET /analytics/interventions/:classroomId
  Query: tenantId
  Response: InterventionRecommendation[]
```

### Predictive Analytics Endpoints
```
POST /analytics/predict/path
  Body: { tenantId, userId, goal }
  Response: LearningPathPrediction

GET /analytics/predict/mastery/:conceptId
  Query: tenantId, userId
  Response: MasteryPrediction

GET /analytics/predict/dropout/:userId
  Query: tenantId
  Response: DropoutPrediction

GET /analytics/gaps
  Query: tenantId, classroomId
  Response: ContentGap[]
```

---

## 7. Performance Metrics

### Prometheus Metrics
**Location:** `services/tutorputor-platform/src/modules/learning/learning-metrics.ts`

**Metrics:**
- `tutorputor_learning_enrollments_total` - Total enrollment events (labels: tenant_id, status)
- `tutorputor_learning_progress_updates_total` - Total progress update events (labels: tenant_id)
- `tutorputor_learning_completions_total` - Total completion events (labels: tenant_id)

### Monitoring
- Prometheus metrics for all analytics operations
- Redis stream monitoring for real-time events
- Database query performance tracking
- Export job performance metrics

---

## Best Practices

1. **Always anonymize exports** - Hash user IDs and mask PII when exporting
2. **Respect data retention** - Implement data retention policies for event data
3. **Monitor performance** - Track query performance and optimize slow queries
4. **Cache aggregations** - Cache expensive calculations where appropriate
5. **Validate inputs** - Validate all export parameters to prevent data leakage

---

## Future Enhancements

- Real-time dashboard updates via WebSocket
- Machine learning model for dropout prediction
- Advanced funnel analysis
- Cohort analysis
- A/B testing analytics integration
- Custom dashboard builder

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
