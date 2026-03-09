/**
 * Tests for IaC Parser (Feature 2.24)
 * 
 * Tests Terraform plan parsing, drift detection, secret handling,
 * canvas conversion, and remediation generation.
 */

import { describe, it, expect } from 'vitest';

import {
  parseTerraformPlan,
  detectDrift,
  convertTerraformToCanvas,
  maskSecrets,
  extractSecretReferences,
  generateRemediationSuggestions,
  groupResourcesByProvider,
  calculateDependencyDepth,
  createIaCParserConfig,
  type TerraformPlan,
  type TerraformResource,
  type DriftDetection
} from '../iacParser';

// ============================================================================
// Test Data
// ============================================================================

const mockTerraformPlan = {
  format_version: '1.2',
  terraform_version: '1.5.0',
  planned_values: {
    root_module: {
      resources: [
        {
          address: 'aws_instance.web',
          mode: 'managed',
          type: 'aws_instance',
          name: 'web',
          values: {
            ami: 'ami-12345',
            instance_type: 't2.micro',
            tags: { Name: 'web-server' }
          }
        },
        {
          address: 'aws_s3_bucket.assets',
          mode: 'managed',
          type: 'aws_s3_bucket',
          name: 'assets',
          values: {
            bucket: 'my-assets',
            acl: 'private'
          },
          depends_on: ['aws_instance.web']
        }
      ]
    }
  },
  resource_changes: [
    {
      address: 'aws_instance.web',
      mode: 'managed',
      type: 'aws_instance',
      name: 'web',
      change: {
        actions: ['create'],
        before: null,
        after: {
          ami: 'ami-12345',
          instance_type: 't2.micro'
        },
        after_unknown: {},
        before_sensitive: {},
        after_sensitive: {}
      }
    },
    {
      address: 'aws_s3_bucket.assets',
      mode: 'managed',
      type: 'aws_s3_bucket',
      name: 'assets',
      change: {
        actions: ['update'],
        before: {
          bucket: 'my-assets',
          acl: 'public-read'
        },
        after: {
          bucket: 'my-assets',
          acl: 'private'
        }
      }
    }
  ],
  prior_state: {
    values: {
      root_module: {
        resources: [
          {
            address: 'aws_instance.web',
            mode: 'managed',
            type: 'aws_instance',
            name: 'web',
            values: {
              ami: 'ami-12345',
              instance_type: 't2.micro'
            }
          },
          {
            address: 'aws_s3_bucket.assets',
            mode: 'managed',
            type: 'aws_s3_bucket',
            name: 'assets',
            values: {
              bucket: 'my-assets',
              acl: 'public-read'
            }
          }
        ]
      }
    }
  }
};

const mockActualState: TerraformPlan = {
  formatVersion: '1.2',
  terraformVersion: '1.5.0',
  resources: [
    {
      address: 'aws_s3_bucket.assets',
      type: 'aws_s3_bucket',
      name: 'assets',
      provider: 'aws' as const,
      mode: 'managed' as const,
      values: {
        bucket: 'my-assets',
        acl: 'public-read',
        tags: { Environment: 'prod' }
      },
      dependencies: []
    },
    {
      address: 'aws_instance.database',
      type: 'aws_instance',
      name: 'database',
      provider: 'aws' as const,
      mode: 'managed' as const,
      values: {
        ami: 'ami-99999',
        instance_type: 't2.small'
      },
      dependencies: []
    }
  ]
};

// ============================================================================
// Terraform Plan Parsing Tests
// ============================================================================

