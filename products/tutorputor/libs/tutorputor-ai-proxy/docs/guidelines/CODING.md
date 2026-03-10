# Tutorputor AI Proxy Service – Coding Guidelines

## 1. Scope

These guidelines apply to `products/tutorputor/services/tutorputor-ai-proxy`.

## 2. Design Principles

- Keep provider-specific details behind well-defined interfaces.
- Centralize safety filters, rate limiting, and retries.
- Make it easy for other Tutorputor services to switch or add providers without code changes.

This document is self-contained and defines how to structure code in the Tutorputor AI Proxy service.
