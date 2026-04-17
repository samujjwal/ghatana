# Remaining Tasks Roadmap

**Date:** 2026-04-17
**Status:** Implementation Guidance Required

## Overview

This document outlines the remaining tasks from the PRODUCT_REVIEW_FINDINGS_2026-04-16.md that require additional implementation work or external coordination.

## Completed Tasks

The following tasks have been completed:

1. ✅ **Mobile app decision** - Documented in PRODUCT_CLARIFICATIONS.md (retract claim)
2. ✅ **Offline mode decision** - Documented in PRODUCT_CLARIFICATIONS.md (retract claim)
3. ✅ **Real-time collaboration verification** - Verified implementation (WebSockets + Redis pub/sub)
4. ✅ **SSL/TLS enforcement for DATABASE_URL** - Added validation in config.ts
5. ✅ **SSL/TLS enforcement for REDIS_URL** - Added validation in config.ts
6. ✅ **S3 server-side encryption** - Added SSE-KMS support with validation
7. ✅ **Field-level encryption for PII** - Implemented service with tests and documentation
8. ✅ **Backup encryption documentation** - Created comprehensive requirements document
9. ✅ **SLO + burn-rate alerts** - Implemented in monitoring module

## Remaining Tasks

### 1. Kernel Signing + Sandbox for Marketplace Trust

**Priority:** Medium
**Effort:** High (4-6 weeks)
**Type:** Implementation

**Current Status:** Not implemented

**Requirements:**
- Digital signature verification for kernel manifests
- Sandbox environment for kernel execution (Deno compartment or isolated VM)
- Kernel marketplace governance and review process
- Signed artifact validation before marketplace listing

**Implementation Approach:**

#### Phase 1: Kernel Signing Infrastructure
1. **Generate signing keys**
   ```bash
   # Generate Ed25519 key pair for signing
   openssl genpkey -algorithm Ed25519 -out private.pem
   openssl pkey -in private.pem -pubout -out public.pem
   ```

2. **Create signing service**
   - Location: `services/tutorputor-platform/src/core/crypto/signing-service.ts`
   - Use Ed25519 for digital signatures
   - Sign kernel manifests (JSON with metadata, code hash, dependencies)
   - Verify signatures on kernel upload/download

3. **Update kernel schema**
   - Add `signature` field to kernel manifest
   - Add `signerPublicKey` field for verification
   - Add `signedAt` timestamp

#### Phase 2: Sandbox Execution
1. **Choose sandbox technology**
   - Option A: Deno runtime (TypeScript/JavaScript sandbox)
   - Option B: Firecracker microVM (full isolation)
   - Option C: WebAssembly (browser-compatible sandbox)

2. **Implement sandbox executor**
   - Location: `services/tutorputor-sim-runtime/src/sandbox/executor.ts`
   - Isolate kernel execution from host system
   - Limit resource usage (CPU, memory, network)
   - Provide safe API surface for kernels

3. **Add sandbox validation**
   - Verify kernel code before execution
   - Check for unsafe operations
   - Enforce timeout limits

#### Phase 3: Marketplace Governance
1. **Implement review workflow**
   - Create kernel review queue
   - Add reviewer approval process
   - Track review history

2. **Add marketplace policies**
   - Code quality requirements
   - Security guidelines
   - Performance benchmarks

**Deliverables:**
- Kernel signing service with Ed25519
- Sandbox executor with Deno or Firecracker
- Updated kernel schema with signature fields
- Marketplace review workflow
- Documentation for kernel developers

**Dependencies:**
- Crypto library for Ed25519 (e.g., `@noble/ed25519`)
- Deno runtime or Firecracker integration
- Updated kernel registry schema

---

### 2. Field-Test LTI AGS Against Canvas + Moodle

**Priority:** Medium
**Effort:** Medium (2-3 weeks)
**Type:** External Testing / Integration

**Current Status:** Implementation exists but not field-tested against real LMS

**Requirements:**
- Test LTI 1.3 Advantage Grade Services (AGS) integration
- Verify grade passback to Canvas LMS
- Verify grade passback to Moodle LMS
- Document any platform-specific quirks or issues

**Implementation Approach:**

#### Phase 1: Test Environment Setup
1. **Canvas Test Setup**
   - Create Canvas developer account
   - Set up test course
   - Configure LTI 1.3 tool registration
   - Enable AGS for test course

2. **Moodle Test Setup**
   - Install Moodle instance (local or cloud)
   - Configure LTI 1.3 plugin
   - Set up test course
   - Enable AGS for test course

#### Phase 2: Integration Testing
1. **Create test suite**
   - Location: `services/tutorputor-platform/src/modules/integration/lti/__tests__/ags-field-test.ts`
   - Test grade passback flow
   - Test score line item creation
   - Test grade update scenarios
   - Test error handling

2. **Execute tests**
   - Run against Canvas test environment
   - Run against Moodle test environment
   - Document results
   - Identify platform-specific issues

#### Phase 3: Documentation
1. **Update LTI integration guide**
   - Add Canvas-specific configuration
   - Add Moodle-specific configuration
   - Document known issues and workarounds
   - Add troubleshooting guide

**Deliverables:**
- LTI AGS field test suite
- Test results for Canvas and Moodle
- Updated integration documentation
- Known issues and workarounds guide

