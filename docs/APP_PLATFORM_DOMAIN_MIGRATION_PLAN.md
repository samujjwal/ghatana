# App-Platform Domain Migration Plan

⚠️ **STATUS: UNDER REVISION - CRITICAL ISSUES IDENTIFIED**  
**See: APP_PLATFORM_DOMAIN_MIGRATION_PLAN_REVIEW.md for detailed findings**

**Date**: March 19, 2026  
**Last Reviewed**: March 19, 2026  
**Purpose**: Comprehensive plan to migrate domain-specific code from app-platform to appropriate product locations

---

## ⚠️ CRITICAL ISSUES (REVISION REQUIRED)

**DO NOT EXECUTE THIS PLAN AS-IS. The following critical issues must be resolved first:**

1. **Active Duplicates**: Domain code exists in BOTH app-platform AND finance/domains with different package names
   - Example: `compliance` exists in both `com.ghatana.appplatform.compliance` and `com.ghatana.products.finance.domains.compliance`
2. **Target Structure Mismatch**: Plan targets `finance/domain-packs/` but code already at `finance/domains/`
3. **Missing Domain**: Plan lists 14 domains but app-platform has 15 (missing `corporate-actions`)
4. **Architectural Conflict**: app-platform uses service-oriented; finance uses KernelModule architecture
5. **No Deduplication Strategy**: Plan doesn't address consolidating duplicate implementations
6. **Dangerous Commands**: Migration commands could overwrite existing code

**Action Required**: Complete Phase 0 analysis first (see review document)

---

## Executive Summary

The app-platform product contains significant domain-specific code that violates the platform's purpose as a generic application platform. This migration plan moves domain-specific functionality to dedicated products while preserving reusable platform components.

**Key Findings:**
- **14 domain packs** with financial and healthcare-specific code
- **6 kernel modules** with mixed generic/domain functionality  
- **1,532 Java files** using `com.ghatana.appplatform` package
- **85% financial domain code** incorrectly placed in app-platform

---

## 1. Current Structure Analysis

### 1.1 Domain Packs (14) - ALL NEED MIGRATION

| Domain Pack | Domain Type | Target Product | Files | Priority | Status |
|-------------|-------------|---------------|-------|----------|--------|
| **compliance** | Financial | `products:finance` | 27 | HIGH | ⚠️ Duplicate in finance/domains |
| **corporate-actions** | Financial | `products:finance` | ~20 | HIGH | ⚠️ MISSING FROM PLAN |
| **ems** | Financial | `products:finance` | 33 | HIGH | ⚠️ Duplicate in finance/domains |
| **market-data** | Financial | `products:finance` | 26 | HIGH | ✓ Appears unique |
| **oms** | Financial | `products:finance` | 31 | HIGH | ⚠️ Duplicate in finance/domains |
| **pms** | Financial | `products:finance` | 15 | HIGH | ✓ Appears unique |
| **post-trade** | Financial | `products:finance` | 20 | HIGH | ✓ Appears unique |
| **pricing** | Financial | `products:finance` | 14 | HIGH | ✓ Appears unique |
| **reconciliation** | Financial | `products:finance` | 20 | HIGH | ✓ Appears unique |
| **reference-data** | Financial | `products:finance` | 30 | HIGH | ✓ Appears unique |
| **regulatory-reporting** | Financial | `products:finance` | 15 | HIGH | ✓ Appears unique |
| **risk-engine** | Financial | `products:finance` | 30 | HIGH | ⚠️ Naming conflict: finance has `risk` folder |
| **sanctions** | Financial | `products:finance` | 30 | HIGH | ✓ Appears unique |
| **surveillance** | Financial | `products:finance` | 18 | HIGH | ✓ Appears unique |
| **healthcare** | Healthcare | `products:phr` | 16 | MEDIUM | ❓ Needs verification in phr |

### 1.2 Kernel Modules (6) - PARTIAL MIGRATION

