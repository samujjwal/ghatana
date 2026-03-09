/**
 * Persona Badge Component
 * 
 * Small badge showing assigned persona for an artifact.
 * Used on canvas nodes to indicate ownership/responsibility.
 * 
 * @doc.type component
 * @doc.purpose Persona assignment indicator
 * @doc.layer product
 * @doc.pattern Badge Component
 */

import React from 'react';
import { Chip, Tooltip } from '@ghatana/ui';

export interface PersonaBadgeProps {
    persona: string;
    size?: 'small' | 'medium';
    variant?: 'filled' | 'outlined';
    onClick?: () => void;
}

export const PERSONA_ICONS: Record<string, string> = {
    'Product Manager': 'PM',
    'UX Designer': 'UX',
    'Frontend Engineer': 'FE',
    'Backend Engineer': 'BE',
    'QA Engineer': 'QA',
    'DevOps Engineer': 'DO',
    'Data Engineer': 'DE',
    'Security Engineer': 'SE',
};

/**
 * PersonaBadge - Compact persona indicator for artifacts
 */
export const PersonaBadge: React.FC<PersonaBadgeProps> = ({
    persona,
    size = 'small',
    variant = 'filled',
    onClick,
}) => {
    const shortName = persona.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();

    return (
        <Tooltip title={`Assigned to: ${persona}`} arrow placement="top">
            <Chip
                label={shortName}
                size={size}
                variant={variant}
                onClick={onClick}
                style={{ fontSize: size === 'small' ? '0.75rem' : '0.875rem', height: size === 'small' ? 20 : 24, cursor: onClick ? 'pointer' : 'default' }}
            />
        </Tooltip>
    );
};

/**
 * Status Badge Component for artifact status
 */
export interface StatusBadgeProps {
    status: 'complete' | 'in-progress' | 'review' | 'pending' | 'blocked';
    size?: 'small' | 'medium';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'small' }) => {
    const getStatusConfig = () => {
        switch (status) {
            case 'complete':
                return { color: 'success' as const, label: 'Done' };
            case 'in-progress':
                return { color: 'info' as const, label: 'In Progress' };
            case 'review':
                return { color: 'warning' as const, label: 'In Review' };
            case 'pending':
                return { color: 'default' as const, label: 'Pending' };
            case 'blocked':
                return { color: 'error' as const, label: 'Blocked' };
        }
    };

    const config = getStatusConfig();

    return (
        <Tooltip title={config.label} arrow placement="top">
            <Chip
                label={config.label}
                size={size}
                color={config.color}
                style={{ fontSize: size === 'small' ? '0.75rem' : '0.875rem', height: size === 'small' ? 20 : 24, minWidth: size === 'small' ? 20 : 24 }}
            />
        </Tooltip>
    );
};
