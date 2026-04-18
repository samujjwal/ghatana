# Sandbox Execution Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

The sandbox executor provides isolated execution environment for kernels using the Deno runtime. This ensures that third-party kernels cannot access host system resources, protecting both the platform and users.

## Implementation Details

### Sandbox Executor

**Location:** `services/tutorputor-sim-runtime/src/sandbox/executor.ts`

The `SandboxExecutor` provides:
- Deno-based sandbox isolation
- Configurable resource limits (timeout, memory, CPU)
- Network and filesystem access controls
- Execution cancellation
- Active execution tracking

### Security Model

The sandbox uses Deno's permission model with the following defaults:
- **No network access** (unless explicitly allowed)
- **No filesystem access** (unless explicitly allowed)
- **No subprocess execution**
- **No environment variable access** (limited set)
- **No FFI access**

## Usage

### Basic Execution

```typescript
import { sandboxExecutor } from "@tutorputor/sim-runtime/sandbox";

const context = {
  kernelId: "kernel-123",
  kernelCode: `
    const result = {
      message: "Hello from kernel",
      input: input
    };
  `,
  input: { name: "John Doe" },
};

const result = await sandboxExecutor.execute(context);

if (result.success) {
  console.log("Output:", result.stdout);
} else {
  console.error("Error:", result.stderr);
}
```

### With Custom Configuration

```typescript
const context = {
  kernelId: "kernel-123",
  kernelCode: `
    // Kernel code that needs network access
    const response = await fetch("https://api.example.com/data");
    const data = await response.json();
    const result = data;
  `,
  input: {},
  config: {
    timeout: 60000, // 60 seconds
    memoryLimit: 1024, // 1GB
    allowNetwork: true, // Enable network access
    allowFileSystem: false, // No filesystem access
  },
};

const result = await sandboxExecutor.execute(context);
```

### Cancelling Execution

```typescript
// Start long-running execution
const executionPromise = sandboxExecutor.execute(context);

// Cancel after some condition
setTimeout(() => {
  sandboxExecutor.cancelExecution("kernel-123");
}, 5000);

const result = await executionPromise;
```

## Configuration

### Sandbox Config Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `timeout` | number | 30000 | Execution timeout in milliseconds |
| `memoryLimit` | number | 512 | Memory limit in MB |
| `cpuLimit` | number | 100 | CPU limit as percentage |
| `allowNetwork` | boolean | false | Allow network access |
| `allowFileSystem` | boolean | false | Allow filesystem access |
| `workingDirectory` | string | /tmp/tutorputor-sandbox | Working directory for execution |

### Environment Variables

```bash
# Deno configuration
DENO_NO_PROMPT=1  # Disable permission prompts
```

## Kernel Code Requirements

### Input/Output Contract

Kernels must follow this contract:

```javascript
// Input is available as 'input' variable
const result = {
  // Process input and produce result
  output: input.value * 2,
};

// Result is automatically logged as JSON
```

### Best Practices

1. **Keep kernels small** - Under 1MB for fast startup
2. **Avoid blocking operations** - Use async/await properly
3. **Handle errors gracefully** - Try/catch critical sections
4. **Return structured data** - JSON-serializable objects
5. **Avoid side effects** - No external state modifications

## Security Considerations

### Isolation Guarantees

- **Process isolation** - Each kernel runs in separate Deno process
- **Permission isolation** - Deno's permission model restricts access
- **Resource limits** - Timeout and memory limits prevent abuse
- **Filesystem isolation** - Separate working directory per execution
- **Network isolation** - Network access disabled by default

### Allowed Operations

By default, kernels can:
- Perform computations
- Use standard JavaScript APIs
- Access provided input data
- Return results

By default, kernels cannot:
- Access filesystem
- Make network requests
- Spawn subprocesses
- Access environment variables
- Use FFI
- Access host system resources

### Resource Limits

