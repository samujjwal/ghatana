/**
 * Database Seeding Script for Simulation & Visualization Examples
 * 
 * This script seeds the database with comprehensive simulation and visualization examples
 * across all supported domains (Physics, Chemistry, Biology, Mathematics, Economics).
 * 
 * Usage:
 * - Import and call: seedAllDomains()
 * - Or use the CLI command provided at the end
 * 
 * @doc.type utility
 * @doc.purpose Database seeding for simulation examples
 * @doc.layer product
 */

import {
    allExamples
} from '../data/simulationExamples';

/**
 * API Request helper with error handling and logging
 */
async function apiRequest(
    method: string,
    path: string,
    body?: any,
    options?: { token?: string; verbose?: boolean }
) {
    const baseUrl = (import.meta.env.VITE_TUTORPUTOR_API_BASE_URL as string | undefined)?.replace(/\/$/, '');
    if (!baseUrl) {
        throw new Error('Missing VITE_TUTORPUTOR_API_BASE_URL. Refusing to run seeding without an explicit API base URL.');
    }
    const url = `${baseUrl}${path}`;
    const config: RequestInit = {
        method,
        headers: {
            'Content-Type': 'application/json',
            ...(options?.token && { Authorization: `Bearer ${options.token}` })
        }
    };

    if (body) {
        config.body = JSON.stringify(body);
    }

    try {
        console.log(`[API] ${method} ${path}`);
        console.log(`   URL: ${url}`);
        console.log(`   Headers:`, config.headers);
        if (body) {
            console.log(`   Body:`, JSON.stringify(body, null, 2));
        }

        const response = await fetch(url, config);
        console.log(`[API] ${method} ${path} -> ${response.status} ${response.statusText}`);

        if (!response.ok) {
            let errorText = '';
            let errorObj: any = {};
            try {
                errorText = await response.text();
                try {
                    errorObj = JSON.parse(errorText);
                } catch {
                    // errorText is not JSON, leave errorObj empty
                }
                console.error(`[API Error Status] ${response.status}`);
                console.error(`[API Error Body] ${errorText}`);
                if (errorObj?.details) {
                    console.error(`[API Error Details] ${errorObj.details}`);
                }
                if (errorObj?.code) {
                    console.error(`[API Error Code] ${errorObj.code}`);
                }
            } catch (e) {
                console.error(`[API] Could not read error body:`, e);
            }
            throw new Error(
                `HTTP ${response.status}: ${errorObj?.details || errorText || response.statusText}`
            );
        }

        const data = response.headers.get('content-length') === '0'
            ? null
            : await response.json();

        return { ok: true, status: response.status, data };
    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        console.error(`[ERROR] ${method} ${path}: ${message}`);
        return { ok: false, status: 500, error: message };
    }
}

/**
 * Domain configuration for seeding
 */
const domains = [
    {
        name: 'Physics 101',
        domain: 'PHYSICS',
        author: 'Physics Department',
        description: 'Introduction to Physics - Mechanics, Waves, and Forces',
        concepts: [
            {
                name: 'Kinematics',
                description: 'Study of motion without considering forces',
                level: 'FOUNDATIONAL',
                example: allExamples[0] // Projectile Motion
            }
        ]
    },
    {
        name: 'Chemistry Fundamentals',
        domain: 'CHEMISTRY',
        author: 'Chemistry Department',
        description: 'Basic concepts in chemistry including molecular structures and reactions',
        concepts: [
            {
                name: 'Molecular Structure',
                description: 'Understanding how molecules are formed and bonded',
                level: 'FOUNDATIONAL',
                example: allExamples[1] // Water Molecule
            }
        ]
    },
    {
        name: 'Biology Essentials',
        domain: 'BIOLOGY',
        author: 'Biology Department',
        description: 'Fundamental biological processes and cellular mechanisms',
        concepts: [
            {
                name: 'Mitosis',
                description: 'The process of cell division in eukaryotic cells',
                level: 'FOUNDATIONAL',
                example: allExamples[2] // Cell Division
            }
        ]
    },
    {
        name: 'Mathematics Fundamentals',
        domain: 'MATHEMATICS',
        author: 'Mathematics Department',
        description: 'Core mathematical concepts including functions, algebra, and geometry',
        concepts: [
            {
                name: 'Functions',
                description: 'Understanding functions and their graphical representations',
                level: 'FOUNDATIONAL',
                example: allExamples[3] // Quadratic Functions
            },
            {
                name: 'Market Equilibrium',
                description: 'Application of functions in economics',
                level: 'INTERMEDIATE',
                example: allExamples[4] // Supply & Demand
            }
        ]
    },
    {
        name: 'Economics 101',
        domain: 'ECONOMICS',
        author: 'Economics Department',
        description: 'Introduction to economics and market principles',
        concepts: [
            {
                name: 'Supply and Demand',
                description: 'Core principles of market equilibrium',
                level: 'FOUNDATIONAL',
                example: allExamples[4] // Supply & Demand
            }
        ]
    }
];

