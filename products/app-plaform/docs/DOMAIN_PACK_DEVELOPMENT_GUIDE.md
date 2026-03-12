# Domain Pack Development Guide

**Version**: 1.0.0  
**Date**: 2026-03-11  
**Purpose**: Guide for developing domain packs for the Siddhanta Multi-Domain Operating System

---

## Overview

Domain packs are pluggable modules that provide industry-specific functionality while leveraging the generic Siddhanta kernel. This guide covers the structure, development process, and best practices for creating domain packs.

## Domain Pack Architecture

### Core Components

A domain pack consists of:

1. **Domain Manifest** (`pack.yaml`) - Metadata and dependencies
2. **Data Models** - Domain-specific entities and schemas
3. **Business Rules** - Domain logic in OPA/Rego format
4. **Workflows** - Business process definitions
5. **Integrations** - External system adapters
6. **User Interface** - Domain-specific UI components
7. **Configuration** - Domain-specific configuration templates

### Domain Pack Structure

```
domain-packs/
├── {domain-name}/
│   ├── pack.yaml                 # Domain manifest
│   ├── README.md                 # Domain documentation
│   ├── schemas/                  # Data models and schemas
│   │   ├── entities.json
│   │   ├── events.json
│   │   └── apis.json
│   ├── rules/                    # Business rules (OPA/Rego)
│   │   ├── validation.rego
│   │   ├── calculation.rego
│   │   └── compliance.rego
│   ├── workflows/                # Business process definitions
│   │   ├── core-workflows.yaml
│   │   └── approval-workflows.yaml
│   ├── integrations/             # External system adapters
│   │   ├── adapters.yaml
│   │   └── protocols.yaml
│   ├── ui/                       # User interface components
│   │   ├── dashboards.json
│   │   ├── forms.json
│   │   └── reports.json
│   ├── config/                   # Configuration templates
│   │   ├── domain-config.json
│   │   └── jurisdiction-overrides.json
│   └── tests/                    # Domain-specific tests
│       ├── unit/
│       ├── integration/
│       └── e2e/
```

## Domain Manifest

### Required Fields

```yaml
domainPack:
  id: "banking-v1.0"
  name: "Banking Domain"
  version: "1.0.0"
  description: "Complete banking domain pack for retail and corporate banking"
  
  # Domain classification
  domainType: "financial-services"
  industry: "banking"
  subdomains:
    - "retail-banking"
    - "corporate-banking"
    - "payments"
    - "treasury"
  
  # Platform compatibility
  platformMinVersion: "2.0.0"
  platformMaxVersion: "3.0.0"
  
  # Required kernel modules
  requiredKernels:
    - "K-01"  # IAM
    - "K-02"  # Config
    - "K-03"  # Rules
    - "K-04"  # Plugin
    - "K-05"  # Events
    - "K-07"  # Audit
    - "K-15"  # Calendar
    - "K-16"  # Ledger
  
  # Domain capabilities
  capabilities:
    - "ACCOUNT_MANAGEMENT"
    - "PAYMENT_PROCESSING"
    - "LOAN_ORIGINATION"
    - "RISK_ASSESSMENT"
    - "COMPLIANCE_REPORTING"
  
  # Data models
  dataModels:
    - file: "schemas/entities.json"
    - file: "schemas/events.json"
    - file: "schemas/apis.json"
  
  # Business rules
  businessRules:
    - file: "rules/account-rules.rego"
    - file: "rules/payment-rules.rego"
    - file: "rules/compliance-rules.rego"
  
  # Workflows
  workflows:
    - file: "workflows/account-opening.yaml"
    - file: "workflows/loan-application.yaml"
    - file: "workflows/payment-processing.yaml"
  
  # External integrations
  integrations:
    - file: "integrations/bank-integrations.yaml"
    - file: "integrations/payment-networks.yaml"
  
  # User interface
  userInterface:
    - file: "ui/banker-dashboard.json"
    - file: "ui/customer-portal.json"
    - file: "ui/compliance-ui.json"
  
  # Configuration
  configuration:
    - file: "config/domain-config.json"
    - file: "config/jurisdiction-overrides.json"
  
  # Testing
  testing:
    unitTests: "tests/unit/"
    integrationTests: "tests/integration/"
    e2eTests: "tests/e2e/"
  
  # Dependencies on other domain packs
  dependencies:
    - domain: "common-financial-v1.0"
      version: ">=1.0.0"
  
  # Author and support
  author:
    name: "Siddhanta Team"
    email: "team@siddhanta.dev"
    organization: "Siddhanta Foundation"
  
  # Licensing
  license: "MIT"
  
  # Certification status
  certification:
    status: "certified"
    certifiedBy: "Siddhanta Foundation"
    certifiedAt: "2026-03-11"
    certificateId: "DP-CERT-2026-001"
```

