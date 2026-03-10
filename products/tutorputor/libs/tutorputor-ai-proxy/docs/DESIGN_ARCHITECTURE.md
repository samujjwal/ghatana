# Tutorputor AI Proxy Service – Design & Architecture

## 1. Purpose

The **Tutorputor AI Proxy** service mediates access to AI providers for Tutorputor flows.

## 2. Responsibilities

- Provide a stable API over multiple AI backends.
- Handle provider-specific details, rate limits, and safety filters.

## 3. Architectural Position

- Backend service under `products/tutorputor/services/tutorputor-ai-proxy`.
- Consumed by Tutorputor learning, assessment, and content services.

This document is self-contained and summarizes the role and architecture of the Tutorputor AI Proxy service.
