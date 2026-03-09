/**
 * Tests for Cloud Topology & IaC Import
 */

import { describe, it, expect } from 'vitest';

import {
  parseTerraform,
  parseCloudFormation,
  topologyToCanvas,
  detectDrift,
  estimateCosts,
  createTopologyConfig,
  getResourceStyle,
  type CloudTopology,
  type CloudResource,
  type CostEstimate,
  type TopologyConfig,
} from '../cloudTopology';

describe.skip('CloudTopology - Configuration', () => {
  it('should create default configuration', () => {
    const config = createTopologyConfig();

    expect(config.layout).toBe('hierarchical');
    expect(config.groupBy).toBe('module');
    expect(config.showCosts).toBe(true);
    expect(config.showDrift).toBe(true);
    expect(config.showDependencies).toBe(true);
  });

  it('should create configuration with overrides', () => {
    const config = createTopologyConfig({
      layout: 'radial',
      groupBy: 'provider',
      showCosts: false,
    });

    expect(config.layout).toBe('radial');
    expect(config.groupBy).toBe('provider');
    expect(config.showCosts).toBe(false);
    expect(config.showDrift).toBe(true); // Not overridden
  });
});

describe.skip('CloudTopology - Terraform Parsing', () => {
  it('should parse simple Terraform configuration', () => {
    const hcl = `
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t2.micro"
}

resource "aws_s3_bucket" "data" {
  bucket = "my-data-bucket"
}
`;

    const topology = parseTerraform(hcl);

    expect(topology.platform).toBe('terraform');
    expect(topology.provider).toBe('aws');
    expect(topology.resources).toHaveLength(2);

    const instance = topology.resources.find(r => r.id === 'aws_instance.web');
    expect(instance).toBeDefined();
    expect(instance?.type).toBe('compute');
    expect(instance?.resourceType).toBe('aws_instance');

    const bucket = topology.resources.find(r => r.id === 'aws_s3_bucket.data');
    expect(bucket).toBeDefined();
    expect(bucket?.type).toBe('storage');
  });

  it('should parse Terraform with dependencies', () => {
    const hcl = `
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
}

resource "aws_subnet" "public" {
  vpc_id     = aws_vpc.main.id
  cidr_block = "10.0.1.0/24"
  depends_on = [aws_vpc.main]
}
`;

    const topology = parseTerraform(hcl);

    const subnet = topology.resources.find(r => r.id === 'aws_subnet.public');
    expect(subnet?.dependsOn).toContain('aws_vpc.main');
  });

  it('should parse Terraform with tags', () => {
    const hcl = `
resource "aws_instance" "web" {
  ami           = "ami-123"
  instance_type = "t2.micro"
  tags = {
    Name = "WebServer"
    Environment = "Production"
  }
}
`;

    const topology = parseTerraform(hcl);

    const instance = topology.resources[0];
    expect(instance.tags).toBeDefined();
    expect(instance.tags?.Name).toBe('WebServer');
    expect(instance.tags?.Environment).toBe('Production');
  });

  it('should parse Terraform with modules', () => {
    const hcl = `
module "vpc" {
  source = "terraform-aws-modules/vpc/aws"
  version = "3.0.0"
}

resource "aws_instance" "web" {
  ami = "ami-123"
}
`;

    const topology = parseTerraform(hcl);

    expect(topology.modules).toHaveLength(1);
    expect(topology.modules[0].name).toBe('vpc');
    expect(topology.modules[0].source).toContain('terraform-aws-modules');
  });

  it('should detect provider from resources', () => {
    const hcl = `
resource "azurerm_virtual_machine" "vm" {
  name = "myvm"
}
`;

    const topology = parseTerraform(hcl);

    const vm = topology.resources[0];
    expect(vm.provider).toBe('azure');
  });

  it('should handle Google Cloud resources', () => {
    const hcl = `
resource "google_compute_instance" "vm" {
  name = "myvm"
  machine_type = "n1-standard-1"
}
`;

    const topology = parseTerraform(hcl);

    const vm = topology.resources[0];
    expect(vm.provider).toBe('gcp');
    expect(vm.type).toBe('compute');
  });

  it('should categorize database resources', () => {
    const hcl = `
resource "aws_rds_cluster" "db" {
  cluster_identifier = "mydb"
}

resource "aws_dynamodb_table" "table" {
  name = "mytable"
}
`;

    const topology = parseTerraform(hcl);

    expect(topology.resources[0].type).toBe('database');
    expect(topology.resources[1].type).toBe('database');
  });

  it('should categorize serverless resources', () => {
    const hcl = `
resource "aws_lambda_function" "processor" {
  function_name = "data-processor"
}
`;

    const topology = parseTerraform(hcl);

    expect(topology.resources[0].type).toBe('serverless');
  });
});

