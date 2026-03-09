# Enhanced Architecture Migration Guide

## Overview

This guide helps you migrate from the legacy YAPPC scaffold system to the Enhanced Architecture. The migration is designed to be **non-breaking** with a clear upgrade path.

## Migration Strategy

### Strategy: Gradual Adoption

The Enhanced Architecture is **fully backward compatible**. You can:

1. Continue using existing packs without changes
2. Gradually adopt new features as needed
3. Migrate at your own pace

**No immediate action required** - existing code continues to work.

---

## What's New

### 1. Multi-Module Compositions

**Before:** Single-module projects only

```json
{
  "name": "my-service",
  "type": "service",
  "pack": "go-service-gin"
}
```

**After:** Multi-module projects with integrations

```json
{
  "version": "1.0",
  "metadata": { "name": "my-fullstack-app" },
  "modules": [
    { "id": "frontend", "pack": "ts-react-vite", "path": "./frontend" },
    { "id": "backend", "pack": "go-service-gin", "path": "./backend" }
  ],
  "integrations": [
    { "id": "api", "type": "api-client", "from": "frontend", "to": "backend" }
  ]
}
```

### 2. Advanced Templates

**Before:** Simple variable substitution

```handlebars
package {{projectName}};
```

**After:** Full Handlebars with conditionals, loops, helpers

```handlebars
package
{{packagePath projectName}};

{{#if enableAuth}}
  import com.auth.AuthService;
{{/if}}

{{#each services}}
  public class
  {{pascalCase this}}Service { }
{{/each}}
```

### 3. Automatic Integration Generation

**New:** Cross-module integration code generated automatically

- API clients with retry logic
- Database connections with pooling
- Event producers/consumers
- Type sharing

### 4. Schema Validation

**New:** JSON Schema validation for all configurations

- Catch errors early
- Better IDE support
- Clear error messages

---

## Migration Paths

### Path 1: Continue Using Existing Packs (No Changes)

**Action:** None required

Your existing packs work unchanged:

```bash
yappc generate --pack go-service-gin --output ./my-service
```

**Status:** ✅ Fully supported

---

### Path 2: Adopt Multi-Module Compositions

**Action:** Create composition file

1. **Create composition.json:**

```json
{
  "version": "1.0",
  "type": "custom",
  "metadata": {
    "name": "my-app",
    "description": "My application"
  },
  "modules": [
    {
      "id": "backend",
      "name": "Backend Service",
      "type": "service",
      "pack": "go-service-gin",
      "path": "./backend",
      "variables": {
        "port": 8080
      }
    }
  ]
}
```

2. **Generate project:**

```bash
yappc generate --composition composition.json --output ./my-app
```

**Benefits:**

- Multiple modules in one project
- Automatic dependency resolution
- Integration code generation

---

### Path 3: Use Advanced Templates

**Action:** Update templates to use Handlebars features

**Before (SimpleTemplateEngine):**

```handlebars
package {{projectName}}; public class {{serviceName}} { // Implementation }
```

**After (HandlebarsTemplateEngine):**

```handlebars
package
{{packagePath projectName}};

{{#if enableAuth}}
  import com.auth.AuthService;
{{/if}}

/** *
{{description}}
* * @author
{{author}}
* @version
{{version}}
*/ public class
{{pascalCase serviceName}}
{
{{#each methods}}
  public void
  {{camelCase this}}() { // Implementation }
{{/each}}
}
```

**Available Helpers:**

- String: `lowercase`, `uppercase`, `capitalize`, `pascalCase`, `camelCase`, `snakeCase`, `kebabCase`
- Comparison: `eq`, `ne`, `gt`, `lt`
- Logical: `and`, `or`, `not`
- Utility: `uuid`, `year`, `date`, `json`
- Path: `pathJoin`, `baseName`, `dirName`
- Text: `pluralize`, `singularize`

**Status:** ✅ Backward compatible (simple variables still work)

---

### Path 4: Add Integration Generation

**Action:** Define integrations in composition