describe.skip('IaC Parser - Terraform Plan Parsing', () => {
  it('should parse Terraform plan JSON', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    
    expect(plan.formatVersion).toBe('1.2');
    expect(plan.terraformVersion).toBe('1.5.0');
    expect(plan.resources).toHaveLength(2);
    expect(plan.resources[0].address).toBe('aws_instance.web');
    expect(plan.resources[1].address).toBe('aws_s3_bucket.assets');
  });
  
  it('should infer cloud provider from resource type', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    
    expect(plan.resources[0].provider).toBe('aws');
    expect(plan.resources[1].provider).toBe('aws');
  });
  
  it('should parse resource changes', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    
    const webInstance = plan.resources.find(r => r.address === 'aws_instance.web');
    expect(webInstance?.change?.actions).toContain('create');
    
    const s3Bucket = plan.resources.find(r => r.address === 'aws_s3_bucket.assets');
    expect(s3Bucket?.change?.actions).toContain('update');
  });
  
  it('should parse resource dependencies', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    
    const s3Bucket = plan.resources.find(r => r.address === 'aws_s3_bucket.assets');
    expect(s3Bucket?.dependencies).toContain('aws_instance.web');
  });
  
  it('should handle JSON string input', () => {
    const planJson = JSON.stringify(mockTerraformPlan);
    const plan = parseTerraformPlan(planJson);
    
    expect(plan.resources).toHaveLength(2);
  });
  
  it('should parse multi-cloud resources', () => {
    const multiCloudPlan = {
      ...mockTerraformPlan,
      planned_values: {
        root_module: {
          resources: [
            {
              address: 'aws_instance.web',
              type: 'aws_instance',
              name: 'web',
              mode: 'managed',
              values: {}
            },
            {
              address: 'azurerm_virtual_machine.app',
              type: 'azurerm_virtual_machine',
              name: 'app',
              mode: 'managed',
              values: {}
            },
            {
              address: 'google_compute_instance.db',
              type: 'google_compute_instance',
              name: 'db',
              mode: 'managed',
              values: {}
            }
          ]
        }
      },
      resource_changes: []
    };
    
    const plan = parseTerraformPlan(multiCloudPlan);
    
    expect(plan.resources[0].provider).toBe('aws');
    expect(plan.resources[1].provider).toBe('azure');
    expect(plan.resources[2].provider).toBe('gcp');
  });
});

// ============================================================================
// Drift Detection Tests
// ============================================================================

describe.skip('IaC Parser - Drift Detection', () => {
  it('should detect missing resources', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const drifts = detectDrift(plan, mockActualState);
    
    const missingDrift = drifts.find(d => d.address === 'aws_instance.web');
    expect(missingDrift?.status).toBe('missing');
    expect(missingDrift?.severity).toBe('error');
  });
  
  it('should detect drifted resources', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const drifts = detectDrift(plan, mockActualState);
    
    const driftedResource = drifts.find(d => 
      d.address === 'aws_s3_bucket.assets' && d.status === 'drifted'
    );
    expect(driftedResource).toBeDefined();
    expect(driftedResource?.differences.length).toBeGreaterThan(0);
  });
  
  it('should detect extra resources', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const drifts = detectDrift(plan, mockActualState);
    
    const extraDrift = drifts.find(d => d.address === 'aws_instance.database');
    expect(extraDrift?.status).toBe('extra');
    expect(extraDrift?.severity).toBe('warning');
  });
  
  it('should generate remediation URLs', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const drifts = detectDrift(plan, mockActualState);
    
    drifts.forEach(drift => {
      expect(drift.remediationUrl).toContain('https://registry.terraform.io');
    });
  });
  
  it('should return empty array when no prior state', () => {
    const plan = parseTerraformPlan({
      ...mockTerraformPlan,
      prior_state: undefined
    });
    const drifts = detectDrift(plan);
    
    expect(drifts).toEqual([]);
  });
  
  it('should determine severity based on changed fields', () => {
    const planWithCriticalChange = {
      ...mockTerraformPlan,
      resource_changes: [
        {
          address: 'aws_instance.web',
          mode: 'managed',
          type: 'aws_instance',
          name: 'web',
          change: {
            actions: ['update'],
            before: {
              security_groups: ['sg-12345']
            },
            after: {
              security_groups: ['sg-99999']
            }
          }
        }
      ],
      prior_state: {
        values: {
          root_module: {
            resources: [
              {
                address: 'aws_instance.web',
                type: 'aws_instance',
                name: 'web',
                mode: 'managed',
                values: {
                  security_groups: ['sg-12345']
                }
              }
            ]
          }
        }
      }
    };
    
    const actualWithDrift: TerraformPlan = {
      formatVersion: '1.2',
      terraformVersion: '1.5.0',
      resources: [
        {
          address: 'aws_instance.web',
          type: 'aws_instance',
          name: 'web',
          provider: 'aws',
          mode: 'managed',
          values: {
            security_groups: ['sg-99999']
          },
          dependencies: []
        }
      ]
    };
    
    const plan = parseTerraformPlan(planWithCriticalChange);
    const drifts = detectDrift(plan, actualWithDrift);
    
    const securityDrift = drifts.find(d => 
      d.differences.some(diff => diff.path.includes('security_groups'))
    );
    expect(securityDrift?.severity).toBe('critical');
  });
});

// ============================================================================
// Canvas Conversion Tests
// ============================================================================

