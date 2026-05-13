# Product Artifact Contract

This document defines the contract for product artifacts.

## Artifact Types

Kernel defines standard artifact types:

```json
{
  "artifactTypes": {
    "jar": "Java JAR file",
    "container": "Docker container image",
    "static-web-bundle": "Static web application bundle",
    "mobile-ios-app": "iOS application package",
    "mobile-android-app": "Android application package"
  }
}
```

## Artifact Manifest

Each build phase emits an artifact manifest:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "product-id",
  "version": "1.0.0",
  "buildId": "build-uuid",
  "artifacts": [
    {
      "id": "artifact-id",
      "type": "jar",
      "surface": "backend-api",
      "path": "build/libs/product.jar",
      "checksumAlgorithm": "sha256",
      "checksum": "abc123...",
      "sizeBytes": 1024000,
      "metadata": {
        "buildTime": "2024-01-01T00:00:00Z"
      }
    }
  ]
}
```

## Artifact Validation

Artifacts are validated against declared types in `kernel-product.yaml`:

```yaml
surfaces:
  backend-api:
    artifacts:
      - type: jar
        required: true
      - type: container
        required: true
```

## Artifact Lifecycle

1. **Build phase**: Emits build artifacts (JAR, bundles)
2. **Package phase**: Consumes build artifacts, emits deployable artifacts (containers)
3. **Deploy phase**: Consumes deployable artifacts
4. **Release phase**: Creates release artifacts with version tags

## Artifact Storage

Artifacts are stored in configured artifact repositories:
- Local filesystem for development
- Container registry for container artifacts
- Package repository for JAR artifacts
