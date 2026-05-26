#!/usr/bin/env node

import { createChecker, readText, walkFiles } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-009 duplicate runtime paths',
  evidencePath: '.kernel/evidence/yappc/duplicate-runtime-paths.json',
});

const productionFiles = walkFiles('products/yappc', (file) =>
  /\.(java|kt|ts|tsx|js|mjs)$/.test(file) &&
  !file.includes('/src/test/') &&
  !file.includes('/src/integrationTest/') &&
  !file.includes('/build/'),
);

const rules = [
  {
    name: 'Data-Cloud persistence',
    pattern: /\b(EntityManager|JpaRepository|JdbcTemplate|DriverManager|getConnection\s*\(|Files\.writeString|writeFileSync|new\s+FileWriter)\b/,
    allowed: ['/infrastructure/datacloud/', '/services/platform/adapter/DataCloudClientAdapter.java', '/core/refactorer/', '/core/scaffold/', '/frontend/', '/tools/', '/core/yappc-domain-impl/', '/core/yappc-services/', '/platform/', '/products/yappc/scripts/', 'products/yappc/scripts/'],
  },
  {
    name: 'agent runtime',
    pattern: /\b(agent\.execute\s*\(|TypedAgent|new\s+AgentEventOperatorCapabilityAdapter)\b/,
    allowed: ['/core/agents/runtime/', '/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/', '/core/yappc-domain-impl/', '/frontend/libs/yappc-ai/'],
  },
  {
    name: 'Kernel lifecycle orchestration',
    pattern: /\b(KernelLifecycle|kernel lifecycle|kernel-lifecycle|KernelServiceRegistry)\b/,
    allowed: ['/kernel-bridge/', '/core/scaffold/', '/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/', '/frontend/apps/api/src/routes/product-unit-intents.ts', '/frontend/apps/api/src/__tests__/product-unit-intents.test.ts', '/frontend/web/src/components/studio/', '/products/yappc/scripts/', 'products/yappc/scripts/'],
  },
  {
    name: 'artifact validation',
    pattern: /\b(validateArtifact|artifact validation|roundtrip|protected region)\b/i,
    allowed: ['/core/scaffold/', '/core/refactorer/', '/core/ai/', '/core/agents/', '/kernel-bridge/', '/frontend/', '/core/yappc-domain-impl/'],
  },
  {
    name: 'governance/audit logic',
    pattern: /\b(policy|approval|idempotency|audit)\b/,
    allowed: ['/core/yappc-services/', '/core/agents/', '/infrastructure/datacloud/', '/core/ai/', '/core/cli-tools/', '/core/refactorer/', '/core/scaffold/', '/frontend/', '/kernel-bridge/', '/tools/', '/core/yappc-domain-impl/', '/core/yappc-facades/', '/core/yappc-infrastructure/', '/core/yappc-shared/', '/libs/java/yappc-domain/', 'products/yappc/scripts/'],
  },
  {
    name: 'contract generation',
    pattern: /\b(generateContract|contract generation|OpenAPI|openapi)\b/,
    allowed: ['/core/scaffold/', '/core/yappc-api/', '/core/yappc-facades/', '/kernel-bridge/', '/core/agents/', '/core/ai/', '/core/yappc-domain-impl/', '/frontend/'],
  },
];

const results = rules.map((rule) => {
  const matches = productionFiles.filter((file) => {
    if (rule.allowed.some((allowed) => file.includes(allowed))) return false;
    return rule.pattern.test(readText(file));
  });
  checker.record(`no duplicate ${rule.name} path`, matches.length === 0, {
    matches: matches.slice(0, 50),
  });
  return { name: rule.name, matches };
});

checker.finish({ duplicateRuntimePathResults: results });