```json
{
  "modules": [
    { "id": "frontend", "pack": "ts-react-vite" },
    { "id": "backend", "pack": "go-service-gin" }
  ],
  "integrations": [
    {
      "id": "frontend-backend-api",
      "type": "api-client",
      "from": "frontend",
      "to": "backend",
      "variables": {
        "apiEndpoint": "http://localhost:8080/api",
        "authType": "jwt"
      }
    }
  ]
}
```

**Generated:**

- `frontend/src/api/backend-client.ts` - Type-safe API client
- `frontend/src/types/backend.ts` - Shared types
- `frontend/.env.example` - Environment config

**Benefits:**

- No manual integration code
- Type-safe clients
- Consistent patterns

---

## API Changes

### Deprecated APIs

#### SimpleTemplateEngine

**Status:** Deprecated (still works)

```java
// Old way (still works)
SimpleTemplateEngine engine = new SimpleTemplateEngine();

// New way (recommended)
HandlebarsTemplateEngine engine = new HandlebarsTemplateEngine();
```

**Migration:** Update `CoreModule` or dependency injection configuration

#### SimpleUnifiedConfigurationManager

**Status:** Deprecated (still works)

```java
// Old way (still works)
SimpleUnifiedConfigurationManager config =
    new SimpleUnifiedConfigurationManager();

// New way (recommended)
HierarchicalConfigurationManager config =
    new HierarchicalConfigurationManager();
```

**Migration:** Will be available in Phase 6

---

## Breaking Changes

### None

**All changes are backward compatible.** Existing code continues to work without modification.

---

## Step-by-Step Migration

### For Pack Authors

#### Step 1: Validate Your Pack

```bash
yappc validate --pack ./my-pack
```

Uses `pack-v1.json` schema to validate structure.

#### Step 2: (Optional) Add Module Support

Add `modules` and `integrations` to your pack.json:

```json
{
  "name": "my-fullstack-pack",
  "type": "fullstack",
  "modules": [
    { "id": "frontend", "pack": "ts-react-vite", "path": "./frontend" },
    { "id": "backend", "pack": "go-service-gin", "path": "./backend" }
  ],
  "integrations": [
    { "id": "api", "type": "api-client", "from": "frontend", "to": "backend" }
  ]
}
```

#### Step 3: (Optional) Enhance Templates

Use Handlebars features in your templates:

- Add conditionals for optional features
- Use loops for repeated structures
- Apply helpers for string transformations

#### Step 4: Test

```bash
yappc generate --pack ./my-pack --output ./test-output
```

### For Pack Users

#### Step 1: Update YAPPC CLI

```bash
# Update to latest version
yappc update
```

#### Step 2: Continue Using Existing Packs

No changes needed. Existing commands work:

```bash
yappc generate --pack go-service-gin --output ./my-service
```

#### Step 3: (Optional) Try Compositions

Create a composition file and generate:

```bash
yappc generate --composition my-composition.json --output ./my-app
```

---

## Feature Comparison

| Feature                | Legacy | Enhanced | Migration |
| ---------------------- | ------ | -------- | --------- |
| Single-module projects | ✅     | ✅       | None      |
| Multi-module projects  | ❌     | ✅       | Optional  |
| Basic templates        | ✅     | ✅       | None      |
| Advanced templates     | ❌     | ✅       | Optional  |
| Integration generation | ❌     | ✅       | Optional  |
| Schema validation      | ❌     | ✅       | Automatic |
| Template inheritance   | ❌     | ✅       | Optional  |
| Language registry      | ❌     | ✅       | Automatic |

---

## Common Migration Scenarios

### Scenario 1: Simple Service

**No migration needed.** Continue using existing packs.

### Scenario 2: Fullstack Application

**Recommended:** Use composition with integrations.

**Before:** Generate frontend and backend separately, write integration code manually.

**After:** Define composition, generate once, integrations included.

### Scenario 3: Microservices

**Recommended:** Use composition with service mesh integration.

**Before:** Generate each service separately, configure service discovery manually.

**After:** Define all services in composition, service mesh config generated.

