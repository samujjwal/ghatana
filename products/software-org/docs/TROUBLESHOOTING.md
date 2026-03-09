# Troubleshooting Guide

**Version:** 1.0.0  
**Last Updated:** November 17, 2025

This guide covers common issues encountered when developing, testing, or running Software-Org.

---

## 🔍 Build Issues

### Issue: Build Fails with "Cannot find symbol"

**Symptoms:**
```
error: cannot find symbol
  symbol:   class AllDepartmentPipelinesRegistrar
  location: package com.ghatana.softwareorg.framework
```

**Causes:**
- Missing module in `settings.gradle.kts`
- Gradle not refreshed after adding dependency
- Classpath issues

**Solutions:**

```bash
# 1. Clean and rebuild
./gradlew clean

# 2. Refresh Gradle dependencies
./gradlew build --refresh-dependencies

# 3. Check module inclusion (from root)
grep -r "software-org" settings.gradle.kts

# 4. Rebuild specific module
./gradlew :products:software-org:libs:java:framework:build
```

---

### Issue: "Type SafetyError" in Type Checking

**Symptoms:**
```
error: [CompileError] Cannot assign type List<?> to List<String>
```

**Causes:**
- Unchecked type conversions
- Missing type parameters
- Generic type mismatches

**Solutions:**

```java
// ❌ WRONG
List list = someMethod();  // Type error

// ✅ CORRECT
List<String> list = someMethod();  // Type-safe
```

**Prevention:**
```bash
# Check type safety
./gradlew :module:compileJava -x test

# Run full type checking
./gradlew :module:build -x test
```

---

### Issue: "Spotless Code Formatting Failed"

**Symptoms:**
```
BUILD FAILED: Spotless code style violation
  Expected: [formatting]
  Actual: [different formatting]
```

**Solutions:**

```bash
# Auto-fix formatting
./gradlew spotlessApply

# Check without fixing
./gradlew spotlessCheck

# Verify after fix
./gradlew :products:software-org:build -x test
```

---

## 🧪 Test Issues

### Issue: Tests Fail with "NullPointerException" in Promise Tests

**Symptoms:**
```
java.lang.NullPointerException
  at io.activej.promise.Promise.getResult(Promise.java:1234)
```

**Cause:** Using `.getResult()` without EventloopTestBase

**Solution:**

```java
// ❌ WRONG - Missing EventloopTestBase
@Test
void shouldProcessAsync() {
    String result = service.async("input").getResult();  // NPE!
}

// ✅ CORRECT - Extends EventloopTestBase
@Test
void shouldProcessAsync() {
    String result = runPromise(() -> service.async("input"));
}
```

**Verify:**
```bash
# Check test extends EventloopTestBase
grep -n "extends EventloopTestBase" src/test/java/**/*Test.java

# Check no .getResult() in tests
grep -n ".getResult()" src/test/java/**/*Test.java
```

---

### Issue: Test Hangs or Times Out

**Symptoms:**
```
Test timed out after 30 seconds
  at [blocked waiting for result]
```

**Causes:**
- Deadlock in async code
- Missing completion handler
- Infinite loop in test setup

**Solutions:**

```bash
# 1. Run with verbose output
./gradlew :module:test --info

# 2. Add timeout annotation
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void shouldCompleteQuickly() {
    // Test code
}

# 3. Run specific test with debug
./gradlew :module:test --tests "TestClass.methodName" -d
```

---

### Issue: Test Fails with "Cannot find bean X"

**Symptoms:**
```
error: Cannot resolve bean of type DevSecOpsRestController
```

**Causes:**
- Missing DI module in test setup
- Bean not properly registered
- Wrong module imported in test

**Solutions:**

```java
// ✅ CORRECT - Set up DI module
@Test
void shouldCreateController() {
    Injector injector = Injector.of(new SoftwareOrgDiModule());
    DevSecOpsRestController controller = injector.getInstance(
        DevSecOpsRestController.class
    );
    assertThat(controller).isNotNull();
}
```

---

### Issue: AssertJ Assertion Fails with Unclear Message

**Symptoms:**
```
AssertionError: expected <[actual]> but was <[expected]>
```

**Solution:** Add `.as()` description

```java
// ❌ UNCLEAR
assertThat(result).isEqualTo(expected);

// ✅ CLEAR
assertThat(result)
    .as("Decision should approve the feature request")
    .isEqualTo(expected);
```

---

## 🚀 Runtime Issues

### Issue: Application Fails to Start - "Port Already in Use"

**Symptoms:**
```
java.net.BindException: Address already in use: bind
```

