# Getting Started with Software-Org DevSecOps

**Version:** 1.0.0  
**Last Updated:** November 17, 2025  
**Status:** Production Ready

This guide will help you set up and run the Software-Org AI-first DevSecOps platform in minutes.

## 📋 Quick Summary

Software-Org is an **AI-driven DevSecOps platform** that orchestrates decisions across 10 departments (Engineering, QA, DevOps, Support, Product, Sales, Finance, HR, Compliance, Marketing) using event-driven architecture.

**Key Features:**
- 🤖 AI-powered decision making (Virtual-Org agents)
- 🔐 Security gates and policy enforcement
- 📊 Real-time event processing
- 🏛️ Multi-tenant architecture
- 📈 Comprehensive observability

---

## ⏱️ 5-Minute Quick Start

### 1. Prerequisites

```bash
# Check Java version (17+)
java -version

# Check Gradle (8.10+)
./gradlew --version

# Check pnpm (if frontend needed)
pnpm --version
```

### 2. Build the Project

```bash
# From repository root
cd /Users/samujjwal/Development/ghatana

# Build Software-Org module
./gradlew :products:software-org:build -x test

# Or build with tests
./gradlew :products:software-org:build
```

### 3. Run Tests

```bash
# Run all tests
./gradlew :products:software-org:test

# Run specific test class
./gradlew :products:software-org:libs:java:framework:test --tests "*DevSecOpsRestControllerTest"

# Run with coverage
./gradlew :products:software-org:jacocoTestReport
```

### 4. Start the Application

```bash
# Using Gradle
./gradlew :products:software-org:bootRun

# Or run directly
java -jar products/software-org/build/libs/software-org-1.0.0.jar
```

### 5. Test the API

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Create feature
curl -X POST http://localhost:8080/api/v1/devsecops/features \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Login with OAuth",
    "department": "engineering",
    "type": "feature",
    "metadata": {
      "story_points": "5",
      "complexity": "medium"
    }
  }'
```

---

## 🏗️ Project Structure

```
products/software-org/
├── docs/                          # Documentation
│   ├── GETTING_STARTED.md        # This file
│   ├── API_REFERENCE.md          # REST API endpoints
│   ├── DEPLOYMENT_GUIDE.md       # Production deployment
│   ├── INCIDENT_RESPONSE_RUNBOOK.md  # Runbooks
│   └── TROUBLESHOOTING.md        # Common issues
├── libs/
│   └── java/
│       ├── framework/            # Core framework (Phases 2-8)
│       │   ├── src/main/java/com/ghatana/softwareorg/framework/
│       │   │   ├── AIDecisionEngine.java          # Decision orchestration
│       │   │   ├── AIMetadataEnricher.java        # Event enrichment
│       │   │   ├── AllDepartmentPipelinesRegistrar.java  # Pipeline registration
│       │   │   ├── api/                           # REST APIs (Phase 7)
│       │   │   │   ├── DevSecOpsRestController.java
│       │   │   │   ├── DevSecOpsApiRequest.java
│       │   │   │   └── DevSecOpsApiResponse.java
│       │   │   ├── flows/                         # Cross-department flows (Phase 5)
│       │   │   │   ├── CrossDepartmentEventFlow.java
│       │   │   │   ├── EngineeringToQaFlow.java
│       │   │   │   └── [10 total flow implementations]
│       │   │   ├── security/                      # Security gates (Phase 8)
│       │   │   │   ├── SecurityGatesController.java
│       │   │   │   └── SecurityGatePolicies.java
│       │   │   └── event/                         # Event models
│       │   │       ├── AIDecisionContext.java
│       │   │       └── EnrichedEventMetadata.java
│       │   └── README.md                          # Framework documentation
│       ├── departments/          # 10 department implementations
│       │   ├── engineering/
│       │   ├── qa/
│       │   ├── devops/
│       │   ├── support/
│       │   ├── product/
│       │   ├── sales/
│       │   ├── finance/
│       │   ├── hr/
│       │   ├── compliance/
│       │   └── marketing/
│       └── software-org/         # Main module
├── contracts/                     # API contracts (Protobuf)
├── integration/                   # Integration tests
└── build.gradle.kts              # Build configuration
```

---

## 🔧 Development Setup

### IDE Configuration

**VS Code:**
```bash
# Install extensions
code --install-extension redhat.java
code --install-extension vscjava.vscode-gradle

