# Virtual-Org Java Framework – User Manual

## 1. Audience

This manual is for backend engineers integrating services with the Virtual-Org Java Framework.

## 2. Basic Integration Steps

1. Add the framework module as a dependency in the target service.
2. Implement or extend framework interfaces for organizations, departments, and agents.
3. Wire events into the platform event runtime.
4. Expose APIs or UIs that surface organization and workflow information.

## 3. Best Practices

- Keep product-specific logic outside the framework layer.
- Reuse shared platform abstractions for HTTP, observability, and state.

This manual is self-contained and explains how to adopt the Virtual-Org Java Framework in services.
