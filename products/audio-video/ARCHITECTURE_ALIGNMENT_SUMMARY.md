# Architecture Alignment Summary - Option A Implementation

**Date:** 2026-04-14  
**Reference:** Ghatana Guidelines (.github/copilot-instructions.md)  
**Decision:** Option A - Follow AEP Pattern

---

## Decision Rationale

Per Ghatana guidelines:
1. **"Reuse before creating"** - If async repositories are truly better, they belong in platform library
2. **"Follow the conventions already present"** - AEP establishes the pattern of sync repos + async services
3. **AEP Pattern** - `JpaAgentRepository` (sync) + `AgentRegistryService` (async)

---

## Changes Made

### 1. Repository Layer (Now Synchronous)

**Before:**
```java
public interface AudioFileRepository {
    Promise<AudioFileEntity> save(String tenantId, AudioFileEntity entity);  // WRONG
    Promise<Optional<AudioFileEntity>> findById(String tenantId, UUID id);   // WRONG
    Promise<Boolean> delete(String tenantId, UUID id);                       // WRONG
}
```

**After (AEP Pattern):**
```java
public interface AudioFileRepository {
    AudioFileEntity save(String tenantId, AudioFileEntity entity);  // CORRECT
    Optional<AudioFileEntity> findById(String tenantId, UUID id);    // CORRECT
    boolean softDelete(String tenantId, UUID id);                   // CORRECT - AEP style
    boolean hardDelete(String tenantId, UUID id);                 // CORRECT
}
```

**Reference:** `products/data-cloud/planes/action/registry/src/main/java/.../JpaAgentRepository.java`

---

### 2. Service Layer (New - Async)

**New Files Created:**
- `AudioFileService.java` - Async service wrapping repository
- `TranscriptionService.java` - Async service wrapping repository

**Pattern:**
```java
public class AudioFileService {
    private final AudioFileRepository repository;
    private final ExecutorService dbExecutor;

    public Promise<AudioFileEntity> save(String tenantId, AudioFileEntity entity) {
        return Promise.ofBlocking(dbExecutor, () -> repository.save(tenantId, entity));
    }
}
```

**Reference:** `products/data-cloud/planes/action/registry/src/main/java/.../AgentRegistryService.java`

---

### 3. Soft Delete (New)

**Added to Entities:**
```java
@Column(name = "deleted", nullable = false)
private boolean deleted = false;

@Column(name = "deleted_at")
private Instant deletedAt;
```

**Added to Repositories:**
```java
boolean softDelete(String tenantId, UUID id);  // Sets deleted=true
boolean hardDelete(String tenantId, UUID id); // Actually removes
```

**Reference:** `products/data-cloud/planes/action/registry/.../JpaAgentRepository.softDelete()`

---

### 4. Jackson JSON Serialization (Fixed)

**Before:**
```java
private String serializeJob(TranscriptionJobMessage job) {
    // Manual string formatting - fragile
    return String.format("{\"jobId\":\"%s\"...}", ...);
}
```

**After:**
```java
private final ObjectMapper objectMapper;  // Constructor injected

private String serializeJob(TranscriptionJobMessage job) {
    return objectMapper.writeValueAsString(job);  // Proper JSON
}
```

---

### 5. Tests (Updated)

**Before:**
```java
class JpaAudioFileRepositoryTest extends EventloopTestBase {
    @Test
    void testSave() {
        AudioFileEntity saved = runPromise(() -> repository.save(tenantId, entity));
    }
}
```

**After:**
```java
class JpaAudioFileRepositoryTest {  // No EventloopTestBase needed
    @Test
    void testSave() {
        AudioFileEntity saved = repository.save(tenantId, entity);  // Direct call
    }

    @Test
    void testSoftDelete() {
        boolean deleted = repository.softDelete(tenantId, id);
        assertThat(found.get().isDeleted()).isTrue();
    }
}
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  Service Layer (ActiveJ Promise-based)                      │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  AudioFileService                                   │  │
│  │  - save(): Promise<AudioFile>                      │  │
│  │  - softDelete(): Promise<Boolean>                │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Repository Layer (Synchronous - follows AEP)              │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  JpaAudioFileRepository                             │  │
│  │  - save(): AudioFileEntity (sync)                  │  │
│  │  - softDelete(): boolean (sync)                    │  │
│  │  - Uses EntityManager directly                     │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  Platform Layer                                              │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐             │
│  │  Database  │ │  Security  │ │  Messaging │             │
│  └────────────┘ └────────────┘ └────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

---

## Benefits of This Pattern

1. **Simplicity** - Repositories are easier to understand and test
2. **Testability** - No async complexity in repository tests
3. **Flexibility** - Can add caching/mapping at service layer
4. **AEP Alignment** - Consistent with existing codebase patterns
5. **Migration Path** - If async repos needed later, add to platform library

---

## Files Modified/Created

### Modified (Alignment)
1. `AudioFileRepository.java` - Removed Promise, added soft delete
2. `TranscriptionRepository.java` - Removed Promise, added soft delete
3. `JpaAudioFileRepository.java` - Synchronous, added soft delete
4. `JpaTranscriptionRepository.java` - Synchronous, added soft delete
5. `AudioFileEntity.java` - Added deleted/deletedAt
6. `TranscriptionEntity.java` - Added deleted/deletedAt
7. `V1__init_schema.sql` - Added soft delete columns
8. `TranscriptionJobProducer.java` - Added Jackson serialization
9. `JpaAudioFileRepositoryTest.java` - Updated for sync repos

### Created (New)
1. `AudioFileService.java` - Async service layer
2. `TranscriptionService.java` - Async service layer

---

## Compliance with Guidelines

✅ **Reuse before creating** - Following AEP pattern, not inventing new async repo pattern  
✅ **Follow conventions already present** - AEP pattern used as reference  
✅ **Boundaries explicit** - Repository (sync) vs Service (async) clearly separated  
✅ **No silent failures** - Soft delete prevents accidental data loss  
✅ **Zero-warning mindset** - Proper JSON serialization with Jackson  
✅ **Tests are part of change** - Updated tests for new pattern  

---

## Future Consideration

If async repositories prove beneficial across multiple products:
1. Add `AsyncRepository<T>` interface to `platform:java:database`
2. Provide `JpaAsyncRepository` base implementation
3. Document when to use sync vs async patterns
4. Migrate products consistently

This keeps the improvement shared, not duplicated.
