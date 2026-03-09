import React from 'react';
import {
  Box,
  Button,
  Chip,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import InfoCard from '../common/InfoCard';
import StatusBadge from '../common/StatusBadge';

export interface CommandParameter {
  id: string;
  label: string;
  type: 'text' | 'select' | 'toggle';
  options?: string[];
  required?: boolean;
  helperText?: string;
}

export interface CommandDetailProps {
  name: string;
  description: string;
  riskLevel: string;
  requiresApproval: boolean;
  estimatedDurationMinutes: number;
  parameters: CommandParameter[];
  onExecute?: () => void;
}

export const CommandDetail: React.FC<CommandDetailProps> = ({
  name,
  description,
  riskLevel,
  requiresApproval,
  estimatedDurationMinutes,
  parameters,
  onExecute,
}) => {
  return (
    <InfoCard
      title={name}
      subtitle={`${estimatedDurationMinutes} min · ${requiresApproval ? 'Approval required' : 'Auto-executable'}`}
      action={<StatusBadge status={riskLevel} label={`Risk ${riskLevel}`} />}
    >
      <Stack spacing={3}>
        <Typography variant="body2" color="text.secondary">
          {description}
        </Typography>

        <Box
          sx={{
            display: 'grid',
            gap: 16,
          }}
        >
          {parameters.map((parameter) => {
            if (parameter.type === 'select') {
              return (
                <TextField
                  select
                  label={parameter.label}
                  key={parameter.id}
                  size="small"
                  SelectProps={{ native: true }}
                  required={parameter.required}
                  helperText={parameter.helperText}
                  defaultValue={
                    parameter.options && parameter.options.length > 0 ? parameter.options[0] : ''
                  }
                >
                  <option value="">--</option>
                  {parameter.options?.map((option) => (
                    <option value={option} key={option}>
                      {option}
                    </option>
                  ))}
                </TextField>
              );
            }

            if (parameter.type === 'toggle') {
              return (
                <Stack key={parameter.id} spacing={0.5}>
                  <FormControlLabel
                    control={<Switch defaultChecked={Boolean(parameter.required)} />}
                    label={parameter.label}
                  />
                  {parameter.helperText ? (
                    <Typography variant="caption" color="text.secondary">
                      {parameter.helperText}
                    </Typography>
                  ) : null}
                </Stack>
              );
            }

            return (
              <TextField
                key={parameter.id}
                label={parameter.label}
                size="small"
                required={parameter.required}
                helperText={parameter.helperText}
              />
            );
          })}
        </Box>

        {requiresApproval ? (
          <Chip
            label="Approval required before execution"
            color="warning"
            variant="outlined"
            sx={{ alignSelf: 'flex-start', fontWeight: 600 }}
          />
        ) : (
          <Typography variant="caption" color="text.secondary">
            This command can be executed immediately.
          </Typography>
        )}

        <Stack direction="row" spacing={1}>
          <Button variant="contained" onClick={onExecute}>
            Execute command
          </Button>
          <Button variant="outlined">Dry run</Button>
        </Stack>
      </Stack>
    </InfoCard>
  );
};

export default CommandDetail;
