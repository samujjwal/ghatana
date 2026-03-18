# PHR Platform — Disaster Recovery Plan

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** DevOps Lead  
**Approval status:** Draft for production readiness  
**Classification:** Internal — Restricted

This plan defines disaster recovery targets and recovery procedures for the PHR platform.

---

## 1. Recovery targets

| Metric | Target |
| --- | --- |
| RPO | less than 24 hours |
| RTO | less than 4 hours |
| Backup verification | monthly restore validation |
| Full DR exercise | quarterly |

---

## 2. Recovery scope

- PostgreSQL transactional data
- Ceph document storage
- configuration, secrets references, and deployment manifests
- observability and audit evidence necessary for recovery analysis

---

## 3. Recovery steps

1. declare disaster and assign incident commander
2. stabilize or isolate damaged primary environment
3. restore database to approved recovery point
4. restore object storage metadata and binaries
5. rehydrate application and worker services
6. validate tenant isolation, auth, consent, and audit paths
7. reopen service in controlled phases

---

## 4. Validation checklist

- health endpoints green
- login and patient summary smoke tests green
- document retrieval and checksum verification green
- audit writes and reads green
- monitoring and alerting restored

This DR plan is not complete until quarterly exercises produce evidence against the target RPO and RTO.