# Open workspace
code /Users/samujjwal/Development/ghatana
```

**IntelliJ IDEA:**
1. Open: `File → Open → /Users/samujjwal/Development/ghatana`
2. Configure JDK: `Project Structure → Project → SDK → Java 17+`
3. Enable annotation processing: `Settings → Build → Compiler → Annotation Processors → Enable`

### Environment Variables

```bash
# Create .env file in project root
cat > .env << 'EOF'
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=software_org
DB_USER=postgres
DB_PASSWORD=postgres

# EventCloud (Kafka)
EVENTCLOUD_BOOTSTRAP_SERVERS=localhost:9092
EVENTCLOUD_TOPIC_PREFIX=software_org

# OpenTelemetry
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_EXPORTER_OTLP_PROTOCOL=grpc

# AI/ML Services
OPENAI_API_KEY=your_api_key_here
OPENAI_MODEL=gpt-4

# Application
APP_PORT=8080
APP_TENANT_ID=default
LOG_LEVEL=INFO
EOF

# Load environment
source .env
```

### Docker Compose (Local Development)

```bash
# Start services (database, Kafka, observability stack)
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

---

## 📚 Key Concepts

### 1. Event-Driven Architecture

All operations flow through events:

```
User Request → REST API → AIDecisionEngine → Event → AllDepartmentPipelinesRegistrar → Department
```

### 2. AI Decision Engine

The `AIDecisionEngine` makes policy-driven decisions:

```java
// Example: Feature Creation with AI Analysis
AIDecisionContext context = AIDecisionContext.builder()
    .confidence(0.85)
    .reasoning("Feature categorized as security-critical")
    .agentId("cto-agent")
    .departmentId("engineering")
    .build();

engine.executeDecision(
    context,
    "ghatana.contracts.software_org.departments.v1.FeatureRequestDecided",
    Map.of("feature_id", "feat-123", "priority", "HIGH"),
    "tenant-123"
);
```

### 3. Cross-Department Flows

Departments communicate through event flows:

```
Engineering Feature → QA Tests → DevOps Build → Support Alert
         ↓
    AI Analysis: test coverage required
```

### 4. Security Gates

All deployments pass through security policy checks:

```
Deployment Request
    ↓
SAST Scan (< 2 critical findings)
    ↓
DAST Scan (< 1 high vulnerability)
    ↓
Dependency Check (< 10 vulnerabilities)
    ↓
Secrets Scan (must be clean)
    ↓
Approved
```

### 5. Multi-Tenant Isolation

All data is isolated by tenant:

```
Tenant = "acme-corp"
Event tenant field = "acme-corp"
Database queries filter by tenant_id
State keys prefixed with tenant ID
```

---

## 🚀 Common Workflows

### Create a Feature Request

```bash
# 1. Post feature to API
curl -X POST http://localhost:8080/api/v1/devsecops/features \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-123" \
  -d '{
    "title": "Implement OAuth 2.0",
    "department": "engineering",
    "type": "feature",
    "metadata": {
      "story_points": "8",
      "complexity": "high",
      "security_related": "true"
    }
  }'

# 2. Response includes AI analysis
{
  "status": "APPROVED",
  "decision": {
    "action": "proceed_to_qa",
    "reasoning": "Security feature - requires 100% test coverage",
    "confidence": 0.92
  },
  "metadata": {
    "decision_id": "dec-uuid",
    "timestamp": "2025-11-17T10:30:00Z"
  }
}
```

### Deploy with Security Gates

```bash
# 1. Trigger deployment
curl -X POST http://localhost:8080/api/v1/devsecops/deployments \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-123" \
  -d '{
    "title": "Release v2.1.0",
    "department": "devops",
    "type": "deployment",
    "metadata": {
      "version": "2.1.0",
      "target_env": "production",
      "sast_critical": "0",
      "sast_high": "1",
      "dast_high": "0",
      "dependency_vulns": "5"
    }
  }'

# 2. Response shows gate results
{
  "status": "APPROVED",
  "decision": {
    "gates": {
      "sast": "PASS",
      "dast": "PASS",
      "dependency": "PASS",
      "secrets": "PASS"
    },
    "action": "proceed_with_deployment",
    "confidence": 0.88
  }
}
```

