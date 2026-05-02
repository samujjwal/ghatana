# Digital Marketing Product

DMOS product workspace for the Digital Marketing Operating System.

## Module Overview

- dm-core-contracts: canonical IDs, actor/context propagation, bridge context conversion
- dm-domain-packs: boundary policy rules, compliance rule packs, plugin startup bindings, pack validations
- dm-kernel-bridge: product-owned bridge adapter over kernel bridge ports
- dm-domain: domain model and invariants
- dm-application: application services and orchestrations
- dm-api: product APIs
- dm-integration-tests: DMOS integration suites

## Required Check Gates

Run from repo root:

- ./gradlew :products:digital-marketing:dm-core-contracts:check
- ./gradlew :products:digital-marketing:dm-domain-packs:check
- ./gradlew :products:digital-marketing:dm-kernel-bridge:check
- ./gradlew :products:digital-marketing:dm-domain:check
- ./gradlew :products:digital-marketing:dm-application:check
- ./gradlew :products:digital-marketing:dm-api:check
- ./gradlew :products:digital-marketing:dm-integration-tests:check

dm-domain-packs check includes:

- validateDomainPackManifest
- validatePolicyPack
- validateComplianceRulePack
- validateReferenceConsumerHygiene

## Product Guardrails

- Product code and rule prefix are DM / DM-
- Default-deny boundary policy is mandatory (DM-BP-999)
- No PHR-/FIN- reference consumer tokens in DMOS pack production source
- Product code must use kernel/plugin public interfaces only
- No DMOS-specific logic in kernel/platform plugin production modules
