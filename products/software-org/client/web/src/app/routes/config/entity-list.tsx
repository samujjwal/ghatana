/**
 * Unified Entity List Route
 *
 * A unified route for listing entities of any type.
 * Uses the EntityListPage component with the entity-registry.
 *
 * @doc.type route
 * @doc.purpose Unified list page for all config entity types
 * @doc.layer product
 * @doc.pattern Route
 */

import { useParams, useLocation, useNavigate } from 'react-router';
import {
    EntityListPage,
    getEntityTypeByRoutePath,
    type EntityItem,
} from '@/features/config';
import { useState, useEffect } from 'react';

export default function EntityListRoute() {
    const location = useLocation();
    const navigate = useNavigate();

    const [items, setItems] = useState<EntityItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Extract entity type from URL path (e.g., /config/services -> services)
    const pathSegments = location.pathname.split('/').filter(Boolean);
    const entitySlug = pathSegments.length > 0 ? pathSegments[pathSegments.length - 1] : '';

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
        const fetchData = async () => {
            setIsLoading(true);
            try {
                // Simulate API call
                await new Promise(resolve => setTimeout(resolve, 500));

                // Mock data
                const mockItems: EntityItem[] = [
                    {
                        id: '1',
                        name: `Sample ${entityType.name} 1`,
                        description: 'Sample description 1',
                        status: 'active',
                        updatedAt: new Date().toISOString(),
                    },
                    {
                        id: '2',
                        name: `Sample ${entityType.name} 2`,
                        description: 'Sample description 2',
                        status: 'inactive',
                        updatedAt: new Date(Date.now() - 86400000).toISOString(),
                    },
                ];
                setItems(mockItems);
                setError(null);
            } catch (err) {
                console.error('Failed to fetch entities:', err);
                setError('Failed to load entities');
            } finally {
                setIsLoading(false);
            }
        };

        fetchData();
    }, [entityType]);

    const handleDelete = async (item: EntityItem) => {
        if (!confirm(`Are you sure you want to delete ${item.name}?`)) return;

        try {
            // Simulate API call
            await new Promise(resolve => setTimeout(resolve, 500));
            setItems(prev => prev.filter(i => i.id !== item.id));
        } catch (err) {
            console.error('Failed to delete entity:', err);
            alert('Failed to delete entity');
        }
    };

    const handleDuplicate = (item: EntityItem) => {
        navigate(`/config/${entityType.routePath}/new?source=${item.id}`);
    };

    return (
        <EntityListPage
            entityType={entityType}
            items={items}
            isLoading={isLoading}
            error={error}
            onDelete={handleDelete}
            onDuplicate={handleDuplicate}
        />
    );
}
