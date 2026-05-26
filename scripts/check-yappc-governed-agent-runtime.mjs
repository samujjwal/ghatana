#!/usr/bin/env node

import { createChecker, readText, walkFiles } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-003 governed agent runtime',
  evidencePath: '.kernel/evidence/yappc/governed-agent-runtime.json',
});

const requiredFiles = [
  'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/AgentDispatchOperator.java',
  'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/AgentDispatchValidatorOperator.java',
  'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/AgentExecutorOperator.java',
  'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/YappcAgentOrchestrationBootstrapper.java',
  'products/yappc/core/agents/runtime/src/main/java/com/ghatana/yappc/agent/WorkflowStepOperatorAdapter.java',
  'products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/agent/capability/EventOperatorCapability.java',
  'products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/spec/PatternSpecValidator.java',
  'products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/registry/AgentEventOperatorCapabilityAdapter.java',
  'products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/YappcDataCloudAgentRuntimeE2ETest.java',
];

for (const file of requiredFiles) checker.requireFile(file);

const executor = readText('products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/AgentExecutorOperator.java');
for (const token of [
  'WorkflowStepOperatorAdapter',
  'capabilityRef',
  'EventOperatorCapability',
  'policy',
  'approval',
  'idempotencyKey',
  'audit',
  'outputValidation',
  'agent_system_not_initialized',
  'agent_not_found',
]) {
  checker.record(`AgentExecutorOperator includes ${token}`, executor.includes(token), { token });
}

checker.record('YAPPC workflow asks for agent action before execution', readText(requiredFiles[0]).includes('lifecycle.agent.dispatched'), {
  path: requiredFiles[0],
});
checker.record('YAPPC dispatch validator gates runtime execution', readText(requiredFiles[1]).includes('agent.dispatch.validated'), {
  path: requiredFiles[1],
});
checker.record('canonical EventOperatorCapability validates capabilityRef', readText(requiredFiles[6]).includes('capabilityRef'), {
  path: requiredFiles[6],
});
checker.record('canonical adapter implements EventOperatorCapability', readText(requiredFiles[7]).includes('implements EventOperatorCapability'), {
  path: requiredFiles[7],
});

const productionFiles = [
  ...walkFiles('products/yappc/core/agents', (file) =>
    /\.(java|kt|ts|tsx|js|mjs)$/.test(file) &&
    !file.includes('/src/test/') &&
    !file.includes('/src/integrationTest/') &&
    !file.includes('/build/'),
  ),
  ...walkFiles('products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle', (file) =>
    /\.(java|kt|ts|tsx|js|mjs)$/.test(file),
  ),
  ...walkFiles('products/yappc/infrastructure/datacloud/src/main/java', (file) =>
    /\.(java|kt|ts|tsx|js|mjs)$/.test(file),
  ),
];

const directBypasses = productionFiles.filter((file) => {
  const source = readText(file);
  if (file.endsWith('WorkflowStepOperatorAdapter.java')) return false;
  if (file.endsWith('YAPPCAgentBase.java')) return false;
  if (file.endsWith('ParallelAgentExecutor.java')) return false;
  return /\bagent\.execute\s*\(/.test(source);
});

checker.record('no YAPPC production path directly executes TypedAgent or workflow agent', directBypasses.length === 0, {
  matches: directBypasses.slice(0, 50),
});

checker.finish({ directBypasses });
