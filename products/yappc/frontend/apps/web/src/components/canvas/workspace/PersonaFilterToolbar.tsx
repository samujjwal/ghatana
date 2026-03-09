/**
 * Persona Filter Toolbar Component
 * 
 * Toolbar for filtering canvas artifacts by persona.
 * Shows task summary and allows toggling between "All" and specific personas.
 * 
 * @doc.type component
 * @doc.purpose Persona-based artifact filtering
 * @doc.layer product
 * @doc.pattern Filter Toolbar
 */

import React from 'react';
import { Box, Chip, ToggleButtonGroup, ToggleButton, Typography, Badge, Divider } from '@ghatana/ui';
import { Filter as FilterList } from 'lucide-react';
import { PERSONA_ICONS } from '../workspace/PersonaBadge';

export interface PersonaFilterData {
    persona: string;
    total: number;
    completed: number;
    blocked: number;
    inProgress: number;
}

export interface PersonaFilterToolbarProps {
    personas: PersonaFilterData[];
    activePersona: string | null; // null = "All"
    onPersonaChange: (persona: string | null) => void;
}

/**
 * PersonaFilterToolbar - Filter artifacts by assigned persona
 */
export const PersonaFilterToolbar: React.FC<PersonaFilterToolbarProps> = ({
    personas,
    activePersona,
    onPersonaChange,
}) => {
    const activeData = personas.find(p => p.persona === activePersona);
    const showSummary = activePersona !== null && activeData;

    return (
        <Box
            className="flex items-center gap-4 p-3 rounded-lg bg-white dark:bg-gray-900 shadow"
        >
            {/* Filter Icon */}
            <FilterList color="action" />

            {/* Persona Toggle Buttons */}
            <ToggleButtonGroup
                value={activePersona || 'all'}
                exclusive
                onChange={(_, value) => {
                    onPersonaChange(value === 'all' ? null : value);
                }}
                size="sm"
                className="[&_.MuiToggleButton-root]:px-3 [&_.MuiToggleButton-root]:py-1 [&_.MuiToggleButton-root]:border [&_.MuiToggleButton-root]:border-gray-300"
            >
                {/* All Personas */}
                <ToggleButton value="all">
                    <Typography as="span" className="text-xs text-gray-500" fontWeight="medium">
                        All Personas
                    </Typography>
                </ToggleButton>

                {/* Individual Personas */}
                {personas.map((personaData) => {
                    const icon = PERSONA_ICONS[personaData.persona] || personaData.persona[0];
                    const hasIssues = personaData.blocked > 0;

                    return (
                        <ToggleButton key={personaData.persona} value={personaData.persona}>
                            <Badge
                                badgeContent={personaData.total}
                                color={hasIssues ? 'error' : 'default'}
                                className="[&_.MuiBadge-badge]:text-[0.65rem] [&_.MuiBadge-badge]:h-4 [&_.MuiBadge-badge]:min-w-[16px]"
                            >
                                <Box className="flex items-center gap-1">
                                    <Typography as="span" className="text-xs text-gray-500">{icon}</Typography>
                                    <Typography as="span" className="text-xs text-gray-500" fontWeight="medium">
                                        {personaData.persona.split(' ')[0]}
                                    </Typography>
                                </Box>
                            </Badge>
                        </ToggleButton>
                    );
                })}
            </ToggleButtonGroup>

            {/* Task Summary for Active Persona */}
            {showSummary && activeData && (
                <>
                    <Divider orientation="vertical" flexItem />
                    <Box className="flex gap-3 items-center">
                        <Typography as="span" className="text-xs text-gray-500" fontWeight="bold">
                            YOUR TASKS ({activeData.persona}):
                        </Typography>
                        <Chip
                            label={`✅ ${activeData.completed} completed`}
                            size="sm"
                            tone="success"
                            variant="outlined"
                            className="h-[22px]"
                        />
                        {activeData.blocked > 0 && (
                            <Chip
                                label={`⚠️ ${activeData.blocked} blocked`}
                                size="sm"
                                tone="danger"
                                variant="outlined"
                                className="h-[22px]"
                            />
                        )}
                        {activeData.inProgress > 0 && (
                            <Chip
                                label={`⏸️ ${activeData.inProgress} in progress`}
                                size="sm"
                                tone="info"
                                variant="outlined"
                                className="h-[22px]"
                            />
                        )}
                    </Box>
                </>
            )}
        </Box>
    );
};
