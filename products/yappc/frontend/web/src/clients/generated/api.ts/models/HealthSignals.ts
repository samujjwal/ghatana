/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { GenerationHealth } from './GenerationHealth';
import type { AgentGovernanceHealth } from './AgentGovernanceHealth';
import type { PreviewHealth } from './PreviewHealth';
import type { RuntimeHealth } from './RuntimeHealth';
export type HealthSignals = {
    preview: PreviewHealth;
    generation: GenerationHealth;
    runtime: RuntimeHealth;
    agentGovernance?: AgentGovernanceHealth;
};

