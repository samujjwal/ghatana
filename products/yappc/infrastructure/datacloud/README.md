# YAPPC Infrastructure - Data-Cloud Integration

## Overview

This module provides the infrastructure layer for integrating YAPPC with the Data-Cloud platform. It serves as the canonical data layer for all YAPPC persistence operations.

## Architecture

### Core Components

1. **YappcEntityMapper** - Bidirectional mapping between YAPPC domain models and data-cloud DynamicEntity instances
2. **YappcDataCloudRepository** - Generic repository adapter providing CRUD operations for any YAPPC entity
3. **Specialized Adapters** - Domain-specific adapters for Dashboard, Knowledge Graph, and Refactorer storage

### Module Structure

```
infrastructure/datacloud/
├── src/main/java/com/ghatana/yappc/infrastructure/datacloud/
│   ├── mapper/
│   │   └── YappcEntityMapper.java
│   └── adapter/
│       ├── YappcDataCloudRepository.java
│       ├── DashboardDataCloudAdapter.java
│       ├── DashboardRepositoryFactory.java
│       ├── KnowledgeGraphDataCloudPlugin.java
│       └── RefactorerStorageAdapter.java
└── src/test/java/com/ghatana/yappc/infrastructure/datacloud/
    ├── mapper/
    │   └── YappcEntityMapperTest.java
    ├── adapter/
    │   └── YappcDataCloudRepositoryTest.java
    └── DataCloudIntegrationTest.java
```

## Usage

### Creating a Repository

```java
// Initialize mapper and repository
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());
YappcEntityMapper mapper = new YappcEntityMapper(objectMapper);

EntityRepository entityRepository = ...; // from data-cloud

// Create Dashboard repository
DashboardRepository dashboardRepo = DashboardRepositoryFactory.createDataCloudRepository(
    entityRepository, 
    mapper
);

// Use repository
Promise<Dashboard> saved = dashboardRepo.save(dashboard);
Promise<Optional<Dashboard>> found = dashboardRepo.findById(dashboardId);
```

### Custom Entity Adapter

```java
// Create a generic repository for any entity type
YappcDataCloudRepository<MyEntity> repo = new YappcDataCloudRepository<>(
    entityRepository,
    mapper,
    "my_collection",
    MyEntity.class
);

Promise<MyEntity> saved = repo.save(entity);
Promise<List<MyEntity>> all = repo.findAll();
```

## Dependencies

- **data-cloud:spi** - Data-Cloud SPI for entity storage
- **data-cloud:domain** - Data-Cloud domain models
- **data-cloud:core** - Data-Cloud core functionality
- **ActiveJ** - Async/Promise support
- **Jackson** - JSON serialization/deserialization
- **SLF4J** - Logging

## Testing

Run tests with:

```bash
./gradlew :products:yappc:infrastructure:datacloud:test
```

Tests include:
- Unit tests for mapper and repository
- Integration tests for adapter behavior
- Mock-based testing for data-cloud interactions

## Migration Path

This module is designed to support gradual migration from JPA to data-cloud:

1. **Phase 1** - Create infrastructure module (✓ Complete)
2. **Phase 2** - Migrate entities to data-cloud adapters
3. **Phase 3** - Update services to use adapters
4. **Phase 4** - Remove JPA dependencies

## Future Enhancements

- [ ] Query API support for complex filtering
- [ ] Batch operations for bulk inserts/updates
- [ ] Caching layer for frequently accessed entities
- [ ] Event sourcing support
- [ ] Audit trail tracking
