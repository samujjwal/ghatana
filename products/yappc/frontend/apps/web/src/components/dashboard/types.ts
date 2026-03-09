// Types for dashboard components
export interface Project {
    id: string;
    name: string;
    type?: string;
    updatedAt?: string | Date;
    workspaceId?: string;
    description?: string;
}

export interface Workflow {
    id: string;
    name: string;
    status: string;
    description?: string;
    taskCount?: number;
}

export interface Workspace {
    id: string;
    name: string;
    description?: string;
}
