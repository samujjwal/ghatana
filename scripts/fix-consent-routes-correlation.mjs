#!/usr/bin/env node

/**
 * Fix PhrConsentRoutes.java to properly pass correlationId to createConsentGrant method.
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__dirname);

const CONSENT_ROUTES_FILE = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'routes', 'PhrConsentRoutes.java');

function fixFile(filePath) {
  let content = readFileSync(filePath, 'utf-8');
  let modified = false;

  // Update method signature to include correlationId parameter
  content = content.replace(
    /private Promise<HttpResponse> createConsentGrant\(\s*HttpRequest request,\s*PhrRouteSupport\.PhrRequestContext context,\s*String idempotencyKey\s*\)/g,
    'private Promise<HttpResponse> createConsentGrant(\n            HttpRequest request,\n            PhrRouteSupport.PhrRequestContext context,\n            String idempotencyKey,\n            String correlationId)'
  );

  // Update calls to include correlationId
  content = content.replace(
    /return createConsentGrant\(request, context, idempotencyKey\);/g,
    'return createConsentGrant(request, context, idempotencyKey, correlationId);'
  );
  content = content.replace(
    /return createConsentGrant\(request, context, null\);/g,
    'return createConsentGrant(request, context, null, correlationId);'
  );

  // Add telemetry manager import
  if (!content.includes('import com.ghatana.kernel.observability.KernelTelemetryManager;')) {
    content = content.replace(
      'import com.ghatana.phr.api.dto.CreateConsentGrantRequest;',
      'import com.ghatana.kernel.observability.KernelTelemetryManager;\nimport com.ghatana.phr.api.dto.CreateConsentGrantRequest;'
    );
  }

  // Add telemetry manager field
  if (!content.includes('private final KernelTelemetryManager telemetryManager;')) {
    content = content.replace(
      'private final PhrPolicyEvaluator policyEvaluator;',
      'private final PhrPolicyEvaluator policyEvaluator;\n    private final KernelTelemetryManager telemetryManager;'
    );
  }

  // Update constructor to accept telemetry manager
  content = content.replace(
    /public PhrConsentRoutes\(\s*Eventloop eventloop,\s*ConsentManagementService consentService,\s*PhrPolicyEvaluator policyEvaluator\s*\)/g,
    'public PhrConsentRoutes(\n            Eventloop eventloop,\n            ConsentManagementService consentService,\n            PhrPolicyEvaluator policyEvaluator,\n            KernelTelemetryManager telemetryManager)'
  );

  // Update constructor assignments
  content = content.replace(
    /this\.policyEvaluator = Objects\.requireNonNull\(policyEvaluator, "policyEvaluator must not be null"\);/g,
    'this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");\n        this.telemetryManager = telemetryManager;'
  );

  // Add metric emission after successful grant creation
  content = content.replace(
    /\.then\(created -> PhrRouteSupport\.jsonResponse\(201, created, correlationId\)\);/g,
    '.then(created -> {\n                    PhrRouteSupport.emitConsentMetric("create", context, true);\n                    return PhrRouteSupport.jsonResponse(201, created, correlationId);\n                })'
  );

  writeFileSync(filePath, content, 'utf-8');
  console.log('Fixed PhrConsentRoutes.java');
}

fixFile(CONSENT_ROUTES_FILE);