/**
 * Seed a single domain with concepts, simulations, and visualizations
 */
async function seedDomain(
    domain: typeof domains[0],
    token?: string,
    _mode: 'skip-duplicates' | 'force-clean' = 'skip-duplicates'
): Promise<{ domainId: string; concepts: Array<{ id: string; name: string }> }> {
    console.log(`\n📚 Seeding Domain: ${domain.name}`);
    console.log('═'.repeat(60));

    // 0. First, check if domain already exists
    console.log(`   Checking for existing domain: ${domain.domain}...`);
    const existingDomainsRes = await apiRequest(
        'GET',
        '/admin/api/v1/content/db/domains',
        undefined,
        { token }
    );

    if (existingDomainsRes.ok && existingDomainsRes.data?.domains) {
        const existingDomain = existingDomainsRes.data.domains.find((d: any) => d.domain === domain.domain);
        if (existingDomain) {
            console.log(`⚠️  Domain already exists: ${domain.name} (${existingDomain.id})`);
            return {
                domainId: existingDomain.id,
                concepts: existingDomain.concepts?.map((c: any) => ({ id: c.id, name: c.name })) || []
            };
        }
    }

    // 1. Create Domain
    console.log(`   Creating new domain: ${domain.name}...`);
    const domainRes = await apiRequest(
        'POST',
        '/admin/api/v1/content/db/domains',
        {
            domain: domain.domain,
            title: domain.name,
            description: domain.description,
            author: domain.author
        },
        { token }
    );

    if (!domainRes.ok) {
        console.error(`❌ Failed to create domain: ${domain.name}`);
        console.error(`   Error: ${domainRes.error}`);
        throw new Error(`Domain creation failed: ${domainRes.error}`);
    }

    if (!domainRes.data?.id) {
        console.error(`❌ Failed to create domain: ${domain.name} (no ID returned)`);
        throw new Error(`Domain creation failed: no ID returned`);
    }

    const domainId = domainRes.data.id;
    console.log(`✅ Domain created: ${domainId}`);

    const conceptResults: Array<{ id: string; name: string }> = [];

    // 2. Create Concepts with Simulations and Visualizations
    for (const concept of domain.concepts) {
        console.log(`\n  📖 Concept: ${concept.name}`);

        // Create concept
        const conceptRes = await apiRequest(
            'POST',
            `/admin/api/v1/content/db/domains/${domainId}/concepts`,
            {
                name: concept.name,
                description: concept.description,
                level: concept.level,
                learningObjectives: JSON.stringify([
                    `Understand ${concept.name}`,
                    `Apply ${concept.name} concepts`,
                    `Analyze problems using ${concept.name}`
                ]),
                prerequisites: JSON.stringify([]),
                competencies: JSON.stringify(['critical-thinking', 'problem-solving']),
                keywords: JSON.stringify([
                    concept.name.toLowerCase(),
                    domain.domain.toLowerCase(),
                    'simulation'
                ])
            },
            { token }
        );

        if (!conceptRes.ok) {
            console.error(`  ❌ Failed to create concept: ${concept.name}`);
            console.error(`     Error: ${conceptRes.error}`);
            continue;
        }

        if (!conceptRes.data?.id) {
            console.error(`  ❌ Failed to create concept: ${concept.name} (no ID returned)`);
            continue;
        }

        const conceptId = conceptRes.data.id;
        console.log(`  ✅ Concept created: ${conceptId}`);
        conceptResults.push({ id: conceptId, name: concept.name });

        // 3. Create Simulation if example has one
        if (concept.example.simulation) {
            const simRes = await apiRequest(
                'PUT',
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/simulation`,
                {
                    type: concept.example.simulation.type,
                    manifest: concept.example.simulation.manifest,
                    estimatedTimeMinutes: concept.example.simulation.estimatedTimeMinutes,
                    interactivityLevel: concept.example.simulation.interactivityLevel,
                    purpose: concept.example.simulation.purpose,
                    previewConfig: concept.example.simulation.previewConfig
                },
                { token }
            );

            if (simRes.ok) {
                console.log(
                    `    ✅ Simulation created (${concept.example.simulation.type})`
                );
            } else {
                console.error(`    ⚠️  Simulation creation failed: ${simRes.error}`);
            }
        }

        // 4. Create Visualization if example has one
        if (concept.example.visualization) {
            const vizRes = await apiRequest(
                'PUT',
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/visualization`,
                {
                    type: concept.example.visualization.type,
                    config: concept.example.visualization.config,
                    dataSource: concept.example.visualization.dataSource
                },
                { token }
            );

            if (vizRes.ok) {
                console.log(
                    `    ✅ Visualization created (${concept.example.visualization.type})`
                );
            } else {
                console.error(`    ⚠️  Visualization creation failed: ${vizRes.error}`);
            }
        }
    }

    return { domainId, concepts: conceptResults };
}