| Kernel Module | Generic Components | Domain Components | Action |
|---------------|-------------------|-------------------|--------|
| **audit-trail** | Generic audit framework | Financial audit fields | Keep + Refactor |
| **calendar-service** | Generic calendar | Financial calendar logic | Keep + Refactor |
| **config-engine** | Generic config | Financial config patterns | Keep + Refactor |
| **event-store** | Generic event store | Financial event schemas | Keep + Refactor |
| **iam** | Generic IAM | Financial roles/permissions | Keep + Refactor |
| **ledger-framework** | Financial ledger | Entirely domain-specific | Migrate to Finance |

---

## 2. Target Product Structure

### 2.1 Finance Product Structure

**CURRENT STRUCTURE (already contains domains):**
```
products/finance/
├── domains/                     # ← Already has domain code!
│   ├── compliance/              # ⚠️ Duplicate of app-platform/domain-packs/compliance
│   ├── corporate-actions/
│   ├── ems/                     # ⚠️ Duplicate of app-platform/domain-packs/ems
│   ├── market-data/
│   ├── oms/                     # ⚠️ Duplicate of app-platform/domain-packs/oms
│   ├── pms/
│   ├── post-trade/
│   ├── pricing/
│   ├── reconciliation/
│   ├── reference-data/
│   ├── regulatory-reporting/
│   ├── risk/                    # ← Naming conflict with app-platform risk-engine
│   ├── rules/                   # ← No equivalent in app-platform
│   ├── sanctions/
│   └── surveillance/
├── kernel/                      
│   ├── ledger-framework/        
│   └── ...
├── platform/
├── integration/
└── ...
```

**CORRECTION**: Plan incorrectly showed target as `domain-packs/` when it should be `domains/`