### Scenario 4: Custom Templates

**Optional:** Enhance with Handlebars features.

**Before:** Simple variable substitution.

**After:** Conditionals, loops, helpers for more flexibility.

---

## Troubleshooting

### Issue: "Template syntax error"

**Cause:** Using Handlebars syntax in old templates

**Solution:**

- Option 1: Update to HandlebarsTemplateEngine
- Option 2: Escape Handlebars syntax: `\{{variable}}`

### Issue: "Schema validation failed"

**Cause:** Invalid pack.json structure

**Solution:** Run validation and fix errors:

```bash
yappc validate --pack ./my-pack
```

### Issue: "Module not found"

**Cause:** Module dependency not resolved

**Solution:** Check module IDs in composition match pack names

### Issue: "Integration generation failed"

**Cause:** Missing integration variables

**Solution:** Provide required variables in integration definition

---

## Rollback Plan

If you encounter issues:

1. **Revert to Legacy Mode:**

```bash
yappc generate --legacy --pack go-service-gin
```

2. **Use Old Template Engine:**

```java
@Provides
TemplateEngine templateEngine() {
    return new SimpleTemplateEngine(); // Old engine
}
```

3. **Disable Validation:**

```bash
yappc generate --no-validate --pack my-pack
```

---

## Timeline

### Phase 1: Backward Compatibility (Current)

- ✅ All existing packs work
- ✅ No breaking changes
- ✅ Optional new features

### Phase 2: Gradual Adoption (Months 1-3)

- Update documentation
- Provide migration examples
- Community feedback

### Phase 3: Deprecation Warnings (Months 4-6)

- Deprecation warnings for old APIs
- Migration tools provided
- Support for both systems

### Phase 4: Legacy Removal (Months 7-12)

- Remove deprecated APIs
- Full migration to enhanced architecture
- Legacy mode still available

---

## Support

### Documentation

- [Enhanced Architecture Proposal](ENHANCED_ARCHITECTURE_PROPOSAL.md)
- [Implementation Plan](ENHANCED_ARCHITECTURE_IMPLEMENTATION_PLAN.md)
- [API Documentation](API_DOCUMENTATION.md)

### Examples

- [Example Compositions](examples/)
- [Template Examples](examples/templates/)
- [Integration Examples](examples/integrations/)

### Community

- GitHub Issues: Report bugs and request features
- Discussions: Ask questions and share experiences
- Discord: Real-time community support

---

## Checklist

### For Pack Authors

- [ ] Validate pack with new schema
- [ ] Test with enhanced architecture
- [ ] Update documentation
- [ ] (Optional) Add multi-module support
- [ ] (Optional) Enhance templates with Handlebars

### For Pack Users

- [ ] Update YAPPC CLI
- [ ] Test existing workflows
- [ ] (Optional) Try composition feature
- [ ] (Optional) Use integration generation
- [ ] Provide feedback

---

## Conclusion

The Enhanced Architecture provides powerful new capabilities while maintaining **100% backward compatibility**. You can:

- ✅ Continue using existing packs without changes
- ✅ Adopt new features gradually
- ✅ Migrate at your own pace
- ✅ Roll back if needed

**No immediate action required** - but new features are available when you're ready.

---

## Quick Reference

### Generate with Legacy Pack

```bash
yappc generate --pack go-service-gin --output ./service
```

### Generate with Composition

```bash
yappc generate --composition app.json --output ./app
```

### Validate Pack

```bash
yappc validate --pack ./my-pack
```

### Validate Composition

```bash
yappc validate --composition ./my-composition.json
```

### Use New Template Engine

```java
HandlebarsTemplateEngine engine = new HandlebarsTemplateEngine();
String result = engine.render(template, context);
```

### Generate Integration

```java
IntegrationTemplateEngine engine = injector.getInstance(IntegrationTemplateEngine.class);
IntegrationResult result = engine.generateIntegration(integration, from, to, vars);
```

---

_Last Updated: January 7, 2026_  
_Version: 1.0_  
_Status: Production-Ready_
