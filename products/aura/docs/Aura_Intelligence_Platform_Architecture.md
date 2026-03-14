# Aura Intelligence Platform Architecture

> **Cross-references:** This document provides detailed narrative design decisions behind the
> architecture. For the canonical 7-layer model and technology tables, see
> [Aura_System_Architecture.md](Aura_System_Architecture.md) and
> [Aura_Technical_Stack_Blueprint.md](Aura_Technical_Stack_Blueprint.md).

## Overview

The Aura Intelligence Platform Architecture defines how Aura operates as a scalable,
event-driven, AI-native personalization platform.

It connects:

- product ingestion pipelines
- canonical knowledge models
- user profile intelligence
- recommendation modules and pipelines
- agent orchestration
- analytics and learning loops

The goal is to support low-latency, explainable, privacy-aware recommendations at scale.

---

# 1. Platform Objectives

Aura’s intelligence platform must support:

1. Personalized recommendations in near real time
2. Explainable reasoning for each recommendation
3. Scalable ingestion of product and community data
4. Continuous learning from user feedback
5. Strong privacy, consent, and governance controls

---

# 2. High-Level Topology

Core platform layers:

1. Source & Ingestion Layer
2. Canonical Knowledge Layer
3. Personal Intelligence Layer
4. Decision & Recommendation Layer
5. Agent Orchestration Layer
6. Experience Delivery Layer
7. Observability, Governance, and Learning Layer

---

# 3. Source & Ingestion Layer

## Inputs

Aura ingests structured and unstructured data from:

- retailer and affiliate product feeds
- brand product pages
- ingredient databases
- product reviews
- community discussions
- optional user-linked receipts
- optional wellness integrations

## Ingestion Responsibilities

- source connection and scheduling
- scraping / API retrieval
- normalization
- deduplication
- entity resolution
- change detection
- freshness scoring

## Core Components

- Source Adapters
- Crawl Scheduler
- Parsing Workers
- Enrichment Workers
- Deduplication Service
- Catalog Merge Service

---

# 4. Canonical Knowledge Layer

This layer turns fragmented raw inputs into durable platform intelligence.

## Core Knowledge Stores

### Product Catalog

Canonical representation of all products.

### Ingredient Graph

Ingredient relationships, functions, conflicts, and suitability.

### Shade Ontology

Cross-brand mapping of shade, tone, undertone, finish, and coverage.

### Style Taxonomy

Style archetypes, color preferences, category mappings.

### Review & Community Corpus

Structured and vectorized review and social insight repository.

### Source Provenance Store

Tracks where data came from, how fresh it is, and confidence level.

## Storage Pattern

Hybrid storage is recommended:

- Data Cloud-managed relational storage for canonical entities and transactional data
- Data Cloud-managed vector storage for semantic retrieval at early scale
- Data Cloud-managed object storage for raw payloads and snapshots
- Data Cloud-managed cache for hot recommendation paths

---

# 5. Personal Intelligence Layer

This layer maintains the user-specific intelligence model.

## Core Modules

### You Index Service

Stores declared, inferred, and imported profile attributes.

### Context Engine

Evaluates moment-sensitive context:

- season
- weather
- time of day
- event context
- mood
- optional wellness/bio signals

### Ownership & History Service

Tracks items viewed, saved, purchased, dismissed, or already owned.

### Preference Learning Service

Builds long-term preference vectors from behavior.

## Design Principles

- user-editable inferred data
- clear separation of declared vs inferred vs imported fields
- privacy-aware optional integrations
- reversible and auditable inference history

---

# 6. Decision & Recommendation Layer

This layer turns knowledge + profile + context into recommendations.

## Pipeline

### Step 1: Candidate Generation

Generate a relevant subset of items based on query, profile, and catalog metadata.

### Step 2: Rules Filtering

Apply hard constraints:

- allergen exclusions
- price bounds
- ethical filters
- availability rules
- ownership similarity suppression

