# Software-Org Documentation Index

**Last Updated:** November 26, 2025

---

## Overview

Software-Org is the **reference plugin implementation** of the Virtual-Org framework, specifically designed for **software development organizations**. This index provides navigation to all Software-Org documentation.

---

## 📚 Documentation Structure

### Core Documentation

| Document                                                                       | Purpose                                                           | Audience             |
| ------------------------------------------------------------------------------ | ----------------------------------------------------------------- | -------------------- |
| [SOFTWARE_ORG_PLUGIN_SPECIFICATION.md](./SOFTWARE_ORG_PLUGIN_SPECIFICATION.md) | **Master guide** - Architecture, departments, agents, event flows | All                  |
| [GETTING_STARTED.md](./GETTING_STARTED.md)                                     | Quick start guide                                                 | New Developers       |
| [FUTURE_FEATURES_ROADMAP.md](./FUTURE_FEATURES_ROADMAP.md)                     | Feature planning and roadmap                                      | Product, Engineering |

### API & Integration

| Document                                                                     | Purpose                         | Audience           |
| ---------------------------------------------------------------------------- | ------------------------------- | ------------------ |
| [REST_API_REFERENCE.md](./REST_API_REFERENCE.md)                             | Complete REST API documentation | Developers         |
| [JAVA_DOMAIN_SERVICES_INTEGRATION.md](./JAVA_DOMAIN_SERVICES_INTEGRATION.md) | Java domain integration         | Backend Developers |

### Frontend

| Document                                                                       | Purpose              | Audience            |
| ------------------------------------------------------------------------------ | -------------------- | ------------------- |
| [SOFTWARE_ORG_FRONTEND_USAGE_GUIDE.md](./SOFTWARE_ORG_FRONTEND_USAGE_GUIDE.md) | Frontend usage guide | Frontend Developers |

### Security & Compliance

| Document                                                           | Purpose                       | Audience          |
| ------------------------------------------------------------------ | ----------------------------- | ----------------- |
| [SECURITY_POSTURE_ASSESSMENT.md](./SECURITY_POSTURE_ASSESSMENT.md) | Security posture              | Security Team     |
| [GDPR_COMPLIANCE.md](./GDPR_COMPLIANCE.md)                         | GDPR compliance documentation | Compliance, Legal |
| [SOC2_EVIDENCE_INDEX.md](./SOC2_EVIDENCE_INDEX.md)                 | SOC2 compliance evidence      | Compliance, Audit |

### Operations

| Document                                                       | Purpose                      | Audience        |
| -------------------------------------------------------------- | ---------------------------- | --------------- |
| [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md)                   | Deployment instructions      | DevOps          |
| [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)                     | Troubleshooting guide        | Support, DevOps |
| [INCIDENT_RESPONSE_RUNBOOK.md](./INCIDENT_RESPONSE_RUNBOOK.md) | Incident response procedures | DevOps, SRE     |

### Archived Documentation

Historical reports and implementation logs have been moved to the [archive](./archive/) directory.

---

## 🗺️ Quick Navigation

### I want to...

| Goal                                 | Start Here                                                                                                 |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------------- |
| Understand Software-Org architecture | [SOFTWARE_ORG_PLUGIN_SPECIFICATION.md](./SOFTWARE_ORG_PLUGIN_SPECIFICATION.md)                             |
| Get started quickly                  | [GETTING_STARTED.md](./GETTING_STARTED.md)                                                                 |
| See all departments and agents       | [SOFTWARE_ORG_PLUGIN_SPECIFICATION.md#2-departments](./SOFTWARE_ORG_PLUGIN_SPECIFICATION.md#2-departments) |
| Use the REST API                     | [REST_API_REFERENCE.md](./REST_API_REFERENCE.md)                                                           |
| Deploy to production                 | [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md)                                                               |
| Troubleshoot issues                  | [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)                                                                 |
| See future roadmap                   | [FUTURE_FEATURES_ROADMAP.md](./FUTURE_FEATURES_ROADMAP.md)                                                 |

---

## 📁 Module Structure

```
products/software-org/
├── apps/
│   ├── backend/                # Node.js User API
│   └── web/                    # React Frontend
├── libs/java/
│   ├── departments/            # 10 Department implementations
│   ├── software-org/           # Core orchestration + Agents
│   └── domain-models/          # Domain value objects
├── contracts/proto/            # API contracts
└── docs/                       # Documentation (you are here)
```

---

## 🔗 Related Documentation

| Project                        | Document                                                                                            |
| ------------------------------ | --------------------------------------------------------------------------------------------------- |
| Virtual-Org (Parent Framework) | [VIRTUAL_ORG_MASTER_ARCHITECTURE.md](../../virtual-org/docs/VIRTUAL_ORG_MASTER_ARCHITECTURE.md)     |
| Configuration-Driven Setup     | [CONFIGURATION_DRIVEN_ARCHITECTURE.md](../../virtual-org/docs/CONFIGURATION_DRIVEN_ARCHITECTURE.md) |
| Extensibility Guide            | [EXTENSIBILITY_GUIDE.md](../../virtual-org/docs/EXTENSIBILITY_GUIDE.md)                             |

---

## 📊 Implementation Status

| Component                 | Status      |
| ------------------------- | ----------- |
| **Departments (10)**      | ✅ Complete |
| **Agents (11)**           | ✅ Complete |
| **Cross-Dept Flows (13)** | ✅ Complete |
| **AI Decision Engine**    | ✅ Complete |
| **Security Gates (8)**    | ✅ Complete |
| **REST API (70+)**        | ✅ Complete |
| **React UI**              | ✅ Complete |

---

_Document Version: 2.0.0_
