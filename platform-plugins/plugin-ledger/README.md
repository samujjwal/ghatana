# plugin-ledger

**Package**: `com.ghatana.plugin.ledger`

## Overview

Platform-level double-entry ledger plugin for cross-scope accounting.
Provides idempotent transaction posting, reversal, multi-currency support,
and audit-trail integration.

## Key types

| Type | Purpose |
|------|---------|
| `LedgerPlugin` | Plugin interface |
| `LedgerTransaction` | Immutable transaction value object |
| `StandardLedgerPlugin` | In-memory implementation (dev/test) |
| `DurableLedgerPlugin` | JDBC-backed durable implementation |

## Usage

```java
LedgerPlugin ledger = context.getPlugin(LedgerPlugin.class);

LedgerTransaction tx = LedgerTransaction.builder()
    .transactionId(UUID.randomUUID().toString())
    .sourceId("scope-a")
    .debitAccount("acct-001")
    .creditAccount("acct-002")
    .amount(new BigDecimal("100.00"))
    .currency("USD")
    .type(LedgerTransaction.TransactionType.CHARGE)
    .tenantId("tenant-1")
    .build();

String entryId = ledger.postTransaction(tx).getResult();
```
