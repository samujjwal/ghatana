# Phase 2 Risk Mitigation & Contingency Plan (Weeks 5-8)

**Purpose**: Anticipate challenges, provide solutions, maintain momentum  
**Scope**: Week 5 (Security) through Week 8 (Database)  
**Owner**: Module leads (with architecture lead support)  

---

## Risk Assessment Matrix

### WEEK 5 (Security) - LOW RISK 🟢

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Template incompleteness | LOW | MEDIUM | 38 tests verified, all compile |
| Dev unfamiliar with async | MEDIUM | MEDIUM | Sample tests provided, pair programming |
| Test flakiness (timing) | MEDIUM | LOW | Use EventLoop delays, timeout buffers |
| Build slowness | LOW | LOW | Gradle cache, parallel builds |

### WEEK 6 (Observability) - MEDIUM RISK 🟡

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Micrometer pattern mismatch | MEDIUM | MEDIUM | Deep dive completed, dry-run testing |
| OpenTelemetry API changes | LOW | HIGH | Use current version, pin in gradle |
| ClickHouse integration test issues | MEDIUM | MEDIUM | Testcontainers available, fallback mock |
| Team unfamiliar with module | MEDIUM | MEDIUM | Lead provides pattern guide + examples |

### WEEK 7 (HTTP) - MEDIUM RISK 🟡

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| JSON serialization edge cases | MEDIUM | LOW | Use Jackson ObjectMapper tests first |
| HTTP handler pattern confusion | MEDIUM | MEDIUM | Study week 6 patterns, templates ready |
| Async servlet timing issues | MEDIUM | MEDIUM | EventLoopExtension, delay mechanisms |

### WEEK 8 (Database) - MEDIUM RISK 🟡

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Transaction isolation issues | MEDIUM | MEDIUM | Test isolation per method, rollback |
| Connection pool exhaustion | MEDIUM | MEDIUM | Mock pool, verify close() behavior |
| Migration test complexity | LOW | MEDIUM | Schema fixtures provided |

---

## Contingency Plans (If X Happens)

### CONTINGENCY 1: "Test Template Is Wrong"

**Symptom**: Multiple tests fail with same error (wrong API method, wrong class name)

**Detection**: 
- 3+ tests fail with same root cause
- Lead realizes template assumption invalid
- Error pattern repeats across developers

**Immediate Actions** (within 1 hour):
1. **Pause**: Stop all developers writing tests of this type
2. **Analyze**: Lead investigates actual API in production code
3. **Fix Template**: Update template with correct API
4. **Validate**: Test 1 template locally before team continues
5. **Resume**: Team continues with fixed template

**Example**:
```
WRONG: SpanData.traceId("trace-123")
RIGHT: SpanData.builder().withTraceId("trace-123").build()

Action: 
1. Read SpanDataBuilder.java to get exact API
2. Update OBSERVABILITY_PATTERNS.md with correct API
3. Team re-runs tests with fixed template
```

**Prevention**: Week 5 dry-run catches these early

### CONTINGENCY 2: "Build Performance Degraded"

**Symptom**: Tests take 120+ seconds to run (target: <60s)

**Causes**:
- Gradle cache corrupted
- Test database slow (ClickHouse, real DB not mocked)
- Parallel test execution conflict

**Immediate Actions** (within 30 min):
```bash
# Try 1: Clear Gradle cache
./gradlew clean --no-cache

# Try 2: Use daemon
./gradlew --daemon platform:java:[module]:test

# Try 3: Parallel execution
./gradlew --parallel platform:java:[module]:test

# Try 4: Rebuild dependency tree
./gradlew --refresh-dependencies cleanBuild
```

**If Still Slow** (escalate):
- Architecture lead reviews test setup
- May need mock database instead of real Testcontainers
- Document workaround in handbook

**Prevention**: Monitor build time daily, investigate <60s violations immediately

### CONTINGENCY 3: "One Developer Falls Behind"

**Symptom**: Dev A has completed 4/12 tests while Dev B completed 8/12 (mismatched pace)

**Root Causes**:
- Dev A less familiar with pattern
- Dev A taking longer per test (perfectionism)
- Dev A distracted by other tasks

**Immediate Actions** (within 1 day):
1. **Pair Programming**: Dev B spends 2 hours with Dev A on next test
2. **Template Clarification**: "What part is unclear? API? Mocking? Assertions?"
3. **Adjust Assignments**: If pattern is genuinely harder, redistribute tests
4. **Document**: Add "common mistakes" section to handbook

