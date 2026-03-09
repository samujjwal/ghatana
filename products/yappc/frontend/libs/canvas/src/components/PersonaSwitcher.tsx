/**
 * @doc.type component
 * @doc.purpose Persona switcher UI component for changing canvas personas
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Box, ToggleButtonGroup, ToggleButton, Tooltip, Surface as Paper } from '@ghatana/ui';
import type { PersonaType } from '../types/persona';
import { getAvailablePersonas, getPersonaConfig } from '../config/personaConfigs';

/**
 * Props for PersonaSwitcher component
 */
export interface PersonaSwitcherProps {
    /** Currently active persona */
    value: PersonaType;
    /** Change handler */
    onChange: (persona: PersonaType) => void;
    /** Optional: Disable certain personas */
    disabledPersonas?: PersonaType[];
    /** Optional: Show as vertical layout */
    vertical?: boolean;
}

/**
 * Persona icon mapping
 */
const PERSONA_ICONS: Record<PersonaType, string> = {
    PM: '📋', // Clipboard for requirements
    Architect: '🏗️', // Building for architecture
    Developer: '💻', // Laptop for coding
    QA: '🧪', // Test tube for testing
};

/**
 * Persona switcher component for changing canvas view modes
 *
 * Features:
 * - Toggle between 4 persona views (PM, Architect, Dev, QA)
 * - Shows persona icons and names
 * - Tooltips with persona descriptions
 * - Disabled state for unavailable personas
 *
 * Usage:
 * ```tsx
 * <PersonaSwitcher
 *   value={currentPersona}
 *   onChange={setCurrentPersona}
 * />
 * ```
 */
export const PersonaSwitcher: React.FC<PersonaSwitcherProps> = ({
    value,
    onChange,
    disabledPersonas = [],
    vertical = false,
}) => {
    const personas = getAvailablePersonas();

    const handleChange = (
        _event: React.MouseEvent<HTMLElement>,
        newPersona: PersonaType | null
    ) => {
        if (newPersona !== null) {
            onChange(newPersona);
        }
    };

    return (
        <Paper
            elevation={2}
            className="p-2 inline-block"
        >
            <ToggleButtonGroup
                value={value}
                exclusive
                onChange={handleChange}
                orientation={vertical ? 'vertical' : 'horizontal'}
                aria-label="persona switcher"
                size="sm"
            >
                {personas.map((persona) => {
                    const config = getPersonaConfig(persona);
                    const disabled = disabledPersonas.includes(persona);

                    return (
                        <Tooltip
                            key={persona}
                            title={`${config.name} - ${config.viewMode}`}
                            placement={vertical ? 'right' : 'top'}
                        >
                            <span>
                                <ToggleButton
                                    value={persona}
                                    disabled={disabled}
                                    aria-label={config.name}
                                    className="flex flex-col gap-1 py-2" style={{ minWidth: vertical ? 'auto' : 100 }}
                                >
                                    <Box className="text-xl">
                                        {PERSONA_ICONS[persona as keyof typeof PERSONA_ICONS]}
                                    </Box>
                                    <Box className="font-medium text-[11px]">{persona}</Box>
                                </ToggleButton>
                            </span>
                        </Tooltip>
                    );
                })}
            </ToggleButtonGroup>
        </Paper>
    );
};

/**
 * Compact persona switcher with icons only
 */
export const CompactPersonaSwitcher: React.FC<PersonaSwitcherProps> = ({
    value,
    onChange,
    disabledPersonas = [],
    vertical = false,
}) => {
    const personas = getAvailablePersonas();

    const handleChange = (
        _event: React.MouseEvent<HTMLElement>,
        newPersona: PersonaType | null
    ) => {
        if (newPersona !== null) {
            onChange(newPersona);
        }
    };

    return (
        <ToggleButtonGroup
            value={value}
            exclusive
            onChange={handleChange}
            orientation={vertical ? 'vertical' : 'horizontal'}
            aria-label="persona switcher"
            size="sm"
        >
            {personas.map((persona) => {
                const config = getPersonaConfig(persona);
                const disabled = disabledPersonas.includes(persona);

                return (
                    <Tooltip
                        key={persona}
                        title={config.name}
                        placement={vertical ? 'right' : 'top'}
                    >
                        <span>
                            <ToggleButton
                                value={persona}
                                disabled={disabled}
                                aria-label={config.name}
                                className="px-4"
                            >
                                {PERSONA_ICONS[persona as keyof typeof PERSONA_ICONS]}
                            </ToggleButton>
                        </span>
                    </Tooltip>
                );
            })}
        </ToggleButtonGroup>
    );
};