## Data Models

### Entity Schema (`schemas/entities.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Banking Domain Entities",
  "description": "Core entities for the banking domain",
  "definitions": {
    "Account": {
      "type": "object",
      "properties": {
        "accountId": {
          "type": "string",
          "pattern": "ACC-[0-9]{10}"
        },
        "accountNumber": {
          "type": "string",
          "pattern": "[0-9]{16}"
        },
        "accountType": {
          "type": "string",
          "enum": ["SAVINGS", "CURRENT", "FIXED_DEPOSIT", "LOAN"]
        },
        "customerId": {
          "type": "string",
          "pattern": "CUST-[0-9]{8}"
        },
        "currency": {
          "type": "string",
          "pattern": "^[A-Z]{3}$"
        },
        "balance": {
          "type": "number",
          "minimum": 0
        },
        "status": {
          "type": "string",
          "enum": ["ACTIVE", "INACTIVE", "FROZEN", "CLOSED"]
        },
        "openedAt": {
          "type": "string",
          "format": "date-time"
        },
        "lastActivityAt": {
          "type": "string",
          "format": "date-time"
        }
      },
      "required": ["accountId", "accountNumber", "accountType", "customerId", "currency", "balance", "status", "openedAt"]
    },
    "Transaction": {
      "type": "object",
      "properties": {
        "transactionId": {
          "type": "string",
          "pattern": "TXN-[0-9]{12}"
        },
        "accountId": {
          "type": "string",
          "pattern": "ACC-[0-9]{10}"
        },
        "transactionType": {
          "type": "string",
          "enum": ["DEBIT", "CREDIT", "TRANSFER"]
        },
        "amount": {
          "type": "number",
          "minimum": 0
        },
        "currency": {
          "type": "string",
          "pattern": "^[A-Z]{3}$"
        },
        "description": {
          "type": "string",
          "maxLength": 500
        },
        "referenceId": {
          "type": "string"
        },
        "status": {
          "type": "string",
          "enum": ["PENDING", "COMPLETED", "FAILED", "REVERSED"]
        },
        "createdAt": {
          "type": "string",
          "format": "date-time"
        },
        "completedAt": {
          "type": "string",
          "format": "date-time"
        }
      },
      "required": ["transactionId", "accountId", "transactionType", "amount", "currency", "status", "createdAt"]
    },
    "Customer": {
      "type": "object",
      "properties": {
        "customerId": {
          "type": "string",
          "pattern": "CUST-[0-9]{8}"
        },
        "customerType": {
          "type": "string",
          "enum": ["INDIVIDUAL", "CORPORATE"]
        },
        "name": {
          "type": "string",
          "maxLength": 100
        },
        "email": {
          "type": "string",
          "format": "email"
        },
        "phone": {
          "type": "string",
          "pattern": "^[0-9]{10}$"
        },
        "address": {
          "$ref": "#/definitions/Address"
        },
        "kycStatus": {
          "type": "string",
          "enum": ["PENDING", "VERIFIED", "REJECTED"]
        },
        "riskCategory": {
          "type": "string",
          "enum": ["LOW", "MEDIUM", "HIGH"]
        },
        "registeredAt": {
          "type": "string",
          "format": "date-time"
        }
      },
      "required": ["customerId", "customerType", "name", "email", "phone", "kycStatus", "riskCategory", "registeredAt"]
    },
    "Address": {
      "type": "object",
      "properties": {
        "street": {
          "type": "string",
          "maxLength": 200
        },
        "city": {
          "type": "string",
          "maxLength": 50
        },
        "state": {
          "type": "string",
          "maxLength": 50
        },
        "postalCode": {
          "type": "string",
          "pattern": "^[0-9]{6}$"
        },
        "country": {
          "type": "string",
          "pattern": "^[A-Z]{2}$"
        }
      },
      "required": ["street", "city", "state", "postalCode", "country"]
    }
  }
}
```

### Event Schema (`schemas/events.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Banking Domain Events",
  "description": "Event schemas for the banking domain",
  "definitions": {
    "AccountOpenedEvent": {
      "type": "object",
      "properties": {
        "eventId": {
          "type": "string",
          "pattern": "EVT-[0-9]{12}"
        },
        "eventType": {
          "type": "string",
          "const": "AccountOpened"
        },
        "eventVersion": {
          "type": "string",
          "const": "1.0.0"
        },
        "timestamp": {
          "type": "string",
          "format": "date-time"
        },
        "domain": {
          "type": "string",
          "const": "banking"
        },
        "data": {
          "$ref": "#/definitions/Account"
        },
        "metadata": {
          "type": "object",
          "properties": {
            "correlationId": {
              "type": "string"
            },
            "causationId": {
              "type": "string"
            },
            "userId": {
              "type": "string"
            },
            "tenantId": {
              "type": "string"
            }
          }
        }
      },
      "required": ["eventId", "eventType", "eventVersion", "timestamp", "domain", "data"]
    },
    "TransactionProcessedEvent": {
      "type": "object",
      "properties": {
        "eventId": {
          "type": "string",
          "pattern": "EVT-[0-9]{12}"
        },
        "eventType": {
          "type": "string",
          "const": "TransactionProcessed"
        },
        "eventVersion": {
          "type": "string",
          "const": "1.0.0"
        },
        "timestamp": {
          "type": "string",
          "format": "date-time"
        },
        "domain": {
          "type": "string",
          "const": "banking"
        },
        "data": {
          "$ref": "#/definitions/Transaction"
        },
        "metadata": {
          "type": "object",
          "properties": {
            "correlationId": {
              "type": "string"
            },
            "causationId": {
              "type": "string"
            },
            "userId": {
              "type": "string"
            },
            "tenantId": {
              "type": "string"
            }
          }
        }
      },
      "required": ["eventId", "eventType", "eventVersion", "timestamp", "domain", "data"]
    }
  }
}
```

## Business Rules

### Account Validation Rules (`rules/account-rules.rego`)

```rego
package banking.account

