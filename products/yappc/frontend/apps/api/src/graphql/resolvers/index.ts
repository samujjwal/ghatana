/**
 * GraphQL Resolvers Index
 * 
 * Exports resolver implementation for YAPPC GraphQL API.
 * All resolvers use Prisma to fetch data from the database.
 * 
 * @doc.type module
 * @doc.purpose GraphQL resolver implementation
 * @doc.layer product
 * @doc.pattern GraphQL Resolvers
 */

import prisma from '../../db';
import { YappcCoreResolver } from './YappcCoreResolver';

// Helper to convert Prisma dates to ISO strings
const toISOString = (date: Date | null | undefined) => date?.toISOString() ?? null;

/** Resolver context shape injected via server context from Fastify auth middleware */
interface ResolverContext {
    userId?: string;
    email?: string;
    role?: string;
}

/**
 * Extract authenticated userId from resolver context.
 * Throws if no userId is present (i.e. unauthenticated in production).
 */
function requireUserId(context: ResolverContext): string {
    const userId = context?.userId;
    if (!userId) {
        throw new Error('Authentication required: no userId in context');
    }
    return userId;
}

export function createSimpleResolvers() {
    return {
        Query: {
            ...YappcCoreResolver.Query,
            
            // Current user - resolved from auth context
            me: async (_parent: unknown, _args: unknown, context: ResolverContext) => {
                const userId = context?.userId;
                if (userId) {
                    return prisma.user.findUnique({
                        where: { id: userId },
                    });
                }
                // Fallback for unauthenticated dev mode
                return prisma.user.findFirst({
                    where: { role: 'ADMIN' },
                });
            },

            // User by ID
            user: async (_parent: unknown, args: { id: string }) => {
                return prisma.user.findUnique({
                    where: { id: args.id },
                });
            },

            // All users
            users: async () => {
                return prisma.user.findMany({
                    orderBy: { name: 'asc' },
                });
            },

            // Workspace by ID
            workspace: async (_parent: unknown, args: { id: string }) => {
                const workspace = await prisma.workspace.findUnique({
                    where: { id: args.id },
                    include: {
                        owner: true,
                        members: {
                            include: { user: true },
                        },
                        ownedProjects: true,
                        includedProjects: {
                            include: { project: true },
                        },
                    },
                });
                
                if (!workspace) return null;
                
                return {
                    ...workspace,
                    projects: [
                        ...workspace.ownedProjects,
                        ...workspace.includedProjects.map(ip => ip.project),
                    ],
                    createdAt: toISOString(workspace.createdAt),
                    updatedAt: toISOString(workspace.updatedAt),
                };
            },

            // All workspaces for current user
            workspaces: async (_parent: unknown, _args: unknown, context: ResolverContext) => {
                const userId = requireUserId(context);
                
                const workspaces = await prisma.workspace.findMany({
                    where: {
                        members: {
                            some: { userId },
                        },
                    },
                    include: {
                        owner: true,
                        members: {
                            include: { user: true },
                        },
                        _count: {
                            select: { ownedProjects: true },
                        },
                    },
                    orderBy: [
                        { isDefault: 'desc' },
                        { updatedAt: 'desc' },
                    ],
                });
                
                return workspaces.map(ws => ({
                    ...ws,
                    projectCount: ws._count.ownedProjects,
                    createdAt: toISOString(ws.createdAt),
                    updatedAt: toISOString(ws.updatedAt),
                }));
            },

            // Project by ID
            project: async (_parent: unknown, args: { id: string }) => {
                const project = await prisma.project.findUnique({
                    where: { id: args.id },
                    include: {
                        ownerWorkspace: true,
                        createdBy: true,
                        documents: true,
                        pages: true,
                    },
                });
                
                if (!project) return null;
                
                return {
                    ...project,
                    workspaceId: project.ownerWorkspaceId,
                    createdAt: toISOString(project.createdAt),
                    updatedAt: toISOString(project.updatedAt),
                };
            },

            // Projects by workspace ID
            projects: async (_parent: unknown, args: { workspaceId?: string }) => {
                const where = args.workspaceId 
                    ? { ownerWorkspaceId: args.workspaceId }
                    : {};
                
                const projects = await prisma.project.findMany({
                    where,
                    include: {
                        ownerWorkspace: true,
                        createdBy: true,
                        documents: { select: { id: true } },
                        pages: { select: { id: true } },
                    },
                    orderBy: { updatedAt: 'desc' },
                });
                
                return projects.map(p => ({
                    ...p,
                    workspaceId: p.ownerWorkspaceId,
                    createdAt: toISOString(p.createdAt),
                    updatedAt: toISOString(p.updatedAt),
                }));
            },

            // Canvas document by ID
            canvasDocument: async (_parent: unknown, args: { id: string }) => {
                const doc = await prisma.canvasDocument.findUnique({
                    where: { id: args.id },
                    include: {
                        project: true,
                        createdBy: true,
                    },
                });
                
                if (!doc) return null;
                
                return {
                    ...doc,
                    content: doc.content ? JSON.parse(doc.content as string) : {},
                    createdAt: toISOString(doc.createdAt),
                    updatedAt: toISOString(doc.updatedAt),
                };
            },

            // Canvas documents by project ID
            canvasDocuments: async (_parent: unknown, args: { projectId: string }) => {
                const docs = await prisma.canvasDocument.findMany({
                    where: { projectId: args.projectId },
                    orderBy: { updatedAt: 'desc' },
                });
                
                return docs.map(d => ({
                    ...d,
                    content: d.content ? JSON.parse(d.content as string) : {},
                    createdAt: toISOString(d.createdAt),
                    updatedAt: toISOString(d.updatedAt),
                }));
            },

            // Page by ID
            page: async (_parent: unknown, args: { id: string }) => {
                const page = await prisma.page.findUnique({
                    where: { id: args.id },
                    include: {
                        project: true,
                        createdBy: true,
                    },
                });
                
                if (!page) return null;
                
                return {
                    ...page,
                    content: page.content ? JSON.parse(page.content as string) : {},
                    createdAt: toISOString(page.createdAt),
                    updatedAt: toISOString(page.updatedAt),
                };
            },

            // Pages by project ID
            pages: async (_parent: unknown, args: { projectId: string }) => {
                const pages = await prisma.page.findMany({
                    where: { projectId: args.projectId },
                    orderBy: { path: 'asc' },
                });
                
                return pages.map(p => ({
                    ...p,
                    content: p.content ? JSON.parse(p.content as string) : {},
                    createdAt: toISOString(p.createdAt),
                    updatedAt: toISOString(p.updatedAt),
                }));
            },

            // Page by project and path
            pageByPath: async (_parent: unknown, args: { projectId: string; path: string }) => {
                const page = await prisma.page.findFirst({
                    where: {
                        projectId: args.projectId,
                        path: args.path,
                    },
                    include: {
                        project: true,
                        createdBy: true,
                    },
                });
                
                if (!page) return null;
                
                return {
                    ...page,
                    content: page.content ? JSON.parse(page.content as string) : {},
                    createdAt: toISOString(page.createdAt),
                    updatedAt: toISOString(page.updatedAt),
                };
            },
        },

        Mutation: {
            ...YappcCoreResolver.Mutation,
            
            // Create workspace
            createWorkspace: async (_parent: unknown, args: { 
                name: string; 
                description?: string;
                createDefaultProject?: boolean;
            }, context: ResolverContext) => {
                const userId = requireUserId(context);
                
                const workspace = await prisma.workspace.create({
                    data: {
                        name: args.name,
                        description: args.description,
                        ownerId: userId,
                        members: {
                            create: { userId, role: 'ADMIN' },
                        },
                    },
                    include: {
                        owner: true,
                        members: { include: { user: true } },
                    },
                });
                
                // Create default project if requested
                if (args.createDefaultProject) {
                    await prisma.project.create({
                        data: {
                            name: `${args.name} - Default Project`,
                            ownerWorkspaceId: workspace.id,
                            createdById: userId,
                            type: 'FULL_STACK',
                            status: 'ACTIVE',
                            isDefault: true,
                        },
                    });
                }
                
                return {
                    ...workspace,
                    projects: [],
                    createdAt: toISOString(workspace.createdAt),
                    updatedAt: toISOString(workspace.updatedAt),
                };
            },

            // Update workspace
            updateWorkspace: async (_parent: unknown, args: {
                id: string;
                name?: string;
                description?: string;
            }) => {
                const workspace = await prisma.workspace.update({
                    where: { id: args.id },
                    data: {
                        name: args.name,
                        description: args.description,
                    },
                    include: {
                        owner: true,
                        members: { include: { user: true } },
                        ownedProjects: true,
                    },
                });
                
                return {
                    ...workspace,
                    projects: workspace.ownedProjects,
                    createdAt: toISOString(workspace.createdAt),
                    updatedAt: toISOString(workspace.updatedAt),
                };
            },

            // Delete workspace
            deleteWorkspace: async (_parent: unknown, args: { id: string }) => {
                await prisma.workspace.delete({
                    where: { id: args.id },
                });
                return true;
            },

            // Create project
            createProject: async (_parent: unknown, args: {
                workspaceId: string;
                name: string;
                description?: string;
                type?: string;
            }, context: ResolverContext) => {
                const userId = requireUserId(context);
                
                const project = await prisma.project.create({
                    data: {
                        name: args.name,
                        description: args.description,
                        ownerWorkspaceId: args.workspaceId,
                        createdById: userId,
                        type: (args.type as unknown) ?? 'FULL_STACK',
                        status: 'ACTIVE',
                    },
                    include: {
                        ownerWorkspace: true,
                        createdBy: true,
                    },
                });
                
                return {
                    ...project,
                    workspaceId: project.ownerWorkspaceId,
                    documents: [],
                    pages: [],
                    createdAt: toISOString(project.createdAt),
                    updatedAt: toISOString(project.updatedAt),
                };
            },

            // Update project
            updateProject: async (_parent: unknown, args: {
                id: string;
                name?: string;
                description?: string;
                type?: string;
                status?: string;
            }) => {
                const project = await prisma.project.update({
                    where: { id: args.id },
                    data: {
                        name: args.name,
                        description: args.description,
                        type: args.type as unknown,
                        status: args.status as unknown,
                    },
                    include: {
                        ownerWorkspace: true,
                        documents: true,
                        pages: true,
                    },
                });
                
                return {
                    ...project,
                    workspaceId: project.ownerWorkspaceId,
                    createdAt: toISOString(project.createdAt),
                    updatedAt: toISOString(project.updatedAt),
                };
            },

            // Delete project
            deleteProject: async (_parent: unknown, args: { id: string }) => {
                await prisma.project.delete({
                    where: { id: args.id },
                });
                return true;
            },

            // Create canvas document
            createCanvasDocument: async (_parent: unknown, args: {
                projectId: string;
                name: string;
                description?: string;
                content?: unknown;
            }, context: ResolverContext) => {
                const userId = requireUserId(context);
                
                const doc = await prisma.canvasDocument.create({
                    data: {
                        projectId: args.projectId,
                        createdById: userId,
                        name: args.name,
                        description: args.description,
                        content: args.content ? JSON.stringify(args.content) : '{}',
                    },
                });
                
                return {
                    ...doc,
                    content: args.content ?? {},
                    createdAt: toISOString(doc.createdAt),
                    updatedAt: toISOString(doc.updatedAt),
                };
            },

            // Update canvas document
            updateCanvasDocument: async (_parent: unknown, args: {
                id: string;
                name?: string;
                description?: string;
                content?: unknown;
            }) => {
                const doc = await prisma.canvasDocument.update({
                    where: { id: args.id },
                    data: {
                        name: args.name,
                        description: args.description,
                        content: args.content ? JSON.stringify(args.content) : undefined,
                    },
                });
                
                return {
                    ...doc,
                    content: doc.content ? JSON.parse(doc.content as string) : {},
                    createdAt: toISOString(doc.createdAt),
                    updatedAt: toISOString(doc.updatedAt),
                };
            },

            // Delete canvas document
            deleteCanvasDocument: async (_parent: unknown, args: { id: string }) => {
                await prisma.canvasDocument.delete({
                    where: { id: args.id },
                });
                return true;
            },

            // Create page
            createPage: async (_parent: unknown, args: {
                projectId: string;
                name: string;
                path: string;
                layout?: string;
                content?: unknown;
            }, context: ResolverContext) => {
                const userId = requireUserId(context);
                
                const page = await prisma.page.create({
                    data: {
                        projectId: args.projectId,
                        createdById: userId,
                        name: args.name,
                        path: args.path,
                        layout: args.layout ?? 'default',
                        content: args.content ? JSON.stringify(args.content) : '{}',
                    },
                });
                
                return {
                    ...page,
                    content: args.content ?? {},
                    createdAt: toISOString(page.createdAt),
                    updatedAt: toISOString(page.updatedAt),
                };
            },

            // Update page
            updatePage: async (_parent: unknown, args: {
                id: string;
                name?: string;
                path?: string;
                layout?: string;
                content?: unknown;
            }) => {
                const page = await prisma.page.update({
                    where: { id: args.id },
                    data: {
                        name: args.name,
                        path: args.path,
                        layout: args.layout,
                        content: args.content ? JSON.stringify(args.content) : undefined,
                    },
                });
                
                return {
                    ...page,
                    content: page.content ? JSON.parse(page.content as string) : {},
                    createdAt: toISOString(page.createdAt),
                    updatedAt: toISOString(page.updatedAt),
                };
            },

            // Delete page
            deletePage: async (_parent: unknown, args: { id: string }) => {
                await prisma.page.delete({
                    where: { id: args.id },
                });
                return true;
            },
        },
        
        // Type resolvers for nested fields
        Workspace: {
            owner: async (parent: { ownerId: string }) => {
                return prisma.user.findUnique({
                    where: { id: parent.ownerId },
                });
            },
            projects: async (parent: { id: string }) => {
                return prisma.project.findMany({
                    where: { ownerWorkspaceId: parent.id },
                });
            },
        },
        
        Project: {
            workspace: async (parent: { ownerWorkspaceId: string }) => {
                return prisma.workspace.findUnique({
                    where: { id: parent.ownerWorkspaceId },
                });
            },
            createdBy: async (parent: { createdById: string }) => {
                return prisma.user.findUnique({
                    where: { id: parent.createdById },
                });
            },
            documents: async (parent: { id: string }) => {
                return prisma.canvasDocument.findMany({
                    where: { projectId: parent.id },
                });
            },
            pages: async (parent: { id: string }) => {
                return prisma.page.findMany({
                    where: { projectId: parent.id },
                });
            },
        },
        
        Domain: YappcCoreResolver.Domain,
    };
}
