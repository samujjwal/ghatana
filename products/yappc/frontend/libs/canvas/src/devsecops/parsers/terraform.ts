/**
 * Terraform Plan Parser
 * 
 * Parses Terraform plan JSON into Runbook format with approval gates for high-risk changes.
 */

import type { Runbook, RunbookStep, ResourceChange, ApprovalGate, ResourceAction } from '../types';

/**
 * Map Terraform action to ResourceAction
 */
function mapTerraformAction(action: string): ResourceAction {
  switch (action) {
    case 'create':
      return 'create';
    case 'update':
    case 'replace':
      return 'update';
    case 'delete':
      return 'delete';
    default:
      return 'no-change';
  }
}

/**
 * Calculate risk level for a resource change
 */
function calculateResourceRisk(action: string, resourceType: string): 'low' | 'medium' | 'high' {
  // High-risk resources
  const highRiskTypes = ['aws_db_instance', 'aws_rds_cluster', 'aws_s3_bucket', 'aws_iam_role'];
  
  if (action === 'delete') {
    return 'high';
  }
  
  if (highRiskTypes.some(type => resourceType.includes(type))) {
    return action === 'create' ? 'medium' : 'high';
  }
  
  return action === 'create' ? 'low' : 'medium';
}

/**
 * Parse Terraform plan JSON into a Runbook
 * 
 * @param plan - Terraform plan JSON string or parsed object
 * @returns Runbook representation with approval gates for high-risk changes
 * 
 * @example
 * ```typescript
 * const runbook = parseTerraformPlan(planJson);
 * console.log(`Found ${runbook.approvalGates.length} approval gates`);
 * ```
 */
export function parseTerraformPlan(plan: string | any): Runbook {
  const planData = typeof plan === 'string' ? JSON.parse(plan) : plan;
  
  const steps: RunbookStep[] = [];
  const changes: ResourceChange[] = [];
  const approvalGates: ApprovalGate[] = [];

  // Parse resource changes
  const resourceChanges = planData.resource_changes || [];
  resourceChanges.forEach((change: unknown, index: number) => {
    const action = change.change?.actions?.[0] || 'no-op';
    const resourceType = change.type;
    const resourceName = change.name;
    const address = change.address;

    if (action === 'no-op') {
      return; // Skip unchanged resources
    }

    const resourceChange: ResourceChange = {
      resource: address,
      type: resourceType,
      action: mapTerraformAction(action),
      before: change.change?.before,
      after: change.change?.after,
      risk: calculateResourceRisk(action, resourceType),
    };
    changes.push(resourceChange);

    // Create step for this resource change
    const stepId = `tf-${action}-${index}`;
    const dependsOn = index > 0 ? [`tf-${resourceChanges[index - 1].change?.actions?.[0]}-${index - 1}`] : [];

    steps.push({
      id: stepId,
      name: `${action} ${address}`,
      type: 'task',
      command: `terraform apply -target=${address}`,
      dependsOn,
      metadata: {
        status: 'pending',
        changes: [resourceChange],
      },
    });

    // Create approval gate for high-risk changes
    if (resourceChange.risk === 'high' || action === 'delete') {
      approvalGates.push({
        id: `approval-${stepId}`,
        stepId,
        approvers: ['ops-team', 'security-team'],
        requiredApprovals: 2,
        approvals: [],
        status: 'pending',
        createdAt: new Date(),
        metadata: {
          resourceChanges: [resourceChange],
          impactAnalysis: `${action} operation on ${resourceType} ${resourceName}`,
          riskLevel: resourceChange.risk === 'high' ? 'high' : 'medium',
        },
      });
    }
  });

  return {
    id: `terraform-${Date.now()}`,
    name: 'Terraform Plan',
    type: 'terraform',
    description: `Terraform plan with ${changes.length} resource change(s)`,
    version: planData.terraform_version || '1.0.0',
    steps,
    approvalGates,
    variables: planData.variables || {},
    metadata: {
      status: 'pending',
      totalSteps: steps.length,
      completedSteps: 0,
      failedSteps: 0,
      skippedSteps: 0,
      rollbackSteps: 0,
    },
  };
}