# Default decision
default allow = false

# Allow account creation if all validations pass
allow {
    input.request.action == "create_account"
    validate_account_type(input.request.accountType)
    validate_customer_eligibility(input.request.customerId)
    validate_initial_deposit(input.request.initialDeposit)
    validate_kyc_status(input.request.customerId)
}

# Validate account type is supported
validate_account_type(accountType) {
    accountType == "SAVINGS"
    accountType == "CURRENT"
    accountType == "FIXED_DEPOSIT"
    accountType == "LOAN"
}

# Validate customer is eligible for account
validate_customer_eligibility(customerId) {
    customer := data.customers[customerId]
    customer.kycStatus == "VERIFIED"
    customer.riskCategory != "HIGH"
}

# Validate minimum initial deposit
validate_initial_deposit(amount) {
    amount >= 1000  # Minimum $1000 initial deposit
}

# Validate KYC status
validate_kyc_status(customerId) {
    customer := data.customers[customerId]
    customer.kycStatus == "VERIFIED"
}

# Account balance rules
allow_transaction {
    input.request.action == "process_transaction"
    validate_sufficient_balance(input.request.accountId, input.request.amount)
    validate_account_status(input.request.accountId)
    validate_transaction_limits(input.request)
}

validate_sufficient_balance(accountId, amount) {
    account := data.accounts[accountId]
    account.balance >= amount
}