**Example**:
```
Dev A: "AES-GCM encryption tests are complex, 1 test = 2 hours"
→ Pair Dev A + Dev B for 2 tests (4 hours)
→ Dev A now knows pattern, can solo remaining 4 tests (8 hours)
→ Adjusted timeline: Wed instead of Tue
```

**Prevention**: Daily standups catch pace issues early

### CONTINGENCY 4: "Critical Bug in Existing Tests Found"

**Symptom**: One of 259 existing security tests suddenly fails

**Actions**:
1. **Isolate**: Run just that test to verify failure
2. **Diagnose**: Is it a new test affecting existing state? (order-dependent test)
3. **Fix**: 
   - If new test: Add cleanup in new test's `@AfterEach`
   - If existing test: Understand why it's failing now
4. **Validate**: Re-run full suite to confirm no regression
5. **Document**: Add to "gotchas" in handbook

**Prevention**: Run full test suite daily (`./gradlew platform:java:security:test`)

### CONTINGENCY 5: "Team Velocity Drops >30%"

**Symptom**: Week 6 only achieves 25/36 observability tests instead of 36

**Root Cause Analysis**:
- Pattern more complex than expected?
- Team energy low?
- Technical blockers?

**Immediate Actions**:
1. **Root Cause**: Friday retrospective identifies why
2. **Adjust Week 7**: Reduce HTTP target from 57 to 45+ tests (still achievable)
3. **Add Support**: Bring architecture lead to Week 7 daily participation
4. **Reset Expectations**: "We'll hit 165+ tests instead of 202+, still strong progress"

**Example**:
```
Week 6 actual: 25 tests (69% of target)
Week 7 adjusted target: 45 tests (instead of 57)
Week 8 adjusted target: 70 tests (instead of 71)
Phase 2 new total: 178 tests (was 202)
Status: Still VERY STRONG, just slightly adjusted
```

**Prevention**: Track velocity daily, trend analysis on Friday

### CONTINGENCY 6: "Team Member Gets Sick/Unavailable"

**Scenario**: Dev A unavailable for 2-3 days (or week)

**Immediate Actions**:
1. **Reassign**: Dev B takes over Dev A's tests
2. **Extend**: Timeline slips by 1-2 days (acceptable)
3. **Keep Momentum**: Other developers continue, don't pause

**Example**:
```
Scenario: Dev A (security) sick Monday-Wednesday
Impact: 12 tests delayed
Action: Dev B continues own work + picks up Dev A's tests after work
Result: Scale back to 6 tests/day instead of 8 = same completion Wed
```

**Prevention**: Pair programming reduces single-person dependency

### CONTINGENCY 7: "Dependency Issue (Import/Gradle)"

**Symptom**: `cannot find symbol` import errors, or Gradle download fails

**Immediate Actions** (within 15 min):
```bash
# Try 1: Refresh dependencies
./gradlew --refresh-dependencies build

# Try 2: Check repo connectivity
ping repo.maven.apache.org

# Try 3: Run without network (offline)
./gradlew build --offline

# Try 4: Delete .gradle folder
rm -rf ~/.gradle
./gradlew build  # Re-download everything
```

**If Still Blocked**:
- Escalate to architecture lead
- May need to add missing version to `gradle/libs.versions.toml`
- Check if import path matches actual JAR structure

**Prevention**: Weekly validation that `./gradlew build` works

### CONTINGENCY 8: "API Changes Mid-Week"

**Scenario**: Production code API changes (someone merges breaking change to main)

**Actions**:
1. **Detect**: Test suite fails on pull
2. **Notify**: Developer who made change updates templates
3. **Team Update**: 30-min all-hands to explain changes
4. **Adjust Tests**: Team updates tests to match new API
5. **Continue**: Momentum maintained

**Protection**: Code review process prevents untested breaking changes

**Prevention**: API changes require template updates before merge

---

## Escalation Decision Tree

```
Issue Found
    ↓
Can dev solve in <15 min?
    ├─→ YES: Solve in place, continue
    ├─→ NO: Can pair solve in <30 min?
           ├─→ YES: Pair for 30 min, continue
           ├─→ NO: Module lead involved
                   ├─→ Can lead solve in <1 hour?
                   │   ├─→ YES: Lead fixes, team continues
                   │   ├─→ NO: Escalate to architecture lead
                   │           (decision point, may need sprint adjustment)
```

---

## Weekly Checkpoint Gates

### Every Friday 4:00 PM

