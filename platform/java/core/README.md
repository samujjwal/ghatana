# platform/java/core

Foundational Java platform module.

This module provides the low-level contracts and utilities used across platform libraries, shared services, and products.

## Main responsibilities

- base exception hierarchy and error categorisation
- async client lifecycle contracts
- core utility classes for validation, strings, JSON, dates, and collections
- paging and common value types

## Error handling model

The canonical internal error taxonomy lives in [platform/java/core/src/main/java/com/ghatana/platform/core/exception/ErrorCode.java](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/exception/ErrorCode.java).

- Use `ErrorCode` for internal classification and branching.
- Use `PlatformException` and the surrounding exception hierarchy for Java service failures.
- Convert to the wire-format protobuf types only at transport boundaries.

The canonical wire contract lives in [platform/contracts/src/main/proto/ghatana/contracts/common/v1/error.proto](/Users/samujjwal/Development/ghatana/platform/contracts/src/main/proto/ghatana/contracts/common/v1/error.proto).

Rule of thumb:

- internal Java logic: `ErrorCode` plus platform exceptions
- cross-service transport: protobuf `ErrorProto` and related messages

## Async client contract

The shared async client baseline is [platform/java/core/src/main/java/com/ghatana/platform/core/client/AsyncClient.java](/Users/samujjwal/Development/ghatana/platform/java/core/src/main/java/com/ghatana/platform/core/client/AsyncClient.java).

Clients should:

- expose explicit `start()` and `stop()` lifecycle methods
- use rejected `Promise`s for transport, timeout, and connectivity failures
- reserve typed result payloads for expected domain outcomes
- avoid silent failure paths

## Utility guidance

Utilities in this module should stay generic and platform-safe.

- Put domain-specific helpers in the owning module, not in `core`
- Prefer focused utility classes over catch-all helpers
- Keep null-safety and error-surfacing behavior explicit

## Testing guidance

- add focused unit tests for utility contracts and exception mapping
- add regression tests when changing shared error or client semantics
- avoid introducing product-owned concepts into this module