validate_account_status(accountId) {
    account := data.accounts[accountId]
    account.status == "ACTIVE"
}

validate_transaction_limits(request) {
    request.amount <= data.limits.dailyTransactionLimit
    request.amount <= data.limits.perTransactionLimit
}
```

### Compliance Rules (`rules/compliance-rules.rego`)

```rego
package banking.compliance

# AML monitoring rules
default aml_flag = false

aml_flag {
    input.transaction.amount > 10000
    not is_regular_customer(input.transaction.customerId)
}

aml_flag {
    input.transaction.amount > 50000
}

is_regular_customer(customerId) {
    customer := data.customers[customerId]
    customer.accountAge > 180  # 6 months
    customer.transactionCount > 50
}

# Transaction monitoring
requires_review {
    input.transaction.amount > 25000
}

requires_review {
    suspicious_pattern(input.transaction)
}

suspicious_pattern(transaction) {
    transaction.amount == 9999
}

suspicious_pattern(transaction) {
    transaction.description == "TEST"
}

# Reporting rules
generate_str_report {
    input.transaction.amount >= 10000
}

generate_ctr_report {
    input.transaction.amount >= 10000
    is_cash_transaction(input.transaction)
}

is_cash_transaction(transaction) {
    transaction.transactionType == "CASH_DEPOSIT"
    transaction.transactionType == "CASH_WITHDRAWAL"
}
```

## Workflows

### Account Opening Workflow (`workflows/account-opening.yaml`)

```yaml
workflow:
  id: "account-opening"
  name: "Account Opening Process"
  version: "1.0.0"
  description: "Complete account opening workflow with KYC and compliance checks"
  
  states:
    - id: "initiated"
      name: "Initiated"
      type: "start"
      
    - id: "kyc_verification"
      name: "KYC Verification"
      type: "task"
      task:
        type: "human"
        assignee: "kyc_team"
        form: "kyc-verification-form"
        timeout: "P2D"
        
    - id: "compliance_check"
      name: "Compliance Check"
      type: "task"
      task:
        type: "automated"
        service: "banking-compliance-service"
        action: "validate_account_opening"
        
    - id: "risk_assessment"
      name: "Risk Assessment"
      type: "task"
      task:
        type: "automated"
        service: "risk-assessment-service"
        action: "assess_customer_risk"
        
    - id: "account_creation"
      name: "Account Creation"
      type: "task"
      task:
        type: "automated"
        service: "account-service"
        action: "create_account"
        
    - id: "welcome_notification"
      name: "Welcome Notification"
      type: "task"
      task:
        type: "automated"
        service: "notification-service"
        action: "send_welcome_email"
        
    - id: "completed"
      name: "Completed"
      type: "end"
      
    - id: "rejected"
      name: "Rejected"
      type: "end"
      
  transitions:
    - from: "initiated"
      to: "kyc_verification"
      condition: "always"
      
    - from: "kyc_verification"
      to: "compliance_check"
      condition: "kyc_approved"
      
    - from: "kyc_verification"
      to: "rejected"
      condition: "kyc_rejected"
      
    - from: "compliance_check"
      to: "risk_assessment"
      condition: "compliance_passed"
      
    - from: "compliance_check"
      to: "rejected"
      condition: "compliance_failed"
      
    - from: "risk_assessment"
      to: "account_creation"
      condition: "risk_acceptable"
      
    - from: "risk_assessment"
      to: "rejected"
      condition: "risk_too_high"
      
    - from: "account_creation"
      to: "welcome_notification"
      condition: "account_created"
      
    - from: "account_creation"
      to: "rejected"
      condition: "account_creation_failed"
      
    - from: "welcome_notification"
      to: "completed"
      condition: "notification_sent"
      
  variables:
    - name: "customer_id"
      type: "string"
      required: true
      
    - name: "account_type"
      type: "string"
      required: true
      enum: ["SAVINGS", "CURRENT", "FIXED_DEPOSIT"]
      
    - name: "initial_deposit"
      type: "number"
      required: true
      minimum: 1000
      
    - name: "kyc_status"
      type: "string"
      enum: ["PENDING", "VERIFIED", "REJECTED"]
      
    - name: "compliance_status"
      type: "string"
      enum: ["PENDING", "PASSED", "FAILED"]
      
    - name: "risk_score"
      type: "number"
      minimum: 0
      maximum: 100
      
    - name: "account_number"
      type: "string"
      
  permissions:
    - role: "banker"
      actions: ["initiate", "view", "assign"]
      
    - role: "kyc_officer"
      actions: ["approve", "reject", "view"]
      
    - role: "compliance_officer"
      actions: ["view", "override"]
      
  notifications:
    - event: "workflow_started"
      recipients: ["customer"]
      template: "account-opening-started"
      
    - event: "workflow_completed"
      recipients: ["customer"]
      template: "account-opening-completed"
      
    - event: "workflow_rejected"
      recipients: ["customer", "banker"]
      template: "account-opening-rejected"