describe.skip('CloudTopology - CloudFormation Parsing', () => {
  it('should parse CloudFormation JSON template', () => {
    const template = JSON.stringify({
      AWSTemplateFormatVersion: '2010-09-09',
      Description: 'Test Stack',
      Resources: {
        WebServer: {
          Type: 'AWS::EC2::Instance',
          Properties: {
            InstanceType: 't2.micro',
            ImageId: 'ami-123',
          },
        },
        DataBucket: {
          Type: 'AWS::S3::Bucket',
          Properties: {
            BucketName: 'my-bucket',
          },
        },
      },
    });

    const topology = parseCloudFormation(template);

    expect(topology.platform).toBe('cloudformation');
    expect(topology.provider).toBe('aws');
    expect(topology.resources).toHaveLength(2);

    const instance = topology.resources.find(r => r.id === 'WebServer');
    expect(instance?.resourceType).toBe('AWS::EC2::Instance');
    expect(instance?.type).toBe('compute');
  });

  it('should parse CloudFormation with dependencies', () => {
    const template = JSON.stringify({
      Resources: {
        VPC: {
          Type: 'AWS::EC2::VPC',
          Properties: { CidrBlock: '10.0.0.0/16' },
        },
        Subnet: {
          Type: 'AWS::EC2::Subnet',
          Properties: { CidrBlock: '10.0.1.0/24' },
          DependsOn: 'VPC',
        },
      },
    });

    const topology = parseCloudFormation(template);

    const subnet = topology.resources.find(r => r.id === 'Subnet');
    expect(subnet?.dependsOn).toContain('VPC');
  });

  it('should parse CloudFormation with outputs', () => {
    const template = JSON.stringify({
      Resources: {
        Bucket: {
          Type: 'AWS::S3::Bucket',
        },
      },
      Outputs: {
        BucketName: {
          Value: { Ref: 'Bucket' },
          Description: 'Name of the S3 bucket',
        },
      },
    });

    const topology = parseCloudFormation(template);

    expect(topology.outputs).toHaveLength(1);
    expect(topology.outputs[0].name).toBe('BucketName');
    expect(topology.outputs[0].description).toBe('Name of the S3 bucket');
  });

  it('should parse CloudFormation with parameters', () => {
    const template = JSON.stringify({
      Parameters: {
        InstanceType: {
          Type: 'String',
          Default: 't2.micro',
          Description: 'EC2 instance type',
        },
      },
      Resources: {},
    });

    const topology = parseCloudFormation(template);

    expect(topology.variables).toHaveLength(1);
    expect(topology.variables[0].name).toBe('InstanceType');
    expect(topology.variables[0].defaultValue).toBe('t2.micro');
  });

  it('should parse CloudFormation with tags', () => {
    const template = JSON.stringify({
      Resources: {
        Instance: {
          Type: 'AWS::EC2::Instance',
          Properties: {
            Tags: [
              { Key: 'Name', Value: 'WebServer' },
              { Key: 'Environment', Value: 'Production' },
            ],
          },
        },
      },
    });

    const topology = parseCloudFormation(template);

    const instance = topology.resources[0];
    expect(instance.tags?.Name).toBe('WebServer');
    expect(instance.tags?.Environment).toBe('Production');
  });

  it('should parse CloudFormation YAML template', () => {
    const yaml = `
AWSTemplateFormatVersion: '2010-09-09'
Resources:
  WebServer:
    Type: AWS::EC2::Instance
    Properties:
      InstanceType: t2.micro
  Database:
    Type: AWS::RDS::DBInstance
`;

    const topology = parseCloudFormation(yaml);

    expect(topology.resources).toHaveLength(2);
    const instance = topology.resources.find(r => r.id === 'WebServer');
    expect(instance?.resourceType).toBe('AWS::EC2::Instance');
  });

  it('should categorize RDS as database', () => {
    const template = JSON.stringify({
      Resources: {
        DB: {
          Type: 'AWS::RDS::DBInstance',
        },
      },
    });

    const topology = parseCloudFormation(template);
    expect(topology.resources[0].type).toBe('database');
  });

  it('should categorize Lambda as serverless', () => {
    const template = JSON.stringify({
      Resources: {
        Function: {
          Type: 'AWS::Lambda::Function',
        },
      },
    });

    const topology = parseCloudFormation(template);
    expect(topology.resources[0].type).toBe('serverless');
  });
});

