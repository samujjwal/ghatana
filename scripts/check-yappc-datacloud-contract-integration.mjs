#!/usr/bin/env node

import { createChecker, readText, walkFiles } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-002 Data-Cloud contract integration',
  evidencePath: '.kernel/evidence/yappc/datacloud-contract-integration.json',
});

const requiredFiles = [
  'products/data-cloud/contracts/openapi/data-cloud.yaml',
  'products/data-cloud/delivery/sdk/src/codegen/java/com/ghatana/datacloud/sdk/codegen/DataCloudSdkGeneratorMain.java',
  'products/yappc/core/yappc-facades/src/main/java/com/ghatana/yappc/facades/datacloud/DataCloudArtifactFacade.java',
  'products/yappc/core/yappc-facades/src/main/java/com/ghatana/yappc/facades/datacloud/DataCloudProjectFacade.java',
  'products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java',
  'products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/datacloud/YappcDataCloudE2ETest.java',
  'products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/YappcDataCloudAgentRuntimeE2ETest.java',
];

for (const file of requiredFiles) {
  checker.requireFile(file);
}

for (const token of ['Query entities in a collection', 'tenant isolation', '/api/v1/entities/{collection}/query/stream']) {
  checker.requireIncludes('products/data-cloud/contracts/openapi/data-cloud.yaml', token);
}

for (const token of ['storeArtifact', 'retrieveArtifact', 'listArtifacts', 'metadata', 'tenantId']) {
  checker.requireIncludes(
    'products/yappc/core/yappc-facades/src/main/java/com/ghatana/yappc/facades/datacloud/DataCloudArtifactFacade.java',
    token,
  );
}

for (const token of ['createProject', 'retrieveProject', 'listProjects', 'workspaceId', 'tenantId']) {
  checker.requireIncludes(
    'products/yappc/core/yappc-facades/src/main/java/com/ghatana/yappc/facades/datacloud/DataCloudProjectFacade.java',
    token,
  );
}

for (const token of ['DataCloudClient', 'save(', 'findById(', 'findAll(', 'deleteById(', 'TenantContext']) {
  checker.requireIncludes(
    'products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java',
    token,
  );
}

const integrationText = [
  readText('products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/datacloud/YappcDataCloudE2ETest.java'),
  readText('products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/YappcDataCloudAgentRuntimeE2ETest.java'),
  readText('products/yappc/core/yappc-facades/src/main/java/com/ghatana/yappc/facades/datacloud/DataCloudProjectFacade.java'),
].join('\n');

const scenarios = {
  artifactMetadataPersistence: ['metadata', 'saved', 'preservesMetadata'],
  projectWorkspaceContextPersistence: ['project', 'workspace', 'TenantContext'],
  generatedContractStorage: ['DataCloudClient', 'YappcDataCloudRepository', 'save'],
  artifactEvidenceStorage: ['audit', 'event', 'Data-Cloud event plane'],
  retrievalQuery: ['retrieved', 'findById', 'findAll', 'query'],
  tenantIsolation: ['tenantIsolation', 'tenant A', 'tenant B'],
};

for (const [name, tokens] of Object.entries(scenarios)) {
  checker.record(
    name,
    tokens.every((token) => integrationText.toLowerCase().includes(token.toLowerCase())),
    { tokens },
  );
}

const persistenceFiles = [
  ...walkFiles('products/yappc/infrastructure/datacloud', (file) =>
    /\.(java|kt|ts|tsx|js|mjs)$/.test(file) &&
    !file.includes('/build/') &&
    !file.includes('/src/test/') &&
    !file.includes('/src/integrationTest/'),
  ),
  ...walkFiles('products/yappc/integration', (file) =>
  /\.(java|kt|ts|tsx|js|mjs)$/.test(file) &&
  !file.includes('/build/') &&
  !file.includes('/src/test/') &&
  !file.includes('/__tests__/'),
  ),
];
const duplicatePersistenceHits = persistenceFiles.filter((file) => {
  const source = readText(file);
  if (file.includes('/infrastructure/datacloud/')) return false;
  if (file.includes('/services/platform/adapter/DataCloudClientAdapter.java')) return false;
  if (!/(artifact|project|workspace|contract|evidence)/i.test(source)) return false;
  return /(EntityManager|JpaRepository|JdbcTemplate|DriverManager|getConnection\(|new\s+FileWriter|Files\.writeString|writeFileSync)/.test(source);
});

checker.record('no duplicate YAPPC persistence paths outside canonical adapters', duplicatePersistenceHits.length === 0, {
  matches: duplicatePersistenceHits.slice(0, 50),
});

checker.finish({
  contractCoverage: Object.keys(scenarios),
  duplicatePersistenceHits,
});