/**
 * Delete all domains (for force-clean mode)
 */
async function deleteAllDomains(token?: string): Promise<void> {
    console.log('\n🗑️  Force Clean Mode: Deleting all existing domains...');

    // Get list of all domains
    const domainsRes = await apiRequest(
        'GET',
        '/admin/api/v1/content/db/domains',
        undefined,
        { token }
    );

    if (!domainsRes.ok || !domainsRes.data?.domains) {
        console.warn('⚠️  Could not fetch domains for deletion');
        return;
    }

    // Delete each domain
    for (const domain of domainsRes.data.domains) {
        try {
            const deleteRes = await apiRequest(
                'DELETE',
                `/admin/api/v1/content/db/domains/${domain.id}`,
                undefined,
                { token }
            );

            if (deleteRes.ok) {
                console.log(`  ✅ Deleted domain: ${domain.domain}`);
            } else {
                console.warn(`  ⚠️  Failed to delete domain ${domain.domain}: ${deleteRes.error}`);
            }
        } catch (error) {
            console.error(`  ❌ Error deleting domain ${domain.domain}:`, error);
        }
    }

    console.log('✅ Deletion complete!\n');
}

/**
 * Main seeding function - seed all domains
 */
export async function seedAllDomains(token?: string, mode: 'skip-duplicates' | 'force-clean' = 'skip-duplicates'): Promise<
    Array<{
        domain: string;
        domainId: string;
        concepts: Array<{ id: string; name: string }>;
    }>
> {
    console.clear();
    console.log('\n╔════════════════════════════════════════════════════════════╗');
    console.log('║  🌱 Simulation & Visualization Database Seeding            ║');
    console.log(`║  Mode: ${mode === 'skip-duplicates' ? 'Skip Duplicates' : 'Force Clean & Reseed'}${' '.repeat(mode === 'skip-duplicates' ? 19 : 14)}║`);
    console.log('║  Populating all domains with examples                     ║');
    console.log('╚════════════════════════════════════════════════════════════╝\n');

    // If force-clean mode, delete all existing domains first
    if (mode === 'force-clean') {
        await deleteAllDomains(token);
    }

    const results: Array<{
        domain: string;
        domainId: string;
        concepts: Array<{ id: string; name: string }>;
    }> = [];

    for (const domain of domains) {
        try {
            const result = await seedDomain(domain, token, mode);
            results.push({
                domain: domain.name,
                ...result
            });
        } catch (error) {
            console.error(`\n❌ Failed to seed domain: ${domain.name}`);
            console.error(error instanceof Error ? error.message : String(error));
        }
    }

    // Print Summary
    console.log('\n╔════════════════════════════════════════════════════════════╗');
    console.log('║  📊 Seeding Summary                                       ║');
    console.log('╚════════════════════════════════════════════════════════════╝\n');

    let totalDomains = 0;
    let totalConcepts = 0;

    for (const result of results) {
        totalDomains++;
        totalConcepts += result.concepts.length;
        console.log(`✅ ${result.domain}`);
        for (const concept of result.concepts) {
            console.log(`   └─ ${concept.name} (${concept.id})`);
        }
    }

    console.log(`\n📈 Total: ${totalDomains} domains, ${totalConcepts} concepts with simulations & visualizations\n`);

    return results;
}

