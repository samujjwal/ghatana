# DCMAAR Secure Connectors – Index & Navigation

**Quick Links**:

## Main Backlog

→ **[DCMAAR_BACKLOG.md](./DCMAAR_BACKLOG.md)** - Primary backlog with all work items

## Secure Connectors Work

→ **[SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md)** - **NEW** Comprehensive connector-based security implementation (12-week roadmap)

---

## How to Use These Backlogs

### I'm a Product Manager

1. Read: [DCMAAR_BACKLOG.md](./DCMAAR_BACKLOG.md) sections 1-7 for current priorities
2. Read: [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Executive Summary + Phases overview
3. Action: Schedule secure connectors work into roadmap (12 weeks, 5.5 FTE)

### I'm a Backend Engineer

1. Read: [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phase 2 (Backend Integration)
2. Reference: [SECURE_BROWSER_IMPLEMENTATION.md](../../../libs/typescript/connectors/SECURE_BROWSER_IMPLEMENTATION.md)
3. Start: Connector initialization framework (Phase 1.1)
4. Test: Use Testcontainers for all 10 connector types (Phase 4.2)

### I'm a Frontend Engineer

1. Read: [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phase 1 (Guardian Browser Setup)
2. Reference: [apps/guardian/SECURE_COMMUNICATION_SETUP.md](../../apps/guardian/SECURE_COMMUNICATION_SETUP.md)
3. Implement: Browser connector factory (Phase 1.2)
4. Test: Integration tests for 4 browser connectors (Phase 4.1)

### I'm a Security Engineer

1. Read: [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phases 3-6
2. Reference: [SECURITY_ARCHITECTURE_VISUAL.md](../../../libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md)
3. Lead: Security audit (Phase 6.1)
4. Own: OAuth2 and E2E encryption implementations (Phase 3.2 & 3.5)

### I'm DevOps/SRE

1. Read: [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phases 1, 5, 6
2. Action: Installation-time configuration (Phase 1.4)
3. Lead: Performance testing & load tests (Phase 4.4)
4. Own: Deployment & rollout (Phase 6.4)

### I'm Implementing a New Product on DCMAAR

1. Start: [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phase 1 sections
2. Use: Installation script from Phase 1.4
3. Setup: Guardian Browser or Device Health connectors as template (Phase 1.2 / 1.3)
4. Reference: [CONNECTOR_QUICKSTART.md](./CONNECTOR_QUICKSTART.md) (to be created in Phase 5)

---

## Relationship Between Backlogs

```
DCMAAR_BACKLOG.md (Main backlog)
    ├─ Section 1: Go Server Decommissioning
    ├─ Section 2: Guardian Backend Proto Adoption
    ├─ Section 3: Device Health Implementation
    ├─ Section 4: Desktop Configuration
    ├─ Section 5: Dependency Guardrails
    ├─ Section 6: Tests & Build Pipelines
    ├─ Section 7: Documentation
    └─ Section 8: Future Enhancements
         └─ **"Explore additional products built on DCMAAR"**
             └─ SECURE_CONNECTORS_BACKLOG.md (Detailed execution plan)
                 ├─ Phase 1: Foundation (Connector setup)
                 ├─ Phase 2: Backend Integration (Services migration)
                 ├─ Phase 3: Security Patterns (5 implementation paths)
                 ├─ Phase 4: Testing & Validation
                 ├─ Phase 5: Documentation & Runbooks
                 └─ Phase 6: Production Hardening
```

**Key Decision**: Secure connectors are the **foundation for all future DCMAAR products**. Complete this backlog before building new products.

---

## Security Documentation Hierarchy

### Level 1: Decision-Makers

→ [SECURITY_ARCHITECTURE_VISUAL.md](../../../libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md) - Diagrams and high-level patterns

### Level 2: Architects

→ [SECURE_COMMUNICATION_SUMMARY.md](../../../libs/typescript/connectors/SECURE_COMMUNICATION_SUMMARY.md) - Comprehensive overview with all 5 patterns

### Level 3: Implementers

→ [SECURE_BROWSER_IMPLEMENTATION.md](../../../libs/typescript/connectors/SECURE_BROWSER_IMPLEMENTATION.md) - Working code for each pattern
→ [apps/guardian/SECURE_COMMUNICATION_SETUP.md](../../apps/guardian/SECURE_COMMUNICATION_SETUP.md) - Production setup guide

### Level 4: Quick Reference

→ [MTLS_MQTTS_QUICK_REFERENCE.md](../../../libs/typescript/connectors/MTLS_MQTTS_QUICK_REFERENCE.md) - Why mTLS/MQTTS don't work in browsers

### Level 5: Troubleshooting

→ [SECURITY_DOCUMENTATION_INDEX.md](../../../libs/typescript/connectors/SECURITY_DOCUMENTATION_INDEX.md) - Navigation guide

---

## Phase Overview

### Phase 1: Foundation (Weeks 1-2)

```
Objective: Setup connectors across all products
├─ 1.1 Connector initialization framework (Java)
├─ 1.2 Guardian browser connectors (4 types)
├─ 1.3 Device Health connectors (4 types)
└─ 1.4 Installation-time configuration (scripts)

Success: All connectors initialize, browsers functional, <30min setup
Status: 🟡 In Progress (browser connectors done, Java framework needed)
```

### Phase 2: Backend Integration (Weeks 3-4)

```
Objective: Migrate services to use connectors
├─ 2.1 Guardian backend services
├─ 2.2 Device Health backend services
└─ 2.3 DCMAAR framework support

Success: All external calls via connectors, metrics working
Status: 🔴 Not Started
```

### Phase 3: Security Patterns (Weeks 5-6)

```
Objective: Implement 5 secure communication patterns
├─ 3.1 Server-Side TLS (automatic)
├─ 3.2 OAuth2/OIDC (authentication)
├─ 3.3 Proxy Pattern (mTLS for browser)
├─ 3.4 Certificate Pinning (CA compromise protection)
└─ 3.5 End-to-End Encryption (field-level)

Success: All patterns working, Guardian uses #2, browser uses #3
Status: 🔴 Not Started
```

### Phase 4: Testing (Weeks 7-8)

```
Objective: Comprehensive test coverage
├─ 4.1 Browser connector integration tests
├─ 4.2 Backend connector tests (Testcontainers)
├─ 4.3 End-to-end security tests
└─ 4.4 Performance & load testing

Success: >90% coverage, all error scenarios handled
Status: 🔴 Not Started
```

### Phase 5: Documentation (Weeks 9-10)

```
Objective: Runbooks, guides, ADRs
├─ 5.1 Developer quick start guides
├─ 5.2 Operations runbooks
├─ 5.3 Architecture decision records
└─ 5.4 API documentation

Success: Team can setup and operate independently
Status: 🔴 Not Started
```

### Phase 6: Production Hardening (Weeks 11-12)

```
Objective: Security audit, optimization, deployment
├─ 6.1 Security audit
├─ 6.2 Performance optimization
├─ 6.3 Disaster recovery procedures
└─ 6.4 Deployment & rollout

Success: Production-ready, zero critical vulnerabilities
Status: 🔴 Not Started
```

**Total**: 12 weeks, 5.5 FTE

---

## Document Map

### Backlog Documents (You are here)

```
products/dcmaar/docs/
├─ DCMAAR_BACKLOG.md .......................... Main backlog (sections 1-8)
├─ SECURE_CONNECTORS_BACKLOG.md .............. Detailed secure connectors work (THIS IS HERE)
└─ DCMAAR_BACKLOG_INDEX.md ................... Navigation guide (you are reading this)
```

### Browser Connector Documentation

```
libs/typescript/connectors/
├─ BROWSER_CONNECTORS.md ..................... Configuration guide
├─ BROWSER_IMPLEMENTATION_SUMMARY.md ......... Technical summary
├─ QUICKSTART.md ............................ 5-minute setup
└─ IMPLEMENTATION_CHECKLIST.md .............. Verification checklist
```

### Security Documentation (NEW)

```
libs/typescript/connectors/
├─ SECURE_COMMUNICATION_BROWSER.md .......... 5 patterns detailed (1200+ lines)
├─ SECURE_BROWSER_IMPLEMENTATION.md ........ Implementation guide (900+ lines)
├─ SECURE_COMMUNICATION_SUMMARY.md ......... Comprehensive overview (800+ lines)
├─ MTLS_MQTTS_QUICK_REFERENCE.md ........... Quick reference (600+ lines)
├─ SECURITY_ARCHITECTURE_VISUAL.md ........ Visual diagrams (1000+ lines)
└─ SECURITY_DOCUMENTATION_INDEX.md ........ Navigation guide (1500+ lines)

apps/guardian/
└─ SECURE_COMMUNICATION_SETUP.md ........... Guardian service worker setup (1000+ lines)
```

---

## Search Guide

**Q: How do I implement OAuth2 in Guardian?**
A: Read [SECURE_BROWSER_IMPLEMENTATION.md](../../../libs/typescript/connectors/SECURE_BROWSER_IMPLEMENTATION.md) Section "OAuth2/OIDC", then see [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phase 3.2

**Q: Why can't browsers use mTLS?**
A: See [MTLS_MQTTS_QUICK_REFERENCE.md](../../../libs/typescript/connectors/MTLS_MQTTS_QUICK_REFERENCE.md)

**Q: What are the 5 secure communication patterns?**
A: See [SECURITY_ARCHITECTURE_VISUAL.md](../../../libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md) for diagrams, or [SECURE_COMMUNICATION_BROWSER.md](../../../libs/typescript/connectors/SECURE_COMMUNICATION_BROWSER.md) for detailed explanation

**Q: How do I setup connectors for a new product?**
A: See [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) Phase 1 sections, or wait for Phase 5 CONNECTOR_QUICKSTART.md

**Q: What's the connection between browser connectors and security?**
A: See [SECURITY_DOCUMENTATION_INDEX.md](../../../libs/typescript/connectors/SECURITY_DOCUMENTATION_INDEX.md) which explains how 4 browser connectors enable 5 security patterns

**Q: How do I know which security pattern to use?**
A: See [SECURITY_ARCHITECTURE_VISUAL.md](../../../libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md) decision tree, or [SECURE_COMMUNICATION_SUMMARY.md](../../../libs/typescript/connectors/SECURE_COMMUNICATION_SUMMARY.md) pattern comparison

**Q: Is this backlog complete?**
A: Yes, it covers all 12 weeks of work. See [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) phases 1-6. Before implementing, team should review and prioritize.

---

## Getting Started

### If you have 5 minutes

1. Read this page
2. Skim [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) "Executive Summary"
3. Done

### If you have 30 minutes

1. Read this page
2. Read [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) "Work Items by Phase"
3. Read [SECURITY_ARCHITECTURE_VISUAL.md](../../../libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md)
4. Done

### If you have 2 hours (full understanding)

1. Read [DCMAAR_BACKLOG.md](./DCMAAR_BACKLOG.md)
2. Read [SECURE_CONNECTORS_BACKLOG.md](./SECURE_CONNECTORS_BACKLOG.md) (all sections)
3. Read [SECURITY_ARCHITECTURE_VISUAL.md](../../../libs/typescript/connectors/SECURITY_ARCHITECTURE_VISUAL.md)
4. Read [SECURE_COMMUNICATION_SUMMARY.md](../../../libs/typescript/connectors/SECURE_COMMUNICATION_SUMMARY.md)
5. Read [apps/guardian/SECURE_COMMUNICATION_SETUP.md](../../apps/guardian/SECURE_COMMUNICATION_SETUP.md)
6. Done - you can now architect and implement

---

## Status Tracking

### Completed ✅

- [x] Browser connector library (4-type restriction)
- [x] Tree-shaking configuration (Vite)
- [x] Security patterns documentation (5 patterns)
- [x] Guardian service worker setup guide
- [x] Visual architecture diagrams
- [x] Security documentation index

### In Progress 🟡

- [ ] Comprehensive backlog (creating now - THIS FILE)

### Not Started 🔴

- [ ] Java connector initialization framework
- [ ] Backend connector migration
- [ ] Security pattern implementations
- [ ] Test suites
- [ ] Documentation & runbooks
- [ ] Production audit & deployment

---

## How to Update This Document

**When to update**:

- After each phase completes
- When risks are identified
- When timelines change
- When new patterns are discovered

**What to update**:

- Phase status (🔴 🟡 🟢)
- Completion percentage
- Risks identified
- Dependencies changed
- Success criteria met

**How to update**:

1. Edit relevant phase section
2. Update status emoji
3. Update "Last Updated" timestamp
4. Commit with explanation
5. Link to any new documentation

---

**Created**: November 15, 2025  
**Last Updated**: November 15, 2025  
**Version**: 1.0

For questions, see [SECURITY_DOCUMENTATION_INDEX.md](../../../libs/typescript/connectors/SECURITY_DOCUMENTATION_INDEX.md) or contact the Tech Lead.