**Question 1: Are we on track for Weeks 6-8?**
- [ ] YES (>80% of weekly target done) → Continue as planned
- [ ] YELLOW (60-80% of target) → Minor adjustment
- [ ] NO (<60% of target) → Major adjustment needed

**Question 2: Do we have confidence in Week N+1?**
- [ ] YES (templates ready, team clear) → Launch Monday
- [ ] YELLOW (need 1-2 days prep) → Light prep week
- [ ] NO (major issues) → Extend current week 2-3 days

**Question 3: Is team morale & energy good?**
- [ ] YES (😊 strong momentum) → Maintain pace
- [ ] YELLOW (😐 getting tired) → Add light Friday + Monday ease
- [ ] NO (😟 burned out) → Reduce target for next week

---

## Buffer Built Into Timeline

**Original Plan**: 202 tests in 3 weeks  
**Aggressive**: 8-10 tests/day per developer

**With Contingency Buffers**:
- Week 5 target: 38 tests (gives Tue/Wed overflow room)
- Week 6 target: 36+ tests (not 57, allows learning week)
- Week 7 target: 57+ tests (increased velocity)
- Week 8 target: 71+ tests (in parallel)
- **Built-In Buffer**: ~20% slack across weeks

**If Issues Happen**:
- Week 5: Slip to 35 tests (still viable)
- Week 6: Slip to 30 tests (recoverable in Week 7)
- Week 7-8: Higher capacity absorbs weeks 5-6 slips
- **Overall**: Still hit 180+ tests (90% of 200 target) by June 13

---

## Communication Plan for Issues

### Issue Found → Whole Team Knows in <4 Hours

**Escalation Communication**:
1. **Dev**: Report in standup immediately
2. **Lead**: Acknowledges in standup, says "we'll address after standup"
3. **Team**: After standup, lead + architecture lead + affected dev meet (30 min)
4. **Decision**: "How do we proceed?" (fix, workaround, delay)
5. **Slack**: Lead posts summary to team: "FYI: JWT tests had [issue]. Fix applied: [solution]. Continue as planned."

**No Silent Failures**: Every blocker communicated same day

---

## Success Patterns to Replicate

### From Security Module (What Worked)

✅ **Daily standups** → Issues surface immediately  
✅ **Pair programming** → Slower dev gets faster  
✅ **Template validation** → Catches API mismatches early  
✅ **Build every 2 hours** → Errors caught fast  
✅ **Friday retrospective** → Team reflects on improvements  

**Replicate for Observability/HTTP/Database weeks**

---

## Risk Review Schedule

| When | What | Owner |
|------|------|-------|
| Monday 9:00 AM | Weekly kickoff, risk review | Module lead |
| Daily 5:00 PM | Standup, issue check | Daily scrum |
| Friday 4:00 PM | Weekly checkpoint gates | Architecture lead |
| End of week | Full retrospective | Full team |

---

## Final Safety Net: Phase 2 Recovery Options

**If we're behind after Week 6**:
1. **Option A**: Extend to 9 weeks (one extra week), maintain quality
2. **Option B**: Reduce targets (180 tests instead of 202), ship on time
3. **Option C**: Add 2-3 more developers for Weeks 7-8 (fast-track)

**Current Confidence**: No recovery needed, but available if unexpected

---

## Handbook: Lessons Learned During Week 5

Use Friday retrospective to add answers to common questions:

```markdown
## Week 5 Lessons

### Q: How do I test async code with ActiveJ promises?
A: [Answer from dev who solved it]

### Q: What's the difference between EventloopTestBase vs EventloopExtension?
A: [Answer documented during week]

### Q: How do I mock SecurityContext in tests?
A: Use SecurityMockFactory.createContext() - here's example code

### Q: Why did this test pass locally but fail in CI?
A: [Common causes and fixes]
```

This becomes team knowledge base for future weeks.

---

## Summary: We're Prepared

✅ **Identified 8 major risks** with specific mitigations  
✅ **Created contingency plans** for each scenario  
✅ **Built 20% buffer** into timeline  
✅ **Escalation path clear** (3-level escalation)  
✅ **Communication plan strong** (no silent failures)  
✅ **Checkpoint gates** to catch issues early  
✅ **Recovery options** available if needed  

**Confidence**: 🟢 VERY HIGH that Phase 2 completes on time

---

**Status**: Ready for Weeks 5-8 execution  
**Last Updated**: April 4, 2026  
**Owner**: Architecture Lead  