```

## Integrations

### External System Adapters (`integrations/bank-integrations.yaml`)

```yaml
adapters:
  - id: "core-banking-system"
    name: "Core Banking System"
    type: "rest"
    description: "Integration with legacy core banking system"
    
    endpoint:
      baseUrl: "${CORE_BANKING_URL}"
      authentication:
        type: "oauth2"
        clientId: "${CORE_BANKING_CLIENT_ID}"
        clientSecret: "${CORE_BANKING_CLIENT_SECRET}"
        
    operations:
      - name: "get_customer"
        method: "GET"
        path: "/api/customers/{customerId}"
        parameters:
          - name: "customerId"
            type: "path"
            required: true
        response:
          schema: "#/definitions/Customer"
          
      - name: "create_account"
        method: "POST"
        path: "/api/accounts"
        requestBody:
          schema: "#/definitions/AccountRequest"
        response:
          schema: "#/definitions/Account"
          
      - name: "get_balance"
        method: "GET"
        path: "/api/accounts/{accountId}/balance"
        parameters:
          - name: "accountId"
            type: "path"
            required: true
        response:
          schema: "#/definitions/Balance"
          
    errorHandling:
      retryPolicy:
        maxAttempts: 3
        backoff: "exponential"
        
      circuitBreaker:
        failureThreshold: 5
        timeout: "PT30S"
        
  - id: "payment-network"
    name: "Payment Network"
    type: "message"
    description: "Integration with payment network for transfers"
    
    endpoint:
      type: "kafka"
      topic: "payment-network-transactions"
      bootstrapServers: "${KAFKA_BOOTSTRAP_SERVERS}"
      
    operations:
      - name: "send_payment"
        eventType: "PaymentRequest"
        responseTopic: "payment-network-responses"
        
      - name: "receive_payment"
        eventType: "PaymentResponse"
        
    schemas:
      PaymentRequest:
        type: "object"
        properties:
          paymentId:
            type: "string"
          fromAccount:
            type: "string"
          toAccount:
            type: "string"
          amount:
            type: "number"
          currency:
            type: "string"
            
      PaymentResponse:
        type: "object"
        properties:
          paymentId:
            type: "string"
          status:
            type: "string"
            enum: ["SUCCESS", "FAILED", "PENDING"]
          reason:
            type: "string"
