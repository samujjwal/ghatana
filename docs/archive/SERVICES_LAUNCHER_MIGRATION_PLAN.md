# Services and Launcher Migration Plan

**Date**: February 5, 2026  
**Status**: Implementation Plan  
**Scope**: AEP and Data Cloud launchers and services migration  

---

## 1. Overview

The initial migration focused on the `platform` modules (core domain logic). This phase migrates:
1. **Launchers** - Application entry points and HTTP server setup
2. **Services** - Optional microservices (if any exist)

---

## 2. Current State Analysis

### 2.1 AEP Legacy Structure

**Launcher**: `/ghatana/products/aep/launcher/`
- Contains `AepLauncher.java` - main entry point
- HTTP server setup (`AepHttpServer.java`)
- TLS, CORS, Rate limiting, Authentication filters

**Services**: `/ghatana/products/aep/services/`
- Currently empty (just build artifacts)
- UI service exists at `/ghatana/products/aep/ui-service/` (separate TypeScript project)

### 2.2 Data Cloud Legacy Structure

**Launcher**: `/ghatana/products/data-cloud/launcher/`
- Contains `DataCloudLauncher.java` and `UnifiedLauncher.java`
- HTTP server setup (`DataCloudHttpServer.java`)
- Similar filters and configuration

**Services**: No separate services directory structure

---

## 3. Target Structure

```
products/aep/
├── platform/          ✅ MIGRATED (526 files)
├── launcher/          ⏳ TO MIGRATE
│   ├── build.gradle.kts
│   └── src/main/java/com/ghatana/aep/launcher/
│       ├── AepLauncher.java
│       ├── http/
│       │   ├── AepHttpServer.java
│       │   ├── TlsConfig.java
│       │   ├── CorsConfig.java
│       │   ├── RateLimiter.java
│       │   └── AuthenticationFilter.java
│       └── config/
└── services/          📝 OPTIONAL (currently empty)

products/data-cloud/
├── platform/          ✅ MIGRATED (465 files)
├── launcher/          ⏳ TO MIGRATE
│   ├── build.gradle.kts
│   └── src/main/java/com/ghatana/datacloud/launcher/
│       ├── DataCloudLauncher.java
│       ├── UnifiedLauncher.java
│       ├── Main.java
│       ├── http/
│       │   ├── DataCloudHttpServer.java
│       │   ├── TlsConfig.java
│       │   ├── CorsConfig.java
│       │   ├── RateLimiter.java
│       │   └── AuthenticationFilter.java
│       └── SimpleGraphQLHandler.java
└── services/          📝 OPTIONAL (none exist)
```

---

## 4. Migration Steps

### Phase 1: Create Directory Structure

```bash
# AEP Launcher
mkdir -p products/aep/launcher/src/main/java/com/ghatana/aep/launcher
mkdir -p products/aep/launcher/src/main/resources

# Data Cloud Launcher
mkdir -p products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher
mkdir -p products/data-cloud/launcher/src/main/resources
```

### Phase 2: Copy Launcher Code

```bash
# AEP
cp -r /ghatana/products/aep/launcher/src/main/java/* \
      /ghatana-new/products/aep/launcher/src/main/java/

# Data Cloud
cp -r /ghatana/products/data-cloud/launcher/src/main/java/* \
      /ghatana-new/products/data-cloud/launcher/src/main/java/
```

### Phase 3: Create build.gradle.kts Files

Each launcher needs:
- Dependency on its product's platform module
- Dependency on platform modules (http, config, observability)
- ActiveJ launcher and inject
- Main class configuration for the application plugin

### Phase 4: Fix Imports

Expected import transformations:
- `com.ghatana.observability.*` → `com.ghatana.platform.observability.*`
- `com.ghatana.core.*` → `com.ghatana.platform.core.*`
- `com.ghatana.http.*` → `com.ghatana.platform.http.*`
- `com.ghatana.config.*` → `com.ghatana.platform.config.*`

### Phase 5: Update settings.gradle.kts

Add launcher modules:
```kotlin
includeIfExists(":products:aep:launcher")
includeIfExists(":products:data-cloud:launcher")
```

### Phase 6: Compilation and Verification

```bash
# Test AEP launcher compilation
./gradlew :products:aep:launcher:compileJava

# Test Data Cloud launcher compilation
./gradlew :products:data-cloud:launcher:compileJava
```

---

## 5. Dependencies Analysis

### AEP Launcher Dependencies (Expected)
- `:products:aep:platform` (own platform)
- `:platform:java:core`
- `:platform:java:http`
- `:platform:java:config`
- `:platform:java:observability`
- `activej-launcher`
- `activej-inject`
- `activej-http`

### Data Cloud Launcher Dependencies (Expected)
- `:products:data-cloud:platform` (own platform)
- `:platform:java:core`
- `:platform:java:http`
- `:platform:java:config`
- `:platform:java:observability`
- `activej-launcher`
- `activej-inject`
- `activej-http`

---

## 6. Known Challenges

1. **Package Name Mismatches**: Launcher may reference old package structures
2. **Missing Platform APIs**: Some HTTP/config utilities may not exist in new platform
3. **ActiveJ Launcher Configuration**: May need updates for new module structure
4. **Main Class Configuration**: Needs correct application plugin setup

---

## 7. Success Criteria

- ✅ Launcher directories created with proper structure
- ✅ All launcher Java files copied and compiling
- ✅ build.gradle.kts files created with correct dependencies
- ✅ Imports refactored to use new platform packages
- ✅ `./gradlew :products:aep:launcher:compileJava` succeeds
- ✅ `./gradlew :products:data-cloud:launcher:compileJava` succeeds

---

## 8. Services Migration (Future)

**AEP Services**: Currently empty - defer until actual services are implemented

**Data Cloud Services**: None exist - defer

If services are added later, follow the pattern:
```
products/<product>/services/<service-name>/
├── build.gradle.kts
└── src/main/java/com/ghatana/<product>/service/<service-name>/
```