**Solutions:**

```bash
# 1. Find process using port 8080
lsof -i :8080

# 2. Kill the process
kill -9 <PID>

# 3. Or use different port
java -jar build/libs/software-org.jar --server.port=8081
```

---

### Issue: "Connection Refused" to Database

**Symptoms:**
```
java.sql.SQLException: Connection refused (Connection refused)
  at DB_HOST:5432
```

**Solutions:**

```bash
# 1. Check database is running
docker ps | grep postgres

# 2. Start database if needed
docker-compose up -d postgres

# 3. Verify connection parameters
env | grep DB_

# 4. Test connectivity
psql -h localhost -U postgres -d software_org -c "SELECT 1"
```

---

### Issue: "EventCloud Connection Failed"

**Symptoms:**
```
org.apache.kafka.common.errors.BootstrapException:
  Unable to connect to bootstrap servers: localhost:9092
```

**Solutions:**

```bash
# 1. Check Kafka is running
docker ps | grep kafka

# 2. Start Kafka if needed
docker-compose up -d kafka

# 3. Verify bootstrap servers
echo $EVENTCLOUD_BOOTSTRAP_SERVERS

# 4. Test connectivity
kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

---

### Issue: REST API Returns 500 Error

**Symptoms:**
```json
{
  "status": "ERROR",
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Internal server error"
  }
}
```

**Solutions:**

```bash
# 1. Check application logs
tail -f logs/software-org.log | grep ERROR

# 2. Check for specific error
tail -f logs/software-org.log | grep "500\|Exception"

# 3. Enable debug logging
LOG_LEVEL=DEBUG java -jar software-org.jar

# 4. Check recent changes
git log --oneline -5
```

---

### Issue: High CPU Usage or Memory Leak

**Symptoms:**
```
Process consuming 80%+ CPU or memory growing over time
```

**Solutions:**

```bash
# 1. Monitor heap usage
jstat -gc <PID> 1000  # Every 1 second

# 2. Generate heap dump on OutOfMemory
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heap.bin \
     -jar software-org.jar

# 3. Check thread count
jstack <PID> | grep -c "tid:"

# 4. Profile with async profiler
./async-profiler.sh -d 30 -f /tmp/profile.jfr <PID>
```

---

## 🔐 Security Issues

### Issue: "Permission Denied" on Log Files

**Symptoms:**
```
java.io.IOException: Permission denied: logs/software-org.log
```

**Solutions:**

```bash
# 1. Check permissions
ls -la logs/

# 2. Fix permissions
chmod 644 logs/software-org.log

# 3. Or create directory with correct perms
mkdir -p logs
chmod 755 logs
```

---

### Issue: "Secrets Scan Found Credentials"

**Symptoms:**
```
Secrets found in code:
  - AWS_ACCESS_KEY_ID in application.properties
  - DATABASE_PASSWORD in config.yaml
```

**Solutions:**

```bash
# 1. Move secrets to environment variables or secrets manager
export AWS_ACCESS_KEY_ID="your-key"
export DATABASE_PASSWORD="your-password"

# 2. Update configuration
# application.properties
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
DATABASE_PASSWORD=${DATABASE_PASSWORD}

# 3. Never commit secrets to git
echo "secrets/" >> .gitignore
git rm --cached secrets/
```

---

## 🌐 Multi-Tenant Issues

### Issue: Data Leaking Between Tenants

**Symptoms:**
```
Events from tenant-A visible in tenant-B queries
```

**Causes:**
- Missing tenant_id filter in query
- Incorrect tenant isolation in state keys

**Solutions:**

```bash
# 1. Verify tenant header is sent
curl -H "X-Tenant-ID: tenant-123" \
  http://localhost:8080/api/v1/devsecops/features

# 2. Check database query has tenant filter
SELECT * FROM events WHERE tenant_id = 'tenant-123'

# 3. Verify all state keys include tenant
# WRONG: "feature:feat-123"
# CORRECT: "tenant-123:feature:feat-123"
```

---

### Issue: "Tenant Not Found"

**Symptoms:**
```
Error: Tenant 'tenant-xyz' not found or not authorized
```

**Solutions:**

```bash
# 1. Check tenant exists
curl http://localhost:8080/api/v1/tenants | jq '.[]' | grep tenant-xyz

# 2. Create tenant if needed
curl -X POST http://localhost:8080/api/v1/tenants \
  -d '{"id": "tenant-xyz", "name": "Company XYZ"}'

