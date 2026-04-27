/**
 * @tutorputor/auth-client
 *
 * Shared client-side auth utilities for the TutorPutor platform.
 *
 * Exports:
 * - `./token` — JWT decode, claim extraction, expiry checks
 * - `./storage` — Platform-agnostic token persistence interface + implementations
 * - `./headers` — Canonical auth request header builder
 *
 * @doc.type module
 * @doc.purpose Re-export barrel for @tutorputor/auth-client
 * @doc.layer product
 * @doc.pattern Facade
 */

export * from "./token.js";
export * from "./storage.js";
export * from "./headers.js";