/**
 * Seed a specific domain
 */
export async function seedDomain_(
    domainName: string,
    token?: string
): Promise<{ domainId: string; concepts: Array<{ id: string; name: string }> }> {
    const domain = domains.find(d => d.name.toLowerCase() === domainName.toLowerCase());
    if (!domain) {
        throw new Error(`Domain not found: ${domainName}`);
    }
    return seedDomain(domain, token);
}

/**
 * Get seeding instructions
 */
export function getSeedingInstructions(): string {
    return `
╔═══════════════════════════════════════════════════════════════╗
║  🌱 How to Seed Simulation Data                              ║
╚═══════════════════════════════════════════════════════════════╝

METHOD 1: From React Component
─────────────────────────────────
  import { seedAllDomains } from '@/utils/seedSimulationData';
  
  // In a useEffect or button handler
  const handleSeed = async () => {
    const results = await seedAllDomains(authToken);
    console.log('Seeding complete:', results);
  };

METHOD 2: From Browser Console
────────────────────────────────
  import * as seed from '/src/utils/seedSimulationData.js';
  await seed.seedAllDomains(authToken);

METHOD 3: Using cURL (One Domain at a Time)
──────────────────────────────────────────
  # Create a domain
  curl -X POST http://localhost:3200/admin/api/v1/content/db/domains \\
    -H "Content-Type: application/json" \\
    -d '{
      "domain": "PHYSICS",
      "title": "Physics 101",
      "description": "Introduction to Physics",
      "author": "Physics Department"
    }'
  
  # Note the returned domainId and use it for concepts

METHOD 4: Using TypeScript/Node Script
────────────────────────────────────────
  // Create file: seed-script.ts
  import { seedAllDomains } from './src/utils/seedSimulationData';
  
  const token = process.env.AUTH_TOKEN;
  seedAllDomains(token)
    .then(results => console.log('✅ Seeding complete', results))
    .catch(err => console.error('❌ Seeding failed', err));
  
  // Run with: npx tsx seed-script.ts

EXPECTED OUTPUT:
────────────────
  📚 Seeding Domain: Physics 101
  ✅ Domain created: clz1abc...
  ✅ Concept created: clz1def...
  ✅ Simulation created (physics-2D)
  ✅ Visualization created (graph-2d)
  
  📊 Seeding Summary
  ✅ Physics 101
     └─ Kinematics (clz1def...)
  ...
  📈 Total: 5 domains, 7 concepts with simulations & visualizations

WHAT GETS CREATED:
─────────────────
  • 5 Domains (Physics, Chemistry, Biology, Mathematics, Economics)
  • 7 Concepts (Kinematics, Molecular Structure, Mitosis, Functions, etc.)
  • 7 Simulations (physics-2D, chemistry-interactive, biology-interactive, etc.)
  • 7 Visualizations (graph-2d, graph-3d, chart, diagram, molecule)

TROUBLESHOOTING:
───────────────
  ❌ "Failed to create domain"
     → Check that API is running (port 3200)
     → Verify authentication token
     → Check database connectivity

  ❌ "HTTP 404: Simulation not found"
     → Ensure domain and concept exist first
     → Use returned domainId and conceptId

  ⚠️  "Visualization creation failed"
     → Check that concept was created successfully
     → Verify visualization config JSON is valid

VIEWING RESULTS:
───────────────
  • Admin UI: http://localhost:3000/admin/concepts
  • API: GET /admin/api/v1/content/db/domains/{domainId}
  • Database: SELECT * FROM "DomainAuthor";

RESETTING DATA:
──────────────
  // Delete all seeded data
  DELETE FROM "SimulationDefinition";
  DELETE FROM "VisualizationDefinition";
  DELETE FROM "DomainAuthorConcept";
  DELETE FROM "DomainAuthor";
`;
}

/**
 * Print instructions to console
 */
export function printSeedingInstructions(): void {
    console.log(getSeedingInstructions());
}

// Export seeding data for direct access
export { allExamples, domains };
