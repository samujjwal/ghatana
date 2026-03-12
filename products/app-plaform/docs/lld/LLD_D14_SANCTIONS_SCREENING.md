# LOW-LEVEL DESIGN: D-14 SANCTIONS SCREENING

**Module**: D-14 Sanctions Screening  
**Layer**: Domain  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Compliance Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

D-14 provides **real-time sanctions and PEP (Politically Exposed Person) screening** at order placement, client onboarding, and periodic batch re-screening. The module supports fuzzy name matching, configurable sanction lists, match review workflows with maker-checker, and air-gap operation with offline signed list bundles.

**Core Responsibilities**:
- Real-time screening at order placement (P99 < 50ms)
- Client onboarding screening with enhanced due diligence triggers
- Fuzzy name matching (Levenshtein, Jaro-Winkler, phonetic encoding)
- Configurable match threshold per jurisdiction
- Match review workflow with maker-checker approval
- Sanction list management (ingest, update, versioning)
- Air-gap support — offline signed list bundles for disconnected environments
- Periodic batch re-screening (daily/weekly)
- Dual-calendar timestamps on all screening records

**Invariants**:
1. ALL orders MUST be screened before execution — zero bypass
2. Screening MUST complete within 50ms P99 to avoid order latency impact
3. Match review MUST follow maker-checker workflow
4. Sanction list updates MUST be applied within 15 minutes of receipt
5. False-negative rate MUST be < 0.01% (safety-critical)
6. Screening audit trail MUST be retained for 10 years
7. Air-gap bundles MUST be cryptographically signed

### 1.2 Explicit Non-Goals

- ❌ KYC document verification — handled by KYC module via K-01 plugins
- ❌ AML transaction monitoring — handled by D-08 Surveillance
- ❌ Regulatory reporting — handled by D-10 / R-02
- ❌ Customer risk scoring — handled by D-07 Compliance risk model

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-01 IAM | User identity for screening context | K-01 stable |
| K-02 Configuration Engine | Threshold config, list sources | K-02 stable |
| K-05 Event Bus | Screening events | K-05 stable |
| K-07 Audit Framework | Screening audit trail | K-07 stable |
| K-15 Dual-Calendar | BS timestamps | K-15 stable |
| K-18 Resilience | External list fetch resilience | K-18 stable |
| D-01 OMS | Order placement hook | D-01 stable |
| D-07 Compliance | Compliance status feed | D-07 stable |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/sanctions/screen
Authorization: Bearer {service_token}

Request:
{
  "screening_type": "ORDER",
  "reference_id": "ORD-2025-001",
  "tenant_id": "tenant_np_1",
  "subjects": [
    {
      "type": "INDIVIDUAL",
      "name": "Ram Bahadur Thapa",
      "aliases": ["R.B. Thapa", "Ram B Thapa"],
      "date_of_birth": "1975-05-15",
      "nationality": "NP",
      "id_number": "NPL-CIT-12345"
    }
  ],
  "lists": ["OFAC_SDN", "UN_CONSOLIDATED", "NRB_LOCAL", "EU_SANCTIONS"],
  "threshold": 0.85
}

Response 200:
{
  "screening_id": "scr_001",
  "result": "NO_MATCH",
  "matches": [],
  "screened_at": "2025-03-02T10:30:00.023Z",
  "screened_at_bs": "2081-11-18 10:30:00",
  "duration_ms": 23,
  "lists_checked": ["OFAC_SDN", "UN_CONSOLIDATED", "NRB_LOCAL", "EU_SANCTIONS"],
  "list_versions": {
    "OFAC_SDN": "2025-03-01T00:00:00Z",
    "UN_CONSOLIDATED": "2025-02-28T12:00:00Z",
    "NRB_LOCAL": "2081-11-17",
    "EU_SANCTIONS": "2025-03-01T08:00:00Z"
  }
}
```

```yaml
# Response when potential match found
Response 200:
{
  "screening_id": "scr_002",
  "result": "POTENTIAL_MATCH",
  "matches": [
    {
      "match_id": "mtch_001",
      "list_name": "OFAC_SDN",
      "list_entry_id": "SDN-45678",
      "matched_name": "Ram Bahadur THAPA",
      "listed_name": "Ram Bahadoor THAPA",
      "match_score": 0.92,
      "match_algorithms": {
        "levenshtein": 0.95,
        "jaro_winkler": 0.93,
        "soundex": true,
        "double_metaphone": true
      },
      "list_entry_details": {
        "designation": "Individual",
        "program": "SDGT",
        "listed_date": "2023-06-15"
      }
    }
  ],
  "action": "HOLD_FOR_REVIEW",
  "screened_at": "2025-03-02T10:30:00.031Z",
  "duration_ms": 31
}
```

```yaml
GET /api/v1/sanctions/matches?status=PENDING_REVIEW&limit=50
Authorization: Bearer {compliance_token}

