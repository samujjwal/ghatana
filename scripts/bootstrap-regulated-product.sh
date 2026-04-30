#!/usr/bin/env bash
#
# Bootstrap a new regulated product in Ghatana.
#
# Usage:
#   ./bootstrap-regulated-product.sh \
#     --name "My Healthcare App" \
#     --id my-health-app \
#     --compliance HIPAA,GDPR \
#     --plugins consent,audit-trail,fraud-detection
#
# This script:
#   - Creates product folder structure
#   - Generates domain pack manifest
#   - Creates API endpoint stubs
#   - Sets up E2E tests
#   - Configures CI/CD gates
#   - Adds incident runbooks
#

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
PRODUCT_NAME=""
PRODUCT_ID=""
COMPLIANCE_FRAMEWORKS=""
REQUIRED_PLUGINS=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --name)
      PRODUCT_NAME="$2"
      shift 2
      ;;
    --id)
      PRODUCT_ID="$2"
      shift 2
      ;;
    --compliance)
      COMPLIANCE_FRAMEWORKS="$2"
      shift 2
      ;;
    --plugins)
      REQUIRED_PLUGINS="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Validate inputs
if [ -z "$PRODUCT_NAME" ] || [ -z "$PRODUCT_ID" ]; then
  echo -e "${RED}Error: --name and --id are required${NC}"
  echo "Usage: $0 --name 'Product Name' --id product-id --compliance HIPAA --plugins consent,audit-trail"
  exit 1
fi

echo -e "${BLUE}Creating regulated product: ${PRODUCT_NAME} (${PRODUCT_ID})${NC}"

# Create directory structure
PRODUCT_DIR="products/${PRODUCT_ID}"
mkdir -p "${PRODUCT_DIR}"/{src/main/java/com/ghatana/product,src/test/java,config,docs,api,.github}

echo -e "${GREEN}✓ Created directory structure${NC}"

# Create build.gradle.kts
cat > "${PRODUCT_DIR}/build.gradle.kts" << 'EOF'
plugins {
  id("java-application")
  id("application")
}

group = "com.ghatana.product.${PRODUCT_ID}"
version = "0.1.0"

application {
  mainClass = "com.ghatana.product.${PRODUCT_ID}.Main"
}

dependencies {
  implementation(project(":platform:java:core"))
  implementation(project(":platform-kernel:kernel-core"))
  
  // Plugins (adjust based on --plugins)
  implementation(project(":platform-plugins:plugin-audit-trail"))
  implementation(project(":platform-plugins:plugin-consent"))
  
  // Testing
  testImplementation(project(":platform:java:testing"))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.mockito.core)
}

tasks.test {
  useJUnitPlatform()
}
EOF

echo -e "${GREEN}✓ Created build.gradle.kts${NC}"

# Create domain pack manifest
cat > "${PRODUCT_DIR}/domain-pack-manifest.yaml" << EOF
name: ${PRODUCT_ID}
version: 0.1.0
label: "${PRODUCT_NAME}"
description: "${PRODUCT_NAME} - Regulated product using Ghatana platform"

compliance:
  frameworks: [${COMPLIANCE_FRAMEWORKS:- }]
  owner: product-team@ghatana.io
  auditLog: true
  encryption: true
  tenantIsolation: true

plugins:
  required: [${REQUIRED_PLUGINS:-audit-trail,consent}]

capabilities:
  - name: patient-management
    description: Patient record management
    scope: TENANT
    requires: [consent]

  - name: operations
    description: Operational workflows
    scope: TENANT
    requires: [audit-trail]

databases:
  - name: patients
    schema: products/${PRODUCT_ID}/config/schema-patients.sql
    migration: flyway
  - name: operations
    schema: products/${PRODUCT_ID}/config/schema-operations.sql
    migration: flyway

healthChecks:
  - name: database-connectivity
    endpoint: /health/db
    timeout: 5000
  - name: plugin-availability
    endpoint: /health/plugins
    timeout: 5000