### Step 3: Feature Construction

Build decision features such as:

- shade compatibility
- ingredient compatibility
- community sentiment alignment
- price fit
- recency/trend signals
- profile similarity signals

### Step 4: Ranking

Use hybrid ranking:

- deterministic scoring rules
- ML ranking model
- reranking for diversity and trust

### Step 5: Explanation Generation

Produce user-facing explanations and reason codes.

### Step 6: Confidence Estimation

Generate confidence signals based on profile completeness, source quality, and model certainty.

## Output

Each recommendation package should include:

- ranked item
- score
- confidence
- reason codes
- evidence links
- safety/trust flags

---

# 7. Agent Orchestration Layer

Aura can support specialized agents coordinated through shared context and events.

## Core Agents

### Discovery Agent

Finds relevant candidate products.

### Compatibility Agent

Evaluates fit with profile and context.

### Ingredient Safety Agent

Checks ingredient-level suitability and conflicts.

### Community Intelligence Agent

Extracts sentiment and warnings from user-generated content.

### Commerce Agent

Finds merchant options, pricing, and availability.

### Explanation Agent

Composes human-readable rationales.

## Orchestration Model

Recommended orchestration flow:

User Intent
→ Intent Interpreter
→ Discovery Agent
→ Compatibility Agent
→ Safety Agent
→ Ranking Engine
→ Explanation Agent
→ Final Result

## Orchestration Principles

- structured intermediate outputs
- event-driven coordination
- traceable reasoning chain
- fallback to deterministic paths when models are uncertain

---

# 8. Experience Delivery Layer

This layer serves results to user-facing applications.

## Delivery Channels

- web app
- mobile app
- AI assistant interface
- future partner APIs

## Core User Experiences

- personalized feed
- product detail intelligence panel
- compare view
- “ask Aura” assistant
- profile dashboard
- privacy and consent center

## Serving Requirements

- low-latency API responses
- cached recommendation paths where safe
- incremental hydration for heavy detail views
- consistent explanation payloads across surfaces

---

# 9. Event-Driven Workflow Design

Aura should use an event-driven architecture for scalability and coordination.

## Canonical Event Streams

- product discovered
- product updated
- profile updated
- recommendation generated
- recommendation outcome captured
- feedback captured
- consent granted / revoked
- model deployed
- drift detected

## Benefits

- asynchronous scaling
- decoupled processors and runtime boundaries
- replayability
- better analytics and model learning

## Processing Design

- immutable event records
- versioned payloads
- idempotent consumers
- PII minimization in shared streams

---

# 10. Vector Search & Retrieval Architecture

Vector retrieval supports semantic intelligence across products, reviews, and community content.

## Retrieval Use Cases

- “show me products similar to this”
- semantic review search
- community insight retrieval
- style inspiration matching
- duplicate or alternative item detection

## Embedding Domains

- product descriptions
- ingredient narratives
- review text
- community discussions
- user preference vectors

## Retrieval Strategy

Use hybrid retrieval:

- metadata filtering first
- keyword / lexical match
- vector similarity retrieval
- reranking with profile-aware scoring

---

# 11. Real-Time Recommendation Infrastructure

## Online Path

Used for feed generation, assistant queries, and product detail recommendations.

Requirements:

- fast candidate retrieval
- cached feature access
- lightweight scoring
- explanation generation in the response path

## Offline / Nearline Path

Used for:

- heavy enrichment
- community analysis
- model retraining
- profile batch updates
- precomputed recommendation sets

## Serving Pattern

- precompute where possible
- compute live when personalization/context requires it
- combine cached base recommendations with real-time reranking

---

# 12. Governance, Trust, and Safety Architecture

Aura must treat trust as a platform capability, not a UI feature.

## Required Controls

### Consent Management

Track exactly what user data can be used and for what purpose.

### Data Lineage

Maintain provenance for product, review, and community data.

### Recommendation Audit Trail

Store model version, input profile snapshot, and reason codes.

