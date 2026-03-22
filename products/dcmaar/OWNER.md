# Owner: DCMAAR (Guardian Platform)

**Team:** Guardian Team  
**Slack:** #platform-dcmaar  
**On-call:** Guardian on-call rotation  
**Architecture lead:** Guardian Tech Lead  
**Boundary audit score:** 6/10 (2026-03-21)  

## Responsibility

DCMAAR (Digital Child Monitoring, Access, and Activity Reporting) hosts the **Guardian platform** — parental controls, child activity monitoring, and digital wellness features.

- Guardian mobile app (iOS/Android) for parents  
- Child device monitoring and activity summaries
- Usage limits and content filtering
- Threat intelligence for harmful content detection

**Domain boundary:** DCMAAR owns child safety and parental control concerns. It uses Data-Cloud for event streaming and AEP for threat detection pipelines.

## Key Library

| Library | Location |
|---------|----------|
| `guardian-threat-service` | `products/dcmaar/libs/java/guardian-threat-service` |

## Consumers

DCMAAR is end-user-facing. Its `guardian-threat-service` library may be consumed by security monitoring systems.
