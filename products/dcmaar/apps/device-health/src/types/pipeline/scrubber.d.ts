/**
 * @file Type definitions for the pipeline scrubber module
 */

export function scrubObject<T>(obj: T, options?: { redactKeys?: string[] }): T;
