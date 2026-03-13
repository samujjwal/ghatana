# Plugin Sandbox Specification

**Document Type**: Normative Reference  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.0.0 | **Status**: Active | **Date**: 2026-01-19  
**Owner**: AppPlatform Security Council  
**Canonical Path**: `products/app-platform/docs/PLUGIN_SANDBOX_SPECIFICATION.md`

---

## Purpose

This document specifies the sandbox boundaries, resource limits, allowed capabilities, violation handling, and security review process for each Domain Pack type (T1, T2, T3) executed by the AppPlatform Kernel's K-04 Plugin Runtime. All domain packs **MUST** comply before receiving a signed certification from the Pack Certification service (K-04 + EPIC-P-01).

---

## Table of Contents

1. [Pack Type Overview](#1-pack-type-overview)
2. [T1 Config Pack Sandbox](#2-t1-config-pack-sandbox)
3. [T2 Rule Pack Sandbox](#3-t2-rule-pack-sandbox)
4. [T3 Executable Pack Sandbox](#4-t3-executable-pack-sandbox)
5. [Sandbox Violation Handling](#5-sandbox-violation-handling)
6. [Security Review & Certification Process](#6-security-review--certification-process)
7. [Sandbox Capability Summary](#7-sandbox-capability-summary)

---

## 1. Pack Type Overview

| Type | Name            | Execution Model          | Source of Truth                     |
| ---- | --------------- | ------------------------ | ----------------------------------- |
| T1   | Config Pack     | Pure data evaluation     | `EPIC-K-02-Configuration-Engine.md` |
| T2   | Rule Pack       | OPA/Rego DSL interpreter | `EPIC-K-03-Rules-Engine.md`         |
| T3   | Executable Pack | JVM process / Java 21    | `EPIC-K-04-Plugin-Runtime.md`       |

The sandbox tier escalates from T1 (most restrictive, stateless) to T3 (most permissive, but still sandboxed). Each tier's limits are **non-negotiable**; no pack version may declare an exemption.

---

## 2. T1 Config Pack Sandbox

### 2.1 Execution Model

T1 packs are **declarative data structures** (JSON/YAML). They are loaded and evaluated by the K-02 Config Engine using a pure-read resolver. No user-provided code is executed.

### 2.2 Allowed Artifacts

| Artifact               | Allowed | Notes                                         |
| ---------------------- | ------- | --------------------------------------------- |
| JSON schema files      | ✅      | Must conform to JSON Schema Draft-07 or later |
| YAML config files      | ✅      | Deserialized by K-02 with strict schema check |
| Rego override snippets | ❌      | Must be in a T2 pack                          |
| JAR / compiled code    | ❌      | Reject immediately; auto-quarantine the pack  |
| Shell scripts          | ❌      | Reject immediately                            |

### 2.3 Evaluation Constraints

- **Schema validation**: K-02 validates every T1 pack against the declared `config.schema.json` before activation.
- **Circular reference detection**: K-02 resolves `$ref` chains and rejects circular references.
- **No runtime execution**: Config values are resolved through static interpolation only. Template expressions are restricted to the K-02 expression grammar (comparable to CEL — no arbitrary code).
- **Max file size**: 5 MB per pack `.pgk` bundle.
- **Field encoding**: All string values are stripped of control characters and validated for UTF-8.

### 2.4 Secrets in T1 Packs

T1 packs **MUST NOT** embed secrets (API keys, passwords, certificates). Use a `${vault:path}` reference token that K-14 resolves at activation time. Packs containing literals matching `PASSWORD|SECRET|API_KEY|TOKEN` patterns (case-insensitive) are rejected by the static scan.

---

## 3. T2 Rule Pack Sandbox

### 3.1 Execution Model

T2 packs contain [OPA Rego](https://www.openpolicyagent.org/docs/latest/policy-language/) policies evaluated by the K-03 Rules Engine. The OPA runtime is embedded in the kernel process but operates with a restricted capability set.

### 3.2 Memory and Time Limits

| Limit                | Value          | Enforcement                                  |
| -------------------- | -------------- | -------------------------------------------- |
| Max memory per eval  | 256 MB         | OPA `–max-bytes` arg; eval aborted on breach |
| Max eval duration    | 5 seconds      | OPA `–timeout=5s`; partial result rejected   |
| Max policy file size | 2 MB           | Rejected at import time                      |
| Max data bundle size | 50 MB          | Rejected at import time                      |
| Max rule depth       | 20 (recursion) | Compile-time check                           |

### 3.3 Allowed OPA Built-ins

T2 packs may use the following OPA built-in categories:

| Category                         | Built-ins Allowed                                                                                        |
| -------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Comparison                       | All (`==`, `!=`, `<`, `>`, `<=`, `>=`)                                                                   |
| Arithmetic                       | `plus`, `minus`, `mul`, `div`, `rem`, `abs`, `round`                                                     |
| Strings                          | `concat`, `contains`, `startswith`, `endswith`, `lower`, `upper`, `trim`, `split`, `format_int`, `count` |
| Aggregates                       | `sum`, `min`, `max`, `sort`, `all`, `any`                                                                |
| Sets / Arrays / Objects          | All set/array/object built-ins                                                                           |
| Encoding                         | `base64.encode`, `base64.decode`, `json.marshal`, `json.unmarshal`, `yaml.marshal`, `yaml.unmarshal`     |
| Cryptography (verification only) | `crypto.x509.parse_certificate`, `crypto.md5`, `crypto.sha256`                                           |
| Time                             | `time.now_ns`, `time.parse_rfc3339_ns`, `time.add_date`, `time.weekday`                                  |
| Regex                            | `regex.match`, `regex.find_all_string_submatch_n`                                                        |

### 3.4 Blocked OPA Built-ins

The following built-ins are **blocked** and will cause pack certification to fail:

| Built-in                                  | Reason Blocked                                                |
| ----------------------------------------- | ------------------------------------------------------------- |
| `http.send`                               | Outbound network access — violates air-gap & isolation        |
| `io.jwt.decode_verify` with external JWKs | Would perform network fetch                                   |
| `opa.runtime`                             | Reveals runtime internals                                     |
| `net.cidr_*`                              | Not blocked, but flagged for security review on custom use    |
| `trace`                                   | Allowed only in development packs; blocked in certified packs |

### 3.5 Filesystem Access

T2 policies may only read from the **data bundle** packaged within the `.pgk` file. They have no access to:

- Kernel process filesystem
- Environment variables
- Other packs' data bundles
- Any network resource

### 3.6 Static Analysis Requirements

Before certification, every T2 pack runs:

```bash
opa check --strict --capabilities capabilities.json rules/
opa test rules/ data/ --timeout 5s
```

Where `capabilities.json` is the platform-maintained allow-list of built-ins from §3.3.

---

## 4. T3 Executable Pack Sandbox

### 4.1 Execution Model

T3 packs are signed JARs executed in a dedicated JVM process managed by K-04. The JVM is launched with a restricted security policy and resource cgroups.

### 4.2 Resource Quotas

| Resource         | Limit                                  | Enforcement Mechanism                               |
| ---------------- | -------------------------------------- | --------------------------------------------------- |
| CPU              | 1 vCPU share (CFS quota)               | cgroup `cpu.cfs_quota_us`                           |
| Heap memory      | 512 MB                                 | JVM `-Xmx512m`                                      |
| Metaspace        | 64 MB                                  | JVM `-XX:MaxMetaspaceSize=64m`                      |
| Thread count     | 50 threads                             | ThreadGroup with max enforced                       |
| File descriptors | 256                                    | `ulimit -n 256` at JVM launch                       |
| Process lifetime | Until K-04 terminates it; no self-fork | `java.lang.SecurityManager` blocks `Runtime.exec()` |
| Disk (scratch)   | 100 MB, `/tmp/packs/{pack_id}/`        | Tmpfs mount; wiped after shutdown                   |

### 4.3 Network Access

T3 packs must declare all required outbound hosts in their `DomainManifest`:

```yaml
network:
  outbound:
    allowList:
      - host: "api.central-depository.np"
        port: 443
        protocol: HTTPS
      - host: "nrb.gov.np"
        port: 443
        protocol: HTTPS
```

K-04 enforces the allow-list via a kernel-managed egress proxy. Any connection attempt to an undeclared host is:

1. Blocked by the proxy.
2. Logged as a `NetworkViolationEvent` in K-07 Audit.
3. Counted toward the violation threshold (see §5.2).

Inbound connections to a T3 pack are **not permitted**. All activation uses kernel-mediated RPC via K-05 event delivery.

### 4.4 Filesystem Access

| Path                              | Access                                            |
| --------------------------------- | ------------------------------------------------- |
| `/opt/packs/{pack_id}/resources/` | Read-only mount                                   |
| `/tmp/packs/{pack_id}/`           | Read-write (scratch) — wiped on shutdown          |
| All other paths                   | No access (SecurityManager `FilePermission` deny) |

### 4.5 Java Security Manager Policy

```java
// Enforced policy grants — all other permissions are denied
grant {
  permission java.lang.RuntimePermission "getClassLoader";
  permission java.net.SocketPermission "declared-hosts:443", "connect,resolve";
  permission java.io.FilePermission "/opt/packs/${pack.id}/resources/-", "read";
  permission java.io.FilePermission "/tmp/packs/${pack.id}/-", "read,write,delete";
  permission java.lang.RuntimePermission "accessDeclaredMembers";
};
```

> **Note**: Java SecurityManager is deprecated in Java 17+ but remains enforced via K-04's custom agent (`platform-security-agent.jar`), which intercepts relevant reflective and native calls.

### 4.6 Prohibited Operations

T3 packs may not:

| Operation                           | Mechanism Blocked                                          |
| ----------------------------------- | ---------------------------------------------------------- |
| Fork/exec subprocesses              | `SecurityManager` + K-04 agent                             |
| Reflective access to kernel classes | K-04 agent + module system `--add-opens` not granted       |
| JNI / native library load           | `SecurityManager RuntimePermission "loadLibrary.*"` denied |
| Class loading outside own JAR       | Custom `ClassLoader` policy                                |
| Thread priority manipulation        | `ThreadGroup` override                                     |
| `System.exit()`                     | K-04 agent replaces with `PackTerminationException`        |
| Shutdown hooks                      | `SecurityManager RuntimePermission "shutdownHooks"` denied |

### 4.7 T3 AI Model Execution (Certified AI Packs)

T3 packs that bundle machine learning models (e.g., custom domain scoring models, specialized embedding models registered via EP-K09-006) must comply with the following additional constraints, verified during pack certification (K-04 + P-01).

#### 4.7.1 Allowed Model Formats

| Format                    | Allowed | Notes                                                                                  |
| ------------------------- | ------- | -------------------------------------------------------------------------------------- |
| ONNX Runtime              | ✅ Yes  | Only supported inference format for certified T3 AI packs                              |
| PyTorch TorchScript (.pt) | ❌ No   | Requires Python runtime — not available in T3 JVM sandbox                              |
| TensorFlow SavedModel     | ❌ No   | Not available in T3 JVM sandbox                                                        |
| Raw Python / `*.py` files | ❌ No   | Python interpreter not permitted in T3 sandbox                                         |
| External cloud API calls  | ❌ No   | All inference through K-09 router; T3 AI packs must not call OpenAI/Anthropic directly |

> **Rationale**: ONNX Runtime has a certified Java binding (`onnxruntime` v1.17+) that runs within the existing T3 JVM environment with no additional interpreter. All external LLM / large model inference is routed through the K-09 Inference Router (EP-K09-006 serving adapter) — T3 packs submit inference requests to K-09 via the K-12 SDK, not directly to external endpoints.

#### 4.7.2 Additional Resource Quotas for AI Packs

| Resource                     | Standard T3 Limit | AI Pack Limit  | Enforcement Mechanism                                       |
| ---------------------------- | ----------------- | -------------- | ----------------------------------------------------------- |
| Heap memory                  | 512 MB            | 2 GB           | JVM `-Xmx2g` (enabled by `ai-pack: true` flag)              |
| Model file size (bundled)    | N/A               | 2 GB per pack  | Static scan at certification upload time                    |
| Inference timeout (per call) | N/A               | 30 seconds     | K-12 SDK `InferenceClient` deadline enforced                |
| GPU quota                    | None              | 0.5 GPU shares | cgroup `gpu.max` via NVIDIA MIG slice (if GPU-enabled node) |
| Scratch disk (model cache)   | 100 MB            | 10 GB          | Tmpfs mount; wiped after shutdown                           |

> **GPU availability**: GPU quota is only granted if the pack's `DomainManifest` declares `resources.gpu: required` AND the deployment topology has GPU-enabled nodes (SaaS, Dedicated, or Hybrid). On-Prem and Air-Gap deployments with CPU-only nodes receive no GPU quota; the pack must function with CPU-only ONNX inference.

#### 4.7.3 Model Storage and Secret Handling

- Model weights bundled directly in the T3 pack JAR are limited to 2 GB (static scan enforced).
- Model weights exceeding 2 GB **MUST** be stored as K-14 Secrets Vault artifacts (type: `BINARY_BLOB`) and referenced in `DomainManifest` as `model.weights.vaultRef: "vault://models/{pack_id}/{model_id}"`. K-04 mounts them read-only at activation.
- Model weights **MUST NOT** be loaded from the internet at runtime. Outbound fetches to model hosting services (Hugging Face, AWS S3 model buckets) are not permitted during inference. All weights must be present at activation time.
- API keys for external LLM services (OpenAI, Anthropic, etc.) **MUST NOT** be embedded in the pack. All external LLM calls flow through the K-09 Inference Router, which manages credential injection via K-14.

#### 4.7.4 Inference Isolation Requirements

- Each inference call **MUST** be stateless: the ONNX session must not retain state between calls (no persistent hidden states that cross tenant boundaries).
- Multi-tenant packs performing inference **MUST** ensure that model inputs and outputs for Tenant A are never accessible from Tenant B's call context. The K-12 SDK `InferenceClient` enforces tenant context injection automatically.
- Inference results (model outputs) are considered sensitive data subject to K-08 Data Governance residency rules. They must not be written to unscoped scratch directories.

#### 4.7.5 AI Pack Certification Requirements

In addition to the standard P-01 certification pipeline, AI packs must pass:

1. **Model bias scan**: K-09 runs the bundled model against the platform's standard bias evaluation dataset (demographic parity, equalized odds). Packs failing bias thresholds require AI Ethics Review Board approval before certification.
2. **Adversarial input test**: Static adversarial inputs crafted to cause model misbehavior (e.g., prompt injection attempts for LLM-backed packs, adversarial examples for classifiers). Pack fails certification if adversarial inputs cause security-relevant misclassifications.
3. **Inference resource profiling**: Certification pipeline runs 1,000 inference calls and records P50/P95/P99 latency and peak memory. Declared resource quotas must match observed usage within a 20% margin.
4. **Data lineage declaration**: Pack must declare in `ModelCard` (EP-K09-001) which training datasets were used, with K-08 dataset IDs traceable back to data governance records.

---

## 5. Sandbox Violation Handling

### 5.1 Violation Severity Levels

| Level    | Examples                                            | Immediate Action                               |
| -------- | --------------------------------------------------- | ---------------------------------------------- |
| WARNING  | Evaluation approaching time limit (> 4s of 5s)      | Log `PackWarningEvent`; notify pack owner      |
| MINOR    | Disk scratch quota at 80%; thread count at 40       | Log `PackResourceWarningEvent`                 |
| MAJOR    | Network attempt to undeclared host; blocked syscall | Terminate evaluation; log `PackViolationEvent` |
| CRITICAL | Memory burst > quota; prohibited native call        | Terminate JVM; quarantine pack; alert on-call  |

### 5.2 Automatic Revocation

A pack is automatically revoked (deactivated and flagged) when it accumulates:

- ≥ 3 MAJOR violations within any 24-hour window, **or**
- ≥ 1 CRITICAL violation at any time

Revocation publishes a `PackRevokedEvent` to K-05. All tenants using the pack receive a `PackRevokedNotification`. Pack owner must submit a patched version through the full certification pipeline before reinstatement.

### 5.3 Quarantine State

A quarantined pack is:

1. Immediately unloaded from the K-04 runtime.
2. Moved to `QUARANTINED` state in the Pack Registry.
3. Blocked from new tenant activations.
4. Preserved in read-only storage for forensic analysis.

Quarantine is reviewed by the AppPlatform Security Council within 5 business days. Resolved packs may be restored to `PENDING_REVIEW` state for re-certification.

---

## 6. Security Review & Certification Process

### 6.1 Overview

All packs must pass an automated gate before receiving the platform's Ed25519 certification signature. Packs without a valid signature are rejected at install time by K-04.

### 6.2 Automated Gate (Pre-Certification CI)

```
Pack Submission
     │
     ▼
┌──────────────────────────────────────────────────────────────────┐
│  Stage 1: Structural Validation                                  │
│  - DomainManifest schema check                                   │
│  - Declared extension points vs EXTENSION_POINT_CATALOG.md       │
│  - No embedded secrets (pattern scan)                            │
└───────────────────────────┬──────────────────────────────────────┘
                            │ PASS
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Stage 2: Type-Specific Security Analysis                        │
│  T1: JSON Schema lint + circular ref check                       │
│  T2: OPA capability check + `opa check --strict` + unit tests    │
│  T3: SAST (SpotBugs + PMD security ruleset) + dependency CVE    │
└───────────────────────────┬──────────────────────────────────────┘
                            │ PASS
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Stage 3: Sandbox Dry Run                                        │
│  T2: Run policy against synthetic adversarial inputs             │
│  T3: Launch in sandbox; probe blocked operations; measure        │
│      peak resource usage; assert within quotas                   │
└───────────────────────────┬──────────────────────────────────────┘
                            │ PASS
                            ▼
             Certification Signature Issued (Ed25519)
             Pack promoted to CERTIFIED state in Registry
```

### 6.3 Human Review Triggers

The following conditions require mandatory human review by the Security Council **before** a certification signature is issued:

| Condition                                                        | Reviewer                 |
| ---------------------------------------------------------------- | ------------------------ |
| T3 pack declares > 5 outbound network hosts                      | Security Council         |
| T3 pack uses JNI (even if allowed via special exemption request) | Security Council + Legal |
| T2 pack uses `crypto.*` or `net.*` built-ins                     | Security Reviewer        |
| Pack touches a STABLE extension point for the first time         | Kernel Interface Owner   |
| Pack declares `requiresPrivilegedTenantScope: true`              | Security Council         |

### 6.4 Dependency CVE Policy

T3 packs must submit a `dependency-report.json` (generated by `./gradlew dependencyCheckAnalyze`) with their pack bundle. Packs with any CVSS ≥ 7.0 unfixed vulnerability are rejected until patched.

---

## 7. Sandbox Capability Summary

| Capability                      | T1 Config            | T2 Rule            | T3 Executable                     |
| ------------------------------- | -------------------- | ------------------ | --------------------------------- |
| Execute code                    | ❌                   | OPA only           | ✅ (JVM)                          |
| Outbound network                | ❌                   | ❌                 | Allow-list only                   |
| Inbound network                 | ❌                   | ❌                 | ❌                                |
| Filesystem read (own resources) | ❌                   | ❌                 | ✅ (read-only)                    |
| Filesystem write (scratch)      | ❌                   | ❌                 | ✅ (100 MB)                       |
| Access K-05 Event Bus directly  | ❌                   | ❌                 | Via K-04 SDK only                 |
| Access K-14 Secrets             | Vault ref token only | ❌                 | Via K-04 SDK only                 |
| Fork subprocesses               | ❌                   | ❌                 | ❌                                |
| Load native libraries           | ❌                   | ❌                 | ❌                                |
| Reflection into kernel classes  | ❌                   | ❌                 | ❌                                |
| Max evaluation/execution time   | N/A                  | 5 s                | Unlimited (until K-04 terminates) |
| Max memory                      | N/A                  | 256 MB             | 512 MB heap                       |
| Auto-revocation threshold       | N/A                  | 3 MAJOR            | 3 MAJOR or 1 CRITICAL             |
| Certification requirement       | Schema + static scan | OPA lint + dry run | SAST + CVE + dry run              |
