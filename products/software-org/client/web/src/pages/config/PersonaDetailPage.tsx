/**
 * Persona Detail Page
 *
 * Detailed view of a persona configuration with permissions, phases, and related entities.
 *
 * @doc.type page
 * @doc.purpose Persona detail view
 * @doc.layer product
 */

import { useParams, useNavigate } from 'react-router';
import { User, AlertCircle, Settings, CheckCircle } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { usePersonas, usePhases } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';

// Extended persona data with complete information
interface ExtendedPersona {
    id: string;
    display_name: string;
    description: string;
    tags: string[];
    permissions: string[];
    responsibilities: string[];
    tools: string[];
    metrics: {
        activeUsers: number;
        avgSessionTime: string;
        tasksCompleted: number;
        automationRate: number;
    };
    accessLevel: 'admin' | 'manager' | 'contributor' | 'viewer';
    status: 'active' | 'inactive';
    createdAt: string;
    updatedAt: string;
}

const extendedPersonas: ExtendedPersona[] = [
    {
        id: 'product_manager',
        display_name: 'Product Manager',
        description: 'Responsible for product strategy, roadmap planning, and feature prioritization. Works closely with engineering and design to deliver customer value.',
        tags: ['product', 'strategy', 'planning'],
        permissions: ['read:all', 'write:roadmap', 'approve:features', 'manage:backlog'],
        responsibilities: [
            'Define product vision and strategy',
            'Prioritize features and manage backlog',
            'Conduct customer research and interviews',
            'Write product requirements and specifications',
            'Coordinate cross-functional teams',
        ],
        tools: ['Jira', 'Confluence', 'Figma', 'ProductBoard', 'Amplitude'],
        metrics: {
            activeUsers: 12,
            avgSessionTime: '4.5 hours',
            tasksCompleted: 156,
            automationRate: 35,
        },
        accessLevel: 'manager',
        status: 'active',
        createdAt: '2022-01-15T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
    {
        id: 'backend_engineer',
        display_name: 'Backend Engineer',
        description: 'Designs and implements server-side logic, APIs, and data storage solutions. Ensures system reliability, scalability, and performance.',
        tags: ['engineering', 'backend', 'development'],
        permissions: ['read:all', 'write:code', 'deploy:staging', 'manage:databases'],
        responsibilities: [
            'Design and implement APIs and services',
            'Write clean, maintainable code',
            'Optimize database queries and performance',
            'Participate in code reviews',
            'Debug and resolve production issues',
        ],
        tools: ['VS Code', 'GitHub', 'Docker', 'PostgreSQL', 'Redis'],
        metrics: {
            activeUsers: 45,
            avgSessionTime: '6.2 hours',
            tasksCompleted: 892,
            automationRate: 72,
        },
        accessLevel: 'contributor',
        status: 'active',
        createdAt: '2022-01-15T00:00:00Z',
        updatedAt: '2024-01-12T00:00:00Z',
    },
    {
        id: 'sre',
        display_name: 'Site Reliability Engineer',
        description: 'Ensures system reliability, availability, and performance. Manages infrastructure, monitoring, and incident response.',
        tags: ['operations', 'infrastructure', 'reliability'],
        permissions: ['read:all', 'deploy:production', 'manage:infrastructure', 'access:monitoring'],
        responsibilities: [
            'Monitor system health and performance',
            'Respond to and resolve incidents',
            'Automate operational tasks',
            'Manage cloud infrastructure',
            'Define and track SLOs/SLIs',
        ],
        tools: ['Kubernetes', 'Terraform', 'Prometheus', 'Grafana', 'PagerDuty'],
        metrics: {
            activeUsers: 8,
            avgSessionTime: '5.8 hours',
            tasksCompleted: 423,
            automationRate: 85,
        },
        accessLevel: 'admin',
        status: 'active',
        createdAt: '2022-03-01T00:00:00Z',
        updatedAt: '2024-01-14T00:00:00Z',
    },
];

function PermissionsGrid({ permissions }: { permissions: string[] }) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {permissions.map((permission) => (
                <div
                    key={permission}
                    className="flex items-center gap-3 p-3 rounded-lg bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-800"
                >
                    <CheckCircle className="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0" />
                    <span className="text-sm font-medium text-green-800 dark:text-green-200 font-mono">
                        {permission}
                    </span>
                </div>
            ))}
        </div>
    );
}

function ResponsibilitiesList({ responsibilities }: { responsibilities: string[] }) {
    return (
        <ul className="space-y-2">
            {responsibilities.map((responsibility, index) => (
                <li key={index} className="flex items-start gap-3">
                    <span className="flex-shrink-0 w-6 h-6 rounded-full bg-blue-100 dark:bg-blue-900/50 text-blue-600 dark:text-blue-400 flex items-center justify-center text-xs font-bold">
                        {index + 1}
                    </span>
                    <span className="text-gray-700 dark:text-gray-300">{responsibility}</span>
                </li>
            ))}
        </ul>
    );
}

