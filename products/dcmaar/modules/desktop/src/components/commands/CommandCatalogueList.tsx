import React from 'react';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import StatusBadge from '../common/StatusBadge';

export interface CommandDescriptor {
  id: string;
  name: string;
  category: string;
  riskLevel: string;
  requiresApproval: boolean;
  estimatedDurationMinutes: number;
  tags: string[];
}

export interface CommandCatalogueListProps {
  commands: CommandDescriptor[];
  selected?: string;
  onSelect: (id: string) => void;
}

export const CommandCatalogueList: React.FC<CommandCatalogueListProps> = ({
  commands,
  selected,
  onSelect,
}) => {
  return (
    <Stack spacing={1.5}>
      {commands.map((command) => {
        const isSelected = command.id === selected;
        return (
          <ButtonBase
            key={command.id}
            onClick={() => onSelect(command.id)}
            focusRipple
            sx={{
              textAlign: 'left',
              borderRadius: 2,
              border: '1px solid',
              borderColor: isSelected ? 'primary.main' : 'rgba(148, 163, 184, 0.14)',
              backgroundColor: isSelected ? 'rgba(56,189,248,0.12)' : 'transparent',
              transition: 'all 0.2s ease',
              width: '100%',
              p: 0,
              '&:hover, &:focus-visible': {
                borderColor: 'primary.main',
                backgroundColor: 'rgba(56,189,248,0.12)',
              },
            }}
            aria-pressed={isSelected}
            aria-label={`${command.name} command`}
          >
            <Box sx={{ p: 2, width: '100%' }}>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Stack spacing={0.5}>
                  <Typography variant="subtitle1">{command.name}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {command.category} · {command.estimatedDurationMinutes} min
                  </Typography>
                </Stack>
                <StatusBadge status={command.riskLevel} label={`Risk ${command.riskLevel}`} />
              </Stack>
              <Stack direction="row" spacing={1} mt={1} flexWrap="wrap">
                {command.requiresApproval ? (
                  <Chip label="Requires approval" size="small" color="warning" />
                ) : null}
                {command.tags.map((tag) => (
                  <Chip key={tag} label={tag} size="small" />
                ))}
              </Stack>
            </Box>
          </ButtonBase>
        );
      })}
    </Stack>
  );
};

export default CommandCatalogueList;
