// Mock API responses for Copilot functionality
// This would normally be handled by the backend server

export interface MockCopilotResponse {
  suggestions: Array<{
    id: string;
    title: string;
    description: string;
    confidence: number;
    actions: Array<{
      id: string;
      title: string;
      description: string;
      command: string;
      parameters: Record<string, any>;
      riskLevel: 'low' | 'medium' | 'high' | 'critical';
      automated: boolean;
      requiresApproval: boolean;
      estimatedDuration: number;
      dependencies: string[];
      validationChecks: Array<{
        id: string;
        title: string;
        description: string;
        command: string;
        expectedResult: string;
        critical: boolean;
      }>;
      rollbackCommand?: string;
    }>;
    estimatedTotalTime: number;
    riskAssessment: {
      overallRisk: 'low' | 'medium' | 'high' | 'critical';
      safetyScore: number;
      riskFactors: string[];
      mitigations: string[];
      requiresHumanApproval: boolean;
    };
    tags: string[];
    createdAt: string;
  }>;
  confidence: number;
  reasoning: string;
  alternativeApproaches: string[];
  warnings: string[];
}

export const mockCopilotResponses: Record<string, MockCopilotResponse> = {
  'high cpu': {
    suggestions: [
      {
        id: 'runbook-cpu-001',
        title: 'High CPU Usage Remediation',
        description: 'Systematic approach to diagnose and resolve high CPU usage across web servers',
        confidence: 0.92,
        actions: [
          {
            id: 'action-001',
            title: 'Check CPU Usage',
            description: 'Monitor current CPU utilization across all web servers',
            command: 'top -bn1 | grep "Cpu(s)" | awk \'{print $2}\' | awk -F\'%\' \'{print $1}\'',
            parameters: {
              timeout: '30s',
              servers: ['web-01', 'web-02', 'web-03']
            },
            riskLevel: 'low',
            automated: true,
            requiresApproval: false,
            estimatedDuration: 15,
            dependencies: [],
            validationChecks: [
              {
                id: 'check-001',
                title: 'Verify SSH connectivity',
                description: 'Ensure we can connect to all target servers',
                command: 'ssh -o ConnectTimeout=5 user@server "echo connected"',
                expectedResult: 'connected',
                critical: true
              }
            ]
          },
          {
            id: 'action-002',
            title: 'Identify Top Processes',
            description: 'Find processes consuming the most CPU',
            command: 'ps aux --sort=-%cpu | head -10',
            parameters: {
              format: 'detailed',
              count: 10
            },
            riskLevel: 'low',
            automated: true,
            requiresApproval: false,
            estimatedDuration: 10,
            dependencies: ['action-001'],
            validationChecks: []
          },
          {
            id: 'action-003',
            title: 'Restart High-CPU Services',
            description: 'Restart services identified as consuming excessive CPU',
            command: 'systemctl restart nginx apache2',
            parameters: {
              services: ['nginx', 'apache2'],
              graceful: true
            },
            riskLevel: 'medium',
            automated: false,
            requiresApproval: true,
            estimatedDuration: 60,
            dependencies: ['action-002'],
            validationChecks: [
              {
                id: 'check-002',
                title: 'Verify service health before restart',
                description: 'Check that services are running and healthy',
                command: 'systemctl is-active nginx apache2',
                expectedResult: 'active',
                critical: true
              }
            ],
            rollbackCommand: 'systemctl start nginx apache2'
          }
        ],
        estimatedTotalTime: 85,
        riskAssessment: {
          overallRisk: 'medium',
          safetyScore: 0.78,
          riskFactors: [
            'Service restart may cause brief downtime',
            'Potential impact on active user sessions'
          ],
          mitigations: [
            'Graceful restart with connection draining',
            'Health checks before and after restart',
            'Rollback plan available'
          ],
          requiresHumanApproval: true
        },
        tags: ['cpu', 'performance', 'web-servers', 'automated'],
        createdAt: new Date().toISOString()
      }
    ],
    confidence: 0.92,
    reasoning: 'Based on the query about high CPU usage, this runbook addresses the most common causes and provides a systematic troubleshooting approach.',
    alternativeApproaches: [
      'Scale horizontally by adding more servers',
      'Implement CPU throttling at application level',
      'Optimize database queries to reduce CPU load'
    ],
    warnings: [
      'Service restarts may cause brief service interruption',
      'Always verify system state before making changes'
    ]
  },
  
  'dns failure': {
    suggestions: [
      {
        id: 'runbook-dns-001',
        title: 'DNS Resolution Failure Recovery',
        description: 'Comprehensive DNS troubleshooting and recovery procedures',
        confidence: 0.88,
        actions: [
          {
            id: 'action-dns-001',
            title: 'Test DNS Resolution',
            description: 'Verify DNS resolution for critical domains',
            command: 'nslookup google.com 8.8.8.8',
            parameters: {
              domains: ['google.com', 'internal.company.com'],
              dns_servers: ['8.8.8.8', '1.1.1.1']
            },
            riskLevel: 'low',
            automated: true,
            requiresApproval: false,
            estimatedDuration: 20,
            dependencies: [],
            validationChecks: []
          },
          {
            id: 'action-dns-002',
            title: 'Restart DNS Services',
            description: 'Restart systemd-resolved and DNS caching services',
            command: 'systemctl restart systemd-resolved dnsmasq',
            parameters: {
              services: ['systemd-resolved', 'dnsmasq']
            },
            riskLevel: 'low',
            automated: false,
            requiresApproval: true,
            estimatedDuration: 30,
            dependencies: ['action-dns-001'],
            validationChecks: [
              {
                id: 'check-dns-001',
                title: 'Verify DNS service status',
                description: 'Ensure DNS services are running',
                command: 'systemctl is-active systemd-resolved',
                expectedResult: 'active',
                critical: true
              }
            ],
            rollbackCommand: 'systemctl start systemd-resolved dnsmasq'
          }
        ],
        estimatedTotalTime: 50,
        riskAssessment: {
          overallRisk: 'low',
          safetyScore: 0.85,
          riskFactors: [
            'Brief DNS resolution interruption during restart'
          ],
          mitigations: [
            'Test resolution before and after changes',
            'Multiple DNS servers configured for redundancy'
          ],
          requiresHumanApproval: false
        },
        tags: ['dns', 'networking', 'infrastructure'],
        createdAt: new Date().toISOString()
      }
    ],
    confidence: 0.88,
    reasoning: 'DNS failures typically require systematic testing and service restarts. This runbook covers the most effective resolution steps.',
    alternativeApproaches: [
      'Switch to alternative DNS providers',
      'Implement local DNS caching',
      'Use DNS load balancing'
    ],
    warnings: []
  }
};

export const mockIncidents = [
  {
    id: 'inc-001',
    title: 'High CPU usage on web servers',
    description: 'CPU utilization has exceeded 90% on multiple web servers, causing slow response times',
    severity: 'high' as const,
    status: 'investigating' as const,
    affectedSystems: ['web-01', 'web-02', 'web-03'],
    assignedTo: 'oncall-engineer',
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2 hours ago
    updatedAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(), // 30 minutes ago
    tags: ['performance', 'web-servers'],
    timeline: [],
    runbooksExecuted: [],
    estimatedImpact: 'Slow response times, potential timeouts',
    customersAffected: 1250
  },
  {
    id: 'inc-002',
    title: 'Database connection pool exhaustion',
    description: 'Database connection pool has reached maximum capacity, causing application errors',
    severity: 'critical' as const,
    status: 'open' as const,
    affectedSystems: ['db-primary', 'app-servers'],
    createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(), // 45 minutes ago
    updatedAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
    tags: ['database', 'connectivity', 'critical'],
    timeline: [],
    runbooksExecuted: [],
    estimatedImpact: 'Complete service outage for new requests',
    customersAffected: 5000
  }
];