# kernel-bridge

## Purpose

`products/aep/kernel-bridge` integrates AEP capabilities into the platform kernel extension system. It owns:

- `AepKernelExtension` — registers AEP as a kernel extension, binding AEP services into the kernel lifecycle
- `AepBridgeCapabilities` — declares the AEP capabilities (agent registry, pipeline execution, event cloud) that the kernel exposes to other products

This module enables cross-product capability discovery: other products ask the kernel for AEP capabilities rather than depending on `aep-*` modules directly.

## Boundaries

- **Uses:** `platform-kernel` SPI for extension registration; `aep-central-runtime` for the registry façade
- **Does not own:** agent execution, identity, or compliance — those belong to their respective modules
- **Products must not depend on this module directly** — they access AEP capabilities through the kernel

## Key classes

| Class | Role |
|---|---|
| `AepKernelExtension` | Kernel extension entry point; registered via `META-INF/services/` |
| `AepBridgeCapabilities` | Typed capability descriptor for kernel capability negotiation |

## Verification

```bash
./gradlew :products:aep:kernel-bridge:test
```
