import { Card, Badge, Button } from '@ghatana/ui';
import { PluginMetadata } from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';

interface PluginCardProps {
    metadata: PluginMetadata;
    status: 'active' | 'inactive' | 'error';
    category: string;
}

export function PluginCard({ metadata, status, category }: PluginCardProps) {
    const statusColors = {
        active: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
        inactive: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
        error: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300',
    };

    return (
        <Card className="p-4 flex flex-col gap-3">
            <div className="flex justify-between items-start">
                <div>
                    <h3 className="font-semibold text-lg text-gray-900 dark:text-white">{metadata.name}</h3>
                    <p className="text-xs text-gray-500 dark:text-gray-400 font-mono">{metadata.id}</p>
                </div>
                <Badge className={statusColors[status]}>{status}</Badge>
            </div>

            <p className="text-sm text-gray-600 dark:text-gray-300 line-clamp-2">
                {metadata.description}
            </p>

            <div className="flex flex-wrap gap-2 mt-auto">
                <Badge variant="outline" className="text-xs">{category}</Badge>
                <Badge variant="outline" className="text-xs">v{metadata.version}</Badge>
                <Badge variant="outline" className="text-xs">Priority: {metadata.priority}</Badge>
            </div>

            {metadata.tags && metadata.tags.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-2">
                    {metadata.tags.map(tag => (
                        <span key={tag} className="text-xs bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded dark:bg-blue-900/30 dark:text-blue-400">
                            #{tag}
                        </span>
                    ))}
                </div>
            )}
        </Card>
    );
}