describe.skip('CloudTopology - Canvas Conversion', () => {
  it('should convert topology to canvas document', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test Infrastructure',
      resources: [
        {
          id: 'aws_instance.web',
          name: 'web',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
        {
          id: 'aws_s3_bucket.data',
          name: 'data',
          type: 'storage',
          provider: 'aws',
          resourceType: 'aws_s3_bucket',
          dependsOn: ['aws_instance.web'],
          properties: {},
          metadata: {},
        },
      ],
      modules: [
        {
          id: 'module.main',
          name: 'main',
          source: 'local',
          resources: ['aws_instance.web', 'aws_s3_bucket.data'],
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const config = createTopologyConfig();
    const doc = topologyToCanvas(topology, config);

    expect(doc.id).toBe('topology-Test Infrastructure');
    expect(doc.title).toBe('Cloud Topology: Test Infrastructure');
    expect(Object.keys(doc.elements)).toHaveLength(3); // 2 resources + 1 edge
  });

  it('should position resources in grid layout', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: Array.from({ length: 6 }, (_, i) => ({
        id: `resource-${i}`,
        name: `Resource ${i}`,
        type: 'compute' as const,
        provider: 'aws' as const,
        resourceType: 'aws_instance',
        dependsOn: [],
        properties: {},
        metadata: {},
      })),
      modules: [
        {
          id: 'module.main',
          name: 'main',
          source: 'local',
          resources: Array.from({ length: 6 }, (_, i) => `resource-${i}`),
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const doc = topologyToCanvas(topology, createTopologyConfig());

    // Should have 6 resource nodes
    const resourceNodes = Object.values(doc.elements).filter(
      el => el.type === 'node'
    );
    expect(resourceNodes).toHaveLength(6);

    // Check grid positioning (4 per row)
    const node0 = doc.elements['resource-0'];
    const node4 = doc.elements['resource-4'];

    if (node0.type === 'node' && node4.type === 'node') {
      // Node 4 should be below node 0 (new row)
      expect(node4.transform.position.y).toBeGreaterThan(
        node0.transform.position.y
      );
    }
  });

  it('should create edges for dependencies when enabled', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'a',
          name: 'A',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
        {
          id: 'b',
          name: 'B',
          type: 'storage',
          provider: 'aws',
          resourceType: 'aws_s3_bucket',
          dependsOn: ['a'],
          properties: {},
          metadata: {},
        },
      ],
      modules: [
        {
          id: 'module.main',
          name: 'main',
          source: 'local',
          resources: ['a', 'b'],
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const config = createTopologyConfig({ showDependencies: true });
    const doc = topologyToCanvas(topology, config);

    const edge = doc.elements['edge-a-b'];
    expect(edge).toBeDefined();
    expect(edge.type).toBe('edge');

    if (edge.type === 'edge') {
      const canvasEdge = edge as import('../../types/canvas-document').CanvasEdge;
      expect(canvasEdge.sourceId).toBe('a');
      expect(canvasEdge.targetId).toBe('b');
    }
  });

  it('should not create edges when dependencies disabled', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'a',
          name: 'A',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
        {
          id: 'b',
          name: 'B',
          type: 'storage',
          provider: 'aws',
          resourceType: 'aws_s3_bucket',
          dependsOn: ['a'],
          properties: {},
          metadata: {},
        },
      ],
      modules: [
        {
          id: 'module.main',
          name: 'main',
          source: 'local',
          resources: ['a', 'b'],
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const config = createTopologyConfig({ showDependencies: false });
    const doc = topologyToCanvas(topology, config);

    expect(doc.elements['edge-a-b']).toBeUndefined();
  });

  it('should group resources by module', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'a',
          name: 'A',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
        {
          id: 'b',
          name: 'B',
          type: 'storage',
          provider: 'aws',
          resourceType: 'aws_s3_bucket',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
      ],
      modules: [
        {
          id: 'module.compute',
          name: 'compute',
          source: 'local',
          resources: ['a'],
        },
        {
          id: 'module.storage',
          name: 'storage',
          source: 'local',
          resources: ['b'],
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const config = createTopologyConfig({ groupBy: 'module', groupSpacing: 300 });
    const doc = topologyToCanvas(topology, config);

    const nodeA = doc.elements['a'];
    const nodeB = doc.elements['b'];

    if (nodeA.type === 'node' && nodeB.type === 'node') {
      // Different modules should have different Y positions (vertical spacing)
      expect(Math.abs(nodeA.transform.position.y - nodeB.transform.position.y)).toBeGreaterThan(200);
    }
  });

  it('should include cost data in metadata when available', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web Server',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {
            cost: {
              monthly: 29.99,
              hourly: 0.041,
              currency: 'USD',
            },
          },
        },
      ],
      modules: [
        {
          id: 'module.main',
          name: 'main',
          source: 'local',
          resources: ['instance'],
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const doc = topologyToCanvas(topology, createTopologyConfig());

    const node = doc.elements['instance'];
    expect(node.metadata.cost).toBeDefined();
    expect((node.metadata.cost as CostEstimate).monthly).toBe(29.99);

    if (node.type === 'node') {
      const canvasNode = node as import('../../types/canvas-document').CanvasNode;
      expect(canvasNode.data.cost).toBe('$29.99/mo');
    }
  });

  it('should include drift status in metadata', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web Server',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {
            drift: {
              status: 'drifted',
              detectedAt: new Date(),
              changes: [
                {
                  property: 'instance_type',
                  expected: 't2.micro',
                  actual: 't2.small',
                },
              ],
            },
          },
        },
      ],
      modules: [
        {
          id: 'module.main',
          name: 'main',
          source: 'local',
          resources: ['instance'],
        },
      ],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const doc = topologyToCanvas(topology, createTopologyConfig());

    const node = doc.elements['instance'];
    expect(node.metadata.drift).toBeDefined();

    if (node.type === 'node') {
      const canvasNode = node as import('../../types/canvas-document').CanvasNode;
      expect(canvasNode.data.drift).toBe('drifted');
    }
  });
});

