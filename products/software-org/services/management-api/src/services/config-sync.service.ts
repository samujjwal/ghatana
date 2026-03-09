import { prisma } from '../db/client.js';
import { getConfigLoader } from './config-loader.service.js';
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'js-yaml';

export class ConfigSyncService {
    private static instance: ConfigSyncService;

    private constructor() {}

    public static getInstance(): ConfigSyncService {
        if (!ConfigSyncService.instance) {
            ConfigSyncService.instance = new ConfigSyncService();
        }
        return ConfigSyncService.instance;
    }

    /**
     * Syncs configuration from YAML files to the database.
     * @param configPath Optional path to config directory. If not provided, uses default.
     */
    async syncFromConfig(configPath?: string): Promise<void> {
        console.log(`[ConfigSync] Starting sync from ${configPath || 'default path'}...`);

        try {
            const configLoader = getConfigLoader(configPath);
            // Force reload to ensure we get fresh data if path changed
            if (configPath) {
                configLoader.clearCache();
            }
            
            const orgConfig = await configLoader.loadOrgConfig();

            console.log(`[ConfigSync] Loaded config for organization: ${orgConfig.name}`);

            // 1. Ensure Organization exists
            const org = await prisma.organization.upsert({
                where: { namespace: orgConfig.id },
                update: {
                    name: orgConfig.id,
                    displayName: orgConfig.name,
                    description: orgConfig.description,
                },
                create: {
                    name: orgConfig.id,
                    namespace: orgConfig.id,
                    displayName: orgConfig.name,
                    description: orgConfig.description,
                    structure: { type: 'hierarchical' },
                    settings: {},
                },
            });
            console.log(`[ConfigSync] Organization synced: ${org.name}`);

            // 2. Sync Departments
            for (const deptConfig of orgConfig.departments) {
                const dept = await prisma.department.upsert({
                    where: { 
                        organizationId_name: {
                            organizationId: org.id,
                            name: deptConfig.name
                        }
                    },
                    update: {
                        type: deptConfig.type as any,
                        description: deptConfig.description,
                        status: 'ACTIVE',
                    },
                    create: {
                        organizationId: org.id,
                        name: deptConfig.name,
                        type: deptConfig.type as any,
                        description: deptConfig.description,
                        status: 'ACTIVE',
                    },
                });
                console.log(`[ConfigSync]   ├─ Department synced: ${dept.name}`);

                // 3. Sync Agents
                if (deptConfig.agents) {
                    for (const agentConfig of deptConfig.agents) {
                        // Handle role being an object in YAML but string in DB
                        const roleValue = typeof agentConfig.role === 'object' 
                            ? (agentConfig.role as any).name || (agentConfig.role as any).title || JSON.stringify(agentConfig.role)
                            : agentConfig.role;

                        await prisma.agent.upsert({
                            where: {
                                organizationId_name: {
                                    organizationId: org.id,
                                    name: agentConfig.name
                                }
                            },
                            update: {
                                departmentId: dept.id,
                                role: roleValue,
                                capabilities: agentConfig.capabilities,
                                configuration: {
                                    personality: agentConfig.personality,
                                    model: agentConfig.model,
                                    system_prompt: agentConfig.system_prompt
                                },
                                status: 'ONLINE'
                            },
                            create: {
                                organizationId: org.id,
                                departmentId: dept.id,
                                name: agentConfig.name,
                                role: roleValue,
                                capabilities: agentConfig.capabilities,
                                configuration: {
                                    personality: agentConfig.personality,
                                    model: agentConfig.model,
                                    system_prompt: agentConfig.system_prompt
                                },
                                status: 'ONLINE'
                            }
                        });
                        console.log(`[ConfigSync]   │  ├─ Agent synced: ${agentConfig.name}`);
                    }
                }
            }

            console.log('[ConfigSync] Sync complete!');
        } catch (error) {
            console.error('[ConfigSync] Error syncing from config:', error);
            throw error;
        }
    }

    /**
     * Exports current database state to YAML configuration files.
     * @param outputDir Directory to write YAML files to.
     */
    async exportToConfig(outputDir: string): Promise<void> {
        console.log(`[ConfigSync] Exporting config to ${outputDir}...`);

        if (!fs.existsSync(outputDir)) {
            fs.mkdirSync(outputDir, { recursive: true });
        }

        // 1. Export Organization
        const org = await prisma.organization.findFirst();
        if (!org) {
            throw new Error('No organization found to export');
        }

        const orgYaml = {
            apiVersion: 'virtualorg.ghatana.com/v1',
            kind: 'Organization',
            metadata: {
                name: org.name,
                namespace: org.namespace,
                labels: {
                    version: '1.0.0' // TODO: Store version in DB
                }
            },
            spec: {
                displayName: org.displayName,
                description: org.description,
                structure: org.structure,
                settings: org.settings
            }
        };

        fs.writeFileSync(path.join(outputDir, 'organization.yaml'), yaml.dump(orgYaml));
        console.log(`[ConfigSync] Exported organization.yaml`);

        // 2. Export Departments
        const departmentsDir = path.join(outputDir, 'departments');
        if (!fs.existsSync(departmentsDir)) {
            fs.mkdirSync(departmentsDir);
        }

        const departments = await prisma.department.findMany({
            where: { organizationId: org.id },
            include: { agents: true }
        });

        for (const dept of departments) {
            const deptYaml = {
                apiVersion: 'virtualorg.ghatana.com/v1',
                kind: 'Department',
                metadata: {
                    name: dept.name,
                    labels: {
                        type: dept.type
                    }
                },
                spec: {
                    description: dept.description,
                    agents: dept.agents.map(agent => ({
                        name: agent.name,
                        role: agent.role,
                        capabilities: agent.capabilities,
                        // @ts-ignore
                        personality: agent.configuration?.personality,
                        // @ts-ignore
                        model: agent.configuration?.model,
                        // @ts-ignore
                        system_prompt: agent.configuration?.system_prompt
                    }))
                }
            };

            const filename = `${dept.name.toLowerCase().replace(/\s+/g, '-')}.yaml`;
            fs.writeFileSync(path.join(departmentsDir, filename), yaml.dump(deptYaml));
            console.log(`[ConfigSync] Exported departments/${filename}`);
        }

        console.log('[ConfigSync] Export complete!');
    }
}
