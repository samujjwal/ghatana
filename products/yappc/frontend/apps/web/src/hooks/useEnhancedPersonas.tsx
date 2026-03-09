/**
 * Enhanced Personas Hook
 * 
 * Transforms backend persona data into the format needed by the comprehensive PersonaSelector.
 * Adds UI metadata like icons, short names, and action labels.
 * 
 * @doc.type hook
 * @doc.purpose Transform backend personas for UI
 * @doc.layer product
 */

import { useMemo } from 'react';
import { usePersonas } from '@ghatana/yappc-ui';
import { getPersonaIcon } from '../utils/personaIcons';
import type { PersonaType, PersonaCategory } from '../types/persona';

/**
 * Persona definition with UI metadata
 */
export interface EnhancedPersona {
    id: PersonaType;
    name: string;
    shortName: string;
    description: string;
    icon: React.ReactNode;
    color: string;
    category: PersonaCategory;
    primaryFocus: string[];
    actionLabel: string;
}

/**
 * Generate short name from label
 */
function getShortName(label: string): string {
    const words = label.split(' ');
    if (words.length === 1) {
        return words[0].substring(0, 3);
    }
    return words.map(w => w[0]).join('');
}

/**
 * Map backend category to UI category
 */
function mapCategory(backendCategory: string): PersonaCategory {
    const categoryMap: Record<string, PersonaCategory> = {
        'TECHNICAL': 'TECHNICAL',
        'MANAGEMENT': 'MANAGEMENT',
        'GOVERNANCE': 'GOVERNANCE',
        'ANALYSIS': 'ANALYSIS',
    };
    return categoryMap[backendCategory] || 'TECHNICAL';
}

/**
 * Hook to get enhanced personas with UI metadata
 */
export function useEnhancedPersonas() {
    const { data: backendPersonas, isLoading, error } = usePersonas();

    const personas = useMemo(() => {
        if (!backendPersonas || backendPersonas.length === 0) {
            return [];
        }

        return backendPersonas.map(p => ({
            id: p.id,
            name: p.label,
            shortName: getShortName(p.label),
            description: p.description,
            icon: getPersonaIcon(p.id),
            color: p.color,
            category: mapCategory(p.category),
            primaryFocus: p.focusAreas || [],
            actionLabel: `View ${p.label} Dashboard`,
        })) as EnhancedPersona[];
    }, [backendPersonas]);

    return { personas, isLoading, error };
}

/**
 * Export for backward compatibility with PERSONAS constant
 * Note: This will be empty until backend data loads
 */
export function usePersonasCompat() {
    const { personas } = useEnhancedPersonas();
    return personas;
}
