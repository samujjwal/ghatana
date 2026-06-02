# Kernel Route Handler Pattern

## Standard Route Handler Pattern

All route handlers should follow this consistent pattern:

```
resolve Kernel context → validate DTO → policy facade → application service → response mapper
```

## Pattern Breakdown

### 1. Resolve Kernel Context
Extract and validate the Kernel-authenticated session context from the request.

```java
return sessionContextResolver.resolve(request)
    .then(contextOpt -> {
        if (contextOpt.isEmpty()) {
            return HttpErrorEnvelope.unauthorized(
                "INVALID_SESSION",
                "No valid session found",
                correlationId
            );
        }
        KernelSessionContext context = contextOpt.get();
        // Continue to next step
    });
```

### 2. Validate DTO
Parse and validate the request body using Kernel request helpers.

```java
return RequestBodyHelpers.parseJsonBody(request)
    .then(body -> {
        try {
            String patientId = RequestBodyHelpers.requireTextField(body, "patientId");
            String action = RequestBodyHelpers.requireTextField(body, "action");
            // Validate against DTO schema if applicable
            return Promise.of(new CreateConsentRequest(patientId, accessorId, action, scope));
        } catch (RequestBodyHelpers.ValidationException e) {
            return Promise.of(HttpErrorEnvelope.badRequest(
                "VALIDATION_ERROR",
                e.getMessage(),
                correlationId
            ));
        }
    });
```

### 3. Policy Facade
Check authorization using the Kernel policy facade.

```java
return policyEvaluator.canAccess(context, patientId, resourceType, action)
    .then(decision -> {
        if (!decision.isAllowed()) {
            return Promise.of(HttpErrorEnvelope.forbidden(
                decision.getReasonCode(),
                "Access denied",
                correlationId
            ));
        }
        // Continue to next step
    });
```

### 4. Application Service
Delegate business logic to the application service.

```java
return consentManagementService.grantAccess(patientId, accessorId, scope, expiresAt)
    .then(result -> {
        // Continue to response mapping
    });
```

### 5. Response Mapper
Map the service result to a standardized HTTP response.

```java
Map<String, Object> responseData = new LinkedHashMap<>();
responseData.put("consentId", result.consentId());
responseData.put("grantedAt", result.grantedAt());
return Promise.of(ResponseMappers.actionResponse(responseData, correlationId));
```

## Complete Example

```java
private Promise<HttpResponse> handleGrantConsent(HttpRequest request) {
    String correlationId = RequestBodyHelpers.getCorrelationId(request);
    
    return sessionContextResolver.resolve(request)
        .then(contextOpt -> {
            if (contextOpt.isEmpty()) {
                return Promise.of(HttpErrorEnvelope.unauthorized(
                    "INVALID_SESSION",
                    "No valid session found",
                    correlationId
                ));
            }
            KernelSessionContext context = contextOpt.get();
            
            return RequestBodyHelpers.parseJsonBody(request)
                .then(body -> {
                    try {
                        String patientId = RequestBodyHelpers.requireTextField(body, "patientId");
                        String accessorId = RequestBodyHelpers.requireTextField(body, "accessorId");
                        String scope = RequestBodyHelpers.requireTextField(body, "scope");
                        String expiresAtStr = RequestBodyHelpers.getOptionalTextField(body, "expiresAt");
                        Instant expiresAt = expiresAtStr != null 
                            ? Instant.parse(expiresAtStr) 
                            : Instant.now().plusSeconds(86400);
                        
                        return policyEvaluator.canAccess(context, patientId, "consent", "grant")
                            .then(decision -> {
                                if (!decision.isAllowed()) {
                                    return Promise.of(HttpErrorEnvelope.forbidden(
                                        decision.getReasonCode(),
                                        "Access denied",
                                        correlationId
                                    ));
                                }
                                
                                return consentManagementService.grantAccess(
                                    patientId, accessorId, scope, expiresAt
                                ).then(result -> {
                                    Map<String, Object> responseData = new LinkedHashMap<>();
                                    responseData.put("consentId", result.consentId());
                                    responseData.put("grantedAt", result.grantedAt());
                                    responseData.put("expiresAt", result.expiresAt());
                                    
                                    return Promise.of(ResponseMappers.actionResponse(
                                        responseData, correlationId
                                    ));
                                });
                            });
                    } catch (RequestBodyHelpers.ValidationException e) {
                        return Promise.of(HttpErrorEnvelope.badRequest(
                            "VALIDATION_ERROR",
                            e.getMessage(),
                            correlationId
                        ));
                    }
                });
        });
}
```

## Key Principles

1. **No business logic in routes**: All business logic lives in application services
2. **Kernel context first**: Always resolve Kernel context before any other work
3. **DTO validation**: Use Kernel request helpers for consistent validation
4. **Policy enforcement**: Use Kernel policy facade for authorization
5. **Standardized responses**: Use Kernel response mappers for consistent output
6. **Error handling**: Use Kernel error envelope for consistent error responses
7. **Correlation ID**: Always include correlation ID in all responses

## Benefits

- Consistent error handling across all routes
- Clear separation of concerns
- Easier testing (each layer can be tested independently)
- Better observability (consistent correlation ID propagation)
- Easier maintenance (pattern is predictable)