### Safety Rules

Prevent unsupported medical-style claims and unsafe advice.

### Transparency Controls

Label sponsored results, inferred attributes, and uncertainty.

## Policy Engine

A dedicated policy engine should validate:

- recommendation eligibility
- data-use compliance
- output safety constraints
- monetization guardrails

---

# 13. Observability & Model Monitoring

## Platform Observability

- service latency
- queue depth
- ingestion freshness
- error rates
- cache hit ratios

## Recommendation Observability

- median time-to-decision
- shade-miss rate
- adverse reaction report volume and review SLA
- recommendation-attributed return reduction
- explanation helpfulness and trust feedback

## Model Monitoring

- drift detection
- cohort performance by skin tone / profile group
- calibration quality
- diversity and repetition metrics

---

# 14. Scalability Strategy

## Early Stage

Start with three deployables: `api`, `core-worker`, and `ml-inference`.

Keep profile, catalog, recommendation, explainability, community, and governance as explicit
internal modules with shared contracts and test boundaries.

## Growth Stage

Split out only when evidence justifies it:

- ingestion if source throughput, retry isolation, or freshness SLOs need their own runtime
- recommendation if ranking latency, scaling profile, or release cadence diverges materially
- community intelligence if corpus ingestion or NLP workloads need independent scaling
- explainability or governance only if compliance, security, or ownership needs demand a separate boundary

## Scale Stage

Adopt:

- AEP fan-out when cross-process retries or throughput require it
- independent model-serving infrastructure
- feature store
- distributed retrieval and caching tiers

---

# 15. Recommended Technology Pattern

## Core Platform

| Component          | Technology                             | Notes                                                     |
| ------------------ | -------------------------------------- | --------------------------------------------------------- |
| User API layer     | Node.js + Fastify + Prisma             | GraphQL/REST for UI state, preferences, CRUD              |
| Core Domain        | Java 21 + ActiveJ                      | High-perf event processing, ranking, ingestion            |
| Web frontend       | React Router v7 + React + Tailwind CSS | SSR for product and feed pages                            |
| Mobile frontend    | React Native + NativeWind              | iOS and Android                                           |
| Event plane        | AEP                                    | Cross-process event communication boundary                |
| Data plane         | Data Cloud                             | Managed storage lifecycle, lineage, retention, restore    |
| Relational storage | Data Cloud relational plugin           | Primary transactional store                               |
| Cache              | Data Cloud cache plugin                | Session state, recommendation cache, feature cache        |
| Async event plane  | AEP                                    | Durable fan-out and async workflow boundary               |
| Vector storage     | Data Cloud vector plugin               | Semantic retrieval; upgrade underlying implementation at scale |
| Object storage     | Data Cloud object-storage plugin       | Raw ingestion payloads, model artifacts                   |

## AI / ML

- Python inference boundary
- embedding pipelines
- ranking pipelines
- model registry
- evaluation pipeline

## Infra

- Docker
- Kubernetes when scale requires
- OpenTelemetry + Prometheus + Grafana
- Gitea Actions CI/CD with environment promotion gates

---

# 16. Reference Runtime Flow

## Example: User asks for a hydrating fragrance-free foundation under $50

1. Request enters assistant/API layer
2. Intent parser extracts category, price, and constraints
3. Discovery service retrieves matching foundation candidates
4. Compatibility engine scores against skin type + undertone
5. Safety engine removes fragrance conflicts
6. Ranking service orders remaining candidates
7. Explanation service generates user-facing reasons
8. Results returned with confidence and buy links
9. Interaction feedback captured for learning

---

# 17. Strategic Outcome

The Aura Intelligence Platform Architecture enables Aura to evolve from
a recommendation app into a durable AI-native intelligence system for
beauty, style, and wellness decisions.

It creates the operating foundation for:

- personalization at scale
- trust and explainability
- agentic recommendation workflows
- proprietary intelligence accumulation
