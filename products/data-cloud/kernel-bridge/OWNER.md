# Owner: Data-Cloud Kernel Bridge

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Adapter bridge that registers a live Data-Cloud storage adapter into the platform
kernel context. Implements the port-adapter pattern: the kernel defines the
`DataCloudKernelAdapter` interface; this module provides the concrete wiring
without introducing a circular product dependency.

**Dependency direction:**
```
kernel-core  ←── data-cloud-kernel-bridge  ←── data-cloud (DataCloudClient impl)
```

## Key Interfaces

| Class | Purpose |
|-------|---------|
| `DataCloudKernelExtension` | KernelExtension that registers the adapter |
| `DataCloudBridgeCapabilities` | Typed KernelCapability constants |

## Dependencies

- `platform-kernel` — `AbstractKernelExtension`, `KernelAdapter` port interfaces
- `products:data-cloud:spi` — `EventLogStore` SPI

## Consumers

- Product launcher wiring — registers bridge extension at startup
- Kernel consumers — resolve `DataCloudKernelAdapter` via `KernelContext`
