/**
 * Unified Entity Create Route
 *
 * A unified route for creating new entities of any type.
 * Uses the EntityEditPage component with the entity-registry.
 *
 * @doc.type route
 * @doc.purpose Unified create page for all config entity types
 * @doc.layer product
 * @doc.pattern Route
 */

import { useLocation } from 'react-router';
import {
    EntityEditPage,
    getEntityTypeByRoutePath,
    type FieldOption,
} from '@/features/config';

// Demo dynamic options loader
async function loadDynamicOptions(): Promise<Record<string, FieldOption[]>> {
    // In production, this would fetch from API
    return {
        departments: [
            { value: 'engineering', label: 'Engineering' },
            { value: 'qa', label: 'Quality Assurance' },
            { value: 'devops', label: 'DevOps' },
            { value: 'security', label: 'Security' },
            { value: 'product', label: 'Product' },
        ],
        personas: [
            { value: 'backend_engineer', label: 'Backend Engineer' },
            { value: 'frontend_engineer', label: 'Frontend Engineer' },
            { value: 'devops_engineer', label: 'DevOps Engineer' },
            { value: 'qa_engineer', label: 'QA Engineer' },
        ],
        phases: [
            { value: 'build', label: 'Build & Integrate' },
            { value: 'test', label: 'Test & Validate' },
            { value: 'deploy', label: 'Deploy & Release' },
            { value: 'operate', label: 'Operate & Monitor' },
        ],
        workflows: [
            { value: 'ci-cd', label: 'CI/CD Pipeline' },
            { value: 'security-scan', label: 'Security Scan' },
            { value: 'deploy', label: 'Deploy Workflow' },
        ],
    };
}

export default function EntityCreatePage() {
    const location = useLocation();

    // Extract entity type from URL path (e.g., /config/agents/new -> agents)
    const pathSegments = location.pathname.split('/').filter(Boolean);
    const newIndex = pathSegments.indexOf('new');
    const entitySlug = newIndex > 0 ? pathSegments[newIndex - 1] : (pathSegments[1] || '');

    const entityType = getEntityTypeByRoutePath(entitySlug);

    // Handle unknown entity type
    if (!entityType) {
        return (
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">
                        Entity Type Not Found
                    </h1>
                    <p className="text-gray-500 dark:text-gray-400">
                        Could not determine entity type from path: {location.pathname}
                    </p>
                </div>
            </div>
        );
    }

    // Demo create handler
    const handleCreate = async (data: Record<string, unknown>) => {
        console.log('Creating entity:', data);
        // In production: await api.post(`/config/${entityType.routePath}`, data);
    };

    return (
        <EntityEditPage
            entityType={entityType}
            mode="create"
            createEntity={handleCreate}
            loadDynamicOptions={loadDynamicOptions}
        />
    );
}
