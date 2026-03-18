# PHR Platform — Secrets Management Playbook

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** DevOps Lead  
**Approval status:** Proposed P0 deployment control  
**Classification:** Internal — Restricted

| Field | Value |
| --- | --- |
| Source-of-truth inputs | [Runtime architecture](phr_runtime_architecture.md), [CI/CD pipeline specification](phr_ci_cd_pipeline_specification.md), [Incident response playbook](../01_governance/phr_incident_response_playbook.md) |
| Decision summary | Use vault-managed secrets for shared and production environments, with environment-injected local development fallbacks |

This document defines how secrets are created, stored, rotated, injected, and revoked for PHR environments.

---

## 1. Secret classes

| Secret class | Examples | Storage policy |
| --- | --- | --- |
| platform credentials | Keycloak client secret, JWT signing public key cache, Ceph S3 access keys | vault only in shared environments |
| integration credentials | openIMIS credentials, SMS provider tokens, email API keys, ASR or OCR endpoints with auth | vault only |
| database and cache | Postgres app users, admin users, Valkey credentials | vault only |
| build and security tooling | Semgrep token, container registry credentials, DAST auth secrets | vault only |
| local developer secrets | local `.env` values for sandbox integrations | local environment only, never committed |

---

## 2. Environment strategy

| Environment | Secret source | Injection method |
| --- | --- | --- |
| local | developer-managed `.env.local` or secret manager helper | process environment |
| CI | short-lived vault-issued credentials | runner environment injection |
| staging | vault | sidecar, agent, or startup environment materialization |
| production | vault with audited access | runtime injection only, no static secret files |

Rule: production secrets must never be copied into repository files, Kubernetes manifests, or long-lived CI variables.

---

## 3. Required secret inventory

| Component | Secret names |
| --- | --- |
| API app | `PHR_DB_URL`, `PHR_VALKEY_URL`, `PHR_KEYCLOAK_CLIENT_SECRET`, `PHR_JWT_JWKS_URL`, `PHR_AES_MASTER_KEY`, `PHR_AUDIT_SIGNING_KEY` |
| Worker app | `PHR_DB_URL`, `PHR_VALKEY_URL`, `PHR_CEPH_ACCESS_KEY`, `PHR_CEPH_SECRET_KEY`, `PHR_SMS_API_KEY`, `PHR_OCR_API_KEY`, `PHR_ASR_API_KEY` |
| Integrations | `PHR_OPENIMIS_BASE_URL`, `PHR_OPENIMIS_CLIENT_ID`, `PHR_OPENIMIS_CLIENT_SECRET`, `PHR_EMAIL_API_KEY` |
| CI/CD | `PHR_CONTAINER_REGISTRY_TOKEN`, `PHR_SEMGREP_TOKEN`, `PHR_DAST_AUTH_SECRET` |

---

## 4. Rotation policy

| Secret type | Rotation frequency | Triggered rotation |
| --- | --- | --- |
| database credentials | every 90 days | staff departure, suspected compromise |
| integration API keys | every 90 days | provider incident, unexpected usage |
| object storage keys | every 90 days | any unauthorized access suspicion |
| encryption master keys | every 180 days with envelope-key strategy | compromise or cryptographic policy change |
| CI and scanner tokens | every 60 days | pipeline or vendor compromise |

Rotation rule: no rotation is complete until old credentials are revoked and rollback instructions are updated.

---

## 5. Bootstrap procedure

1. create secret path for environment and service
2. write least-privilege credential set
3. grant access only to the deployment identity for that service
4. inject into runtime on startup
5. run startup validation and fail fast on missing secrets
6. emit audit event for secret materialization

---

## 6. Local development rule

- local `.env.local` files are ignored by git
- only sandbox or dummy credentials may be used locally for third-party services
- production-like secrets are never distributed through chat, email, or shared docs

---

## 7. Incident handling

If a secret is suspected compromised:

1. revoke the credential immediately
2. rotate dependent credentials and keys
3. invalidate sessions or tokens that depend on the secret
4. capture audit evidence and timeline
5. run post-incident review and update this playbook

This playbook blocks staging and production until the environment secret paths, rotation owners, and bootstrap procedures are in place.