describe.skip('IaC Parser - Canvas Conversion', () => {
  it('should convert Terraform plan to canvas graph', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const graph = convertTerraformToCanvas(plan);
    
    expect(graph.nodes).toHaveLength(2);
    expect(graph.edges).toHaveLength(1);
    expect(graph.metadata.totalResources).toBe(2);
  });
  
  it('should create nodes with correct data', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const graph = convertTerraformToCanvas(plan);
    
    const webNode = graph.nodes.find(n => n.id === 'aws_instance.web');
    expect(webNode?.label).toBe('aws_instance.web');
    expect(webNode?.data.provider).toBe('aws');
    expect(webNode?.data.changeType).toBe('create');
  });
  
  it('should create edges for dependencies', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const graph = convertTerraformToCanvas(plan);
    
    const edge = graph.edges.find(e => 
      e.source === 'aws_instance.web' && e.target === 'aws_s3_bucket.assets'
    );
    expect(edge).toBeDefined();
    expect(edge?.data.type).toBe('depends_on');
  });
  
  it('should apply drift highlighting', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const drifts: DriftDetection[] = [
      {
        address: 'aws_s3_bucket.assets',
        status: 'drifted',
        differences: [],
        severity: 'warning'
      }
    ];
    const graph = convertTerraformToCanvas(plan, drifts);
    
    const driftedNode = graph.nodes.find(n => n.id === 'aws_s3_bucket.assets');
    expect(driftedNode?.data.driftStatus).toBe('drifted');
    expect(driftedNode?.style.borderStyle).toBe('dashed');
    expect(driftedNode?.style.borderWidth).toBe(4);
  });
  
  it('should calculate node positions in grid layout', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const graph = convertTerraformToCanvas(plan);
    
    graph.nodes.forEach(node => {
      expect(node.position.x).toBeGreaterThanOrEqual(0);
      expect(node.position.y).toBeGreaterThanOrEqual(0);
    });
  });
  
  it('should style nodes by change type', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const graph = convertTerraformToCanvas(plan);
    
    const createNode = graph.nodes.find(n => n.data.changeType === 'create');
    expect(createNode?.style.borderColor).toBe('#00AA00');
    
    const updateNode = graph.nodes.find(n => n.data.changeType === 'update');
    expect(updateNode?.style.borderColor).toBe('#FF9800');
  });
  
  it('should include metadata statistics', () => {
    const plan = parseTerraformPlan(mockTerraformPlan);
    const graph = convertTerraformToCanvas(plan);
    
    expect(graph.metadata.terraformVersion).toBe('1.5.0');
    expect(graph.metadata.totalResources).toBe(2);
    expect(graph.metadata.changedResources).toBe(2);
  });
});

// ============================================================================
// Secret Handling Tests
// ============================================================================

describe.skip('IaC Parser - Secret Handling', () => {
  it('should mask secrets in resource values', () => {
    const values = {
      username: 'admin',
      password: 'secret123',
      api_key: 'abc-xyz',
      normal_field: 'normal_value'
    };
    
    const masked = maskSecrets(values);
    
    expect(masked.password).toBe('***MASKED***');
    expect(masked.api_key).toBe('***MASKED***');
    expect(masked.username).toBe('admin');
    expect(masked.normal_field).toBe('normal_value');
  });
  
  it('should mask nested secrets', () => {
    const values = {
      database: {
        host: 'localhost',
        password: 'dbpass',
        auth: {
          token: 'secret-token'
        }
      }
    };
    
    const masked = maskSecrets(values);
    
    expect(masked.database.host).toBe('localhost');
    expect(masked.database.password).toBe('***MASKED***');
    expect(masked.database.auth.token).toBe('***MASKED***');
  });
  
  it('should extract Vault references', () => {
    const values = {
      password: 'vault://secret/data/db#password',
      api_token: 'vault://secret/data/api#token',
      normal_field: 'normal_value'
    };
    
    const secrets = extractSecretReferences(values);
    
    expect(secrets).toHaveLength(2);
    expect(secrets[0].path).toBe('secret/data/db');
    expect(secrets[0].key).toBe('password');
    expect(secrets[1].path).toBe('secret/data/api');
    expect(secrets[1].key).toBe('token');
  });
  
  it('should handle custom secret patterns', () => {
    const values = {
      sensitive_data: 'secret',
      public_info: 'public'
    };
    
    const customPattern = /sensitive/i;
    const masked = maskSecrets(values, customPattern);
    
    expect(masked.sensitive_data).toBe('***MASKED***');
    expect(masked.public_info).toBe('public');
  });
});

// ============================================================================
// Remediation Tests
// ============================================================================

