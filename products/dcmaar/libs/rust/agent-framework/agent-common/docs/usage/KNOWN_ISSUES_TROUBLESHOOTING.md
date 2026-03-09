# DCMaar Agent Common – Known Issues & Troubleshooting

## 1. Known Issues

- **Breaking changes in shared types** can break multiple binaries at once.

## 2. Troubleshooting Scenarios

### 2.1 Type or Serialization Mismatches

- **Checks**:
  - Verify versions of dependent crates.
  - Regenerate protobuf bindings if relevant.

This document is self-contained and lists common issues and mitigations for `dcmaar-agent-common`.
