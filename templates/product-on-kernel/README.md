# Product-on-Kernel Template

This template shows how to wire a Ghatana product into the **Platform Kernel** using the correct dependency direction.

## Four Key Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Gradle module declaration with kernel dependencies |
| `ProductNameKernelModule.java` | Full product module (initializes, starts, stops services) |
| `ProductNameKernelExtension.java` | Thin extension contributing a single capability |
| `README.md` (this file) | Architecture guide |

## Architecture: Kernel Ports, Product Adapters

```
                  ┌──────────────────────────────────────────┐
                  │            platform-kernel:kernel-core    │
                  │  KernelModule  KernelExtension  Adapters  │
                  │  DataCloudKernelAdapter  AepKernelAdapter │
                  └──────────────────────────────────────────┘
                              ▲ (imports)
              ┌───────────────┼────────────────────┐
              │               │                    │
   ┌──────────┴──────┐   ┌────┴────────────┐  ┌───┴──────────────────────┐
   │ data-cloud-     │   │ aep-kernel-     │  │  yappc-kernel-bridge     │
   │ kernel-bridge   │   │ bridge          │  │  (YappcPluginBridge)     │
   │ (DataCloud ext) │   │ (AepExtension)  │  └──────────────────────────┘
   └─────────────────┘   └─────────────────┘
              ▲                    ▲
    products/data-cloud     products/data-cloud/planes/action
         (provides               (provides
      DataCloudClient)         AepClient)
```

**Kernel does not import products.** Products import kernel-core and register their capabilities
into the context via `KernelExtension` implementations.

## When to use KernelModule vs KernelExtension

| Scenario | Use |
|----------|-----|
| Full product lifecycle (init → start → stop) with multiple services | `KernelModule` |
| Bridging a single external service into the context | `KernelExtension` |
| Contributing a new capability to an existing module | `KernelExtension` |
| Creating a standalone deployable product node | `KernelModule` |

## Registration Pattern

### Extension (recommended for bridge modules)

```java
// In your product launcher / bootstrap
MyKernelExtension extension = new MyKernelExtension(myExternalClient);
hostModule.registerExtension(extension);
```

### Module

```java
// In your KernelRuntime initialization
KernelRuntime runtime = KernelRuntime.builder()
    .withModule(new MyProductKernelModule())
    .build();
runtime.start();
```

## Capability Declaration

Extensions and modules declare capabilities they **provide** (via `getContributedCapabilities()`)
and capabilities they **require** (via `getRequiredCapabilities()` on modules). The kernel runtime
validates dependencies before starting modules.

```java
// Require Data-Cloud storage before starting
@Override
public Set<KernelCapability> getRequiredCapabilities() {
    return Set.of(DataCloudBridgeCapabilities.DATA_CLOUD_STORAGE);
}
```

## Accessing a Kernel Service

Once a bridge extension registers a service, any module in the same kernel runtime can retrieve it:

```java
// In a module's afterInitialized:
DataCloudKernelAdapter dataCloud = context.getDependency(DataCloudKernelAdapter.class);
AepKernelAdapter aep = context.getDependency(AepKernelAdapter.class);
PluginRegistry plugins = context.getDependency(PluginRegistry.class);
```

## Testing

For unit tests, depend on `platform-kernel:kernel-testing`:

```java
class MyModuleTest extends EventloopTestBase {

    @Mock KernelContext context;

    @Test
    void myModuleRegistersAdapter() {
        MyKernelExtension ext = new MyKernelExtension(new StubClient());
        ext.onModuleInitialized(context);
        verify(context).registerService(eq(MyAdapter.class), any(MyAdapter.class));
    }
}
```
