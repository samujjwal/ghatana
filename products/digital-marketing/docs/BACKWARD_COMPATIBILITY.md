# DMOS Backward Compatibility Policy

## Database Schema

### Breaking Changes

Database schema changes that break backward compatibility require:
- A new migration that preserves existing data
- A data migration script to transform old data to new format
- Both old and new code to be supported during transition

### Non-Breaking Changes

Non-breaking schema changes:
- Adding new tables
- Adding new columns (with defaults)
- Adding new indexes
- Adding new views

### Migration Process

1. Deploy migration
2. Deploy new application code
3. Verify compatibility
4. Deprecate old code after transition period

## API

### Versioning

API version is included in URL: `/api/v1/...`

### Breaking Changes

API breaking changes require:
- New version endpoint (e.g., `/api/v2/...`)
- Old version maintained for at least 2 major versions
- Migration guide for clients

### Non-Breaking Changes

Non-breaking API changes:
- Adding new endpoints
- Adding optional fields to responses
- Adding new query parameters

## Configuration

### Environment Variables

New environment variables must:
- Have sensible defaults
- Be documented in ENVIRONMENT_VARIABLES.md
- Not break existing functionality if unset

### Deprecation

Deprecated environment variables:
- Log deprecation warning
- Support for at least 2 releases
- Document migration path

## Data Formats

### JSON

JSON responses must:
- Maintain existing fields
- Add new fields at end of objects
- Support both old and new formats during transition

### Dates

All dates use ISO 8601 format: `YYYY-MM-DDTHH:MM:SSZ`

## Client Compatibility

### UI

UI must:
- Support at least 2 major browser versions back
- Graceful degradation for older browsers
- Progressive enhancement for newer features

### Connectors

Connector changes must:
- Support existing connector configurations
- Provide migration path for new configurations
- Maintain compatibility with external APIs

## Rollback Plan

If a breaking change causes issues:
1. Revert to previous version
2. Investigate root cause
3. Provide fix
4. Re-deploy with fix
5. Communicate with stakeholders
