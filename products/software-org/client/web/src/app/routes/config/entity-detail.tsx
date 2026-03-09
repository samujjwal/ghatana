/**
 * Unified Entity Detail Route
 *
 * A unified route for viewing entity details.
 * Uses the EntityDetailPage component with the entity-registry.
 *
 * @doc.type route
 * @doc.purpose Unified detail page for all config entity types
 * @doc.layer product
 * @doc.pattern Route
 */

import { useParams, useLocation, useNavigate } from 'react-router';
import {
    EntityDetailPage,
    getEntityTypeByRoutePath,
} from '@/features/config';
import { useState, useEffect } from 'react';

export default function EntityDetailRoute() {
    const params = useParams<{ id: string }>();
    const location = useLocation();
    const navigate = useNavigate();

    const [data, setData] = useState<Record<string, unknown> | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);

    // Extract entity type from URL path (e.g., /config/services/123 -> services)
    const pathSegments = location.pathname.split('/').filter(Boolean);
    const entitySlug = pathSegments.length >= 2 ? pathSegments[pathSegments.length - 2] : (pathSegments[1] || '');

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

    // Fetch data
    useEffect(() => {
        if (!params.id) return;

        const fetchData = async () => {
            setIsLoading(true);
            try {
                // Simulate API call
                await new Promise(resolve => setTimeout(resolve, 500));

                // Mock data
                const mockData = {
                    id: params.id,
                    name: `Sample ${entityType.name}`,
                    description: 'This is a sample entity for demonstration purposes. It showcases the unified detail view component.',
                    status: 'active',
                    department: 'engineering',
                    role: 'Sample Role',
                    type: entityType.fields.find(f => f.key === 'type')?.options?.[0]?.value || 'default',
                    capabilities: ['capability-1', 'capability-2', 'capability-3'],
                    tags: ['tag-a', 'tag-b'],
                    'model.id': 'gpt-4-turbo',
                    'model.maxTokens': 4096,
                    'personality.temperature': 0.7,
                    systemPrompt: 'You are a helpful AI assistant that specializes in software development tasks.',
                    enabled: true,
                    createdAt: new Date(Date.now() - 7 * 86400000).toISOString(),
                    updatedAt: new Date().toISOString(),
                };
                setData(mockData);
                setError(null);
            } catch (err) {
                console.error('Failed to fetch entity:', err);
                setError('Failed to load entity details');
            } finally {
                setIsLoading(false);
            }
        };

        fetchData();
    }, [params.id, entityType]);

    const handleDelete = async () => {
        if (!confirm('Are you sure you want to delete this entity?')) return;

        setIsDeleting(true);
        try {
            // Simulate API call
            await new Promise(resolve => setTimeout(resolve, 1000));
            console.log('Deleted entity:', params.id);
            navigate(`/config/${entityType.routePath}`);
        } catch (err) {
            console.error('Failed to delete entity:', err);
            alert('Failed to delete entity');
        } finally {
            setIsDeleting(false);
        }
    };

    const handleDuplicate = () => {
        navigate(`/config/${entityType.routePath}/new?source=${params.id}`);
    };

    return (
        <EntityDetailPage
            entityType={entityType}
            data={data}
            isLoading={isLoading}
            error={error}
            onDelete={handleDelete}
            onDuplicate={handleDuplicate}
            isDeleting={isDeleting}
        />
    );
}
