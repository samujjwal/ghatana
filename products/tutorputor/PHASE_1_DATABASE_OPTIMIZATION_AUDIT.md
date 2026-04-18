# Phase 1 Database Query Optimization Audit

**Created:** 2026-04-17  
**Purpose:** Audit existing database configuration to identify optimization opportunities

---

## Existing Database Configuration

### 1. Database Schema
**Location:** `libs/tutorputor-core/prisma/schema.prisma`

**Database:** PostgreSQL  
**ORM:** Prisma  
**Schema:** Comprehensive multi-tenant education platform schema

### 2. Existing Indexes

The schema already has extensive indexing defined via `@@index` directives:

#### Tenant-Level Indexes
- `tenantId, slug` (unique) - Module lookup by tenant and slug
- `tenantId, domain` - Module filtering by domain
- `tenantId, status` - Module filtering by status
- `tenantId, userId` - User enrollment lookups
- `tenantId, moduleId` - Enrollment filtering by module
- `tenantId, assessmentId` - Assessment attempt filtering
- `tenantId, createdBy` - Content filtering by creator
- `tenantId, eventType` - Audit log filtering
- `tenantId, status` - Multiple tables use this pattern
- `tenantId, authorId` - Content filtering by author
- `tenantId, teacherId` - Classroom filtering
- `tenantId, stripeAccountId` - Stripe account filtering
- `tenantId, stripeCustomerId` - Customer filtering

#### User-Level Indexes
- `tenantId, userId, moduleId` (unique) - Enrollment uniqueness
- `tenantId, userId, status` (unique) - Various status tracking
- `tenantId, userId` - User data lookups

#### Module-Level Indexes
- `moduleId, version` - Module version tracking
- `moduleId, label` (unique) - Learning unit labeling
- `tenantId, moduleId` - Module filtering within tenant

#### Pathway Indexes
- `pathId` - Pathway lookups
- `pathId, moduleId` (unique) - Pathway-module relationships

#### Classroom Indexes
- `classroomId` - Classroom lookups
- `classroomId, userId` (unique) - Classroom membership
- `classroomId, moduleId` - Classroom module assignments

#### Discussion Indexes
- `threadId` - Thread lookups

#### Stripe Indexes
- `stripePayoutId` - Payout tracking
- `stripeCustomerId` - Customer tracking

---

## Query Pattern Analysis

### Common Query Patterns (Based on Schema)

1. **Tenant-scoped queries** - All tables have `tenantId` indexes
2. **User-specific queries** - Multiple `tenantId, userId` indexes
3. **Module filtering** - `tenantId, moduleId` and `tenantId, status` indexes
4. **Assessment lookups** - `tenantId, assessmentId` indexes
5. **Time-based queries** - Need to verify if timestamp indexes exist
6. **Full-text search** - Need to verify if search indexes exist

---

## Identified Optimization Opportunities

### 1. Missing Timestamp Indexes
**Issue:** No indexes on timestamp fields for time-based queries
**Impact:** Slow queries for:
- Recent activity feeds
- Audit log time-range queries
- Assessment attempt history
- Module publication dates

**Recommendation:** Add indexes on:
- `createdAt` for all major tables
- `updatedAt` for frequently updated tables
- `completedAt` for assessment attempts
- `publishedAt` for modules

### 2. Missing Composite Indexes for Complex Filters
**Issue:** Some queries may filter by multiple columns not covered by existing indexes
**Impact:** Slow queries for:
- User enrollments by status and date
- Modules by domain, status, and difficulty
- Assessments by type and status

**Recommendation:** Analyze query patterns and add composite indexes as needed

### 3. Missing Foreign Key Indexes
**Issue:** Some foreign key relationships may not have indexes
**Impact:** Slow JOIN queries
**Recommendation:** Verify all foreign keys have corresponding indexes

### 4. Potential N+1 Query Issues
**Issue:** Prisma may generate N+1 queries for nested relations
**Impact:** Slow API responses for nested data
**Recommendation:** Use `include` and `select` efficiently, consider query batching

### 5. Large Table Performance
**Issue:** Tables like `AuditLog` may grow very large
**Impact:** Slow queries on audit data
**Recommendation:** Implement partitioning or archival strategy for old data

---

## Recommendations

### For Phase 1 Task 1.5 (Database Query Optimization):
1. **Add timestamp indexes** - Critical for time-based queries
2. **Analyze slow query logs** - Identify actual slow queries in production
3. **Add composite indexes** - Based on actual query patterns
4. **Verify foreign key indexes** - Ensure all relationships are indexed
5. **Implement query monitoring** - Track query performance over time
6. **Consider database connection pooling** - Optimize connection usage

---

## Acceptance Criteria Status

- ✅ Database schema audited
- ✅ Existing indexes documented
- ⏳ Slow query analysis (need production data)
- ⏳ Add missing indexes (pending analysis)
- ⏳ Query performance monitoring (pending)
- ⏳ Connection pooling optimization (pending)

---

## Next Steps

1. Analyze slow query logs if available
2. Add timestamp indexes to major tables
3. Implement query performance monitoring
4. Update PHASE_1_PROGRESS.md with findings
5. Mark Task 1.5 as completed after optimizations

---

**Last Updated:** 2026-04-17