# 3. Verify default tenant
echo $APP_TENANT_ID
```

---

## 📊 Metrics and Observability Issues

### Issue: Metrics Not Appearing in Prometheus

**Symptoms:**
```
No metrics found at http://localhost:9090
```

**Solutions:**

```bash
# 1. Check metrics endpoint is available
curl http://localhost:8080/metrics

# 2. Check Prometheus is running
curl http://localhost:9090/-/healthy

# 3. Check Prometheus configuration
cat prometheus.yml | grep targets

# 4. Verify metrics are being emitted
curl http://localhost:8080/metrics | head -20
```

---

### Issue: "High Latency" Alert

**Symptoms:**
```
AlertManager: software_org.event.processing_duration > 10ms (p99)
```

**Solutions:**

```bash
# 1. Check pod resource usage
kubectl top pods -n software-org

# 2. Check database performance
EXPLAIN ANALYZE SELECT * FROM events WHERE tenant_id = 'tenant-123';

# 3. Scale deployment
kubectl scale deployment software-org-api --replicas=5

# 4. Check for memory leaks
jstat -gc <PID> 1000
```

---

## 🔧 Development Environment Issues

### Issue: IDE Cannot Find Classes

**Symptoms:**
```
Unresolved symbol 'AIDecisionEngine'
```

**Solutions:**

**VS Code:**
```bash
# 1. Rebuild Gradle project
Cmd+Shift+P → "Gradle: Run Build"

# 2. Refresh Java language server
Cmd+Shift+P → "Java: Clean Java Language Server Workspace"

# 3. Close and reopen IDE
```

**IntelliJ:**
```bash
# 1. Invalidate caches
File → Invalidate Caches and Restart

# 2. Sync Gradle project
View → Tool Windows → Gradle → Refresh

# 3. Rebuild project
Build → Rebuild Project
```

---

### Issue: Git Merge Conflicts in build.gradle

**Symptoms:**
```
conflict: build.gradle.kts
  <<<<<<< HEAD
  >>>>>>> branch
```

**Solutions:**

```bash
# 1. Keep both versions of dependencies (no duplicates)
git checkout --theirs build.gradle.kts

# 2. Verify build still works
./gradlew build -x test

# 3. Commit merge
git add build.gradle.kts
git commit -m "Resolve build.gradle.kts conflict"
```

---

## ✅ Verification Checklist

After troubleshooting, verify the system is working:

```bash
# 1. Build passes
./gradlew :products:software-org:build

# 2. All tests pass
./gradlew :products:software-org:test

# 3. Code formatting correct
./gradlew spotlessCheck

# 4. No linting errors
./gradlew checkstyleMain

# 5. Application starts
java -jar build/libs/software-org.jar &

# 6. Health check passes
curl http://localhost:8080/api/v1/health | jq '.status'
# Expected: "UP"

# 7. API endpoint works
curl -X POST http://localhost:8080/api/v1/devsecops/features \
  -H "Content-Type: application/json" \
  -d '{"title": "Test", "department": "engineering", "type": "feature", "metadata": {}}'
# Expected: status 200 with JSON response
```

---

## 📞 Getting More Help

1. **Check Logs**: `tail -f logs/software-org.log`
2. **Check Docs**: Review [Getting Started](GETTING_STARTED.md), [API Reference](API_REFERENCE.md)
3. **Run Tests**: Verify with `./gradlew test`
4. **Review Issues**: Check GitHub issues for similar problems
5. **Ask Team**: Post question with error log, code snippet, and expected vs actual behavior

---

## 🐛 Reporting Issues

When reporting a bug, include:

1. **Error Message**: Full stack trace from logs
2. **Reproduction Steps**: Exact commands to reproduce
3. **Environment**: OS, Java version, build output
4. **Expected vs Actual**: What should happen vs what did happen
5. **Attachments**: Log files, code snippets, test results

**Example Issue Report:**

```
Title: API returns 500 error when creating feature with special characters

Description:
When creating a feature with special characters in title, API returns 500 error.

Reproduction:
curl -X POST http://localhost:8080/api/v1/devsecops/features \
  -d '{"title": "Test & Debug < >", "department": "engineering", ...}'

Expected: Status 200 with decision

Actual: Status 500 with INTERNAL_ERROR

Environment:
- OS: macOS 12.6
- Java: 17.0.4
- Gradle: 8.10.2

Error Log:
java.lang.IllegalArgumentException: Invalid character in title
  at AIMetadataEnricher.enrichWithAiMetadata(AIMetadataEnricher.java:120)
```

---

**Last Updated:** November 17, 2025  
**Status:** Production Ready
