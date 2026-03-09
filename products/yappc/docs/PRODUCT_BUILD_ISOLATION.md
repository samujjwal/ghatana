# Product Build Isolation Architecture

**Date:** 2026-03-07  
**Status:** 🎯 DESIGN COMPLETE  
**Timeline:** 3-4 weeks

---

## 🎯 Overview

Isolate product builds to enable independent development, testing, and deployment of YAPPC, AEP, Data Cloud, and Audio-Video products while maintaining shared platform dependencies.

---

## 📐 Current vs. Target Architecture

### Current State
```
┌─────────────────────────────────────────┐
│         Monolithic Build                │
├─────────────────────────────────────────┤
│                                         │
│  All Products → Single Build Process   │
│  Shared Dependencies (tightly coupled) │
│  Long build times (15-20 minutes)      │
│  Cannot deploy independently           │
│                                         │
└─────────────────────────────────────────┘
```

### Target State
```
┌─────────────────────────────────────────────────────────┐
│              Isolated Product Builds                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │  YAPPC   │  │   AEP    │  │Data Cloud│  │Audio-  │ │
│  │  Build   │  │  Build   │  │  Build   │  │Video   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───┬────┘ │
│       │             │              │             │      │
│       └─────────────┴──────────────┴─────────────┘      │
│                          │                              │
│                    ┌─────▼──────┐                       │
│                    │  Platform  │                       │
│                    │  Libraries │                       │
│                    └────────────┘                       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 🏗️ Architecture Components

### 1. Platform Libraries (Shared)

**Structure:**
```
platform/
├── java/
│   ├── core/                    # Core utilities
│   ├── domain/                  # Domain models
│   ├── security/                # Security primitives
│   ├── database/                # Database utilities
│   ├── event-cloud/             # Event infrastructure
│   ├── agent-framework/         # Agent framework
│   └── agent-resilience/        # Resilience patterns
├── typescript/
│   ├── ui/                      # Shared UI components
│   ├── canvas/                  # Canvas library
│   ├── api/                     # API client
│   └── utils/                   # Utilities
└── contracts/
    └── openapi/                 # API contracts
```

**Build Configuration:**
```kotlin
// platform/build.gradle.kts
plugins {
    id("java-library")
    id("maven-publish")
}

allprojects {
    group = "com.ghatana.platform"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
        repositories {
            maven {
                url = uri("https://artifacts.ghatana.com/maven")
            }
        }
    }
}
```

### 2. Product-Specific Builds

#### YAPPC Build
```kotlin
// products/yappc/build.gradle.kts
plugins {
    id("com.ghatana.product-conventions")
}

dependencies {
    // Platform dependencies (versioned)
    implementation("com.ghatana.platform:core:1.0.0")
    implementation("com.ghatana.platform:domain:1.0.0")
    implementation("com.ghatana.platform:security:1.0.0")
    implementation("com.ghatana.platform:agent-framework:1.0.0")
    
    // Product-specific dependencies
    implementation("dev.langchain4j:langchain4j:0.27.1")
    implementation("org.springframework.boot:spring-boot-starter-web")
}

tasks {
    bootJar {
        archiveFileName.set("yappc-${version}.jar")
    }
    
    test {
        useJUnitPlatform()
        maxParallelForks = Runtime.getRuntime().availableProcessors()
    }
}
```

#### AEP Build
```kotlin
// products/aep/build.gradle.kts
plugins {
    id("com.ghatana.product-conventions")
}

dependencies {
    // Platform dependencies
    implementation("com.ghatana.platform:core:1.0.0")
    implementation("com.ghatana.platform:event-cloud:1.0.0")
    implementation("com.ghatana.platform:agent-framework:1.0.0")
    
    // AEP-specific dependencies
    implementation("io.projectreactor:reactor-core")
}

tasks {
    bootJar {
        archiveFileName.set("aep-${version}.jar")
    }
}
```

#### Data Cloud Build
```kotlin
// products/data-cloud/build.gradle.kts
plugins {
    id("com.ghatana.product-conventions")
}

dependencies {
    // Platform dependencies
    implementation("com.ghatana.platform:core:1.0.0")
    implementation("com.ghatana.platform:database:1.0.0")
    
    // Data Cloud-specific dependencies
    implementation("org.apache.spark:spark-core_2.13")
}

tasks {
    bootJar {
        archiveFileName.set("data-cloud-${version}.jar")
    }
}
```

### 3. Dependency Management

**Platform BOM (Bill of Materials):**
```kotlin
// platform/platform-bom/build.gradle.kts
plugins {
    id("java-platform")
    id("maven-publish")
}