function MetricsGrid({ metrics }: { metrics: ExtendedPersona['metrics'] }) {
    return (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-lg bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800">
                <div className="text-sm font-medium text-blue-700 dark:text-blue-300 mb-1">Active Users</div>
                <div className="text-2xl font-bold text-blue-900 dark:text-blue-100">{metrics.activeUsers}</div>
            </div>
            <div className="p-4 rounded-lg bg-purple-50 dark:bg-purple-900/30 border border-purple-200 dark:border-purple-800">
                <div className="text-sm font-medium text-purple-700 dark:text-purple-300 mb-1">Avg Session</div>
                <div className="text-2xl font-bold text-purple-900 dark:text-purple-100">{metrics.avgSessionTime}</div>
            </div>
            <div className="p-4 rounded-lg bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-800">
                <div className="text-sm font-medium text-emerald-700 dark:text-emerald-300 mb-1">Tasks Done</div>
                <div className="text-2xl font-bold text-emerald-900 dark:text-emerald-100">{metrics.tasksCompleted}</div>
            </div>
            <div className="p-4 rounded-lg bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800">
                <div className="text-sm font-medium text-amber-700 dark:text-amber-300 mb-1">Automation</div>
                <div className="text-2xl font-bold text-amber-900 dark:text-amber-100">{metrics.automationRate}%</div>
            </div>
        </div>
    );
}

export function PersonaDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const { data: personas, isLoading } = usePersonas();
    const { data: phases } = usePhases();

    // Find persona from API or extended data
    const persona = personas?.find(p => p.id === id);
    const extendedData = extendedPersonas.find(p => p.id === id);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600" />
            </div>
        );
    }

    if (!persona && !extendedData) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>Persona Not Found</h2>
                <p className="text-gray-500 mt-2">The persona "{id}" could not be found.</p>
                <button
                    onClick={() => navigate('/personas')}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to Personas
                </button>
            </div>
        );
    }

    const displayData = extendedData || {
        ...persona,
        description: 'No description available',
        responsibilities: [],
        tools: [],
        metrics: { activeUsers: 0, avgSessionTime: '0', tasksCompleted: 0, automationRate: 0 },
        accessLevel: 'viewer' as const,
        status: 'active' as const,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    };

    // Find phases this persona is involved in
    const personaPhases = phases?.filter(p => p.personas?.includes(id || '')) || [];

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Display Name', value: displayData.display_name },
                { key: 'accessLevel', label: 'Access Level', value: displayData.accessLevel, type: 'badge' },
                { key: 'status', label: 'Status', value: displayData.status, type: 'badge' },
                { key: 'tags', label: 'Tags', value: displayData.tags, type: 'array' },
            ],
        },
        {
            id: 'metrics',
            title: 'Usage Metrics',
            content: extendedData?.metrics ? (
                <MetricsGrid metrics={extendedData.metrics} />
            ) : (
                <p className="text-gray-500">No metrics available</p>
            ),
        },
        {
            id: 'permissions',
            title: 'Permissions',
            description: `${displayData.permissions?.length || 0} permissions granted`,
            content: displayData.permissions?.length ? (
                <PermissionsGrid permissions={displayData.permissions} />
            ) : (
                <p className="text-gray-500">No permissions configured</p>
            ),
        },
        {
            id: 'responsibilities',
            title: 'Responsibilities',
            content: extendedData?.responsibilities?.length ? (
                <ResponsibilitiesList responsibilities={extendedData.responsibilities} />
            ) : (
                <p className="text-gray-500">No responsibilities defined</p>
            ),
        },
        {
            id: 'tools',
            title: 'Tools & Integrations',
            fields: [
                { key: 'tools', label: 'Tools', value: extendedData?.tools || [], type: 'array' },
            ],
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'Persona ID', value: displayData.id, type: 'code' },
                { key: 'createdAt', label: 'Created', value: displayData.createdAt, type: 'date' },
                { key: 'updatedAt', label: 'Last Updated', value: displayData.updatedAt, type: 'date' },
            ],
        },
    ];

    // Build related entities
    const relatedEntities: RelatedEntity[] = [];

    // Add related phases
    personaPhases.forEach(phase => {
        relatedEntities.push({
            id: phase.id,
            name: phase.display_name,
            type: 'Phase',
            href: `/phases/${phase.id}`,
            status: 'active',
        });
    });

    return (
        <EntityDetailPage
            entityType="Persona"
            entityId={displayData.id || id || ''}
            title={displayData.display_name || 'Unknown Persona'}
            subtitle={`${displayData.accessLevel} access`}
            description={displayData.description}
            status={displayData.status}
            icon={<User className="w-6 h-6" />}
            backHref="/personas"
            backLabel="Back to Personas"
            sections={sections}
            relatedEntities={relatedEntities}
            onEdit={() => console.log('Edit persona')}
            actions={[
                {
                    id: 'settings',
                    label: 'Settings',
                    icon: <Settings className="w-4 h-4" />,
                    onClick: () => console.log('Persona settings'),
                    variant: 'secondary',
                },
            ]}
        />
    );
}

export default PersonaDetailPage;