### Report and Triage Incident

```bash
# 1. Report incident
curl -X POST http://localhost:8080/api/v1/devsecops/incidents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-123" \
  -d '{
    "title": "Production Database Cluster Down",
    "department": "devops",
    "type": "incident",
    "metadata": {
      "affected_services": "api,web,mobile",
      "duration_seconds": "300",
      "impact": "complete_outage"
    }
  }'

# 2. Response includes AI triage
{
  "status": "ESCALATED",
  "decision": {
    "severity": "P0",
    "sla_minutes": 15,
    "action": "immediate_escalation",
    "reasoning": "Complete outage across all services"
  }
}
```

---

## 🧪 Testing

### Run Test Suite

```bash
# All tests
./gradlew :products:software-org:test

# Specific module
./gradlew :products:software-org:libs:java:framework:test

# Specific test class
./gradlew :products:software-org:libs:java:framework:test \
  --tests "com.ghatana.softwareorg.framework.api.DevSecOpsRestControllerTest"

# Specific test method
./gradlew :products:software-org:libs:java:framework:test \
  --tests "com.ghatana.softwareorg.framework.api.DevSecOpsRestControllerTest.shouldCreateFeatureWithAiAnalysis"
```

### Generate Coverage Report

```bash
# Generate Jacoco report
./gradlew :products:software-org:jacocoTestReport

# View HTML report
open build/reports/jacoco/test/html/index.html
```

### Integration Testing

```bash
# Run integration tests (requires Docker)
./gradlew :products:software-org:integrationTest

# Skip integration tests
./gradlew :products:software-org:test -x integrationTest
```

---

## 📊 Monitoring

### Health Checks

```bash
# Application health
curl http://localhost:8080/api/v1/health

# Expected response
{
  "status": "UP",
  "components": {
    "database": "UP",
    "eventcloud": "UP"
  }
}
```

### Metrics Endpoint

```bash
# Prometheus metrics
curl http://localhost:8080/metrics

# Filter specific metric
curl http://localhost:8080/metrics | grep cross_department_flow
```

### Logs

```bash
# View application logs
tail -f logs/software-org.log

# Filter by department
tail -f logs/software-org.log | grep "department=engineering"

# Filter by decision
tail -f logs/software-org.log | grep "AIDecisionEngine"
```

---

## 🐛 Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues and solutions.

**Quick Links:**
- [API Reference](API_REFERENCE.md) - Endpoint specifications
- [Deployment Guide](DEPLOYMENT_GUIDE.md) - Production setup
- [Incident Response](INCIDENT_RESPONSE_RUNBOOK.md) - Operational procedures
- [Framework README](../libs/java/framework/README.md) - Architecture details

---

## 📞 Support

### Getting Help

1. **Check the docs**: [API_REFERENCE.md](API_REFERENCE.md), [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
2. **Review examples**: See `integration/` directory for sample workflows
3. **Check logs**: Application logs in `logs/software-org.log`
4. **Run tests**: Verify setup with `./gradlew :products:software-org:test`

### Key Contacts

- **Architecture Questions**: Review `docs/PHASE_6_VIRTUAL_ORG_ALIGNMENT_AUDIT.md`
- **API Issues**: See `docs/API_REFERENCE.md`
- **Deployment Issues**: See `docs/DEPLOYMENT_GUIDE.md`
- **Incidents**: See `docs/INCIDENT_RESPONSE_RUNBOOK.md`

---

## 📖 Next Steps

1. ✅ **You're here**: Getting Started (this guide)
2. 📖 **Read**: [API_REFERENCE.md](API_REFERENCE.md) for endpoint details
3. 🚀 **Deploy**: Follow [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
4. 🔧 **Operate**: Use [INCIDENT_RESPONSE_RUNBOOK.md](INCIDENT_RESPONSE_RUNBOOK.md)
5. 🏗️ **Explore**: Review framework architecture in [Framework README](../libs/java/framework/README.md)

---

**Questions?** Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or review the relevant documentation above.