monitoring:
  dashboards:
    - name: operations
      grafana: /dashboards/grafana/${PRODUCT_ID}-operations.json
  
  metrics:
    - ghatana.product.${PRODUCT_ID}.requests_total
    - ghatana.product.${PRODUCT_ID}.request_latency_ms
    - ghatana.product.${PRODUCT_ID}.errors_total

  runbooks:
    - name: incident-response
      path: docs/runbooks/
EOF

echo -e "${GREEN}✓ Created domain pack manifest${NC}"

# Create database schema (patients)
cat > "${PRODUCT_DIR}/config/schema-patients.sql" << 'EOF'
-- Patients table
CREATE TABLE IF NOT EXISTS patients (
  id VARCHAR(255) PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  date_of_birth DATE,
  email VARCHAR(255),
  phone VARCHAR(20),
  address TEXT,
  
  -- PHI redaction tracking
  pii_classification VARCHAR(50), -- PHI, PII, GENERAL
  
  -- Audit trail
  created_by VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(255),
  updated_at TIMESTAMP,
  
  -- Correlation ID for traceability
  correlation_id VARCHAR(255),
  
  INDEX idx_tenant(tenant_id),
  INDEX idx_email(email),
  INDEX idx_created(created_at)
);

-- Consent records
CREATE TABLE IF NOT EXISTS consents (
  id VARCHAR(255) PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  patient_id VARCHAR(255) NOT NULL,
  provider_id VARCHAR(255) NOT NULL,
  scope VARCHAR(255), -- medical-records-read, etc.
  status VARCHAR(50), -- PENDING, APPROVED, REVOKED
  requested_at TIMESTAMP,
  approved_at TIMESTAMP,
  revoked_at TIMESTAMP,
  expires_at TIMESTAMP,
  
  FOREIGN KEY (patient_id) REFERENCES patients(id),
  INDEX idx_tenant(tenant_id),
  INDEX idx_patient(patient_id),
  INDEX idx_status(status)
);
EOF

echo -e "${GREEN}✓ Created database schemas${NC}"

# Create main application class
cat > "${PRODUCT_DIR}/src/main/java/com/ghatana/product/${PRODUCT_ID}/Main.java" << 'EOF'
package com.ghatana.product.${PRODUCT_ID};

import com.ghatana.kernel.core.KernelBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Entry point for ${PRODUCT_NAME}
 * @doc.layer product
 * @doc.pattern Application
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting ${PRODUCT_NAME} (${PRODUCT_ID})");

        // Initialize kernel with plugins
        var kernel = KernelBootstrap.builder()
            .productId("${PRODUCT_ID}")
            .port(8080)
            .build();

        kernel.start();

        // Setup graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down ${PRODUCT_NAME}");
            kernel.stop();
        }));
    }
}
EOF

echo -e "${GREEN}✓ Created main application class${NC}"

# Create API endpoint stub
cat > "${PRODUCT_DIR}/src/main/java/com/ghatana/product/${PRODUCT_ID}/api/OperationsController.java" << 'EOF'
package com.ghatana.product.${PRODUCT_ID}.api;

import com.ghatana.kernel.core.http.HttpController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose REST API endpoints for product operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public class OperationsController implements HttpController {
    private static final Logger logger = LoggerFactory.getLogger(OperationsController.class);

    @Override
    public String getBasePath() {
        return "/api/v1/${PRODUCT_ID}";
    }

    // TODO: Implement API endpoints
    // GET  /operations          - List operations
    // POST /operations          - Create operation
    // GET  /operations/{id}     - Get operation
    // PUT  /operations/{id}     - Update operation
}
EOF

echo -e "${GREEN}✓ Created API controller stub${NC}"

# Create integration test
cat > "${PRODUCT_DIR}/src/test/java/com/ghatana/product/${PRODUCT_ID}/api/OperationsControllerIT.java" << 'EOF'
package com.ghatana.product.${PRODUCT_ID}.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OperationsController.
 * 
 * TODO: Implement tests
 * - [ ] Create operation
 * - [ ] List operations with pagination
 * - [ ] Retrieve operation details
 * - [ ] Update operation
 * - [ ] Verify tenant isolation
 * - [ ] Verify audit trail
 */
