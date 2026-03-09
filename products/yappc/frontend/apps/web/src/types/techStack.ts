/**
 * @doc.type type
 * @doc.purpose Define tech stack types for project detection
 * @doc.layer product
 * @doc.pattern ValueObject
 */

// Tech stack categories
export type TechCategory =
    | 'language'
    | 'framework'
    | 'database'
    | 'cloud'
    | 'devops'
    | 'testing'
    | 'ui'
    | 'api'
    | 'auth'
    | 'messaging'
    | 'monitoring'
    | 'other';

// Individual technology
export interface Technology {
    id: string;
    name: string;
    category: TechCategory;
    icon?: string;
    color?: string;
    version?: string;
    confidence: number; // 0-1 confidence of detection
    source: 'detected' | 'manual' | 'inferred';
}

// Tech stack for a project
export interface TechStack {
    projectId: string;
    technologies: Technology[];
    lastUpdated: string;
    detectionMethod: 'auto' | 'manual' | 'mixed';
}

// Category metadata
export interface TechCategoryInfo {
    category: TechCategory;
    label: string;
    icon: string;
    color: string;
}

// Category registry
export const TECH_CATEGORIES: TechCategoryInfo[] = [
    { category: 'language', label: 'Languages', icon: '💻', color: '#3B82F6' },
    { category: 'framework', label: 'Frameworks', icon: '🏗️', color: '#8B5CF6' },
    { category: 'database', label: 'Databases', icon: '🗄️', color: '#10B981' },
    { category: 'cloud', label: 'Cloud', icon: '☁️', color: '#06B6D4' },
    { category: 'devops', label: 'DevOps', icon: '🔧', color: '#F59E0B' },
    { category: 'testing', label: 'Testing', icon: '🧪', color: '#EC4899' },
    { category: 'ui', label: 'UI/UX', icon: '🎨', color: '#EF4444' },
    { category: 'api', label: 'APIs', icon: '🔌', color: '#14B8A6' },
    { category: 'auth', label: 'Auth', icon: '🔐', color: '#F97316' },
    { category: 'messaging', label: 'Messaging', icon: '📨', color: '#6366F1' },
    { category: 'monitoring', label: 'Monitoring', icon: '📊', color: '#84CC16' },
    { category: 'other', label: 'Other', icon: '📦', color: '#6B7280' },
];

