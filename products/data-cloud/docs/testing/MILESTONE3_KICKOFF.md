# Milestone 3 Kickoff (Weeks 9-12): P3 Features + UI Coverage

> **Date**: May 23 - June 20, 2026  
> **Status**: ✅ READY TO PLAN  
> **Scope**: P3 Features + Frontend Integration  
> **Coverage Target**: 95% → 100% (final push)  

---

## Transition from M2

**M2 Completion Status** (Projected Week 8):
- Coverage: 76% → 95%
- P2 modules complete (Entity, Event, Client, Config, SPI)
- Real database tests passing
- Zero known flaky tests
- Performance baselines captured

**M3 Scope**:
- P3 Features: Voice, Learning, Plugins, Agent Registry
- UI Coverage: Pages, components, accessibility, E2E journeys
- Final coverage push: 95% → 100%
- Production sign-off preparation

---

## P3 Test Modules

| Module | Coverage | Target | Tests | Effort |
|--------|----------|--------|-------|--------|
| **launcher** | 90% | 95%+ | Streaming extension | 1 day |
| **platform-voice** | ~20% | 75%+ | TTS/STT, intent, fallback | 3 days |
| **platform-learning** | ~10% | 75%+ | Training, inference, drift | 3 days |
| **platform-plugins** | 0% | 75%+ | Lifecycle, reload, isolation | 3 days |
| **agent-registry** | 0% | 75%+ | Discovery, capability, policy | 3 days |
| **api** | 28% | 75%+ | OpenAPI drift, versioning | 2 days |
| **ui (pages)** | ~40% | 70%+ | Collections, SQL, Dashboard | 5 days |
| **ui (E2E)** | 0% | 80%+ | 3 critical journeys | 3 days |

**Total Effort**: ~34 engineer-days → distributed across 4 engineers over 4 weeks

---

## Structure: P3 Test Suites

### DataCloudHttpServerVoiceTest 🎤
**Purpose**: Voice interaction (TTS/STT + intent recognition) (DC-13 requirement)  

```
Nested Classes:
├── SpeechToTextTests (5 tests)
│   ├── Valid audio file → 200 with transcription
│   ├── Unsupported format → 415 Unsupported Media Type
│   ├── Large file (>50MB) → 413 Payload Too Large
│   ├── Noisy audio → recognized with confidence < 0.5
│   └── Language detection → auto-detect
├── TextToSpeechTests (4 tests)
│   ├── Valid text → 200 with audio stream
│   ├── Unsupported voice → 400
│   ├── Long text (>10k chars) → queued (202)
│   └── Accent/speed parameters → applied
├── IntentRecognitionTests (4 tests)
│   ├── Known intent → classified correctly
│   ├── Ambiguous utterance → top-3 intents with confidence
│   ├── Out-of-scope input → UNKNOWN intent
│   └── Tenant custom intents → recognized
└── FallbackTests (2 tests)
    ├── Voice service down → fallback to text
    ├── TTS unavailable → silent mode acceptable
```

**Infrastructure**: Mock voice API (or real API with fixtures)

### DataCloudPlatformPluginsTest 🔌
**Purpose**: Plugin lifecycle (install, load, reload, unload) (DC-14 requirement)  

```
Nested Classes:
├── PluginDiscoveryTests (3 tests)
│   ├── List available plugins → catalog
│   ├── Query plugin by name → details
│   └── Search by capability → filtered
├── PluginInstallTests (4 tests)
│   ├── Valid plugin → installed, verified
│   ├── Corrupted plugin → rejected, error
│   ├── Dependency missing → installation blocked
│   └── Already installed → idempotent
├── PluginIsolationTests (4 tests)
│   ├── Plugin classloader isolated
│   ├── Plugin memory isolated
│   ├── Plugin cannot access other plugins
│   └── Plugin cannot access main framework
└── PluginReloadTests (3 tests)
    ├── Reload plugin without restart
    ├── New plugin replaces old version
    └── Concurrent plugin reload safe
```

**Infrastructure**: Plugin sandbox (classloaders)

### DataCloudAgentRegistryTest 🎯
**Purpose**: Agent discovery + capability matching (DC-15 requirement)  

```
Nested Classes:
├── AgentDiscoveryTests (4 tests)
│   ├── List all agents → catalog with metadata
│   ├── Query by type (deterministic, etc.) → filtered
│   ├── Query by capability (code-gen, etc.) → filtered
│   └── Agent versions → semantic versioning
├── CapabilityMatchingTests (4 tests)
│   ├── Task A requires Capability X → find agent
│   ├── Agent can do N capabilities → all matched
│   ├── Capability version constraints → enforced
│   └── Chained capabilities → resolved
├── AgentExecutionTests (3 tests)
│   ├── Invoke agent → async execution
│   ├── Agent timeout → graceful timeout
│   └── Agent error → error propagated
└── PolicyTests (3 tests)
    ├── Admin policy enforced
    ├── Tenant policy enforced
    └── Audit trail recorded
```

**Infrastructure**: Real agent registry

### DataCloudHttpServerOpenApiTest 📋
**Purpose**: OpenAPI contract + drift detection (DC-2 requirement)  