@DisplayName("OperationsController Integration Tests")
class OperationsControllerIT {

    @Test
    void shouldBeImplemented() {
        fail("TODO: Implement integration tests");
    }
}
EOF

echo -e "${GREEN}✓ Created integration test stub${NC}"

# Create product runbooks
mkdir -p "${PRODUCT_DIR}/docs/runbooks"
cat > "${PRODUCT_DIR}/docs/runbooks/incident-response.md" << 'EOF'
# ${PRODUCT_NAME} Incident Response

## Overview
Operational procedures for common incidents in ${PRODUCT_NAME}.

## Compliance Frameworks
- ${COMPLIANCE_FRAMEWORKS}

## Runbooks

### 1. Data Breach
**Detection:** Unexpected data access, permission errors, audit log anomalies

**Immediate Actions:**
1. Isolate affected systems (disable read access)
2. Preserve evidence (logs, audit trail)
3. Notify security team

**Investigation:**
1. Query audit trail for unauthorized access
2. Identify affected records
3. Determine root cause

**Remediation:**
1. Restore from backup if needed
2. Patch vulnerability
3. Reset passwords/credentials

**Communication:**
1. Notify affected users
2. Report to regulators (within required timeframe)
3. Post-incident review

---

### 2. Plugin Failure
**Detection:** Plugin not responding, /health endpoint returns error

**Immediate Actions:**
1. Check plugin logs
2. Verify database connectivity
3. Review recent deployments

**Investigation:**
1. Check correlation IDs in logs
2. Look for error patterns
3. Review plugin metrics (latency, errors)

**Remediation:**
1. Restart plugin if safe
2. Rollback recent deployment
3. Increase log verbosity for debugging

---

### 3. Performance Degradation
**Detection:** API latency > 5s P99, error rate > 1%

**Immediate Actions:**
1. Check database connection pool
2. Monitor CPU/memory usage
3. Review recent queries

**Investigation:**
1. Identify slow operations (Jaeger traces)
2. Check database slow query log
3. Review recent code changes

**Remediation:**
1. Add missing indexes
2. Optimize queries
3. Increase resources if needed

EOF

echo -e "${GREEN}✓ Created incident response runbooks${NC}"

# Create CI/CD configuration
cat > "${PRODUCT_DIR}/.github/workflows/validate.yml" << 'EOF'
name: Validate ${PRODUCT_NAME}

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'products/${PRODUCT_ID}/**'
  pull_request:
    branches: [ main ]
    paths:
      - 'products/${PRODUCT_ID}/**'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build
        run: ./gradlew :products:${PRODUCT_ID}:build
      
      - name: Test
        run: ./gradlew :products:${PRODUCT_ID}:test
      
      - name: Coverage
        run: ./gradlew :products:${PRODUCT_ID}:jacocoTestReport
      
      - name: Security Scan
        run: ./gradlew :products:${PRODUCT_ID}:spotbugsMain :products:${PRODUCT_ID}:pmdMain
EOF

echo -e "${GREEN}✓ Created CI/CD configuration${NC}"

# Create README
cat > "${PRODUCT_DIR}/README.md" << 'EOF'
# ${PRODUCT_NAME}

**Product ID:** ${PRODUCT_ID}  
**Version:** 0.1.0  
**Compliance:** ${COMPLIANCE_FRAMEWORKS}  

## Overview

${PRODUCT_NAME} is a regulated product built on the Ghatana platform. It provides [brief description of product capabilities].

## Architecture

```
├── src/main/java/com/ghatana/product/${PRODUCT_ID}/
│   ├── Main.java                 - Application entry point
│   └── api/
│       └── OperationsController  - REST API endpoints
├── src/test/java/
│   └── api/OperationsControllerIT.java - Integration tests
├── config/
│   ├── schema-patients.sql       - Database schema
│   └── schema-operations.sql
└── domain-pack-manifest.yaml     - Product metadata
```