**Dependencies:**
- Canvas developer account
- Moodle test instance
- Existing LTI 1.3 implementation

---

### 3. Complete Marketplace Payout/Tax/Dispute Depth (Stripe Connect)

**Priority:** Medium
**Effort:** High (6-8 weeks)
**Type:** Implementation

**Current Status:** Basic Stripe integration exists; full Connect features not implemented

**Requirements:**
- Stripe Connect onboarding for marketplace sellers
- Tax collection and reporting (Stripe Tax)
- Payout automation and scheduling
- Dispute resolution workflow
- Seller dashboard for managing payouts

**Implementation Approach:**

#### Phase 1: Stripe Connect Setup
1. **Configure Stripe Connect**
   - Set up Stripe account (platform account)
   - Enable Connect (Standard or Express accounts)
   - Configure webhook endpoints
   - Set up API keys

2. **Create onboarding flow**
   - Location: `services/tutorputor-platform/src/modules/marketplace/onboarding.ts`
   - Stripe Connect OAuth flow
   - Account collection (business info, banking details)
   - Verification process
   - Account status tracking

#### Phase 2: Tax Integration
1. **Enable Stripe Tax**
   - Configure tax calculation
   - Set up tax registration collection
   - Integrate tax reporting
   - Handle tax exemption certificates

2. **Tax documentation**
   - Location: `services/tutorputor-platform/src/modules/marketplace/tax-service.ts`
   - Generate tax invoices
   - Track tax collected vs. remitted
   - Provide tax reports to sellers

#### Phase 3: Payout Automation
1. **Implement payout scheduling**
   - Location: `services/tutorputor-platform/src/modules/marketplace/payout-service.ts`
   - Configure payout schedules (daily, weekly, monthly)
   - Calculate seller balances
   - Handle payout failures and retries
   - Track payout history

2. **Add payout controls**
   - Minimum payout thresholds
   - Maximum payout limits
   - Manual payout approval
   - Payout holds for disputes

#### Phase 4: Dispute Resolution
1. **Implement dispute workflow**
   - Location: `services/tutorputor-platform/src/modules/marketplace/dispute-service.ts`
   - Receive Stripe dispute webhooks
   - Notify sellers of disputes
   - Collect evidence from sellers
   - Submit evidence to Stripe
   - Track dispute outcomes

2. **Seller dispute dashboard**
   - View active disputes
   - Submit evidence
   - Track dispute status
   - View dispute history

**Deliverables:**
- Stripe Connect onboarding flow
- Tax collection and reporting service
- Payout automation service
- Dispute resolution workflow
- Seller dashboard for payouts and disputes
- Documentation for marketplace sellers

**Dependencies:**
- Stripe account with Connect enabled
- Stripe Tax integration
- Existing marketplace infrastructure
- Seller onboarding UI (frontend work)

---

## Summary

### Immediate Actions (Next Sprint)

1. **Kernel Signing + Sandbox** - Start with Phase 1 (signing infrastructure)
2. **LTI AGS Field Testing** - Set up test environments and create test suite

### Medium-Term (Next 2-3 Sprints)

1. **Kernel Sandbox Execution** - Complete Phase 2 of kernel signing task
2. **LTI AGS Documentation** - Complete field testing and documentation
3. **Stripe Connect Onboarding** - Start Phase 1 of marketplace payout task

### Long-Term (Next 3-6 Months)

1. **Marketplace Governance** - Complete Phase 3 of kernel signing
2. **Full Stripe Connect** - Complete all phases of marketplace payout task
3. **Seller Tools** - Build seller dashboard and management tools

## Risk Assessment

| Task | Risk | Mitigation |
|------|------|------------|
| Kernel Signing | Complex crypto implementation | Use well-tested libraries (e.g., @noble/ed25519) |
| Sandbox Execution | Performance overhead | Benchmark and optimize sandbox performance |
| LTI AGS Testing | External dependency on LMS providers | Use test environments, document platform quirks |
| Stripe Connect | Regulatory complexity | Consult Stripe documentation, legal review |

## Resource Requirements

| Task | Engineering | QA | DevOps | External |
|------|-------------|-----|--------|----------|
| Kernel Signing + Sandbox | 2 senior engineers | 1 QA engineer | 1 DevOps engineer | None |
| LTI AGS Testing | 1 engineer | 1 QA engineer | None | Canvas/Moodle access |
| Stripe Connect | 2 engineers | 1 QA engineer | 1 DevOps engineer | Stripe account setup |

## Success Criteria

### Kernel Signing + Sandbox
- [ ] All kernels in marketplace are signed
- [ ] Sandbox execution prevents system access
- [ ] Signature verification works on kernel upload
- [ ] Performance overhead < 100ms per kernel execution

### LTI AGS Testing
- [ ] Grade passback works with Canvas
- [ ] Grade passback works with Moodle
- [ ] Test suite covers all AGS scenarios
- [ ] Documentation includes platform-specific notes

### Stripe Connect
- [ ] Sellers can complete onboarding flow
- [ ] Tax is collected and reported correctly
- [ ] Payouts execute on schedule
- [ ] Disputes are tracked and resolved
- [ ] Seller dashboard provides visibility