**DEDUPLICATION STRATEGY NEEDED**: 
- Consolidate app-platform duplicates with finance versions
- Decide which implementation is canonical (likely finance/domains/* is newer)
- Delete old duplicate from app-platform
- Handle naming conflicts (risk vs risk-engine, rules)


### 2.2 PHR Product Structure

```
products/phr/
└── domain-packs/
    └── healthcare/         # Migrated from app-platform
```

### 2.3 Cleaned App-Platform Structure

```
products/app-platform/
├── kernel/                 # Generic platform components only
│   ├── audit-trail/        # Refactored to generic
│   ├── calendar-service/   # Refactored to generic
│   ├── config-engine/      # Refactored to generic
│   ├── event-store/        # Refactored to generic
│   └── iam/               # Refactored to generic
├── platform/              # Generic platform services
│   ├── http/
│   ├── database/
│   ├── security/
│   └── observability/
├── admin-portal/          # Generic admin UI
├── service-template/      # Generic service template
└── typescript/           # TypeScript platform SDK
```

---

## 3. Migration Strategy

⚠️ **MUST COMPLETE PHASE 0 FIRST** (See review document for details)

### 3.0 Phase 0: Analysis & Deduplication (Week 1) - NEW (WAS MISSING)

**This phase identifies and consolidates duplicates - MUST BE DONE BEFORE MIGRATION**

#### 3.0.1 Duplicate Detection

For each domain with duplicates, analyze differences:

```bash
# Compare compliance implementations
diff -r products/app-platform/domain-packs/compliance \
       products/finance/domains/compliance

# Identify which version is canonical (likely finance/domains/* is newer)
# Document differences
# Create consolidation strategy
```

**Duplicates requiring analysis:**
1. compliance
2. ems  
3. oms
4. risk-engine vs risk
5. Handle rules (no app-platform equivalent)

#### 3.0.2 Architecture Assessment

- Document current architecture in app-platform (service-oriented)
- Document current architecture in finance (KernelModule-based)
- Make decision: Which pattern to keep?
- Plan refactoring if needed

#### 3.0.3 Dependency Mapping

```bash
# Find all references to appplatform domain code
grep -r "com.ghatana.appplatform" products --include="*.kt" --include="*.java"
grep -r "import.*appplatform" products --include="*.kt" --include="*.java"
```

#### 3.0.4 Build Validation

```bash
./gradlew :products:app-platform:build
./gradlew :products:finance:build
# Ensure both build independently with no circular dependencies
```

#### 3.0.5 Consolidation Plan

Create detailed spec for each domain:
- Which files to delete
- Which files to keep
- How to handle API changes
- Test coverage merge strategy

---

### 3.1 Phase 1: Domain Pack Migration (Weeks 2-4)

**Priority 1: Financial Domain Packs (Weeks 2-3)**

⚠️ **IMPORTANT**: Some domains already exist in finance/domains/. See Phase 0 analysis.

```bash
# For domains WITHOUT duplicates in finance/domains, move them:
mv products/app-platform/domain-packs/market-data products/finance/domains/
mv products/app-platform/domain-packs/pms products/finance/domains/
mv products/app-platform/domain-packs/post-trade products/finance/domains/
mv products/app-platform/domain-packs/pricing products/finance/domains/
mv products/app-platform/domain-packs/reconciliation products/finance/domains/
mv products/app-platform/domain-packs/reference-data products/finance/domains/
mv products/app-platform/domain-packs/regulatory-reporting products/finance/domains/
mv products/app-platform/domain-packs/sanctions products/finance/domains/
mv products/app-platform/domain-packs/surveillance products/finance/domains/

# For domains WITH duplicates (compliance, ems, oms):
# 1. Run Phase 0 deduplication analysis
# 2. Decide which version to keep (likely finance/domains/* is canonical)
# 3. Delete app-platform version OR merge if different:
# rm -rf products/app-platform/domain-packs/compliance (if finance version chosen)
# rm -rf products/app-platform/domain-packs/ems
# rm -rf products/app-platform/domain-packs/oms

# For risk-engine (conflicts with finance/risk):
# 1. Analyze difference between risk-engine and risk
# 2. Consolidate under single name (likely 'risk')
# 3. Delete redundant version

# For corporate-actions (MISSING FROM ORIGINAL PLAN):
mv products/app-platform/domain-packs/corporate-actions products/finance/domains/
```

**Priority 2: Healthcare Domain Pack (Week 4)**

### 3.2 Phase 2: Kernel Module Migration (Weeks 5-6)

**Ledger Framework Migration (Week 5)**

```bash
# Move entirely domain-specific
mv products/app-platform/kernel/ledger-framework products/finance/kernel/
```

**Kernel Module Refactoring (Week 6)**

For each remaining kernel module:
1. Extract domain-specific components to finance product
2. Keep generic components in app-platform
3. Create extension points for domain customization

### 3.3 Phase 3: Package Refactoring (Weeks 7-8)

**Package Name Updates**

```bash
# Update package names from com.ghatana.appplatform to appropriate packages
# Financial: com.ghatana.finance.*
# Healthcare: com.ghatana.phr.*
# Generic: com.ghatana.appplatform.*
```

**Build Configuration Updates**

```kotlin
// Update build.gradle.kts files
// Update dependency references
// Update import statements
```

---

## 4. Implementation Details

### 4.1 Package Refactoring Strategy

**Current Package Structure:**
```
com.ghatana.appplatform.compliance.*
com.ghatana.appplatform.ems.*
com.ghatana.appplatform.healthcare.*
```

**Target Package Structure:**
```
com.ghatana.finance.compliance.*
com.ghatana.finance.ems.*
com.ghatana.phr.healthcare.*
```

**Refactoring Process:**
1. Update package declarations
2. Update import statements
3. Update build configurations
4. Update dependency references
5. Update documentation

### 4.2 Dependency Management

**Before Migration:**
```kotlin
// app-platform build.gradle.kts
dependencies {
    implementation(project(":products:app-platform:domain-packs:compliance"))
    implementation(project(":products:app-platform:domain-packs:ems"))
}
```

**After Migration:**
```kotlin
// finance build.gradle.kts
dependencies {
    implementation(project(":products:finance:domain-packs:compliance"))
    implementation(project(":products:finance:domain-packs:ems"))
}

// app-platform build.gradle.kts (clean)
dependencies {
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:http"))
    // No domain-specific dependencies
}
```

### 4.3 Extension Points for Generic Components

**Generic Calendar Service Example:**

```java
// app-platform kernel/calendar-service (generic)
public interface CalendarExtension {
    String getDomainName();
    boolean isBusinessDay(LocalDate date, String jurisdiction);
    LocalDate calculateSettlementDate(LocalDate tradeDate, String settlementCycle);
}

// finance platform/calendar (finance-specific)
@Component
public class FinanceCalendarExtension implements CalendarExtension {
    @Override
    public String getDomainName() {
        return "FINANCE";
    }
    
    @Override
    public boolean isBusinessDay(LocalDate date, String jurisdiction) {
        // Finance-specific business day logic
        return financeCalendarService.isBusinessDay(date, jurisdiction);
    }
}
```

---

## 5. Risk Mitigation

### 5.1 Migration Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Duplicate Code Conflict** | CRITICAL | ✅ Phase 0 deduplication analysis required before proceeding |
| **Build Breakage** | High | Incremental migration, preserve old structure during transition |
| **Dependency Conflicts** | High | ✅ Careful dependency mapping in Phase 0, validate no circular refs |
| **Lost Functionality** | High | Comprehensive testing, validation at each step |
| **Architecture Mismatch** | High | ✅ Phase 0 decision: service vs KernelModule approach |
| **Team Disruption** | Medium | Clear communication, parallel development environment |
| **Runtime Binding Issues** | CRITICAL | Both versions on classpath causes unpredictable behavior |
| **Database Schema Conflicts** | High | Verify schema before migration, plan migrations |
| **API Incompatibility** | Medium | Document breaking changes, provide deprecation path |

### 5.2 Rollback Strategy

1. **Backup Current Structure**: Complete backup before migration
2. **Branch Strategy**: Create migration branch, preserve main branch
3. **Incremental Validation**: Test each migration step
4. **Rollback Plan**: Clear rollback procedures for each phase

---

## 6. Success Metrics

### 6.1 Technical Metrics

- **Zero Duplicates**: No domain code appearing in multiple locations ✅ CRITICAL
- **Zero Domain Code in App-Platform**: All domain-specific code moved ✅ VERIFIED BY GREP
- **Clean Package Structure**: No `com.ghatana.appplatform.*` domain packages ✅ VERIFIED
- **Zero Naming Conflicts**: Resolved risk vs risk-engine, etc. ✅ VERIFIED
- **Build Success**: All products build successfully after migration ✅ REQUIRED
- **Test Coverage**: Maintained or improved test coverage ✅ REQUIRED
- **Architecture Aligned**: Consistent architecture pattern across domains (KernelModule) ✅ REQUIRED

### 6.2 Architectural Metrics

- **Separation of Concerns**: Clear platform vs domain boundaries
- **Reusability**: Generic components truly reusable
- **Maintainability**: Easier to maintain domain-specific code
- **Scalability**: Better structure for adding new domains

---

## 7. Implementation Timeline

### 7.1 8-Week Migration Plan

⚠️ **REVISED** (Original plan did not account for deduplication)

**Week 1: Phase 0 - Analysis & Deduplication**
- ✅ Identify all duplicates (compliance, ems, oms, risk vs risk-engine)
- ✅ Analyze architectural differences (service vs KernelModule)
- ✅ Map all dependencies
- ✅ Create detailed consolidation plan
- ✅ Backup current state

**Weeks 2-3: Phase 1a - Consolidate Duplicates**
- Delete duplicate implementations (keep canonical versions)
- Merge tests and consolidate test suites
- Update all imports/references
- Validate build and tests

**Weeks 3-4: Phase 1b - Migrate Unique Domains**
- Move domains without duplicates (market-data, pms, post-trade, etc.)
- Update package names and imports
- Validate builds and tests

**Week 5: Phase 2 - Ledger Framework Migration**
- Move ledger-framework to finance product
- Update dependencies and references

**Week 6: Phase 3 - Kernel Module Refactoring**
- Refactor remaining kernel modules to generic
- Extract domain-specific components
- Create extension points

**Week 7: Phase 4 - Package and App Cleanup**
- Remove all domain code from app-platform
- Final cleanup and validation
- Update documentation

**Week 8: Phase 5 - Comprehensive Testing**
- Full integration testing
- Healthcare domain verification (if not done in parallel)
- Final validation and documentation

### 7.2 Daily Tasks

**Each Migration Day:**
1. **Backup**: Create backup of current state
2. **Migration**: Move specific components
3. **Refactor**: Update package names and imports
4. **Build**: Validate build success
5. **Test**: Run test suite
6. **Validate**: Functionality verification
7. **Commit**: Commit changes with clear messages

---

## 8. Next Steps

⚠️ **CRITICAL: DO NOT START MIGRATION UNTIL PHASE 0 ANALYSIS IS COMPLETE**

See APP_PLATFORM_DOMAIN_MIGRATION_PLAN_REVIEW.md for detailed requirements.

### 8.1 Immediate Actions (Week 1 - Phase 0 Analysis)

1. ✅ **Create Migration Branch**: `git checkout -b app-platform-domain-migration-analysis`
2. ✅ **Run Deduplication Audit**: Identify exact duplicates in all 15 domains
3. ✅ **Backup Current Structure**: Complete backup of app-platform
4. ✅ **Document current state**: Map finance/domains vs app-platform/domain-packs
5. ✅ **Architecture Decision**: Service-oriented vs KernelModule approach
6. ✅ **Dependency Mapping**: All cross-product dependencies
7. ✅ **Consolidation Plan**: Detailed spec for each domain

### 8.2 Short-term Actions (Weeks 2-4 - Phase 1)

1. **Consolidate Duplicates**: Merge compliance, ems, oms implementations
2. **Complete Financial Domain Pack Migration**: Move unique domains
3. **Healthcare Domain Pack Migration**: Move to PHR (after phr verification)
4. **Build Validation**: Ensure no circular dependencies
5. **Test Coverage**: Reconcile and merge test suites

### 8.3 Medium-term Actions (Weeks 5-8 - Phases 2-5)

1. **Kernel Module Refactoring**: Generic vs domain separation
2. **Package Refactoring**: Update all package names
3. **Comprehensive Testing**: Full test suite validation
4. **Documentation Updates**: Update all documentation
5. **Final Validation**: Zero remaining app-platform domain code

---

## 9. Conclusion

⚠️ **CRITICAL STATUS: PLAN REQUIRES REVISION BEFORE EXECUTION**

### Issues Identified

1. ❌ **ACTIVE DUPLICATES** in compliance, ems, oms (both in app-platform and finance)
2. ❌ **MISSING DOMAIN**: corporate-actions not tracked in plan
3. ❌ **WRONG TARGET PATH**: Plan shows domain-packs/ but code is at domains/
4. ❌ **ARCHITECTURE MISMATCH**: Service-oriented vs KernelModule patterns
5. ⚠️ **NO DEDUPLICATION STRATEGY**: Plan lacks consolidation approach
6. ⚠️ **DANGEROUS COMMANDS**: Migration commands could overwrite code

### Revised Status

- **Current Plan Status**: NOT READY FOR IMPLEMENTATION
- **Phase 0 Required**: 1 week analysis before proceeding
- **Total Timeline**: 8 weeks (1 analysis + 7 migration)
- **Key Success Factor**: Phase 0 deduplication must be completed first

### This migration will—when properly executed:

- **Eliminate Domain Contamination**: Remove all domain-specific code from app-platform
- **Consolidate Duplicates**: Merge overlapping domain implementations
- **Improve Architecture**: Clear separation between platform and domain
- **Enhance Maintainability**: Easier to maintain and evolve domain-specific code
- **Align with Platform Philosophy**: App-platform as truly generic platform

### Recommended Next Actions

1. ✅ Review APP_PLATFORM_DOMAIN_MIGRATION_PLAN_REVIEW.md (detailed findings)
2. ✅ Complete Phase 0 analysis  (see section 3.0)
3. ✅ Create consolidation scripts with safety checks
4. ✅ Get stakeholder sign-off on deduplication strategy
5. ✅ Schedule migration in protected environment

**Status**: Ready for Phase 0 analysis (not production migration)

**Estimated Effort**: 5-8 weeks total (with proper Phase 0 analysis)
**Risk Level**: MEDIUM → LOW (with Phase 0 completion)
**Value**: HIGH (architectural improvement + duplicate cleanup)