## Building

```bash
./gradlew :products:${PRODUCT_ID}:build
```

## Testing

```bash
# Unit tests
./gradlew :products:${PRODUCT_ID}:test

# Integration tests
./gradlew :products:${PRODUCT_ID}:test --include-unmodified

# Coverage report
./gradlew :products:${PRODUCT_ID}:jacocoTestReport
```

## Running

```bash
./gradlew :products:${PRODUCT_ID}:run
```

The application will start on http://localhost:8080.

## Configuration

See `domain-pack-manifest.yaml` for:
- Required plugins
- Database configurations
- Compliance settings
- Health checks
- Monitoring dashboards

## Plugins

This product uses the following plugins:

- **audit-trail** - Records all operations for compliance
- **consent** - Manages user consent

Add more plugins by updating `domain-pack-manifest.yaml`.

## Monitoring

**Metrics:** http://localhost:9090/metrics

**Grafana:** http://localhost:3000/dashboards/d/${PRODUCT_ID}

**Jaeger Traces:** http://localhost:16686/search

## Incident Response

See [docs/runbooks/incident-response.md](docs/runbooks/incident-response.md) for operational procedures.

## Compliance

${PRODUCT_NAME} is designed to meet compliance requirements:

- **${COMPLIANCE_FRAMEWORKS}**

See [docs/runbooks/incident-response.md](docs/runbooks/incident-response.md) for compliance procedures.

## Development

New developers should read:
1. [Platform Architecture](../../docs/MONOREPO_ARCHITECTURE.md)
2. [Plugin Author Guide](../../docs/PLUGIN_AUTHOR_ONBOARDING_GUIDE.md)
3. [Testing Standards](../../docs/TESTING.md)
4. [OpenTelemetry Conventions](../../docs/OTEL_OBSERVABILITY_CONVENTIONS.md)

## Support

For questions or issues:
- Check [docs/runbooks/incident-response.md](docs/runbooks/incident-response.md)
- Review [OpenTelemetry Conventions](../../docs/OTEL_OBSERVABILITY_CONVENTIONS.md)
- Contact product-team@ghatana.io
EOF

echo -e "${GREEN}✓ Created README${NC}"

# Update root settings.gradle.kts to include new product
if ! grep -q "products:${PRODUCT_ID}" settings.gradle.kts; then
  echo "include(\"products:${PRODUCT_ID}\")" >> settings.gradle.kts
  echo -e "${GREEN}✓ Added product to root settings.gradle.kts${NC}"
fi

# Print summary
echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}✓ Product bootstrap complete!${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Product created at: ${PRODUCT_DIR}"
echo ""
echo "Next steps:"
echo "  1. cd ${PRODUCT_DIR}"
echo "  2. Update src/main/java with domain logic"
echo "  3. Update config/schema-*.sql with database schema"
echo "  4. Implement API endpoints in api/OperationsController.java"
echo "  5. Add integration tests in src/test/java"
echo "  6. ./gradlew :${PRODUCT_DIR}:build"
echo "  7. git add -A && git commit -m 'Bootstrap product: ${PRODUCT_ID}'"
echo ""
echo "Documentation:"
echo "  - Manifest: ${PRODUCT_DIR}/domain-pack-manifest.yaml"
echo "  - README: ${PRODUCT_DIR}/README.md"
echo "  - Runbooks: ${PRODUCT_DIR}/docs/runbooks/"
echo ""
EOF

chmod +x "${PRODUCT_DIR}/bootstrap-product.sh"

echo -e "${GREEN}✓ Created bootstrap script${NC}"

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}✓ Regulated product template complete!${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Bootstrap complete. You can now:"
echo ""
echo "  cd products/${PRODUCT_ID}"
echo "  ./gradlew build"
echo "  ./gradlew run"
echo ""
