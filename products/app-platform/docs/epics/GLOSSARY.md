# Platform Glossary & Abbreviations

**Generated:** March 2, 2026 | **Revised:** March 12, 2026  
**Purpose:** Centralized glossary of terms, abbreviations, and concepts used across all epic specifications

---

## Table of Contents

1. [Epic Categories & Layers](#epic-categories--layers)
2. [Platform Components](#platform-components)
3. [Architectural Concepts](#architectural-concepts)
4. [Technical Terms](#technical-terms)
5. [Regulatory & Compliance](#regulatory--compliance)
6. [Abbreviations](#abbreviations)
7. [Nepal-Specific Terms](#nepal-specific-terms) *(Capital Markets domain pack)*

---

## Epic Categories & Layers

### D-\* (Domain Layer)

Domain Packs implementing industry-specific business logic. Each D-\* module belongs to one or more domain packs. The base platform ships with the **Capital Markets (Siddhanta)** domain pack for Nepal; other industries add their own D-\* modules via new domain packs. Examples in Capital Markets: OMS (D-01), EMS (D-02), PMS (D-03), Compliance (D-07).

### K-\* (Kernel Layer)

Platform kernel services providing foundational capabilities. Examples: IAM, Event Bus, Rules Engine, Ledger Framework.

### W-\* (Workflow Layer)

Cross-domain workflow orchestration modules. Examples: Workflow Orchestration, Client Onboarding.

### O-\* (Operations Layer)

Operational management and runbook automation. Examples: Operator Console.

### P-\* (Packs Layer)

Extension pack governance and certification. Examples: Pack Certification & Marketplace.

### R-\* (Regulatory Layer)

Regulator-facing interfaces and evidence export. Examples: Regulator Portal.

### T-\* (Testing Layer)

Platform-wide integration and end-to-end testing. Examples: Integration Testing.

### PU-\* (Platform Unity)

Platform-wide unification and manifest management. Examples: Platform Manifest.

---

## Platform Components

### OMS (Order Management System)

**Epic:** D-01  
**Description:** Manages the complete order lifecycle from capture through execution, including validation, routing, and position tracking.

### EMS (Execution Management System)

**Epic:** D-02  
**Description:** Handles smart order routing, execution algorithms, and venue connectivity abstraction.

### PMS (Portfolio Management System)

**Epic:** D-03  
**Description:** Manages client portfolios, performance tracking, and asset allocation.

### Market Data

**Epic:** D-04  
**Description:** Real-time and historical market data ingestion, normalization, and distribution.

### Pricing Engine

**Epic:** D-05  
**Description:** Instrument valuation, mark-to-market calculations, and pricing model management.

### Risk Engine

**Epic:** D-06  
**Description:** Pre-trade and post-trade risk calculations, margin requirements, and exposure monitoring.

### Compliance

**Epic:** D-07  
**Description:** Regulatory compliance checks, AML/KYC screening, and insider trading prevention.

### Surveillance

**Epic:** D-08  
**Description:** Market surveillance, trade monitoring, and suspicious activity detection.

### Post-Trade

**Epic:** D-09  
**Description:** Trade settlement, reconciliation, and clearing operations.

### Regulatory Reporting

**Epic:** D-10  
**Description:** Automated generation and submission of regulatory reports to authorities.

### Reference Data

**Epic:** D-11  
**Description:** Master data management for instruments, counterparties, and market reference data.

### Corporate Actions

**Epic:** D-12  
**Description:** Processing of dividends, bonus shares, rights issues, and other corporate events.

### IAM (Identity & Access Management)

**Epic:** K-01  
**Description:** Authentication, authorization, SSO, MFA, and RBAC/ABAC for the platform.

### Configuration Engine

**Epic:** K-02  
**Description:** Dynamic configuration management with hot-reload capabilities.

### Rules Engine

**Epic:** K-03  
**Description:** Declarative policy evaluation engine for compliance, validation, and business rules.

### Plugin Runtime

**Epic:** K-04  
**Description:** Sandboxed execution environment for T3 Executable Packs.

### Event Bus

**Epic:** K-05  
**Description:** Event-sourced messaging backbone with append-only event store.

### Observability

**Epic:** K-06  
**Description:** Metrics, logging, tracing, and monitoring infrastructure.

### Audit Framework

**Epic:** K-07  
**Description:** Immutable audit trail for all platform actions and state changes.

### Data Governance

**Epic:** K-08  
**Description:** Data classification, encryption, residency, and lifecycle management.

### AI Governance

**Epic:** K-09  
**Description:** AI model registry, versioning, explainability, and drift monitoring.

### Deployment Abstraction

**Epic:** K-10  
**Description:** Multi-cloud and on-prem deployment orchestration.

### API Gateway

**Epic:** K-11  
**Description:** Unified API entry point with authentication, rate limiting, and routing.

### Platform SDK

**Epic:** K-12  
**Description:** Multi-language SDK providing clients for all kernel services.

### Admin Portal

**Epic:** K-13  
**Description:** Web-based administrative interface for platform management.

### Secrets Management

**Epic:** K-14  
**Description:** Centralized secrets lifecycle management with vault integration.

### Multi-Calendar Service

**Epic:** K-15  
**Description:** Platform-agnostic calendar conversion service. Supports any number of calendar systems (Gregorian, Bikram Sambat, Hijri, Thai Solar, etc.) registered via T1 Calendar Config Packs. Returns `CalendarDate` composite objects. Previously called "Dual-Calendar Service" — renamed in v2.0.0 to reflect domain-agnostic design.

### Ledger Framework

**Epic:** K-16  
**Description:** Double-entry bookkeeping engine for financial tracking.

---

## Architectural Concepts

### Generic Core

Jurisdiction-agnostic platform code that contains no hardcoded business rules or market-specific logic. All jurisdiction-specific behavior is externalized to extension packs.

### Jurisdiction Isolation

Architectural principle ensuring that market-specific logic (Nepal, India, Bangladesh) is completely separated from the generic core via plugin architecture.

### Extension Packs

Pluggable modules that extend platform functionality without modifying core code:

- **T1 Config Packs:** Schema and configuration data (JSON/YAML) — e.g., holiday calendars, fiscal year rules, instrument types
- **T2 Rule Packs:** Declarative logic (OPA/Rego, SQL, DSL) — sandbox-safe, no network access
- **T3 Executable Packs:** Code adapters (Java/Kotlin/Python/JS) — managed thread pool, explicit permission grants

### Domain Pack

A self-contained, versioned bundle of D-\* modules, T1/T2/T3 extension packs, data models, workflows, and UI components that implements a specific industry domain. Domain packs are installed on top of the generic AppPlatform Kernel (K-01 through K-19) without modifying kernel code. Examples: Capital Markets (Siddhanta), Banking, Healthcare, Insurance.

### DomainManifest

The `domain-pack.yaml` descriptor that declares a domain pack's identity, `domainTypes`, `requiredKernels` (semver constraints), `capabilities` (CoreDomainCapability list + `extendedCapabilities`), T1/T2/T3 components, lifecycle hooks, feature flags, and author metadata.

### KernelModule

One of the 19 generic platform kernel services (K-01 through K-19). Kernel modules are domain-agnostic and may not contain any industry-specific logic. All domain-specific behavior is externalized to domain packs.

### PackCertification

Formal review process a domain pack must pass before marketplace publication. Checks code quality (>90% coverage), security scans, performance benchmarks, compatibility matrix, and documentation completeness. Managed by the P-01 Pack Certification module.

### Event Sourcing

Architectural pattern where all state changes are captured as immutable events in an append-only log, enabling audit trails and state reconstruction.

### CQRS (Command Query Responsibility Segregation)

Pattern separating write operations (commands) from read operations (queries) for scalability and consistency.

### Maker-Checker

Dual approval workflow requiring two distinct authorized users to approve critical operations, preventing fraud and ensuring oversight.

### CalendarDate

Platform-level multi-calendar timestamp (defined in ADR-004 v2, introduced in K-15 Multi-Calendar Service v2):
- `primary`: Canonical UTC instant (ISO 8601) — always present.
- `timezone`: IANA timezone string.
- `calendars`: `Record<CalendarId, CalendarDateTime>` — populated by K-15 from active T1 calendar packs. Empty when no calendar packs are installed.
- `fiscalYear?`: Optional per-calendar fiscal year label map.

Replaces the obsolete `DualDate` type (which hard-coded Gregorian + BS fields).

### DualDate *(deprecated as of v2.0.0)*

Obsolete data structure that stored exactly two calendar dates (Gregorian + Bikram Sambat). **Replaced by `CalendarDate`**, which supports any number of calendars via a `Record<CalendarId, CalendarDateTime>` map. Migration: replace `dual_date.gregorian` with `calendar_date.primary`; replace `dual_date.bs` with `calendar_date.calendars['bs']`.

### Hot Reload

Capability to update configuration, rules, or packs without restarting services or causing downtime.

### Saga

Distributed transaction pattern for coordinating long-running workflows across multiple services with compensation logic.

### Circuit Breaker

Resilience pattern that prevents cascading failures by temporarily blocking requests to failing services.

---

## Technical Terms

### ABAC (Attribute-Based Access Control)

Authorization model using attributes (user properties, resource properties, environment) to make access decisions.

### RBAC (Role-Based Access Control)

Authorization model using predefined roles to grant permissions.

### mTLS (Mutual TLS)

Bidirectional TLS authentication where both client and server verify each other's certificates.

### JWT (JSON Web Token)

Compact, URL-safe token format for securely transmitting claims between parties.

### OIDC (OpenID Connect)

Authentication protocol built on OAuth 2.0 for identity verification.

### SAML (Security Assertion Markup Language)

XML-based standard for exchanging authentication and authorization data.

### gRPC

High-performance RPC framework using Protocol Buffers.

### Idempotency

Property where performing an operation multiple times has the same effect as performing it once.

### P99 Latency

99th percentile latency - 99% of requests complete faster than this threshold.

### TPS (Transactions Per Second)

Throughput metric measuring system capacity.

### RTO (Recovery Time Objective)

Maximum acceptable time to restore service after a disruption.

### RPO (Recovery Point Objective)

Maximum acceptable data loss measured in time.

### SLA (Service Level Agreement)

Commitment to specific performance, availability, or quality metrics.

### SLO (Service Level Objective)

Target value or range for a service level metric.

### TCA (Transaction Cost Analysis)

Analysis of trading costs including spreads, slippage, and fees.

### VWAP (Volume-Weighted Average Price)

Trading algorithm that executes orders to match the volume-weighted average price.

### TWAP (Time-Weighted Average Price)

Trading algorithm that executes orders evenly over a specified time period.

---

## Regulatory & Compliance

### SEBON (Securities Board of Nepal)

Nepal's securities market regulator responsible for market oversight and investor protection.

### NRB (Nepal Rastra Bank)

Nepal's central bank regulating banking and financial institutions.

### NEPSE (Nepal Stock Exchange)

Nepal's primary securities exchange.

### CDSC (Central Depository System and Clearing Limited)

Nepal's central securities depository and clearing house.

### AML (Anti-Money Laundering)

Regulations and procedures to prevent money laundering and terrorist financing.

### KYC (Know Your Customer)

Process of verifying client identity and assessing risk.

### PEP (Politically Exposed Person)

Individual with prominent public function who may pose higher risk.

### TDS (Tax Deducted at Source)

Tax withheld at the point of income generation.

### FATF (Financial Action Task Force)

International body setting standards for combating money laundering and terrorist financing.

### OFAC (Office of Foreign Assets Control)

US Treasury department administering economic sanctions.

### MiFID II (Markets in Financial Instruments Directive)

European regulation governing investment services (reference for best practices).

### SOX (Sarbanes-Oxley Act)

US regulation for corporate governance and financial disclosure (reference for audit requirements).

### GDPR (General Data Protection Regulation)

European data protection regulation (reference for privacy requirements).

---

## Abbreviations

### General

- **API:** Application Programming Interface
- **BS:** Bikram Sambat (Nepali calendar)
- **CA:** Corporate Action
- **CLI:** Command Line Interface
- **CMK:** Customer-Managed Key
- **CoA:** Chart of Accounts
- **CSV:** Comma-Separated Values
- **DAG:** Directed Acyclic Graph
- **DLQ:** Dead Letter Queue
- **DNS:** Domain Name System
- **DR:** Disaster Recovery
- **DSL:** Domain-Specific Language
- **E2E:** End-to-End
- **FIX:** Financial Information eXchange (protocol)
- **FR:** Functional Requirement
- **HSM:** Hardware Security Module
- **HTTP:** Hypertext Transfer Protocol
- **ISIN:** International Securities Identification Number
- **JSON:** JavaScript Object Notation
- **KMS:** Key Management Service
- **LCA:** Local Compliance & Audit (code prefix)
- **MTTR:** Mean Time To Recovery
- **MFA:** Multi-Factor Authentication
- **NFR:** Non-Functional Requirement
- **NPR:** Nepalese Rupee
- **PDF:** Portable Document Format
- **PII:** Personally Identifiable Information
- **REST:** Representational State Transfer
- **SDK:** Software Development Kit
- **SOR:** Smart Order Routing
- **SQL:** Structured Query Language
- **SSO:** Single Sign-On
- **TLS:** Transport Layer Security
- **UI:** User Interface
- **URL:** Uniform Resource Locator
- **UUID:** Universally Unique Identifier
- **WAF:** Web Application Firewall
- **XBRL:** eXtensible Business Reporting Language
- **YAML:** YAML Ain't Markup Language
- **ZIP:** File compression format

### Platform-Specific

- **ASR:** Authoritative Source Register (code prefix)
- **T1:** Tier 1 Extension Pack (Config/Schema)
- **T2:** Tier 2 Extension Pack (Rules/Policies)
- **T3:** Tier 3 Extension Pack (Executable/Adapters)

---

## Nepal-Specific Terms

### Bikram Sambat (BS)

Official calendar of Nepal, approximately 56-57 years ahead of the Gregorian calendar. Used for all official dates and fiscal year calculations.

### Kitta

Unit of share quantity in Nepal. Minimum tradable lot is typically 10 kitta.

### Dashain

Major Hindu festival in Nepal (September-October), market holiday period.

### Tihar

Festival of lights in Nepal (October-November), market holiday period.

### Fiscal Year (Nepal)

Runs from Shrawan 1 to Ashadh 32 (approximately mid-July to mid-July).

### NRB Directive

Regulatory directive issued by Nepal Rastra Bank with binding requirements.

### SEBON Directive

Regulatory directive issued by Securities Board of Nepal.

### Promoter Shares

Shares held by company founders/promoters, often subject to lock-in periods and trading restrictions.

### Demat Account

Dematerialized account holding securities in electronic form.

---

## Usage Guidelines

### For Epic Authors

- Use terms consistently as defined in this glossary
- Define new terms before first use in epic
- Add new terms to this glossary via pull request
- Link to glossary from epic specifications

### For Developers

- Reference glossary for correct terminology
- Use exact abbreviations as defined
- Implement features using glossary definitions
- Update glossary when introducing new concepts

### For Reviewers

- Verify terminology consistency with glossary
- Flag undefined terms for addition
- Ensure abbreviations match glossary
- Check for conflicting definitions

---

## Maintenance

### Adding New Terms

1. Identify appropriate section
2. Provide clear, concise definition
3. Include epic references where applicable
4. Add cross-references to related terms
5. Submit via pull request
6. Get approval from technical lead

### Updating Existing Terms

1. Document reason for change
2. Update all references
3. Notify affected teams
4. Version control the change
5. Update epic specifications if needed

### Quarterly Review

- Review all terms for accuracy
- Add new terms from recent epics
- Archive deprecated terms
- Update regulatory references
- Validate cross-references

---

**Glossary Status:** ✅ ACTIVE  
**Last Updated:** March 10, 2026  
**Next Review:** June 10, 2026  
**Owner:** Platform Architecture Team  
**Approver:** Chief Technology Officer
