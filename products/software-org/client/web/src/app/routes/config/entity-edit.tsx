/**
 * Unified Entity Edit Route
 *
 * A unified route for editing existing entities of any type.
 * Uses the EntityEditPage component with the entity-registry.
 *
 * @doc.type route
 * @doc.purpose Unified edit page for all config entity types
 * @doc.layer product
 * @doc.pattern Route
 */

import { useParams, useLocation } from 'react-router';
import {
    EntityEditPage,
    getEntityTypeByRoutePath,
    type FieldOption,
} from '@/features/config';
import { useCallback } from 'react';

// Demo dynamic options loader
async function loadDynamicOptions(): Promise<Record<string, FieldOption[]>> {
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

export default function EntityEditPageRoute() {
    const params = useParams<{ id: string }>();
    const location = useLocation();

    // Extract entity type from URL path (e.g., /config/agents/123/edit -> agents)
    const pathSegments = location.pathname.split('/').filter(Boolean);
    const editIndex = pathSegments.indexOf('edit');
    const entitySlug = editIndex > 1 ? pathSegments[editIndex - 2] : (pathSegments[1] || '');

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

    // Demo fetch handler
    const handleFetch = useCallback(async (id: string) => {
        console.log('Fetching entity:', id);
        // In production: const data = await api.get(`/config/${entityType?.routePath}/${id}`);

        // Return demo data
        return {
            id,
            name: `Sample ${entityType?.name || 'Entity'}`,
            description: 'This is a sample entity for demonstration purposes.',
            status: 'active',
            department: 'engineering',
            role: 'Sample Role',
            capabilities: ['capability-1', 'capability-2'],
            'model.id': 'gpt-4-turbo',
            'model.maxTokens': 4096,
            'personality.temperature': 0.7,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };
    }, [entityType?.name]);

    // Demo update handler
    const handleUpdate = useCallback(async (id: string, data: Record<string, unknown>) => {
        console.log('Updating entity:', id, data);
        // In production: await api.put(`/config/${entityType?.routePath}/${id}`, data);
    }, []);

    return (
        <EntityEditPage
            entityType={entityType}
            mode="edit"
            entityId={params.id}
            fetchEntity={handleFetch}
            updateEntity={handleUpdate}
            loadDynamicOptions={loadDynamicOptions}
        />
    );
}
