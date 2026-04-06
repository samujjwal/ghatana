# QUICK START: TEST EXECUTION AND COVERAGE MEASUREMENT

## Ghatana Monorepo - Post-Implementation Guide

**Last Updated:** April 6, 2026  
**Status:** Ready for Execution

---

## ONE-COMMAND QUICK EXECUTION

### Run All New Tests (Complete)

```bash
cd /home/samujjwal/Developments/ghatana

# Compile and test all new test files
./gradlew build \
  :platform-plugins:test \
  :platform:java:agent-core:test \
  :platform-kernel:test \
  :products:audio-video:test \
  :products:data-cloud:test \
  --no-build-cache
```

### Measure All Coverage

```bash
# Generate JaCoCo reports for critical modules
./gradlew \
  :platform-plugins:jacocoTestReport \
  :platform:java:agent-core:jacocoTestReport \
  :platform-kernel:jacocoTestReport \
  --continue
```

---

## STEP-BY-STEP EXECUTION

### Step 1: Verify Compilation

```bash
# Platform-Plugins (6 test files)
./gradlew :platform-plugins:compileTestJava -v

# Agent Framework (7 test files)
./gradlew :platform:java:agent-core:compileTestJava -v

# Platform-Kernel (4 test files)
./gradlew :platform-kernel:compileTestJava -v

# Audio-Video (4 test files)
./gradlew :products:audio-video:compileTestJava -v

# Data-Cloud (4 test files)
./gradlew :products:data-cloud:platform:compileTestJava -v

# Contracts (10 test files)
./gradlew :platform:contracts:compileTestJava -v
```

### Step 2: Run Tests by Module

```bash
# Platform-Plugins (71 tests)
./gradlew :platform-plugins:test --info 2>&1 | tail -50

# Agent Framework (222 tests)
./gradlew :platform:java:agent-core:test --info 2>&1 | tail -50

# Platform-Kernel (170 tests)
./gradlew :platform-kernel:test --info 2>&1 | tail -50

# Audio-Video (220 tests)
./gradlew :products:audio-video:test --info 2>&1 | tail -50

# Data-Cloud (90 tests)
./gradlew :products:data-cloud:test --info 2>&1 | tail -50
```

### Step 3: Generate Coverage Reports

```bash
# All modules with JaCoCo
./gradlew jacocoTestReport --continue

# Specific modules
./gradlew \
  :platform-plugins:plugin-audit-trail:jacocoTestReport \
  :platform-plugins:plugin-billing-ledger:jacocoTestReport \
  :platform-plugins:plugin-compliance:jacocoTestReport \
  :platform-plugins:plugin-consent:jacocoTestReport \
  :platform-plugins:plugin-fraud-detection:jacocoTestReport \
  :platform-plugins:plugin-risk-management:jacocoTestReport
```

### Step 4: View Coverage Reports

```bash
# Open HTML coverage reports in browser
# Platform-Plugins examples:
open platform-plugins/plugin-audit-trail/build/reports/jacoco/test/html/index.html
open platform-plugins/plugin-billing-ledger/build/reports/jacoco/test/html/index.html

# Agent Framework
open platform/java/agent-core/build/reports/jacoco/test/html/index.html

# Platform-Kernel
open platform-kernel/build/reports/jacoco/test/html/index.html

# Or launch file server:
cd platform-plugins/plugin-audit-trail/build/reports/jacoco/test/html
python3 -m http.server 8080
# Then visit: http://localhost:8080/
```

---

## EXPECTED OUTPUTS

### Test Compilation Success Indicators

```
> Task :platform-plugins:compileTestJava
> Task :platform:java:agent-core:compileTestJava
> Task :platform-kernel:compileTestJava
[success] Compilation completed
```

### Test Execution Success Indicators

```
> Task :platform-plugins:test
71 tests completed
[success] Tests passed: 71
```

### Coverage Report Generation

```
> Task :platform-plugins:plugin-audit-trail:jacocoTestReport
Generated JaCoCo code coverage report to:
platform-plugins/plugin-audit-trail/build/reports/jacoco/test/html/index.html
```

---

## COVERAGE REPORT INTERPRETATION

### HTML Report Structure

Each report generates:

- **index.html** - Overall coverage summary
- **Coverage bars** - Red (uncovered) → Yellow → Green (fully covered)
- **Coverage metrics** - Instruction %, Branch %, Line %, Complexity %
- **Detailed breakdown** - Classes, methods, lines

### Key Metrics to Monitor

- **Line Coverage** - % of executable lines covered by tests
- **Branch Coverage** - % of conditional branches tested
- **Complexity** - Cyclomatic complexity of code
- **Methods** - Number of methods tested

### Target Coverage Goals

- **Platform-Plugins**: Target > 80% (critical plugins)
- **Agent Framework**: Target > 85% (complex async logic)
- **Audio-Video**: Target > 75% (media processing)
- **Data-Cloud**: Target > 70%+ (large codebase with generated code)

---

## TROUBLESHOOTING

### Build Fails on Compilation

```bash
# Clean and retry with verbose output
./gradlew clean :platform-plugins:compileTestJava -v --stacktrace
```

### Tests Fail to Run

```bash
# Check for missing dependencies
./gradlew dependencies :platform-plugins

# Run with more details
./gradlew :platform-plugins:test -v --info --stacktrace
```

### JaCoCo Report Not Generated

```bash
# Ensure test task ran successfully first
./gradlew :platform-plugins:test

# Then explicitly run JaCoCo task
./gradlew :platform-plugins:jacocoTestReport --info
```

