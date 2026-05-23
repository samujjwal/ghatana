# Multi-stage build for Rust service
FROM rust:1.75-alpine AS builder
WORKDIR /app
COPY Cargo.toml Cargo.lock ./
COPY src ./src
RUN cargo build --release

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/target/release/polyglot-fixture-rust .
EXPOSE 3002
CMD ["./polyglot-fixture-rust"]