Response 200:
{
  "matches": [
    {
      "match_id": "mtch_001",
      "screening_id": "scr_002",
      "subject_name": "Ram Bahadur Thapa",
      "listed_name": "Ram Bahadoor THAPA",
      "match_score": 0.92,
      "list_name": "OFAC_SDN",
      "reference_type": "ORDER",
      "reference_id": "ORD-2025-001",
      "status": "PENDING_REVIEW",
      "created_at": "2025-03-02T10:30:00Z",
      "created_at_bs": "2081-11-18 10:30:00"
    }
  ]
}
```

```yaml
POST /api/v1/sanctions/matches/{match_id}/review
Authorization: Bearer {compliance_token}

Request:
{
  "decision": "FALSE_POSITIVE",
  "rationale": "Different date of birth (1975 vs 1958). Different citizenship number.",
  "evidence": ["dob_verification.pdf", "citizenship_scan.jpg"],
  "reviewer": "compliance_officer_01"
}

Response 200:
{
  "match_id": "mtch_001",
  "status": "PENDING_APPROVAL",
  "message": "Review submitted — awaiting checker approval"
}
```

```yaml
POST /api/v1/sanctions/matches/{match_id}/approve
Authorization: Bearer {senior_compliance_token}

Request:
{
  "approved": true,
  "checker_comment": "Verified DOB mismatch — confirmed false positive"
}

Response 200:
{
  "match_id": "mtch_001",
  "status": "CLEARED",
  "order_released": true,
  "released_at": "2025-03-02T11:00:00Z"
}
```

```yaml
POST /api/v1/sanctions/lists/import
Authorization: Bearer {admin_token}

Request (multipart):
{
  "list_name": "NRB_LOCAL",
  "format": "CSV",
  "file": <binary>,
  "signature": "base64_ed25519_signature",
  "effective_date": "2025-03-02",
  "effective_date_bs": "2081-11-18"
}

Response 200:
{
  "list_name": "NRB_LOCAL",
  "version": "2025-03-02T10:00:00Z",
  "entries_added": 15,
  "entries_removed": 3,
  "entries_modified": 7,
  "total_entries": 1250,
  "index_rebuilt": true
}
```

```yaml
POST /api/v1/sanctions/batch-rescreen
Authorization: Bearer {admin_token}

Request:
{
  "scope": "ALL_ACTIVE_CLIENTS",
  "lists": ["OFAC_SDN", "UN_CONSOLIDATED", "NRB_LOCAL"],
  "tenant_id": "tenant_np_1"
}

