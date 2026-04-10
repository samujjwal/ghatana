# State Ownership Patterns

## Platform-Level Atoms
Location: `@ghatana/state`

Atoms that are shared across all products and live in the platform:
- Authentication state (auth token, user email, expiry)
- Tenant selection (current tenant, available tenants)
- Platform notifications (notification list, unread count)
- Generic platform settings applicable to all apps

## Canvas-Specific Atoms
Location: `@ghatana/canvas`

Atoms that are specific to the canvas rendering engine and should live co-located with the canvas package:
- Canvas viewport state (pan, zoom, viewport bounds)
- Canvas selection state (selected element IDs)
- Canvas tool state (active tool, tool options)
- Canvas layer state (layer order, visibility)

## Product-Level Atoms
Location: Product libraries (e.g., `@yappc/state` if needed)

Atoms that are specific to a single product's business logic:
- Product-specific workflow state
- Product-specific UI configuration
- Product-specific session data not shared with other products

## App-Level Atoms
Location: App code (`apps/*/src/`)

Atoms that are transient or bound to a single application:
- App-specific transient UI state
- Component-level state that doesn't need to be shared
- State that would cause circular dependencies if placed in a library

## Rule of Thumb

An atom belongs in `@ghatana/state` when:
1. More than one product or app reads or writes it
2. It represents a platform-level concern (auth, tenant, notifications)
3. It needs to survive navigation and be consistent across the shell

An atom should stay local when:
1. Only one feature or component uses it
2. It's transient (cleared on unmount or navigation)
3. It's product-specific business logic