### Out of Memory

```bash
# Increase gradle heap size
export JAVA_OPTS="-Xmx4g -Xms2g"
./gradlew build
```

---

## CI/CD INTEGRATION (NEXT STEP)

### GitHub Actions Workflow Available

Workflow file created: `.github/workflows/test-coverage-verification.yml`

**To enable in GitHub:**

1. Trigger manually: Actions → Test Coverage Verification → Run workflow
2. Review results in: Artifacts → jacoco-coverage-reports
3. Summary shows in workflow run details

**To run locally (simulate CI):**

```bash
# Using act (GitHub Actions local runner)
act -j measure-coverage
```

---

## QUICK REFERENCE: NEW TEST FILES

### Platform-Plugins (6 files)

- `plugin-audit-trail/src/test/java/.../StandardAuditTrailPluginTest.java`
- `plugin-billing-ledger/src/test/java/.../StandardBillingLedgerPluginTest.java`
- `plugin-compliance/src/test/java/.../StandardCompliancePluginTest.java`
- `plugin-consent/src/test/java/.../StandardConsentPluginTest.java`
- `plugin-fraud-detection/src/test/java/.../StandardFraudDetectionPluginTest.java`
- `plugin-risk-management/src/test/java/.../StandardRiskManagementPluginTest.java`

### Agent Framework (7 files)

- `platform/java/agent-core/src/test/java/.../DeterministicAgentBehavioralTest.java`
- `platform/java/agent-core/src/test/java/.../ProbabilisticAgentBehavioralTest.java`
- `platform/java/agent-core/src/test/java/.../HybridAgentBehavioralTest.java`
- `platform/java/agent-core/src/test/java/.../CompositeAgentBehavioralTest.java`
- `platform/java/agent-core/src/test/java/.../ReactiveAgentBehavioralTest.java`
- `platform/java/agent-core/src/test/java/.../AdaptiveAgentBehavioralTest.java`
- `platform/java/agent-core/src/test/java/.../PlanningAgentBehavioralTest.java`

### Data-Cloud Services (4 files)

- `products/data-cloud/platform-launcher/src/test/java/.../ResilientEventLogStoreTest.java`
- `products/data-cloud/platform-api/src/test/java/.../EntityServiceTest.java`
- `products/data-cloud/platform-api/src/test/java/.../AIAssistServiceTest.java`
- `products/data-cloud/agent-registry/src/test/java/.../DataCloudAgentRegistryComprehensiveTest.java`

### Audio-Video Services (4 files)

- `products/audio-video/modules/speech/stt-service/src/test/java/.../SpeechRecognitionServiceTest.java`
- `products/audio-video/modules/speech/tts-service/src/test/java/.../TextToSpeechServiceTest.java`
- `products/audio-video/modules/vision/vision-service/src/test/java/.../ComputerVisionServiceTest.java`
- `products/audio-video/modules/intelligence/multimodal-service/src/test/java/.../MultimodalEngineServiceTest.java`

### Kernel Plugin Tests (4 files)

- `platform-kernel/kernel-plugin/src/test/java/.../PluginDependencyResolutionTest.java`
- `platform-kernel/kernel-plugin/src/test/java/.../PluginActivationTest.java`
- `platform-kernel/kernel-plugin/src/test/java/.../PluginIntegrationTest.java`
- `platform-kernel/kernel-core/src/test/java/.../PluginEdgeCasesTest.java`

### Contract Tests (10 files)

- Proto: `platform/contracts/src/test/java/.../EventMessageContractTest.java`
- OpenAPI: `platform/contracts/src/test/java/.../AuthGatewayApiContractTest.java`
- HTTP: `platform/java/http/src/test/java/.../Http*ContractTest.java` (3 files)
- Application: `products/data-cloud/platform-api/src/test/java/.../DataCloud*ContractTest.java` (4 files)

---

## REFERENCE DOCUMENTATION

- **Main Audit Report**: TEST_AUDIT_REPORT_2026-04-05.md
- **Session Summary**: TEST_IMPLEMENTATION_SESSION_SUMMARY.md
- **Closure Checklist**: TEST_IMPLEMENTATION_CLOSURE_CHECKLIST.md
- **Data-Cloud Analysis**: DATA_CLOUD_COVERAGE_ANALYSIS.md
- **Agent Framework Tests**: AGENT_BEHAVIORAL_TEST_SUITE_SUMMARY.md
- **Contract Tests**: COMPREHENSIVE_CONTRACT_TEST_SUMMARY.md
- **CI/CD Workflow**: .github/workflows/test-coverage-verification.yml

---

## NEXT STEPS SEQUENCE

1. ✅ **Execute full test compilation** (5 minutes)

   ```bash
   ./gradlew build --dry-run
   ```

2. ✅ **Run all tests** (15-30 minutes)

   ```bash
   ./gradlew test --continue
   ```

3. ✅ **Generate coverage reports** (5-10 minutes)

   ```bash
   ./gradlew jacocoTestReport
   ```

4. ⏳ **Review coverage baselines** (20 minutes)
   - Open HTML reports
   - Identify top coverage gaps
   - Plan second-wave improvements

5. ⏳ **Implement CI/CD gates** (1-2 hours)
   - Enable GitHub Actions workflow
   - Configure minimum coverage thresholds
   - Set up automatic reporting

---

**Ready to Execute!** 🚀

Run the one-command quick execution above to verify all tests compile and generate baseline coverage metrics.
