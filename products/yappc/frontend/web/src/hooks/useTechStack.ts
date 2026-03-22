/**
 * @doc.type hook
 * @doc.purpose Manage tech stack detection and display
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useMemo } from 'react';
import { useAtom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import {
    TechStack,
    Technology,
    TechCategory,
    createDefaultTechStack,
    groupByCategory,
    COMMON_TECHNOLOGIES,
    getTechnologyInfo,
} from '../types/techStack';

// Persisted tech stack per project
const techStackAtomFamily = new Map<string, ReturnType<typeof atomWithStorage<TechStack>>>();

function getTechStackAtom(projectId: string) {
    if (!techStackAtomFamily.has(projectId)) {
        techStackAtomFamily.set(
            projectId,
            atomWithStorage<TechStack>(
                `ghatana-tech-stack-${projectId}`,
                createDefaultTechStack(projectId)
            )
        );
    }
    return techStackAtomFamily.get(projectId)!;
}

export interface UseTechStackOptions {
    autoDetect?: boolean;
    onTechAdded?: (tech: Technology) => void;
    onTechRemoved?: (techId: string) => void;
}

export function useTechStack(projectId: string, options: UseTechStackOptions = {}) {
    const { onTechAdded, onTechRemoved } = options;

    const techStackAtom = useMemo(() => getTechStackAtom(projectId), [projectId]);
    const [techStack, setTechStack] = useAtom(techStackAtom);

    // Technologies grouped by category
    const groupedTech = useMemo(
        () => groupByCategory(techStack.technologies),
        [techStack.technologies]
    );

    // Get top technologies (by confidence)
    const topTechnologies = useMemo(
        () => [...techStack.technologies]
            .sort((a, b) => b.confidence - a.confidence)
            .slice(0, 6),
        [techStack.technologies]
    );

    // Primary tech stack (highest confidence per main category)
    const primaryStack = useMemo(() => {
        const mainCategories: TechCategory[] = ['language', 'framework', 'database'];
        return mainCategories
            .map(cat => groupedTech[cat]?.[0])
            .filter((t): t is Technology => !!t);
    }, [groupedTech]);

    // Add technology manually
    const addTechnology = useCallback((
        tech: Omit<Technology, 'confidence' | 'source'> & { confidence?: number }
    ) => {
        const newTech: Technology = {
            ...tech,
            confidence: tech.confidence ?? 1.0,
            source: 'manual',
        };

        setTechStack(prev => {
            // Check if already exists
            if (prev.technologies.find(t => t.id === newTech.id)) {
                return prev;
            }

            const updated = {
                ...prev,
                technologies: [...prev.technologies, newTech],
                lastUpdated: new Date().toISOString(),
                detectionMethod: prev.detectionMethod === 'auto' ? 'mixed' : prev.detectionMethod,
            } as TechStack;

            onTechAdded?.(newTech);
            return updated;
        });
    }, [setTechStack, onTechAdded]);

    // Add technology by ID (using common registry)
    const addTechnologyById = useCallback((id: string, version?: string) => {
        const techInfo = getTechnologyInfo(id);
        if (!techInfo) {
            console.warn(`Technology ${id} not found in registry`);
            return;
        }

        addTechnology({
            ...techInfo,
            version,
        });
    }, [addTechnology]);

    // Remove technology
    const removeTechnology = useCallback((techId: string) => {
        setTechStack(prev => ({
            ...prev,
            technologies: prev.technologies.filter(t => t.id !== techId),
            lastUpdated: new Date().toISOString(),
        }));

        onTechRemoved?.(techId);
    }, [setTechStack, onTechRemoved]);

    // Update technology
    const updateTechnology = useCallback((
        techId: string,
        updates: Partial<Omit<Technology, 'id'>>
    ) => {
        setTechStack(prev => ({
            ...prev,
            technologies: prev.technologies.map(t =>
                t.id === techId ? { ...t, ...updates } : t
            ),
            lastUpdated: new Date().toISOString(),
        }));
    }, [setTechStack]);

    // Detect technologies from project files (mock implementation)
    const detectFromFiles = useCallback(async (files: string[]) => {
        const detected: Technology[] = [];

        // Simple file-based detection
        const filePatterns: Record<string, string> = {
            'package.json': 'javascript',
            'tsconfig.json': 'typescript',
            'requirements.txt': 'python',
            'pyproject.toml': 'python',
            'Cargo.toml': 'rust',
            'go.mod': 'go',
            'pom.xml': 'java',
            'build.gradle': 'java',
            'Dockerfile': 'docker',
            'docker-compose.yml': 'docker',
            '.github/workflows': 'github-actions',
            'prisma/schema.prisma': 'prisma',
            'tailwind.config': 'tailwind',
            'vite.config': 'vitest',
            'jest.config': 'jest',
            'playwright.config': 'playwright',
        };

        for (const [pattern, techId] of Object.entries(filePatterns)) {
            if (files.some(f => f.includes(pattern))) {
                const techInfo = getTechnologyInfo(techId);
                if (techInfo) {
                    detected.push({
                        ...techInfo,
                        confidence: 0.9,
                        source: 'detected',
                    });
                }
            }
        }

        // Add detected technologies
        if (detected.length > 0) {
            setTechStack(prev => ({
                ...prev,
                technologies: mergeDetectedTech(prev.technologies, detected),
                lastUpdated: new Date().toISOString(),
                detectionMethod: prev.technologies.some(t => t.source === 'manual') ? 'mixed' : 'auto',
            }));
        }

        return detected;
    }, [setTechStack]);

    // Set technologies from a preset stack
    const setFromPreset = useCallback((presetIds: string[]) => {
        const techs = presetIds
            .map(id => {
                const info = getTechnologyInfo(id);
                return info ? {
                    ...info,
                    confidence: 1.0,
                    source: 'manual' as Technology['source'],
                } : null;
            })
            .filter((t): t is NonNullable<typeof t> => t !== null);

        setTechStack({
            projectId,
            technologies: techs,
            lastUpdated: new Date().toISOString(),
            detectionMethod: 'manual',
        });
    }, [projectId, setTechStack]);

    // Clear all technologies
    const clearTechStack = useCallback(() => {
        setTechStack(createDefaultTechStack(projectId));
    }, [projectId, setTechStack]);

    // Check if technology exists
    const hasTechnology = useCallback(
        (techId: string) => techStack.technologies.some(t => t.id === techId),
        [techStack.technologies]
    );

    // Get available technologies not yet added
    const availableTechnologies = useMemo(
        () => COMMON_TECHNOLOGIES.filter(
            t => !techStack.technologies.some(existing => existing.id === t.id)
        ),
        [techStack.technologies]
    );

    return {
        // State
        techStack,
        technologies: techStack.technologies,
        groupedTech,
        topTechnologies,
        primaryStack,

        // Actions
        addTechnology,
        addTechnologyById,
        removeTechnology,
        updateTechnology,
        detectFromFiles,
        setFromPreset,
        clearTechStack,

        // Helpers
        hasTechnology,
        availableTechnologies,

        // Constants
        commonTechnologies: COMMON_TECHNOLOGIES,
    };
}

// Helper to merge detected tech with existing
function mergeDetectedTech(existing: Technology[], detected: Technology[]): Technology[] {
    const result = [...existing];

    for (const tech of detected) {
        const existingIndex = result.findIndex(t => t.id === tech.id);
        if (existingIndex >= 0) {
            // Keep manual over detected, update confidence
            if (result[existingIndex].source === 'manual') {
                continue;
            }
            result[existingIndex] = {
                ...result[existingIndex],
                confidence: Math.max(result[existingIndex].confidence, tech.confidence),
            };
        } else {
            result.push(tech);
        }
    }

    return result;
}
