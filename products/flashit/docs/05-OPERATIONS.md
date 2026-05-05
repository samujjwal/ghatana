# FlashIt Operations

FlashIt runtime wiring must inherit shared compose, Docker, secret, and observability conventions. Product-local overrides are limited to domain services, ports, and dashboards.

Local development ports must be declared through [products/flashit/.env.example](/Users/samujjwal/Development/ghatana/products/flashit/.env.example) and consumed by [products/flashit/docker-compose.local.yml](/Users/samujjwal/Development/ghatana/products/flashit/docker-compose.local.yml). Repository CI enforces cross-product uniqueness with `check:local-dev-port-allocations`, so new runtime services must reserve ports through env-backed declarations rather than hardcoded compose literals.
