# Data Cloud SDK

Auto-generated SDKs for the Data Cloud Platform API.

## Overview

The Data Cloud SDK provides language-specific client libraries for interacting with the Data Cloud Platform API. SDKs are automatically generated from the OpenAPI specification and support Java, TypeScript, and Python.

## Supported Languages

- **Java**: `java/src/main/java/com/ghatana/datacloud/sdk/generated/DataCloudJavaSdk.java`
- **TypeScript**: `typescript/src/index.ts`
- **Python**: `python/datacloud_sdk/client.py`

## Installation

### Java

The Java SDK is built as part of the Data Cloud project. Add the dependency to your project:

```gradle
implementation 'com.ghatana:datacloud-sdk:1.0.0-SNAPSHOT'
```

### TypeScript

```bash
npm install @ghatana/datacloud-sdk
```

### Python

```bash
pip install datacloud-sdk
```

## Quick Start

### Java

```java
import com.ghatana.datacloud.sdk.generated.DataCloudJavaSdk;

// Initialize SDK
DataCloudJavaSdk sdk = new DataCloudJavaSdk("http://localhost:8080", "your-tenant-id");

// Health check
Map<String, Object> health = sdk.health();
System.out.println("Status: " + health.get("status"));

// Create entity
Map<String, Object> entity = Map.of("name", "Example", "type", "test");
Map<String, Object> created = sdk.createEntity("my-collection", entity);
String entityId = created.get("id").toString();

// Get entity
Map<String, Object> fetched = sdk.getEntity("my-collection", entityId);

// Query entities
Map<String, Object> results = sdk.queryEntities("my-collection", 10);

// Delete entity
Map<String, Object> deleted = sdk.deleteEntity("my-collection", entityId);

// Close SDK when done
sdk.close();
```

### TypeScript

```typescript
import { DataCloudTypeScriptSdk } from '@ghatana/datacloud-sdk';

// Initialize SDK
const sdk = new DataCloudTypeScriptSdk('http://localhost:8080', 'your-tenant-id');

// Health check
const health = await sdk.health();
console.log('Status:', health.status);

// Create entity
const entity = { name: 'Example', type: 'test' };
const created = await sdk.createEntity('my-collection', entity);
const entityId = created.id;

// Get entity
const fetched = await sdk.getEntity('my-collection', entityId);

// Query entities
const results = await sdk.queryEntities('my-collection', 10);

// Delete entity
const deleted = await sdk.deleteEntity('my-collection', entityId);
```

### Python

```python
from datacloud_sdk import DataCloudPythonSdk

# Initialize SDK
sdk = DataCloudPythonSdk('http://localhost:8080', 'your-tenant-id')

# Health check
health = sdk.health()
print(f'Status: {health["status"]}')

# Create entity
entity = {'name': 'Example', 'type': 'test'}
created = sdk.create_entity('my-collection', entity)
entity_id = created['id']

# Get entity
fetched = sdk.get_entity('my-collection', entity_id)

# Query entities
results = sdk.query_entities('my-collection', 10)

# Delete entity
deleted = sdk.delete_entity('my-collection', entity_id)
```

## API Reference

### Health Check

Check the health status of the Data Cloud Platform.

- **Java**: `Map<String, Object> health()`
- **TypeScript**: `health(): Promise<JsonObject>`
- **Python**: `health(): Dict[str, Any]`

### Entity Operations

#### Create Entity

Create a new entity in a collection.

- **Java**: `Map<String, Object> createEntity(String collection, Map<String, Object> payload)`
- **TypeScript**: `createEntity(collection: string, payload: JsonObject): Promise<JsonObject>`
- **Python**: `create_entity(collection: str, payload: Dict[str, Any]) -> Dict[str, Any]`

#### Get Entity

Retrieve an entity by ID.

- **Java**: `Map<String, Object> getEntity(String collection, String id)`
- **TypeScript**: `getEntity(collection: string, id: string): Promise<JsonObject>`
- **Python**: `get_entity(collection: str, id: str) -> Dict[str, Any]`

#### Query Entities

Query entities in a collection with pagination.

- **Java**: `Map<String, Object> queryEntities(String collection, int limit)`
- **TypeScript**: `queryEntities(collection: string, limit: number): Promise<JsonObject>`
- **Python**: `query_entities(collection: str, limit: int) -> Dict[str, Any]`

#### Delete Entity

Delete an entity by ID.

- **Java**: `Map<String, Object> deleteEntity(String collection, String id)`
- **TypeScript**: `deleteEntity(collection: string, id: string): Promise<JsonObject>`
- **Python**: `delete_entity(collection: str, id: str) -> Dict[str, Any]`

## Authentication

The SDKs use tenant-based authentication. Provide your tenant ID when initializing the SDK:

- **Java**: `new DataCloudJavaSdk(baseUrl, tenantId)`
- **TypeScript**: `new DataCloudTypeScriptSdk(baseUrl, tenantId)`
- **Python**: `DataCloudPythonSdk(baseUrl, tenantId)`

The SDK will automatically include the tenant ID in request headers.

## Error Handling

All SDK methods may throw exceptions on errors:

- **Java**: `RuntimeException` with error message
- **TypeScript**: Rejected promises with error details
- **Python**: Exceptions with error message

Example error handling:

### Java

```java
try {
    Map<String, Object> entity = sdk.getEntity("collection", "id");
} catch (RuntimeException e) {
    System.err.println("Error: " + e.getMessage());
}
```

### TypeScript

```typescript
try {
    const entity = await sdk.getEntity('collection', 'id');
} catch (error) {
    console.error('Error:', error);
}
```

### Python

```python
try:
    entity = sdk.get_entity('collection', 'id')
except Exception as e:
    print(f'Error: {e}')
```

## Testing

The SDK includes comprehensive tests:

- **Smoke Tests**: Basic functionality tests against a running server
- **Correctness Tests**: Comprehensive correctness tests for error handling, edge cases, data types, and tenant isolation
- **Documentation Tests**: Verification that generated SDKs have complete and accurate documentation
- **Generation Tests**: Verification that SDK generation produces correct artifacts

Run tests with:

```bash
./gradlew :sdk:test
```

Run cross-language SDK drift checks (Java, TypeScript, Python) from repository root with:

```bash
./gradlew :products:data-cloud:delivery:sdk:check --no-build-cache --rerun-tasks
```

## Generation

SDKs are automatically generated from the OpenAPI specification. To regenerate:

```bash
./gradlew :sdk:generateSdk
```

The generation process:
1. Reads the canonical OpenAPI specification
2. Generates Java, TypeScript, and Python SDKs
3. Creates metadata documenting the generation
4. Validates generated artifacts

## Development

### Project Structure

```
sdk/
├── src/
│   ├── codegen/java/          # SDK generation code
│   └── test/java/              # SDK tests
│       ├── smoke/              # Smoke tests
│       ├── correctness/         # Correctness tests
│       ├── documentation/      # Documentation tests
│       └── generation/         # Generation tests
├── build/generated/sdk/       # Generated SDK artifacts
│   ├── java/                   # Java SDK
│   ├── typescript/             # TypeScript SDK
│   └── python/                 # Python SDK
└── build.gradle.kts
```

## License

Copyright (c) 2026 Ghatana Inc. All rights reserved.