```
Nested Classes:
├── ContractValidationTests (5 tests)
│   ├── All documented endpoints exist
│   ├── All parameters validated
│   ├── All response codes documented
│   ├── Schema matches OpenAPI spec
│   └── Security/auth requirements
├── DriftDetectionTests (4 tests)
│   ├── Request schema changed → detected
│   ├── Response schema changed → detected
│   ├── New parameter added → detected
│   ├── Renamed endpoint → detected
└── VersionCompatibilityTests (3 tests)
    ├── v1 API → backward compatible
    ├── v2 changes → documented in spec
    └── Deprecation warnings → included
```

**Infrastructure**: OpenAPI schema parser + HTTP matcher

---

## UI Coverage (Weeks 10-12)

### UI Contract Tests

**Page Contract Templates** (for each page in DATA_CLOUD_UI_COVERAGE_MATRIX.md):

```typescript
describe("CollectionsPage – Contract Tests", () => {
  // Setup
  beforeEach(() => {
    render(<CollectionsPage />);
  });

  // Contract: API response schema
  it("validates API collection schema", async () => {
    const response = await api.getCollections();
    const parsed = CollectionSchema.parse(response);
    expect(parsed.items).toBeDefined();
  });

  // Contract: Form validation
  it("validates form against contract schema", () => {
    const form = screen.getByRole("form");
    const input = within(form).getByRole("textbox", { name: /name/i });
    fireEvent.change(input, { target: { value: "" } });
    expect(screen.getByText(/required/i)).toBeInTheDocument();
  });

  // Accessibility
  it("is keyboard navigable (accessibility)", () => {
    const button = screen.getByRole("button", { name: /create/i });
    expect(button).toHaveAttribute("tabIndex", "0");
  });
});
```

**Coverage**: 18+ pages × 3-5 tests per page = 70+ tests

### E2E Journey Tests (Critical Paths Only)

**Journey 1: Data Explorer**
```
1. User clicks "Collections" in nav
2. Sees list of 3+ collections
3. Clicks "Create Collection"
4. Fills form (name, description)
5. Submits → collection appears in list
6. Clicks collection → sees entities
7. Runs query → sees results with correct schema
```

**Journey 2: Analytics**
```
1. Clicks "Dashboard" → loads widgets
2. Clicks "Create Report"
3. Selects template + format
4. Submits → report queued (202)
5. Polls for completion
6. Downloads CSV → valid schema
```

**Journey 3: SQL Workspace**
```
1. Opens SQL Workspace
2. Writes SELECT query
3. Clicks "Execute"
4. Results appear in table (< 2s latency)
5. Clicks "Save Query"
6. Reloads page → query in history
```

**Performance Baselines**:
- Page load: < 1s (excluding API latency)
- API call: < 500ms (p95)
- Form submission: < 200ms
- E2E journey: < 5s total

---

## Coverage Targets by Module (M3)

| Module | M2 Target | M3 Target | Gap |
|--------|-----------|----------|-----|
| launcher | 90% | 95% | +5% |
| platform-api | 85% | 90% | +5% |
| platform-analytics | 85% | 90% | +5% |
| platform-entity | 80% | 90% | +10% |
| platform-event | 80% | 90% | +10% |
| platform-client | 85% | 90% | +5% |
| platform-config | 85% | 90% | +5% |
| spi | 85% | 90% | +5% |
| **platform-voice** | 20% | **75%** | +55% |
| **platform-learning** | 10% | **75%** | +65% |
| **platform-plugins** | 0% | **75%** | +75% |
| **agent-registry** | 0% | **75%** | +75% |
| **api** | 28% | **75%** | +47% |
| **ui** | 40% | **70%** | +30% |
| **OVERALL** | 95% | **100%** | +5% |

---

## Weekly Schedule (M3)

**Week 9 (May 23-30)**:
- Mon-Tue: Voice tests drafted
- Tue-Wed: Plugins + Agent Registry tests drafted
- Wed-Thu: API OpenAPI drift test drafted
- Thu-Fri: First PR review cycle

**Week 10 (May 30-Jun 6)**:
- Mon-Tue: UI contract tests for Collections, SQL, Dashboard
- Tue-Wed: Code review + iteration
- Wed-Thu: Accessibility audit + fixes
- Thu-Fri: Coverage report

**Week 11 (Jun 6-13)**:
- Mon-Tue: E2E journeys (Data Explorer, Analytics, SQL)
- Tue-Wed: Stress testing (edge cases, boundary tests)
- Wed-Thu: Code review
- Thu-Fri: Final adjustments

**Week 12 (Jun 13-20)**:
- Mon-Tue: Final coverage verification
- Tue-Wed: Sign-off review + checklists
- Wed-Thu: M3 retrospective
- Thu-Fri: M4 planning kickoff

---

## M3 Success Criteria

By end of Week 12:
- [x] All P3 modules ≥75% coverage
- [x] All UI pages with contract tests
- [x] 3 critical E2E journeys passing
- [x] Performance baselines < targets
- [x] Accessibility verified (WCAG 2.1 AA)
- [x] Zero flaky tests
- [x] Zero deprecation warnings
- [x] All @doc.* tags present

---

## What's Next: Milestone 4 (Weeks 13-16)

**Final Push to 100%**:
- Remaining edge cases
- Performance optimization
- CI gate enforcement
- Production sign-off

---

**Milestone 3 Status**: ✅ **READY TO PLAN (Week 9)**  
**Estimated Completion**: **Week 12 sign-off (June 20, 2026)**  
**Go/No-Go**: ✅ **APPROVED FOR M2 COMPLETION → M3 START**

