# Artifacts

## Overview

The Kernel artifact system manages artifact manifests and validation. Artifacts are the outputs of the build and package phases.

## Artifact Types

### jar
- **Description**: Java JAR artifact
- **Produced by**: GradleJavaServiceAdapter
- **Surfaces**: backend-api, worker, operator
- **Validation**: Must exist, must be valid JAR

### static-web-bundle
- **Description**: Static web bundle (HTML, CSS, JS)
- **Produced by**: PnpmViteReactAdapter
- **Surfaces**: web
- **Validation**: Must exist, must contain index.html

### docker-image
- **Description**: Docker container image
- **Produced by**: DockerBuildxAdapter
- **Surfaces**: backend-api, web
- **Validation**: Must exist, must be valid image reference

### mobile-ios-app
- **Description**: iOS mobile application
- **Produced by**: XcodeIosAdapter
- **Surfaces**: mobile-ios
- **Validation**: Must exist, must be valid .app or .ipa

### mobile-android-app
- **Description**: Android mobile application
- **Produced by**: GradleAndroidAdapter
- **Surfaces**: mobile-android
- **Validation**: Must exist, must be valid .apk or .aab

## Artifact Manifest

Artifact manifests are generated during the build phase and validated during package phase:

```json
{
  "schemaVersion": "1.0.0",
  "productId": "digital-marketing",
  "version": "1.0.0",
  "artifacts": {
    "backend-api": {
      "type": "jar",
      "path": "products/digital-marketing/dm-api/build/libs/dm-api-1.0.0.jar",
      "checksum": "sha256:abc123..."
    },
    "web": {
      "type": "static-web-bundle",
      "path": "products/digital-marketing/ui/dist",
      "checksum": "sha256:def456..."
    }
  }
}
```

## Artifact Validation

Artifact validation ensures:
- Declared artifacts exist
- Artifact types match expected types
- Artifact checksums are valid
- Artifact paths are accessible

Missing declared artifacts fail the build/package phase.