describe.skip('CloudTopology - Drift Detection', () => {
  it('should detect missing resources', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: { instance_type: 't2.micro' },
          metadata: {},
        },
      ],
      modules: [],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const actualState = {}; // Empty - resource doesn't exist

    const result = detectDrift(topology, actualState);

    expect(result.resources[0].metadata.drift?.status).toBe('missing');
  });

  it('should detect drifted properties', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: { instance_type: 't2.micro', ami: 'ami-123' },
          metadata: {},
        },
      ],
      modules: [],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const actualState = {
      instance: {
        instance_type: 't2.small', // Drifted!
        ami: 'ami-123',
      },
    };

    const result = detectDrift(topology, actualState);

    expect(result.resources[0].metadata.drift?.status).toBe('drifted');
    expect(result.resources[0].metadata.drift?.changes).toHaveLength(1);
    expect(result.resources[0].metadata.drift?.changes?.[0]).toEqual({
      property: 'instance_type',
      expected: 't2.micro',
      actual: 't2.small',
    });
  });

  it('should detect in-sync resources', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: { instance_type: 't2.micro' },
          metadata: {},
        },
      ],
      modules: [],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const actualState = {
      instance: {
        instance_type: 't2.micro', // Matches!
      },
    };

    const result = detectDrift(topology, actualState);

    expect(result.resources[0].metadata.drift?.status).toBe('in-sync');
    expect(result.resources[0].metadata.drift?.changes).toBeUndefined();
  });
});

