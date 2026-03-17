import re
with open("/Users/samujjwal/Development/ghatana/products/app-platform/kernel/audit-trail/src/test/java/com/ghatana/appplatform/audit/integration/AuditedAggregateEventStoreTest.java", "r") as f:
    text = f.read()

text = re.sub(r'new com\.ghatana\.appplatform\.audit\.domain\.AuditReceipt\(\s*UUID\.randomUUID\(\)\.toString\(\),\s*"hash",\s*0L\)', 
              'new com.ghatana.appplatform.audit.domain.AuditReceipt(UUID.randomUUID().toString(), 0L, "prev-hash", "hash", java.time.Instant.now())', text, flags=re.MULTILINE)

with open("/Users/samujjwal/Development/ghatana/products/app-platform/kernel/audit-trail/src/test/java/com/ghatana/appplatform/audit/integration/AuditedAggregateEventStoreTest.java", "w") as f:
    f.write(text)
