# Tutorputor API Gateway – Coding Guidelines

## 1. Scope

These guidelines apply to `products/tutorputor/apps/api-gateway`.

## 2. Design Principles

- Keep gateway handlers thin; delegate business logic to backend services.
- Centralize authentication, authorization, rate limiting, and request validation.
- Use clear routing and versioned APIs for clients.

This document is self-contained and defines how to structure code in the Tutorputor API Gateway.
