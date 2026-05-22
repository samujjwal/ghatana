# Rust Fixture Product

A minimal Rust service and library used to validate the Kernel's CargoRustAdapter can handle Rust projects through the full lifecycle.

## Purpose

This fixture product validates:
- CargoRustAdapter validate phase (fmt, check, clippy)
- CargoRustAdapter test phase (cargo test)
- CargoRustAdapter build phase (cargo build --release)
- CargoRustAdapter package phase (cargo build --release)
- Workspace member detection
- Binary name resolution from Cargo metadata
- Cross-platform artifact handling

## Structure

- `src/lib.rs` - Simple library with greeting function and tests
- `src/main.rs` - Minimal HTTP service using tokio
- `Cargo.toml` - Package configuration with lib and bin targets

## Lifecycle Phases

```bash
# Validate (runs fmt --check, check, clippy)
cargo fmt --check
cargo check
cargo clippy -- -D warnings

# Test (runs cargo test)
cargo test

# Build (runs cargo build --release)
cargo build --release

# Package (runs cargo build --release)
cargo build --release
```

## Artifacts

- Library: `target/release/librust_fixture.rlib` (Unix) or `rust_fixture.dll` (Windows)
- Binary: `target/release/rust_fixture_service` (Unix) or `rust_fixture_service.exe` (Windows)
