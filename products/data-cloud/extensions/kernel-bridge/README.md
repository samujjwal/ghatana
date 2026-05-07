# data-cloud-kernel-bridge

**Module:** `products:data-cloud:kernel-bridge`
**Owner:** Platform Kernel Team

## Purpose

This module bridges **Data-Cloud** into the **Platform Kernel** via the `KernelExtension` SPI.

The dependency direction is:

```
kernel-core   ← [no knowledge of Data-Cloud]
      ↑ (imports)
data-cloud-kernel-bridge  ← [knows both kernel-core and data-cloud:spi]
      ↑ (imports)
data-cloud:spi            ← [concrete DataCloudClient implementations]
```

The kernel defines the **port** (`DataCloudKernelAdapter` interface + request/result types in
`kernel-core`). This bridge module provides the **`KernelExtension`** that wires a concrete
`DataCloudKernelAdapterImpl.DataCloudClient` implementation into the kernel context at boot time.

## Usage

### In a product launcher (e.g. Data-Cloud launcher wired alongside a kernel module)

```java
// 1. Obtain a DataCloudKernelAdapterImpl.DataCloudClient (your product implementation)
DataCloudKernelAdapterImpl.DataCloudClient client = new MyDataCloudClientImpl(...);

// 2. Create the bridge extension
DataCloudKernelExtension extension = new DataCloudKernelExtension(client);

// 3. Register with the hosting KernelModule before module start
hostModule.registerExtension(extension);
```

After the module initializes, any code that calls:

```java
DataCloudKernelAdapter adapter = context.getDependency(DataCloudKernelAdapter.class);
```

will receive the live, Data-Cloud-backed adapter instance.

## Architecture

- `DataCloudKernelExtension` — `AbstractKernelExtension` impl; registers the adapter on `onInitialize`
- `DataCloudBridgeCapabilities` — canonical `KernelCapability` constants contributed by this extension

## Changelog

| Version | Change |
|---------|--------|
| 1.0.0   | Initial bridge module — moves adapter registration out of `kernel-core` |
