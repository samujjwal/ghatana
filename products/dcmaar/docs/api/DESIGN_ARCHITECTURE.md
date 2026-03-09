# DCMaar – API & Contracts – Design & Architecture

## 1. Purpose

This document describes the **API and contract layer** of the DCMaar platform: gRPC/HTTP APIs and their protobuf/JSON contracts.

## 2. Responsibilities

- Define stable, versioned contracts between agents, backends, and UIs.
- Enforce mTLS, deadlines, and message size limits for secure and reliable communication.

## 3. Architectural Position

- gRPC and HTTP APIs are generated from protobuf definitions and shared JSON schemas.
- Agents and backends rely on generated clients; UIs integrate via HTTP/JSON and bridge protocols.

This document is self-contained and summarizes the role of APIs and contracts in the DCMaar architecture.