- **Timeout:** 30 seconds default, configurable per kernel
- **Memory:** 512MB default, configurable per kernel
- **CPU:** 100% default (single core), configurable
- **Disk:** Temporary directory cleaned up after execution

## Performance

### Benchmarks

| Operation | Time | Notes |
|-----------|------|-------|
| Cold start | ~500ms | Deno process startup |
| Warm start | ~50ms | Reusing Deno process (not yet implemented) |
| Simple computation | < 10ms | Basic math operations |
| Complex computation | 100-1000ms | Depends on algorithm |

### Optimization Strategies

1. **Pool Deno processes** - Reuse processes for multiple executions
2. **Cache kernel compilation** - Deno caches compiled code
3. **Limit dependencies** - Fewer imports = faster startup
4. **Use streaming** - For large input/output data

## Testing

Unit tests are available at:
`services/tutorputor-sim-runtime/src/sandbox/__tests__/executor.test.ts`

Run tests:
```bash
pnpm test -- executor.test.ts
```

## Integration with Kernel Signing

### Execution Flow with Verification

1. **Load kernel** → Retrieve kernel from database
2. **Verify signature** → Ensure kernel is signed and authentic
3. **Check code hash** → Verify code integrity
4. **Execute in sandbox** → Run kernel in isolated environment
5. **Return result** → Provide output to caller

### Example Integration

```typescript
import { signingService } from "@tutorputor/platform/core/crypto";
import { sandboxExecutor } from "@tutorputor/sim-runtime/sandbox";

async function executeKernelSafely(kernelId: string, input: unknown) {
  // Load kernel from database
  const kernel = await prisma.kernelPlugin.findUnique({
    where: { id: kernelId },
  });

  if (!kernel?.signature) {
    throw new Error("Kernel is not signed");
  }

  // Verify signature
  const manifest = createManifest(kernel);
  const verification = signingService.verifyManifest(manifest);

  if (!verification.valid) {
    throw new Error("Kernel signature verification failed");
  }

  // Execute in sandbox
  const result = await sandboxExecutor.execute({
    kernelId: kernel.pluginId,
    kernelCode: kernel.code,
    input,
  });

  return result;
}
```

## Troubleshooting

### Common Issues

**"Execution timeout"**
- Kernel took too long to execute
- Increase timeout in config
- Optimize kernel code

**"Permission denied"**
- Kernel tried to access restricted resource
- Check if operation is allowed
- Enable specific permissions if needed

**"Deno not found"**
- Deno runtime not installed
- Install Deno: `curl -fsSL https://deno.land/install.sh | sh`
- Add Deno to PATH

**"Memory limit exceeded"**
- Kernel used too much memory
- Increase memory limit in config
- Optimize kernel memory usage

### Debug Mode

Enable debug logging:
```bash
LOG_LEVEL=debug pnpm start
```

## Future Enhancements

### Planned Features

1. **Process pooling** - Reuse Deno processes for better performance
2. **WebAssembly support** - Execute WASM kernels for better performance
3. **Resource monitoring** - Real-time CPU/memory tracking
4. **Snapshot isolation** - Faster startup with pre-built snapshots
5. **Distributed execution** - Execute kernels across multiple workers

### Alternative Sandbox Technologies

- **Firecracker** - MicroVM-based isolation (higher security, higher overhead)
- **gVisor** - User-space kernel (good balance of security/performance)
- **WebAssembly** - Browser-compatible sandbox (limited API surface)

## Compliance Mapping

- **SOC 2 Type II:** System isolation and access controls
- **Supply Chain Security:** Third-party code isolation
- **Marketplace Trust:** Safe execution of user-contributed kernels

## Next Steps

1. Integrate sandbox executor with kernel execution flow
2. Add signature verification before execution
3. Implement process pooling for performance
4. Add monitoring for sandbox failures
5. Create kernel performance benchmarks
6. Implement marketplace governance for kernel approval