```

## User Interface

### Dashboard Configuration (`ui/banker-dashboard.json`)

```json
{
  "dashboard": {
    "id": "banker-dashboard",
    "name": "Banker Dashboard",
    "description": "Main dashboard for banking operations",
    "layout": "grid",
    "widgets": [
      {
        "id": "account-summary",
        "type": "metric-card",
        "title": "Account Summary",
        "position": {"row": 1, "col": 1, "width": 3, "height": 2},
        "dataSource": {
          "type": "api",
          "endpoint": "/api/banking/accounts/summary",
          "refreshInterval": "PT5M"
        },
        "metrics": [
          {
            "label": "Total Accounts",
            "value": "{{data.totalAccounts}}",
            "trend": "{{data.accountsTrend}}"
          },
          {
            "label": "Total Balance",
            "value": "{{data.totalBalance | currency}}",
            "trend": "{{data.balanceTrend}}"
          },
          {
            "label": "New Accounts Today",
            "value": "{{data.newAccountsToday}}"
          }
        ]
      },
      {
        "id": "pending-transactions",
        "type": "data-table",
        "title": "Pending Transactions",
        "position": {"row": 1, "col": 4, "width": 4, "height": 3},
        "dataSource": {
          "type": "api",
          "endpoint": "/api/banking/transactions?status=PENDING",
          "refreshInterval": "PT1M"
        },
        "columns": [
          {
            "field": "transactionId",
            "label": "Transaction ID",
            "sortable": true
          },
          {
            "field": "accountNumber",
            "label": "Account",
            "sortable": true
          },
          {
            "field": "amount",
            "label": "Amount",
            "sortable": true,
            "format": "currency"
          },
          {
            "field": "description",
            "label": "Description"
          },
          {
            "field": "createdAt",
            "label": "Time",
            "sortable": true,
            "format": "datetime"
          }
        ],
        "actions": [
          {
            "label": "Approve",
            "action": "approve_transaction",
            "condition": "{{row.canApprove}}"
          },
          {
            "label": "Reject",
            "action": "reject_transaction",
            "condition": "{{row.canReject}}"
          }
        ]
      },
      {
        "id": "compliance-alerts",
        "type": "alert-panel",
        "title": "Compliance Alerts",
        "position": {"row": 4, "col": 1, "width": 3, "height": 2},
        "dataSource": {
          "type": "api",
          "endpoint": "/api/banking/compliance/alerts",
          "refreshInterval": "PT2M"
        },
        "alertTypes": [
          {
            "type": "aml",
            "severity": "high",
            "color": "red"
          },
          {
            "type": "kyc",
            "severity": "medium",
            "color": "orange"
          },
          {
            "type": "limit",
            "severity": "low",
            "color": "yellow"
          }
        ]
      },
      {
        "id": "customer-activity",
        "type": "line-chart",
        "title": "Customer Activity",
        "position": {"row": 4, "col": 4, "width": 4, "height": 2},
        "dataSource": {
          "type": "api",
          "endpoint": "/api/banking/analytics/customer-activity",
          "refreshInterval": "PT10M"
        },
        "xAxis": {
          "field": "date",
          "type": "datetime"
        },
        "yAxis": {
          "field": "count",
          "type": "number"
        },
        "series": [
          {
            "name": "New Customers",
            "field": "newCustomers"
          },
          {
            "name": "Active Customers",
            "field": "activeCustomers"
          }
        ]
      }
    ]
  }
}
```

## Configuration

### Domain Configuration (`config/domain-config.json`)

```json
{
  "domain": "banking",
  "version": "1.0.0",
  "configuration": {
    "account": {
      "limits": {
        "minimumInitialDeposit": 1000,
        "maximumDailyWithdrawal": 50000,
        "maximumTransactionAmount": 1000000,
        "maximumAccountsPerCustomer": 10
      },
      "fees": {
        "accountMaintenanceFee": 10,
        "transactionFee": 0.005,
        "minimumBalanceFee": 5
      },
      "interest": {
        "savingsRate": 0.025,
        "fixedDepositRates": {
          "6months": 0.035,
          "12months": 0.045,
          "24months": 0.055
        }
      }
    },
    "transaction": {
      "limits": {
        "dailyTransactionLimit": 100000,
        "perTransactionLimit": 50000,
        "monthlyTransactionLimit": 1000000
      },
      "processing": {
        "batchSize": 1000,
        "processingInterval": "PT1M",
        "retryAttempts": 3
      }
    },
    "compliance": {
      "aml": {
        "reportingThreshold": 10000,
        "suspiciousTransactionThreshold": 25000,
        "highRiskTransactionThreshold": 50000
      },
      "kyc": {
        "requiredDocuments": ["id_proof", "address_proof", "income_proof"],
        "verificationTimeout": "P2D",
        "automaticApprovalThreshold": 1000
      },
      "risk": {
        "riskCategories": ["LOW", "MEDIUM", "HIGH"],
        "riskScoreThresholds": {
          "low": 30,
          "medium": 70,
          "high": 100
        }
      }
    },
    "notifications": {
      "email": {
        "enabled": true,
        "templates": {
          "accountOpened": "account-opened-template",
          "transactionProcessed": "transaction-processed-template",
          "complianceAlert": "compliance-alert-template"
        }
      },
      "sms": {
        "enabled": true,
        "templates": {
          "otp": "otp-template",
          "transactionAlert": "transaction-alert-template"
        }
      }
    }
  }
}
```

## Testing

### Unit Tests (`tests/unit/account-rules.test.js`)

```javascript
const { test } = require('@jest/globals');
const { evaluate } = require('@open-policy-agent/opa-wasm');