describe.skip('IaC Parser - Remediation', () => {
  it('should generate remediation for missing resources', () => {
    const drift: DriftDetection = {
      address: 'aws_instance.web',
      status: 'missing',
      differences: [],
      severity: 'error'
    };
    
    const suggestions = generateRemediationSuggestions(drift);
    
    expect(suggestions.length).toBeGreaterThan(0);
    expect(suggestions.some(s => s.includes('terraform apply'))).toBe(true);
  });
  
  it('should generate remediation for drifted resources', () => {
    const drift: DriftDetection = {
      address: 'aws_s3_bucket.assets',
      status: 'drifted',
      differences: [
        {
          path: 'acl',
          expected: 'private',
          actual: 'public-read',
          description: 'ACL changed'
        }
      ],
      severity: 'warning'
    };
    
    const suggestions = generateRemediationSuggestions(drift);
    
    expect(suggestions.some(s => s.includes('terraform apply'))).toBe(true);
    expect(suggestions.some(s => s.includes('acl'))).toBe(true);
  });
  
  it('should generate remediation for extra resources', () => {
    const drift: DriftDetection = {
      address: 'aws_instance.unmanaged',
      status: 'extra',
      differences: [],
      severity: 'warning'
    };
    
    const suggestions = generateRemediationSuggestions(drift);
    
    expect(suggestions.some(s => s.includes('terraform import'))).toBe(true);
  });
});

// ============================================================================
// Resource Grouping Tests
// ============================================================================

describe.skip('IaC Parser - Resource Grouping', () => {
  it('should group resources by cloud provider', () => {
    const resources: TerraformResource[] = [
      {
        address: 'aws_instance.web',
        type: 'aws_instance',
        name: 'web',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: []
      },
      {
        address: 'azurerm_vm.app',
        type: 'azurerm_vm',
        name: 'app',
        provider: 'azure',
        mode: 'managed',
        values: {},
        dependencies: []
      },
      {
        address: 'aws_s3_bucket.data',
        type: 'aws_s3_bucket',
        name: 'data',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: []
      }
    ];
    
    const groups = groupResourcesByProvider(resources);
    
    expect(groups.get('aws')).toHaveLength(2);
    expect(groups.get('azure')).toHaveLength(1);
  });
  
  it('should handle empty resource list', () => {
    const groups = groupResourcesByProvider([]);
    
    expect(groups.size).toBe(0);
  });
});

// ============================================================================
// Dependency Calculation Tests
// ============================================================================

describe.skip('IaC Parser - Dependency Calculation', () => {
  it('should calculate dependency depth', () => {
    const resources: TerraformResource[] = [
      {
        address: 'aws_vpc.main',
        type: 'aws_vpc',
        name: 'main',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: []
      },
      {
        address: 'aws_subnet.public',
        type: 'aws_subnet',
        name: 'public',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: ['aws_vpc.main']
      },
      {
        address: 'aws_instance.web',
        type: 'aws_instance',
        name: 'web',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: ['aws_subnet.public']
      }
    ];
    
    const depths = calculateDependencyDepth(resources);
    
    expect(depths.get('aws_vpc.main')).toBe(0);
    expect(depths.get('aws_subnet.public')).toBe(1);
    expect(depths.get('aws_instance.web')).toBe(2);
  });
  
  it('should handle circular dependencies', () => {
    const resources: TerraformResource[] = [
      {
        address: 'aws_instance.a',
        type: 'aws_instance',
        name: 'a',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: ['aws_instance.b']
      },
      {
        address: 'aws_instance.b',
        type: 'aws_instance',
        name: 'b',
        provider: 'aws',
        mode: 'managed',
        values: {},
        dependencies: ['aws_instance.a']
      }
    ];
    
    const depths = calculateDependencyDepth(resources);
    
    expect(depths.size).toBeGreaterThan(0);
  });
});

// ============================================================================
// Configuration Tests
// ============================================================================

describe.skip('IaC Parser - Configuration', () => {
  it('should create default configuration', () => {
    const config = createIaCParserConfig();
    
    expect(config.enableDriftDetection).toBe(true);
    expect(config.enableSecretMasking).toBe(true);
    expect(config.secretPattern).toBeInstanceOf(RegExp);
  });
  
  it('should apply configuration overrides', () => {
    const config = createIaCParserConfig({
      enableDriftDetection: false,
      vaultUrl: 'https://vault.example.com'
    });
    
    expect(config.enableDriftDetection).toBe(false);
    expect(config.vaultUrl).toBe('https://vault.example.com');
    expect(config.enableSecretMasking).toBe(true);
  });
});
