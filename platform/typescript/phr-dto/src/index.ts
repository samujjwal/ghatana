/**
 * Shared PHR DTO contracts
 *
 * This package contains shared Data Transfer Object (DTO) types used across
 * PHR web and mobile applications. By sharing these contracts, we ensure
 * consistency between platforms and reduce duplication.
 *
 * @doc.type module
 * @doc.purpose Shared PHR DTO contracts for web and mobile
 * @doc.layer platform
 */

// Export Zod schemas for runtime validation (includes type inference)
export * from './schemas';

// Export missing data state utilities
export * from './missing-data';

// Export FHIR to UI transformation adapter
export * from './fhir-adapter';
