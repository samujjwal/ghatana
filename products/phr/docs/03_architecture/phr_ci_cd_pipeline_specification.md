# PHR Platform — CI/CD Pipeline Specification

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** DevOps Lead  
**Approval status:** Draft for delivery automation  
**Classification:** Internal

This document defines the minimum pipeline stages required for PHR continuous integration and controlled delivery.

---

## 1. Pipeline stages

| Stage | Purpose | Blocking |
| --- | --- | --- |
| source validation | install deps, validate lockfiles, check formatting | yes |
| lint and typecheck | code quality and schema consistency | yes |
| unit and contract tests | module and API feedback | yes |
| integration tests | DB, storage, cache, and stubbed external dependencies | yes |
| security scans | SAST, dependency scan, secret scan, container scan | yes |
| build and package | build deployable artifacts | yes |
| staging deploy | deploy to controlled environment | yes |
| staging verification | smoke, DAST, accessibility, tenant isolation | yes |
| approval gate | manual sign-off for release candidate | yes |
| production deploy | blue-green or canary rollout | yes |
| post-deploy verification | health, smoke, error budget, rollback check | yes |

---

## 2. Required tools

- package and build tooling already used by the repo
- Semgrep for SAST
- OWASP Dependency-Check and Trivy for vulnerabilities
- OWASP ZAP for DAST
- Playwright for web smoke verification
- k6 for scheduled performance checks

---

## 3. Branch and approval policy

- protected branches require green CI before merge
- any change to auth, consent, tenancy, or secrets requires security reviewer approval
- production deploy requires DevOps lead plus product or technical lead approval

---

## 4. Artifact retention

- build artifacts retained for 30 days minimum
- security and compliance reports retained for 1 year minimum
- release candidate evidence retained through the release lifecycle

This pipeline blocks continuous delivery until all mandatory quality and security gates are automated.