describe.skip('CloudTopology - Cost Estimation', () => {
  it('should estimate resource costs', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
      ],
      modules: [],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const pricing: Record<string, CostEstimate> = {
      aws_instance: {
        monthly: 29.99,
        hourly: 0.041,
        currency: 'USD',
      },
    };

    const result = estimateCosts(topology, pricing);

    expect(result.resources[0].metadata.cost).toBeDefined();
    expect(result.resources[0].metadata.cost?.monthly).toBe(29.99);
    expect(result.resources[0].metadata.cost?.hourly).toBe(0.041);
  });

  it('should calculate total costs', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance1',
          name: 'Web1',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
        {
          id: 'instance2',
          name: 'Web2',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
      ],
      modules: [],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const pricing: Record<string, CostEstimate> = {
      aws_instance: {
        monthly: 30.0,
        hourly: 0.041,
        currency: 'USD',
      },
    };

    const result = estimateCosts(topology, pricing);

    expect(result.metadata.totalCost).toBeDefined();
    expect(result.metadata.totalCost?.monthly).toBe(60.0); // 2 instances
    expect(result.metadata.totalCost?.currency).toBe('USD');
  });

  it('should fallback to resource type pricing', () => {
    const topology: CloudTopology = {
      platform: 'terraform',
      provider: 'aws',
      name: 'Test',
      resources: [
        {
          id: 'instance',
          name: 'Web',
          type: 'compute',
          provider: 'aws',
          resourceType: 'aws_instance',
          dependsOn: [],
          properties: {},
          metadata: {},
        },
      ],
      modules: [],
      outputs: [],
      variables: [],
      metadata: {},
    };

    const pricing: Record<string, CostEstimate> = {
      compute: {
        // Only category pricing, no specific resource type
        monthly: 25.0,
        hourly: 0.034,
        currency: 'USD',
      },
    };

    const result = estimateCosts(topology, pricing);

    expect(result.resources[0].metadata.cost?.monthly).toBe(25.0);
  });
});

describe.skip('CloudTopology - Resource Styling', () => {
  it('should apply compute resource style', () => {
    const resource: CloudResource = {
      id: 'instance',
      name: 'Web',
      type: 'compute',
      provider: 'aws',
      resourceType: 'aws_instance',
      dependsOn: [],
      properties: {},
      metadata: {},
    };

    const style = getResourceStyle(resource);

    expect(style.backgroundColor).toBe('#eff6ff');
    expect(style.borderColor).toBe('#3b82f6');
  });

  it('should apply storage resource style', () => {
    const resource: CloudResource = {
      id: 'bucket',
      name: 'Data',
      type: 'storage',
      provider: 'aws',
      resourceType: 'aws_s3_bucket',
      dependsOn: [],
      properties: {},
      metadata: {},
    };

    const style = getResourceStyle(resource);

    expect(style.backgroundColor).toBe('#f0fdf4');
    expect(style.borderColor).toBe('#22c55e');
  });

  it('should apply drifted status style', () => {
    const resource: CloudResource = {
      id: 'instance',
      name: 'Web',
      type: 'compute',
      provider: 'aws',
      resourceType: 'aws_instance',
      dependsOn: [],
      properties: {},
      metadata: {
        drift: {
          status: 'drifted',
          detectedAt: new Date(),
        },
      },
    };

    const style = getResourceStyle(resource);

    expect(style.backgroundColor).toBe('#fef2f2');
    expect(style.borderColor).toBe('#ef4444');
  });

  it('should apply missing status style', () => {
    const resource: CloudResource = {
      id: 'instance',
      name: 'Web',
      type: 'compute',
      provider: 'aws',
      resourceType: 'aws_instance',
      dependsOn: [],
      properties: {},
      metadata: {
        drift: {
          status: 'missing',
          detectedAt: new Date(),
        },
      },
    };

    const style = getResourceStyle(resource);

    expect(style.backgroundColor).toBe('#fefce8');
    expect(style.borderColor).toBe('#eab308');
  });

  it('should apply in-sync status style', () => {
    const resource: CloudResource = {
      id: 'instance',
      name: 'Web',
      type: 'compute',
      provider: 'aws',
      resourceType: 'aws_instance',
      dependsOn: [],
      properties: {},
      metadata: {
        drift: {
          status: 'in-sync',
          detectedAt: new Date(),
        },
      },
    };

    const style = getResourceStyle(resource);

    expect(style.backgroundColor).toBe('#f0fdf4');
    expect(style.borderColor).toBe('#22c55e');
  });
});