Response 202:
{
  "batch_id": "batch_rescreen_001",
  "status": "IN_PROGRESS",
  "total_subjects": 125000,
  "estimated_duration_minutes": 25
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.sanctions.v1;

service SanctionsService {
  rpc Screen(ScreenRequest) returns (ScreenResponse);
  rpc BatchScreen(BatchScreenRequest) returns (BatchScreenResponse);
  rpc ReviewMatch(ReviewMatchRequest) returns (ReviewMatchResponse);
  rpc ApproveMatch(ApproveMatchRequest) returns (ApproveMatchResponse);
  rpc ImportList(ImportListRequest) returns (ImportListResponse);
  rpc GetListStatus(GetListStatusRequest) returns (ListStatusResponse);
}
```

### 2.3 SDK Method Signatures

```typescript
interface SanctionsClient {
  /** Real-time screen (called inline during order placement) */
  screen(request: ScreenRequest): Promise<ScreenResult>;

  /** Batch re-screen all active clients */
  batchRescreen(scope: string, lists: string[]): Promise<BatchJob>;

  /** Submit match review (maker) */
  reviewMatch(matchId: string, review: MatchReview): Promise<void>;

  /** Approve match review (checker) */
  approveMatch(matchId: string, approved: boolean, comment: string): Promise<void>;

  /** Import/update sanction list */
  importList(listName: string, data: Buffer, signature: Buffer): Promise<ListImportResult>;

  /** Get current list versions */
  getListVersions(): Promise<ListVersion[]>;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| SAN_E001 | 503 | Yes | Screening service unavailable — order must be held |
| SAN_E002 | 400 | No | Invalid list format |
| SAN_E003 | 401 | No | Invalid list signature (air-gap verification failed) |
| SAN_E004 | 409 | No | Match already reviewed |
| SAN_E005 | 403 | No | Same user cannot be maker and checker |
| SAN_E006 | 400 | No | Unknown sanction list |
| SAN_E007 | 500 | No | Screening timeout — order held for manual review |

---

## 3. DATA MODEL

### 3.1 Sanction Lists

```sql
CREATE TABLE sanction_lists (
  list_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  list_name VARCHAR(100) NOT NULL UNIQUE,
  source_url TEXT,                       -- NULL for air-gap lists
  format VARCHAR(20) NOT NULL DEFAULT 'CSV',
  current_version TIMESTAMPTZ NOT NULL,
  total_entries INT NOT NULL DEFAULT 0,
  last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_updated_bs VARCHAR(30) NOT NULL,
  signature_public_key TEXT,             -- Ed25519 public key for verification
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);
```

### 3.2 Sanction List Entries

```sql
CREATE TABLE sanction_list_entries (
  entry_id VARCHAR(100) NOT NULL,
  list_id UUID NOT NULL REFERENCES sanction_lists(list_id),
  entry_type VARCHAR(20) NOT NULL CHECK (entry_type IN ('INDIVIDUAL', 'ENTITY', 'VESSEL', 'AIRCRAFT')),
  primary_name VARCHAR(500) NOT NULL,
  aliases JSONB NOT NULL DEFAULT '[]',       -- ["alias1", "alias2"]
  date_of_birth DATE,
  nationality VARCHAR(10),
  id_numbers JSONB NOT NULL DEFAULT '[]',    -- [{"type": "PASSPORT", "value": "N1234"}]
  program VARCHAR(100),                      -- SDGT, CYBER2, etc.
  listed_date DATE,
  delisted_date DATE,
  metadata JSONB NOT NULL DEFAULT '{}',
  name_tokens TSVECTOR,                      -- full-text search tokens
  phonetic_codes JSONB NOT NULL DEFAULT '[]', -- pre-computed Soundex / Double Metaphone
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (entry_id, list_id)
);

CREATE INDEX idx_sanctions_name_fts ON sanction_list_entries USING GIN(name_tokens);
CREATE INDEX idx_sanctions_phonetic ON sanction_list_entries USING GIN(phonetic_codes);
CREATE INDEX idx_sanctions_active ON sanction_list_entries(list_id, is_active);
```

### 3.3 Screening Results

```sql
CREATE TABLE screening_results (
  screening_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  screening_type VARCHAR(20) NOT NULL CHECK (screening_type IN ('ORDER', 'ONBOARDING', 'PERIODIC', 'AD_HOC')),
  reference_type VARCHAR(50) NOT NULL,
  reference_id VARCHAR(255) NOT NULL,
  subjects JSONB NOT NULL,                 -- subjects screened
  result VARCHAR(20) NOT NULL CHECK (result IN ('NO_MATCH', 'POTENTIAL_MATCH', 'CONFIRMED_MATCH')),
  lists_checked JSONB NOT NULL,
  list_versions JSONB NOT NULL,
  duration_ms INT NOT NULL,
  screened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  screened_at_bs VARCHAR(30) NOT NULL
);

CREATE INDEX idx_screening_ref ON screening_results(reference_type, reference_id);
CREATE INDEX idx_screening_result ON screening_results(result, screened_at);
CREATE INDEX idx_screening_tenant ON screening_results(tenant_id, screened_at);

ALTER TABLE screening_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY screening_tenant ON screening_results
  USING (tenant_id = current_setting('app.current_tenant')::UUID);
```

### 3.4 Match Reviews

```sql
CREATE TABLE match_reviews (
  match_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  screening_id UUID NOT NULL REFERENCES screening_results(screening_id),
  tenant_id UUID NOT NULL,
  subject_name VARCHAR(500) NOT NULL,
  listed_name VARCHAR(500) NOT NULL,
  match_score DECIMAL(5, 4) NOT NULL,
  match_algorithms JSONB NOT NULL,
  list_name VARCHAR(100) NOT NULL,
  list_entry_id VARCHAR(100) NOT NULL,
  status VARCHAR(30) NOT NULL CHECK (status IN (
    'PENDING_REVIEW', 'PENDING_APPROVAL', 'CLEARED', 'CONFIRMED_MATCH', 'ESCALATED'
  )),
  decision VARCHAR(30),                    -- FALSE_POSITIVE, TRUE_MATCH
  rationale TEXT,
  evidence JSONB NOT NULL DEFAULT '[]',
  reviewer VARCHAR(100),
  reviewer_at TIMESTAMPTZ,
  reviewer_at_bs VARCHAR(30),
  checker VARCHAR(100),
  checker_comment TEXT,
  checked_at TIMESTAMPTZ,
  checked_at_bs VARCHAR(30),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL
);

CREATE INDEX idx_match_status ON match_reviews(status, created_at);
CREATE INDEX idx_match_screening ON match_reviews(screening_id);
CREATE INDEX idx_match_tenant ON match_reviews(tenant_id, status);

ALTER TABLE match_reviews ENABLE ROW LEVEL SECURITY;
CREATE POLICY match_tenant ON match_reviews
  USING (tenant_id = current_setting('app.current_tenant')::UUID);
```

---

## 4. CONTROL FLOW

### 4.1 Real-Time Screening at Order Placement

```
D-01 OMS receives order
  → OMS calls SanctionsClient.screen(orderSubjects)
    → [1] Pre-filter: Check local whitelist cache (known cleared entities)
      → IF whitelisted recently (within 24h): RETURN NO_MATCH (fast path, ~1ms)
    → [2] Fuzzy match against in-memory name index
      → For each subject:
        → Token decomposition: "Ram Bahadur Thapa" → ["ram", "bahadur", "thapa"]
        → Phonetic encoding: Soundex("thapa") → "T100", DMP("thapa") → "TP/TP"
        → Query in-memory trie with edit-distance ≤ 2
        → Score candidates using Jaro-Winkler + Levenshtein composite
        → Filter by threshold (default 0.85)
    → [3] IF matches found:
        → Store screening_result(POTENTIAL_MATCH)
        → Create match_reviews(PENDING_REVIEW)
        → Return HOLD_FOR_REVIEW to OMS
        → OMS places order on COMPLIANCE_HOLD
    → [4] IF no matches:
        → Store screening_result(NO_MATCH)
        → Return NO_MATCH to OMS
        → OMS proceeds with order
  → Total latency: < 50ms P99
```

### 4.2 Match Review Workflow

```
Compliance Officer reviews match:
  → GET /api/v1/sanctions/matches?status=PENDING_REVIEW
  → Examines match details, subject vs listed entity
  → POST /api/v1/sanctions/matches/{match_id}/review
    → decision: FALSE_POSITIVE or TRUE_MATCH
    → Attach evidence
    → UPDATE match_reviews SET status='PENDING_APPROVAL'
    → Emit K-07 audit: SANCTIONS_MATCH_REVIEWED

Senior Compliance Officer approves:
  → POST /api/v1/sanctions/matches/{match_id}/approve
    → Validate: checker ≠ reviewer
    → IF approved AND FALSE_POSITIVE:
        → UPDATE match_reviews SET status='CLEARED'
        → Release held order via D-01 OMS
        → Add entity to whitelist cache (24h TTL)
    → IF approved AND TRUE_MATCH:
        → UPDATE match_reviews SET status='CONFIRMED_MATCH'
        → D-01 OMS cancels order
        → D-07 Compliance: flag client for enhanced monitoring
        → R-02 Regulatory Reporting: file STR (Suspicious Transaction Report)
    → Emit K-07 audit: SANCTIONS_MATCH_RESOLVED
```

### 4.3 Sanction List Update Flow

```
List Update (scheduled or manual):
  → [1] Fetch latest list from source URL (or import signed bundle)
  → [2] Verify signature (Ed25519):
      → IF invalid: REJECT, alert P1
      → IF valid: proceed
  → [3] Diff against current entries:
      → Identify ADDED, REMOVED, MODIFIED entries
  → [4] Update sanction_list_entries table
  → [5] Rebuild in-memory index:
      → Trie reconstruction with new entries
      → Phonetic code recomputation for new/modified entries
  → [6] Update list version
  → [7] Trigger batch re-screen of all active clients (if new entries added)
  → [8] Emit K-05 event: siddhanta.sanctions.list.updated
  → Total: < 15 minutes from receipt to active screening
```

### 4.4 Air-Gap Bundle Flow

```
For disconnected / air-gapped deployments:
  → [1] Central server generates signed bundle:
      → Bundle = { list_data, version, timestamp, sha256_hash }
      → Signature = Ed25519.sign(bundle_hash, private_key)
  → [2] Bundle transferred via secure physical media (USB)
  → [3] Air-gap node imports bundle:
      → POST /api/v1/sanctions/lists/import with signature
      → Verify Ed25519 signature against trusted public key
      → IF valid: apply list update
      → IF invalid: reject, alert
  → [4] Audit: Record physical import event with media serial number
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Composite Fuzzy Matching

```python
import jellyfish
from typing import NamedTuple

class MatchResult(NamedTuple):
    score: float
    algorithms: dict

def composite_match(subject_name: str, listed_name: str) -> MatchResult:
    """Multi-algorithm fuzzy name matching."""
    s = subject_name.lower().strip()
    l = listed_name.lower().strip()
    
    # Levenshtein normalized similarity
    lev = 1.0 - (jellyfish.levenshtein_distance(s, l) / max(len(s), len(l)))
    
    # Jaro-Winkler similarity
    jw = jellyfish.jaro_winkler_similarity(s, l)
    
    # Soundex match
    soundex_match = jellyfish.soundex(s) == jellyfish.soundex(l)
    
    # Double Metaphone match
    dmp_s = jellyfish.metaphone(s)
    dmp_l = jellyfish.metaphone(l)
    dmp_match = dmp_s == dmp_l
    
    # Composite score: weighted average + phonetic bonus
    composite = (lev * 0.4) + (jw * 0.4) + (0.1 if soundex_match else 0) + (0.1 if dmp_match else 0)
    
    return MatchResult(
        score=round(composite, 4),
        algorithms={
            'levenshtein': round(lev, 4),
            'jaro_winkler': round(jw, 4),
            'soundex': soundex_match,
            'double_metaphone': dmp_match
        }
    )
```

### 5.2 Token-Based Name Decomposition

```python
def tokenize_name(name: str) -> list[str]:
    """Decompose name into normalized tokens for multi-part matching."""
    # Remove honorifics, titles
    stopwords = {'mr', 'mrs', 'ms', 'dr', 'prof', 'sri', 'shri', 'shrimati'}
    tokens = name.lower().split()
    tokens = [t for t in tokens if t not in stopwords]
    
    # Generate permutations for different name orderings
    # South Asian names may have patronymics in different positions
    return tokens

def token_match(subject_tokens: list[str], listed_tokens: list[str], threshold: float) -> float:
    """Match individual name tokens with best-pair alignment."""
    if not subject_tokens or not listed_tokens:
        return 0.0
    
    scores = []
    for s_tok in subject_tokens:
        best = max(
            composite_match(s_tok, l_tok).score 
            for l_tok in listed_tokens
        )
        scores.append(best)
    
    return sum(scores) / len(scores)
```

### 5.3 In-Memory Trie with Edit Distance

```python
class SanctionsTrie:
    """BK-tree for efficient fuzzy search with edit distance."""
    
    def __init__(self):
        self.entries = {}       # phonetic_key → [SanctionEntry]
        self.bk_tree = None     # BK-tree for Levenshtein
    
    def build(self, entries: list[SanctionEntry]):
        """Build search index from sanction list entries."""
        for entry in entries:
            # Index by phonetic code
            for code in entry.phonetic_codes:
                self.entries.setdefault(code, []).append(entry)
            # Add to BK-tree
            self.bk_tree.insert(entry.primary_name.lower())
    
    def search(self, name: str, max_distance: int = 2) -> list[SanctionEntry]:
        """Find candidates within edit distance."""
        candidates = set()
        
        # Phonetic lookup (O(1))
        codes = compute_phonetic_codes(name)
        for code in codes:
            candidates.update(self.entries.get(code, []))
        
        # BK-tree lookup (O(log n))
        bk_results = self.bk_tree.search(name.lower(), max_distance)
        candidates.update(bk_results)
        
        return list(candidates)
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| Real-time screen (single name) | 5ms | 20ms | 50ms | 200ms |
| Real-time screen (5 names) | 15ms | 40ms | 80ms | 300ms |
| Whitelist cache hit | 0.1ms | 0.5ms | 1ms | 2ms |
| Match review submission | 10ms | 30ms | 50ms | 100ms |
| List import (10K entries) | 5s | 15s | 30s | 60s |
| Index rebuild | 2s | 5s | 10s | 30s |
| Batch re-screen (125K clients) | 15min | 20min | 25min | 45min |

### 6.2 Throughput & Scale

| Metric | Target |
|--------|--------|
| Screening throughput | 10K screenings/sec |
| Max sanction list entries (all lists) | 5M entries |
| In-memory index size | < 2GB |
| Concurrent screenings | 1K |
| Batch re-screen throughput | 5K clients/sec |

### 6.3 Accuracy Targets

| Metric | Target |
|--------|--------|
| False negative rate | < 0.01% |
| False positive rate | < 5% (tunable via threshold) |
| Match precision | > 95% at default threshold |

---

## 7. SECURITY DESIGN

### 7.1 Access Control

| Operation | Required Permission |
|-----------|-------------------|
| Trigger screening | `sanctions:screen:execute` |
| View screening results | `sanctions:screen:view` |
| View matches | `sanctions:match:view` |
| Review matches (maker) | `sanctions:match:review` |
| Approve matches (checker) | `sanctions:match:approve` |
| Import sanction lists | `sanctions:list:import` (admin) |
| Batch re-screen | `sanctions:batch:execute` (admin) |

### 7.2 Data Protection

- Sanction list data is Confidential — internal use only
- Match review evidence may contain PII — encrypted at rest
- Subject screening data encrypted at rest and in transit
- Air-gap bundle signature verification is mandatory

### 7.3 Tenant Isolation

- RLS on screening_results and match_reviews
- Each tenant's screening threshold may differ (via K-02 config)
- Sanction lists are shared across tenants (global regulatory data)

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_sanctions_screen_total{tenant, type, result}              counter
siddhanta_sanctions_screen_duration_ms{type}                        histogram
siddhanta_sanctions_match_total{list_name}                          counter
siddhanta_sanctions_match_pending{tenant}                           gauge
siddhanta_sanctions_match_resolved_total{decision}                  counter
siddhanta_sanctions_list_entries{list_name}                         gauge
siddhanta_sanctions_list_version_age_hours{list_name}               gauge
siddhanta_sanctions_batch_progress{batch_id}                        gauge
siddhanta_sanctions_whitelist_hit_total                             counter
siddhanta_sanctions_index_rebuild_duration_seconds                  histogram
```

### 8.2 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Screening latency > 50ms P99 | P99 > 50ms over 5 min | P1 |
| Screening service down | Health check fails | P0 |
| List stale > 24h | Any list version > 24h old | P1 |
| Unreviewed matches > 4h | Any match PENDING_REVIEW > 4h | P2 |
| True match detected | Any CONFIRMED_MATCH | P1 |
| Air-gap signature failure | Signature verification failed | P0 |
| Batch re-screen failure | Batch job fails | P1 |
| False negative detected | Post-facto detection of missed match | P0 |

### 8.3 K-07 Audit Events

| Event | Trigger |
|-------|---------|
| SANCTIONS_SCREEN_EXECUTED | Any screening performed |
| SANCTIONS_MATCH_DETECTED | Potential match found |
| SANCTIONS_MATCH_REVIEWED | Match reviewed by compliance |
| SANCTIONS_MATCH_CLEARED | Match cleared as false positive |
| SANCTIONS_MATCH_CONFIRMED | True match confirmed |
| SANCTIONS_LIST_UPDATED | Sanction list imported/updated |
| SANCTIONS_LIST_SIGNATURE_VERIFIED | Air-gap bundle verified |
| SANCTIONS_LIST_SIGNATURE_FAILED | Air-gap bundle rejected |
| SANCTIONS_BATCH_RESCREEN_STARTED | Batch re-screen initiated |
| SANCTIONS_BATCH_RESCREEN_COMPLETED | Batch re-screen finished |
| SANCTIONS_ORDER_HELD | Order held due to match |
| SANCTIONS_ORDER_RELEASED | Order released after clearing |
| SANCTIONS_ORDER_CANCELLED | Order cancelled due to true match |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Sanction Lists (T1)

```json
{
  "content_pack_type": "T1",
  "jurisdiction": "NP",
  "name": "nepal-local-sanctions",
  "sanction_lists": [
    {
      "list_name": "NRB_LOCAL",
      "source_url": "https://nrb.org.np/sanctions/latest.csv",
      "format": "CSV",
      "update_frequency_hours": 24,
      "signature_public_key": "ed25519_public_key_base64"
    }
  ]
}
```

### 9.2 Custom Matching Algorithms (T3)

```typescript
interface MatchingAlgorithmPlugin {
  name: string;
  weight: number;
  match(subject: string, listed: string): number;  // 0.0 - 1.0
}

// Example: Devanagari script-aware matching for Nepal
class DevanagariMatcher implements MatchingAlgorithmPlugin {
  name = 'devanagari';
  weight = 0.2;
  match(subject: string, listed: string): number {
    // Transliterate to Latin, then compare
    // Handle Nepali naming conventions
  }
}
```

### 9.3 Custom Screening Rules (T2)

```rego
# OPA/Rego rules for jurisdiction-specific screening logic
package sanctions.screening

# Skip screening for intra-company transfers
exempt_screening {
    input.order.type == "INTERNAL_TRANSFER"
    input.order.counterparty.type == "SAME_ENTITY"
}

# Enhanced screening for high-value orders
enhanced_screening {
    input.order.value >= 10000000  # 1 Crore NPR
}
```

### 9.4 Future: Digital Asset Address Screening

- Screen blockchain wallet addresses against CHAINALYSIS / TRM Labs
- Cross-reference DeFi protocol addresses
- Real-time on-chain transaction monitoring integration

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-SAN-001 | Levenshtein fuzzy match | Correct similarity score |
| UT-SAN-002 | Jaro-Winkler fuzzy match | Correct similarity score |
| UT-SAN-003 | Composite match scoring | Weighted score correct |
| UT-SAN-004 | Token decomposition | Stopwords removed, tokens correct |
| UT-SAN-005 | Phonetic encoding | Soundex and Double Metaphone correct |
| UT-SAN-006 | Threshold filtering | Only matches above threshold returned |
| UT-SAN-007 | Whitelist cache hit | Cached entity returns NO_MATCH fast |
| UT-SAN-008 | Maker-checker validation | Same user blocked from both roles |
| UT-SAN-009 | Air-gap signature verification | Valid/invalid signatures handled |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-SAN-001 | Real-time screen → D-01 OMS hold | Matched order placed on COMPLIANCE_HOLD |
| IT-SAN-002 | Match review → order release | Cleared order released from hold |
| IT-SAN-003 | True match → order cancel + STR | Order cancelled, R-02 STR filed |
| IT-SAN-004 | List import → index rebuild | New entries discoverable after import |
| IT-SAN-005 | Batch re-screen | All active clients screened, matches created |
| IT-SAN-006 | K-07 audit trail completeness | All screening events audited |
| IT-SAN-007 | Tenant isolation | Tenant A cannot see Tenant B's matches |

### 10.3 Accuracy Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| AT-SAN-001 | Known-true-match test set (100 entries) | 100% detection rate |
| AT-SAN-002 | Known-false-positive test set (1000 entries) | FP rate < 5% |
| AT-SAN-003 | Transliteration variants | "Thapa" / "Thapaa" / "THAPA" all match |
| AT-SAN-004 | Name ordering variants | "Ram B Thapa" matches "Thapa, Ram Bahadur" |
| AT-SAN-005 | Missing middle name | "Ram Thapa" matches "Ram Bahadur Thapa" above threshold |

### 10.4 Performance Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| PT-SAN-001 | Screening latency (5M entries) | P99 < 50ms |
| PT-SAN-002 | Throughput (10K screenings/s) | Sustained for 5 minutes |
| PT-SAN-003 | Index rebuild (5M entries) | < 30 seconds |
| PT-SAN-004 | Batch re-screen (125K clients) | < 45 minutes |

### 10.5 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-SAN-001 | Screening service crash during order | Order held, alert fired, no bypass |
| CT-SAN-002 | List source unavailable | Stale list used, alert fired |
| CT-SAN-003 | Index corruption | Auto-rebuild from DB, alert fired |
| CT-SAN-004 | Air-gap bundle tampering | Signature fails, import rejected |

---

**END OF D-14 SANCTIONS SCREENING LLD**
