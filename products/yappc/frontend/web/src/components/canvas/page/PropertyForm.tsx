import {
  Box,
  Button,
  FormControlLabel,
  Switch,
  TextField,
  Typography,
} from '@ghatana/design-system';
import React, { useEffect, useMemo, useState } from 'react';

import {
  getConfiguratorGroups,
  getContractGovernanceProfile,
  getRegistryFields,
} from './registry';

export interface PropertyFormProps {
  readonly contractName: string;
  readonly instanceName?: string;
  readonly initialProps: Record<string, unknown>;
  readonly onUpdate: (payload: {
    readonly props: Record<string, unknown>;
    readonly name?: string;
  }) => void;
  readonly onCancel?: () => void;
}

type PrimitiveValue = string | number | boolean;

function toPrimitiveValue(value: unknown): PrimitiveValue {
  if (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  ) {
    return value;
  }

  return '';
}

export const PropertyForm: React.FC<PropertyFormProps> = ({
  contractName,
  instanceName,
  initialProps,
  onUpdate,
  onCancel,
}) => {
  const fields = useMemo(() => getRegistryFields(contractName), [contractName]);
  const configuratorGroups = useMemo(
    () => getConfiguratorGroups(contractName),
    [contractName],
  );
  const governance = useMemo(
    () => getContractGovernanceProfile(contractName),
    [contractName],
  );
  const reviewRequiredProps = useMemo(
    () => new Set(governance.reviewRequiredProps),
    [governance.reviewRequiredProps],
  );
  const [draftName, setDraftName] = useState(instanceName ?? '');
  const [draftProps, setDraftProps] = useState<Record<string, PrimitiveValue>>(() =>
    Object.fromEntries(
      Object.entries(initialProps).map(([key, value]) => [key, toPrimitiveValue(value)]),
    ),
  );

  useEffect(() => {
    setDraftName(instanceName ?? '');
    setDraftProps(
      Object.fromEntries(
        Object.entries(initialProps).map(([key, value]) => [key, toPrimitiveValue(value)]),
      ),
    );
  }, [initialProps, instanceName]);

  const isDirty =
    draftName !== (instanceName ?? '') ||
    JSON.stringify(draftProps) !==
      JSON.stringify(
        Object.fromEntries(
          Object.entries(initialProps).map(([key, value]) => [key, toPrimitiveValue(value)]),
        ),
      );

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    onUpdate({
      props: draftProps,
      name: draftName || undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit}>
      <Box className="flex flex-col gap-4 p-4">
      <TextField
        label="Node Name"
        value={draftName}
        fullWidth
        size="sm"
        onChange={(event) => setDraftName(event.target.value)}
      />

      {fields.length === 0 ? (
        <Typography color="subtle">No configurable fields exposed by this contract.</Typography>
      ) : (
        fields.map((field) => {
          const value = draftProps[field.name] ?? toPrimitiveValue(field.defaultValue);

          if (field.control === 'toggle' || field.type === 'boolean') {
            return (
              <FormControlLabel
                key={field.name}
                control={
                  <Switch
                    checked={Boolean(value)}
                    onChange={(event) => {
                      setDraftProps((current) => ({
                        ...current,
                        [field.name]: event.target.checked,
                      }));
                    }}
                  />
                }
                label={field.label}
              />
            );
          }

          if (field.control === 'select' && field.enumOptions && field.enumOptions.length > 0) {
            return (
              <Box key={field.name} className="flex flex-col gap-1">
                <Typography variant="caption" color="subtle">
                  {field.label}
                  {field.required && <span aria-hidden="true"> *</span>}
                  {reviewRequiredProps.has(field.name) && (
                    <span aria-label="review-required"> {' '}(review required)</span>
                  )}
                </Typography>
                <select
                  aria-label={field.label}
                  value={String(value ?? '')}
                  required={field.required}
                  onChange={(event) => {
                    setDraftProps((current) => ({
                      ...current,
                      [field.name]: event.target.value,
                    }));
                  }}
                  style={{
                    width: '100%',
                    padding: '6px 8px',
                    border: '1px solid #d1d5db',
                    borderRadius: '4px',
                    fontSize: '0.875rem',
                    background: 'white',
                    color: '#111827',
                  }}
                >
                  {!field.required && <option value="">—</option>}
                  {field.enumOptions.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </Box>
            );
          }

          return (
            <TextField
              key={field.name}
              label={reviewRequiredProps.has(field.name) ? `${field.label} (review required)` : field.label}
              type={field.control === 'number' ? 'number' : 'text'}
              value={String(value ?? '')}
              fullWidth
              size="sm"
              required={field.required}
              onChange={(event) => {
                const rawValue = event.target.value;
                setDraftProps((current) => ({
                  ...current,
                  [field.name]:
                    field.control === 'number' ? Number(rawValue || 0) : rawValue,
                }));
              }}
            />
          );
        })
      )}

      {configuratorGroups.length > 0 && (
        <Typography variant="caption" color="muted">
          {configuratorGroups.map((g) => g.label).join(' · ')}
        </Typography>
      )}

      {(governance.requiredA11yProps.length > 0 || governance.telemetryEventNames.length > 0) && (
        <Typography variant="caption" color="muted">
          A11y required: {governance.requiredA11yProps.join(', ') || 'none'} | Telemetry: {governance.telemetryEventNames.join(', ') || 'none'}
        </Typography>
      )}

        <Box className="flex justify-end gap-2 pt-2">
          {onCancel ? (
            <Button variant="outline" onClick={onCancel} type="button">
              Cancel
            </Button>
          ) : null}
          <Button type="submit" disabled={!isDirty}>
            Apply
          </Button>
        </Box>
      </Box>
    </form>
  );
};