// Common technologies with their icons and colors
export const COMMON_TECHNOLOGIES: Omit<Technology, 'confidence' | 'source'>[] = [
    // Languages
    { id: 'typescript', name: 'TypeScript', category: 'language', icon: '🔷', color: '#3178C6' },
    { id: 'javascript', name: 'JavaScript', category: 'language', icon: '🟨', color: '#F7DF1E' },
    { id: 'python', name: 'Python', category: 'language', icon: '🐍', color: '#3776AB' },
    { id: 'java', name: 'Java', category: 'language', icon: '☕', color: '#007396' },
    { id: 'go', name: 'Go', category: 'language', icon: '🐹', color: '#00ADD8' },
    { id: 'rust', name: 'Rust', category: 'language', icon: '🦀', color: '#DEA584' },

    // Frameworks
    { id: 'react', name: 'React', category: 'framework', icon: '⚛️', color: '#61DAFB' },
    { id: 'nextjs', name: 'Next.js', category: 'framework', icon: '▲', color: '#000000' },
    { id: 'remix', name: 'Remix', category: 'framework', icon: '💿', color: '#121212' },
    { id: 'angular', name: 'Angular', category: 'framework', icon: '🅰️', color: '#DD0031' },
    { id: 'vue', name: 'Vue', category: 'framework', icon: '💚', color: '#4FC08D' },
    { id: 'svelte', name: 'Svelte', category: 'framework', icon: '🔥', color: '#FF3E00' },
    { id: 'fastify', name: 'Fastify', category: 'framework', icon: '⚡', color: '#000000' },
    { id: 'express', name: 'Express', category: 'framework', icon: '🚂', color: '#000000' },
    { id: 'spring', name: 'Spring Boot', category: 'framework', icon: '🍃', color: '#6DB33F' },
    { id: 'activej', name: 'ActiveJ', category: 'framework', icon: '⚡', color: '#FF6B00' },

    // Databases
    { id: 'postgresql', name: 'PostgreSQL', category: 'database', icon: '🐘', color: '#336791' },
    { id: 'mysql', name: 'MySQL', category: 'database', icon: '🐬', color: '#4479A1' },
    { id: 'mongodb', name: 'MongoDB', category: 'database', icon: '🍃', color: '#47A248' },
    { id: 'redis', name: 'Redis', category: 'database', icon: '🔴', color: '#DC382D' },
    { id: 'prisma', name: 'Prisma', category: 'database', icon: '🔷', color: '#2D3748' },

    // Cloud
    { id: 'aws', name: 'AWS', category: 'cloud', icon: '☁️', color: '#FF9900' },
    { id: 'gcp', name: 'Google Cloud', category: 'cloud', icon: '🌩️', color: '#4285F4' },
    { id: 'azure', name: 'Azure', category: 'cloud', icon: '☁️', color: '#0078D4' },
    { id: 'vercel', name: 'Vercel', category: 'cloud', icon: '▲', color: '#000000' },

    // DevOps
    { id: 'docker', name: 'Docker', category: 'devops', icon: '🐳', color: '#2496ED' },
    { id: 'kubernetes', name: 'Kubernetes', category: 'devops', icon: '☸️', color: '#326CE5' },
    { id: 'github-actions', name: 'GitHub Actions', category: 'devops', icon: '🔄', color: '#2088FF' },
    { id: 'terraform', name: 'Terraform', category: 'devops', icon: '🏗️', color: '#7B42BC' },

    // Testing
    { id: 'jest', name: 'Jest', category: 'testing', icon: '🃏', color: '#C21325' },
    { id: 'vitest', name: 'Vitest', category: 'testing', icon: '⚡', color: '#729B1B' },
    { id: 'playwright', name: 'Playwright', category: 'testing', icon: '🎭', color: '#2EAD33' },
    { id: 'cypress', name: 'Cypress', category: 'testing', icon: '🌲', color: '#17202C' },

    // UI
    { id: 'tailwind', name: 'Tailwind CSS', category: 'ui', icon: '🌊', color: '#06B6D4' },
    { id: 'mui', name: 'Material UI', category: 'ui', icon: '🎨', color: '#007FFF' },
    { id: 'chakra', name: 'Chakra UI', category: 'ui', icon: '⚡', color: '#319795' },

    // Auth
    { id: 'auth0', name: 'Auth0', category: 'auth', icon: '🔐', color: '#EB5424' },
    { id: 'clerk', name: 'Clerk', category: 'auth', icon: '👤', color: '#6C47FF' },
    { id: 'supabase-auth', name: 'Supabase Auth', category: 'auth', icon: '🔑', color: '#3ECF8E' },

    // Monitoring
    { id: 'posthog', name: 'PostHog', category: 'monitoring', icon: '🦔', color: '#F9BD2B' },
    { id: 'sentry', name: 'Sentry', category: 'monitoring', icon: '🔺', color: '#362D59' },
    { id: 'datadog', name: 'Datadog', category: 'monitoring', icon: '🐕', color: '#632CA6' },
];

// Helper to get category info
export function getCategoryInfo(category: TechCategory): TechCategoryInfo {
    const info = TECH_CATEGORIES.find(c => c.category === category);
    return info || { category, label: category, icon: '📦', color: '#6B7280' };
}

// Helper to get technology info
export function getTechnologyInfo(id: string): Omit<Technology, 'confidence' | 'source'> | undefined {
    return COMMON_TECHNOLOGIES.find(t => t.id === id);
}

// Group technologies by category
export function groupByCategory(technologies: Technology[]): Record<TechCategory, Technology[]> {
    const grouped: Record<TechCategory, Technology[]> = {
        language: [],
        framework: [],
        database: [],
        cloud: [],
        devops: [],
        testing: [],
        ui: [],
        api: [],
        auth: [],
        messaging: [],
        monitoring: [],
        other: [],
    };

    technologies.forEach(tech => {
        grouped[tech.category].push(tech);
    });

    return grouped;
}

// Default tech stack for new projects
export function createDefaultTechStack(projectId: string): TechStack {
    return {
        projectId,
        technologies: [],
        lastUpdated: new Date().toISOString(),
        detectionMethod: 'auto',
    };
}