describe('Banking Account Rules', () => {
  test('should allow account creation with valid data', async () => {
    const input = {
      request: {
        action: 'create_account',
        accountType: 'SAVINGS',
        customerId: 'CUST-12345678',
        initialDeposit: 5000
      },
      customers: {
        'CUST-12345678': {
          kycStatus: 'VERIFIED',
          riskCategory: 'LOW',
          accountAge: 365,
          transactionCount: 100
        }
      },
      limits: {
        dailyTransactionLimit: 100000,
        perTransactionLimit: 50000
      }
    };

    const result = await evaluate('banking.account.allow', input);
    expect(result).toBe(true);
  });

  test('should reject account creation with insufficient KYC', async () => {
    const input = {
      request: {
        action: 'create_account',
        accountType: 'SAVINGS',
        customerId: 'CUST-12345678',
        initialDeposit: 5000
      },
      customers: {
        'CUST-12345678': {
          kycStatus: 'PENDING',
          riskCategory: 'LOW',
          accountAge: 365,
          transactionCount: 100
        }
      },
      limits: {
        dailyTransactionLimit: 100000,
        perTransactionLimit: 50000
      }
    };

    const result = await evaluate('banking.account.allow', input);
    expect(result).toBe(false);
  });

  test('should reject account creation with insufficient deposit', async () => {
    const input = {
      request: {
        action: 'create_account',
        accountType: 'SAVINGS',
        customerId: 'CUST-12345678',
        initialDeposit: 500
      },
      customers: {
        'CUST-12345678': {
          kycStatus: 'VERIFIED',
          riskCategory: 'LOW',
          accountAge: 365,
          transactionCount: 100
        }
      },
      limits: {
        dailyTransactionLimit: 100000,
        perTransactionLimit: 50000
      }
    };

    const result = await evaluate('banking.account.allow', input);
    expect(result).toBe(false);
  });
});
```

## Development Process

### 1. Setup Development Environment

```bash
# Clone domain pack template
git clone https://github.com/siddhanta/domain-pack-template.git my-domain-pack
cd my-domain-pack

# Install development dependencies
npm install

