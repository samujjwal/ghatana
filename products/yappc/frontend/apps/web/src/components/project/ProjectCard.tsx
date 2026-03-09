import { useNavigate } from 'react-router';
import { Clock as AccessTime, ChevronRight, Folder } from 'lucide-react';
import { Typography, Chip } from '@ghatana/ui';

interface Project {
    id: string;
    name: string;
    description?: string;
    type?: string;
    updatedAt?: string | Date;
    workspaceId?: string;
}

interface ProjectCardProps {
    project: Project;
    onClick: (id: string) => void;
}

export function ProjectCard({ project, onClick }: ProjectCardProps) {
    return (
        <div
            onClick={() => onClick(project.id)}
            className="p-3 rounded-lg border border-divider hover:border-primary-300 dark:hover:border-primary-700 hover:bg-bg-paper-secondary cursor-pointer transition-all group"
        >
            <div className="flex items-center justify-between">
                <div className="flex-1">
                    <Typography as="p" className="text-sm group-hover:text-primary-600 transition-colors" fontWeight="medium">
                        {project.name}
                    </Typography>
                    <div className="flex items-center gap-2 mt-1">
                        <Chip
                            label={project.type || 'webapp'}
                            size="sm"
                            variant="outlined"
                            className="h-[20px] text-[0.65rem]"
                        />
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {project.updatedAt ? new Date(project.updatedAt).toLocaleDateString() : 'Recently'}
                        </Typography>
                    </div>
                </div>
                <ChevronRight size={16} className="text-text-secondary group-hover:text-primary-600 transition-colors" />
            </div>
        </div>
    );
}