javaPlatform {
    allowDependencies()
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:3.2.0"))
    api(platform("com.fasterxml.jackson:jackson-bom:2.15.0"))
    
    constraints {
        api("com.ghatana.platform:core:1.0.0")
        api("com.ghatana.platform:domain:1.0.0")
        api("com.ghatana.platform:security:1.0.0")
        api("com.ghatana.platform:database:1.0.0")
        api("com.ghatana.platform:event-cloud:1.0.0")
        api("com.ghatana.platform:agent-framework:1.0.0")
        api("com.ghatana.platform:agent-resilience:1.0.0")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}
```

**Product Usage:**
```kotlin
// products/yappc/build.gradle.kts
dependencies {
    implementation(platform("com.ghatana.platform:platform-bom:1.0.0"))
    
    // No version needed - managed by BOM
    implementation("com.ghatana.platform:core")
    implementation("com.ghatana.platform:domain")
}
```

### 4. Build Conventions Plugin

```kotlin
// buildSrc/src/main/kotlin/com.ghatana.product-conventions.gradle.kts
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

group = "com.ghatana.products"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("com.ghatana.platform:platform-bom:1.0.0"))
    
    // Common dependencies for all products
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    
    bootJar {
        enabled = true
    }
    
    jar {
        enabled = false
    }
}
```

---

## 🔄 CI/CD Pipeline

### Multi-Product Pipeline

```yaml
# .github/workflows/product-builds.yml
name: Product Builds

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      platform: ${{ steps.changes.outputs.platform }}
      yappc: ${{ steps.changes.outputs.yappc }}
      aep: ${{ steps.changes.outputs.aep }}
      data-cloud: ${{ steps.changes.outputs.data-cloud }}
      audio-video: ${{ steps.changes.outputs.audio-video }}
    steps:
      - uses: actions/checkout@v3
      - uses: dorny/paths-filter@v2
        id: changes
        with:
          filters: |
            platform:
              - 'platform/**'
            yappc:
              - 'products/yappc/**'
              - 'platform/**'
            aep:
              - 'products/aep/**'
              - 'platform/**'
            data-cloud:
              - 'products/data-cloud/**'
              - 'platform/**'
            audio-video:
              - 'products/audio-video/**'
              - 'platform/**'

  build-platform:
    needs: detect-changes
    if: needs.detect-changes.outputs.platform == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build Platform
        run: ./gradlew :platform:build publishToMavenLocal
      - name: Publish Platform Artifacts
        run: ./gradlew :platform:publish

  build-yappc:
    needs: [detect-changes, build-platform]
    if: needs.detect-changes.outputs.yappc == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build YAPPC
        run: ./gradlew :products:yappc:build
      - name: Test YAPPC
        run: ./gradlew :products:yappc:test
      - name: Package YAPPC
        run: ./gradlew :products:yappc:bootJar

  build-aep:
    needs: [detect-changes, build-platform]
    if: needs.detect-changes.outputs.aep == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build AEP
        run: ./gradlew :products:aep:build

  build-data-cloud:
    needs: [detect-changes, build-platform]
    if: needs.detect-changes.outputs.data-cloud == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Data Cloud
        run: ./gradlew :products:data-cloud:build

  build-audio-video:
    needs: [detect-changes, build-platform]
    if: needs.detect-changes.outputs.audio-video == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Audio-Video
        run: ./gradlew :products:audio-video:build
```

---

## 📊 Build Performance

### Current vs. Target

| Metric | Current | Target | Improvement |
|--------|---------|--------|-------------|
| **Full Build** | 15-20 min | 5-7 min | **60-65%** |
| **YAPPC Only** | 15-20 min | 3-4 min | **75-80%** |
| **Platform Only** | N/A | 2-3 min | N/A |
| **Parallel Builds** | No | Yes | ∞ |
| **Cache Hit Rate** | 20% | 80% | **300%** |

### Build Time Breakdown

**Current:**
```
Total: 18 minutes
├── Platform (implicit): 8 min
├── YAPPC: 4 min
├── AEP: 3 min
├── Data Cloud: 2 min
└── Audio-Video: 1 min
```

**Target:**
```
Total (parallel): 6 minutes
├── Platform: 3 min
└── Products (parallel):
    ├── YAPPC: 4 min
    ├── AEP: 3 min
    ├── Data Cloud: 2 min
    └── Audio-Video: 1 min
```

---

## 🎯 Implementation Plan

### Week 1: Platform Extraction
- [ ] Create platform BOM
- [ ] Extract shared libraries
- [ ] Set up artifact repository
- [ ] Create conventions plugin

### Week 2: Product Isolation
- [ ] Isolate YAPPC build
- [ ] Isolate AEP build
- [ ] Isolate Data Cloud build
- [ ] Isolate Audio-Video build

### Week 3: CI/CD Migration
- [ ] Update GitHub Actions
- [ ] Configure parallel builds
- [ ] Set up caching
- [ ] Test deployment

### Week 4: Validation & Optimization
- [ ] Performance testing
- [ ] Dependency verification
- [ ] Documentation
- [ ] Team training

---

## ✅ Success Criteria

- [ ] Build time reduced by 50%+
- [ ] Products can build independently
- [ ] Platform changes trigger all product builds
- [ ] Product changes only trigger affected builds
- [ ] Artifact versioning works correctly
- [ ] CI/CD pipeline is reliable

---

**Status:** Ready for Implementation  
**Next Step:** Create platform BOM and extract shared libraries