# Start development server
npm run dev
```

### 2. Create Domain Pack Structure

```bash
# Create domain pack directories
mkdir -p schemas rules workflows integrations ui config tests/{unit,integration,e2e}

# Initialize domain pack manifest
cp pack-template.yaml pack.yaml
```

### 3. Develop Domain Components

1. **Define Data Models**: Create entity schemas in `schemas/entities.json`
2. **Implement Business Rules**: Write OPA/Rego rules in `rules/`
3. **Create Workflows**: Define business processes in `workflows/`
4. **Configure Integrations**: Set up external system adapters
5. **Design UI Components**: Create dashboard and form configurations
6. **Write Tests**: Implement unit, integration, and E2E tests

### 4. Test Domain Pack

```bash
# Run unit tests
npm run test:unit

# Run integration tests
npm run test:integration

# Run E2E tests
npm run test:e2e

# Run all tests
npm run test
```

### 5. Package Domain Pack

```bash
# Build domain pack
npm run build

# Package for distribution
npm run package

# This creates:
# - banking-v1.0.0.tar.gz
# - banking-v1.0.0.tar.gz.sha256
# - banking-v1.0.0.manifest.json
```

### 6. Submit for Certification

```bash
# Submit to certification service
curl -X POST https://certify.siddhanta.dev/api/packs/submit \
  -H "Authorization: Bearer ${CERT_TOKEN}" \
  -F "pack=@banking-v1.0.0.tar.gz" \
  -F "manifest=@banking-v1.0.0.manifest.json"
```

## Best Practices

### 1. Domain Isolation
- Keep domain logic separate from kernel logic
- Use domain-specific namespaces for all entities
- Avoid hardcoding kernel dependencies

### 2. Schema Design
- Use JSON Schema for all data models
- Include validation rules and constraints
- Document all fields with descriptions

### 3. Rule Development
- Write testable, composable rules
- Use descriptive policy names
- Include comprehensive test coverage

### 4. Workflow Design
- Keep workflows simple and linear
- Use human tasks for approval steps
- Implement proper error handling

### 5. Integration Design
- Use circuit breakers for external calls
- Implement proper retry policies
- Handle failures gracefully

### 6. UI Design
- Follow platform design system
- Use responsive layouts
- Implement proper accessibility

### 7. Configuration Management
- Use hierarchical configuration
- Provide sensible defaults
- Support environment-specific overrides

### 8. Testing
- Achieve >90% code coverage
- Test all integration points
- Include performance tests

## Certification Requirements

Domain packs must meet the following certification criteria:

### 1. Code Quality
- No critical security vulnerabilities
- Code coverage >90%
- All tests passing
- No deprecated API usage

### 2. Performance
- API response time <100ms (P95)
- Memory usage <512MB per instance
- CPU usage <50% under normal load

### 3. Security
- No hardcoded credentials
- Proper input validation
- Secure communication protocols
- Compliance with security standards

### 4. Compatibility
- Compatible with target kernel version
- No conflicting dependencies
- Proper version management

### 5. Documentation
- Complete API documentation
- User guide and tutorials
- Troubleshooting guide

## Support and Maintenance

### 1. Version Management
- Follow semantic versioning
- Maintain backward compatibility
- Provide migration guides

### 2. Updates and Patches
- Regular security updates
- Bug fixes and improvements
- Feature enhancements

### 3. Community Support
- GitHub issues and discussions
- Community forums
- Developer documentation

### 4. Enterprise Support
- Priority bug fixes
- Dedicated support channels
- Custom development services

## Resources

- [Domain Pack API Reference](../api/domain-pack-api.md)
- [Kernel SDK Documentation](../sdk/kernel-sdk.md)
- [Testing Framework Guide](../testing/testing-guide.md)
- [Certification Process](../certification/certification-process.md)
- [Community Forums](https://community.siddhanta.dev)
- [Developer Portal](https://developers.siddhanta.dev)
