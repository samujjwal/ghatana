import {
  Box,
  Button,
  FormControlLabel,
  Switch,
  TextField,
  Typography,
} from '@ghatana/design-system';
import type { Binding, ResponsiveVariant, StateVariant } from '@ghatana/ui-builder';
import React, { useEffect, useMemo, useState } from 'react';

import {
  getConfiguratorGroups,
  getContractGovernanceProfile,
  getRegistryFields,
} from './registry';

export interface PropertyFormUpdatePayload {
  readonly props: Record<string, unknown>;
  readonly name?: string;
  readonly responsiveVariant?: ResponsiveVariant;
  readonly stateVariant?: StateVariant;
  readonly dataBinding?: Binding;
  readonly actionBinding?: Binding;
}

export interface PropertyFormProps {
  readonly contractName: string;
  readonly instanceName?: string;
  readonly initialProps: Record<string, unknown>;
  readonly initialBindings?: readonly Binding[];
  readonly initialResponsiveVariants?: readonly ResponsiveVariant[];
  readonly initialStateVariants?: readonly StateVariant[];
  readonly onUpdate: (payload: PropertyFormUpdatePayload) => void;
  readonly onCancel?: () => void;
  readonly readOnly?: boolean;
  readonly readOnlyReason?: string;
}

type PrimitiveValue = string | number | boolean;
type EditableStateVariant = StateVariant['state'];

const STATE_VARIANT_OPTIONS: readonly EditableStateVariant[] = [
  'hover',
  'focus',
  'active',
  'disabled',
  'error',
  'loading',
  'selected',
];

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

function hasMeaningfulValue(value: unknown): boolean {
  if (typeof value === 'string') {
    return value.trim().length > 0;
  }

  return value !== null && value !== undefined && value !== false;
}

function requiresPrivacyAcknowledgement(privacyLevel: string | undefined): boolean {
  if (!privacyLevel) {
    return false;
  }

  const normalized = privacyLevel.trim().toLowerCase();
  return normalized.length > 0 && normalized !== 'public' && normalized !== 'internal';
}

function getFirstBinding(bindings: readonly Binding[] | undefined, type: Binding['type']): Binding | undefined {
  return bindings?.find((binding) => binding.type === type);
}

function formatPropsJson(props: Record<string, unknown> | undefined): string {
  return JSON.stringify(props ?? {}, null, 2);
}

function parsePropsJson(value: string): { readonly props: Record<string, unknown>; readonly error?: string } {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return { props: {} };
  }

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return { props: parsed as Record<string, unknown> };
    }
  } catch {
    return { props: {}, error: 'Enter a valid JSON object.' };
  }

  return { props: {}, error: 'Enter a JSON object, not an array or primitive.' };
}

function stableStringify(value: unknown): string {
  return JSON.stringify(value ?? null);
}

function bindingIdFor(type: Binding['type'], source: string, target: string): string {
  const safeSource = source.trim().replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
  const safeTarget = target.trim().replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
  return `inspector-${type}-${safeSource}-${safeTarget}`;
}

export const PropertyForm: React.FC<PropertyFormProps> = ({
  contractName,
  instanceName,
  initialProps,
  initialBindings = [],
  initialResponsiveVariants = [],
  initialStateVariants = [],
  onUpdate,
  onCancel,
  readOnly = false,
  readOnlyReason,
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
  const [reviewAcknowledged, setReviewAcknowledged] = useState(false);
  const [telemetryConsentAcknowledged, setTelemetryConsentAcknowledged] = useState(false);
  const [privacyAcknowledged, setPrivacyAcknowledged] = useState(false);
  const initialResponsiveVariant = initialResponsiveVariants[0];
  const initialStateVariant = initialStateVariants[0];
  const initialDataBinding = getFirstBinding(initialBindings, 'data');
  const initialActionBinding = getFirstBinding(initialBindings, 'event');
  const [responsiveBreakpoint, setResponsiveBreakpoint] = useState(initialResponsiveVariant?.breakpoint ?? '');
  const [responsivePropsJson, setResponsivePropsJson] = useState(() =>
    initialResponsiveVariant ? formatPropsJson(initialResponsiveVariant.props) : '',
  );
  const [stateVariantState, setStateVariantState] = useState<EditableStateVariant>(
    initialStateVariant?.state ?? 'hover',
  );
  const [stateVariantPropsJson, setStateVariantPropsJson] = useState(() =>
    initialStateVariant ? formatPropsJson(initialStateVariant.props) : '',
  );
  const [dataBindingSource, setDataBindingSource] = useState(initialDataBinding?.source ?? '');
  const [dataBindingTarget, setDataBindingTarget] = useState(initialDataBinding?.target ?? '');
  const [dataBindingTransform, setDataBindingTransform] = useState(initialDataBinding?.transform ?? '');
  const [dataBindingBidirectional, setDataBindingBidirectional] = useState(
    Boolean(initialDataBinding?.bidirectional),
  );
  const [actionBindingEvent, setActionBindingEvent] = useState(initialActionBinding?.source ?? '');
  const [actionBindingTarget, setActionBindingTarget] = useState(initialActionBinding?.target ?? '');
  const [actionBindingPayload, setActionBindingPayload] = useState(initialActionBinding?.transform ?? '');

  useEffect(() => {
    setDraftName(instanceName ?? '');
    setDraftProps(
      Object.fromEntries(
        Object.entries(initialProps).map(([key, value]) => [key, toPrimitiveValue(value)]),
      ),
    );
    setReviewAcknowledged(false);
    setTelemetryConsentAcknowledged(false);
    setPrivacyAcknowledged(false);
    setResponsiveBreakpoint(initialResponsiveVariant?.breakpoint ?? '');
    setResponsivePropsJson(initialResponsiveVariant ? formatPropsJson(initialResponsiveVariant.props) : '');
    setStateVariantState(initialStateVariant?.state ?? 'hover');
    setStateVariantPropsJson(initialStateVariant ? formatPropsJson(initialStateVariant.props) : '');
    setDataBindingSource(initialDataBinding?.source ?? '');
    setDataBindingTarget(initialDataBinding?.target ?? '');
    setDataBindingTransform(initialDataBinding?.transform ?? '');
    setDataBindingBidirectional(Boolean(initialDataBinding?.bidirectional));
    setActionBindingEvent(initialActionBinding?.source ?? '');
    setActionBindingTarget(initialActionBinding?.target ?? '');
    setActionBindingPayload(initialActionBinding?.transform ?? '');
  }, [
    initialActionBinding,
    initialDataBinding,
    initialProps,
    initialResponsiveVariant,
    initialStateVariant,
    instanceName,
  ]);

  const initialPrimitiveProps = useMemo(
    () =>
      Object.fromEntries(
        Object.entries(initialProps).map(([key, value]) => [key, toPrimitiveValue(value)]),
      ),
    [initialProps],
  );

  const responsivePropsParse = parsePropsJson(responsivePropsJson);
  const stateVariantPropsParse = parsePropsJson(stateVariantPropsJson);
  const responsiveVariant: ResponsiveVariant | undefined = responsiveBreakpoint.trim()
    ? {
        breakpoint: responsiveBreakpoint.trim(),
        props: responsivePropsParse.props,
      }
    : undefined;
  const stateVariant: StateVariant | undefined =
    stateVariantPropsJson.trim() && !stateVariantPropsParse.error
      ? {
          state: stateVariantState,
          props: stateVariantPropsParse.props,
        }
      : undefined;
  const dataBinding: Binding | undefined =
    dataBindingSource.trim() && dataBindingTarget.trim()
      ? {
          id:
            initialDataBinding?.id ??
            bindingIdFor('data', dataBindingSource, dataBindingTarget),
          type: 'data',
          source: dataBindingSource.trim(),
          target: dataBindingTarget.trim(),
          ...(dataBindingTransform.trim() ? { transform: dataBindingTransform.trim() } : {}),
          ...(dataBindingBidirectional ? { bidirectional: true } : {}),
        }
      : undefined;
  const actionBinding: Binding | undefined =
    actionBindingEvent.trim() && actionBindingTarget.trim()
      ? {
          id:
            initialActionBinding?.id ??
            bindingIdFor('event', actionBindingEvent, actionBindingTarget),
          type: 'event',
          source: actionBindingEvent.trim(),
          target: actionBindingTarget.trim(),
          ...(actionBindingPayload.trim() ? { transform: actionBindingPayload.trim() } : {}),
        }
      : undefined;
  const variantOrBindingDirty =
    stableStringify(responsiveVariant) !== stableStringify(initialResponsiveVariant) ||
    stableStringify(stateVariant) !== stableStringify(initialStateVariant) ||
    stableStringify(dataBinding) !== stableStringify(initialDataBinding) ||
    stableStringify(actionBinding) !== stableStringify(initialActionBinding);
  const isDirty =
    draftName !== (instanceName ?? '') ||
    JSON.stringify(draftProps) !==
      JSON.stringify(initialPrimitiveProps) ||
    variantOrBindingDirty;

  const changedReviewRequiredProps = governance.reviewRequiredProps.filter(
    (propName) => draftProps[propName] !== initialPrimitiveProps[propName],
  );
  const missingA11yProps = governance.requiredA11yProps.filter(
    (propName) => !hasMeaningfulValue(draftProps[propName] ?? initialProps[propName]),
  );
  const privacyAcknowledgementRequired = requiresPrivacyAcknowledgement(governance.privacyLevel);
  const canSubmit =
    !readOnly &&
    isDirty &&
    !responsivePropsParse.error &&
    !stateVariantPropsParse.error &&
    missingA11yProps.length === 0 &&
    (changedReviewRequiredProps.length === 0 || reviewAcknowledged) &&
    (governance.telemetryEventNames.length === 0 || telemetryConsentAcknowledged) &&
    (!privacyAcknowledgementRequired || privacyAcknowledged);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }

    const payload: PropertyFormUpdatePayload = {
      props: draftProps,
      name: draftName || undefined,
      ...(responsiveVariant ? { responsiveVariant } : {}),
      ...(stateVariant ? { stateVariant } : {}),
      ...(dataBinding ? { dataBinding } : {}),
      ...(actionBinding ? { actionBinding } : {}),
    };

    onUpdate(payload);
  };

  return (
    <form onSubmit={handleSubmit}>
      <Box className="flex flex-col gap-4 p-4">
      <TextField
        label="Node Name"
        value={draftName}
        fullWidth
        size="sm"
        disabled={readOnly}
        onChange={(event) => setDraftName(event.target.value)}
      />

      {readOnly ? (
        <Typography variant="caption" color="muted" data-testid="property-form-read-only">
          {readOnlyReason ?? 'Properties are read-only in this canvas mode.'}
        </Typography>
      ) : null}

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
                    disabled={readOnly}
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
                  disabled={readOnly}
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
              disabled={readOnly}
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

      <Box className="rounded-md border border-neutral-border bg-neutral-bg p-3">
        <Typography variant="body2" style={{ fontWeight: 600, display: 'block', marginBottom: 8 }}>
          Responsive variant
        </Typography>
        <TextField
          label="Responsive breakpoint"
          placeholder="md"
          value={responsiveBreakpoint}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setResponsiveBreakpoint(event.target.value)}
        />
        <label style={{ display: 'block', marginTop: 8 }}>
          <Typography variant="caption" color="subtle">Responsive props JSON</Typography>
          <textarea
            aria-label="Responsive props JSON"
            value={responsivePropsJson}
            disabled={readOnly}
            placeholder={'{\n  "size": "lg"\n}'}
            rows={4}
            onChange={(event) => setResponsivePropsJson(event.target.value)}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 12, marginTop: 4 }}
          />
        </label>
        {responsivePropsParse.error ? (
          <Typography variant="caption" color="danger" data-testid="property-responsive-json-error">
            {responsivePropsParse.error}
          </Typography>
        ) : null}
      </Box>

      <Box className="rounded-md border border-neutral-border bg-neutral-bg p-3">
        <Typography variant="body2" style={{ fontWeight: 600, display: 'block', marginBottom: 8 }}>
          State variant
        </Typography>
        <Box className="flex flex-col gap-1">
          <Typography variant="caption" color="subtle">State</Typography>
          <select
            aria-label="State variant"
            value={stateVariantState}
            disabled={readOnly}
            onChange={(event) => setStateVariantState(event.target.value as EditableStateVariant)}
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
            {STATE_VARIANT_OPTIONS.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
        </Box>
        <label style={{ display: 'block', marginTop: 8 }}>
          <Typography variant="caption" color="subtle">State props JSON</Typography>
          <textarea
            aria-label="State props JSON"
            value={stateVariantPropsJson}
            disabled={readOnly}
            placeholder={'{\n  "variant": "outline"\n}'}
            rows={4}
            onChange={(event) => setStateVariantPropsJson(event.target.value)}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 12, marginTop: 4 }}
          />
        </label>
        {stateVariantPropsParse.error ? (
          <Typography variant="caption" color="danger" data-testid="property-state-json-error">
            {stateVariantPropsParse.error}
          </Typography>
        ) : null}
      </Box>

      <Box className="rounded-md border border-neutral-border bg-neutral-bg p-3">
        <Typography variant="body2" style={{ fontWeight: 600, display: 'block', marginBottom: 8 }}>
          Data binding
        </Typography>
        <TextField
          label="Data source"
          placeholder="dataSource.users"
          value={dataBindingSource}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setDataBindingSource(event.target.value)}
        />
        <TextField
          label="Data target prop"
          placeholder="value"
          value={dataBindingTarget}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setDataBindingTarget(event.target.value)}
        />
        <TextField
          label="Data transform"
          placeholder="user.name"
          value={dataBindingTransform}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setDataBindingTransform(event.target.value)}
        />
        <FormControlLabel
          control={
            <Switch
              checked={dataBindingBidirectional}
              disabled={readOnly}
              onChange={(event) => setDataBindingBidirectional(event.target.checked)}
            />
          }
          label="Bidirectional data binding"
        />
      </Box>

      <Box className="rounded-md border border-neutral-border bg-neutral-bg p-3">
        <Typography variant="body2" style={{ fontWeight: 600, display: 'block', marginBottom: 8 }}>
          Action binding
        </Typography>
        <TextField
          label="Action event"
          placeholder="onClick"
          value={actionBindingEvent}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setActionBindingEvent(event.target.value)}
        />
        <TextField
          label="Action target"
          placeholder="navigate:/projects"
          value={actionBindingTarget}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setActionBindingTarget(event.target.value)}
        />
        <TextField
          label="Action payload"
          placeholder="projectId"
          value={actionBindingPayload}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setActionBindingPayload(event.target.value)}
        />
      </Box>

      {(governance.requiredA11yProps.length > 0 || governance.telemetryEventNames.length > 0) && (
        <Typography variant="caption" color="muted">
          A11y required: {governance.requiredA11yProps.join(', ') || 'none'} | Telemetry: {governance.telemetryEventNames.join(', ') || 'none'} | Privacy: {governance.privacyLevel ?? 'none'}
        </Typography>
      )}

      {missingA11yProps.length > 0 && (
        <Typography variant="caption" color="danger" data-testid="property-policy-a11y-blocker">
          Required accessibility props missing: {missingA11yProps.join(', ')}
        </Typography>
      )}

      {changedReviewRequiredProps.length > 0 && (
        <Box className="rounded-md border border-warning-border bg-warning-bg p-3">
          <Typography variant="caption" color="warning" style={{ display: 'block', marginBottom: 8 }}>
            Review required before applying governed prop changes: {changedReviewRequiredProps.join(', ')}
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={reviewAcknowledged}
                disabled={readOnly}
                onChange={(event) => setReviewAcknowledged(event.target.checked)}
              />
            }
            label="I have reviewed the governed change"
          />
        </Box>
      )}

      {governance.telemetryEventNames.length > 0 && (
        <Box className="rounded-md border border-info-border bg-info-bg p-3">
          <Typography variant="caption" color="info" style={{ display: 'block', marginBottom: 8 }}>
            This component can emit telemetry events: {governance.telemetryEventNames.join(', ')}
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={telemetryConsentAcknowledged}
                disabled={readOnly}
                onChange={(event) => setTelemetryConsentAcknowledged(event.target.checked)}
              />
            }
            label="Telemetry consent has been confirmed for this change"
          />
        </Box>
      )}

      {privacyAcknowledgementRequired && (
        <Box className="rounded-md border border-warning-border bg-warning-bg p-3">
          <Typography variant="caption" color="warning" style={{ display: 'block', marginBottom: 8 }}>
            This component handles {governance.privacyLevel} data. Confirm the privacy classification and data handling policy before applying changes.
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={privacyAcknowledged}
                disabled={readOnly}
                onChange={(event) => setPrivacyAcknowledged(event.target.checked)}
              />
            }
            label="Privacy classification has been reviewed for this change"
          />
        </Box>
      )}

        <Box className="flex justify-end gap-2 pt-2">
          {onCancel ? (
            <Button variant="outline" onClick={onCancel} type="button">
              Cancel
            </Button>
          ) : null}
          <Button type="submit" disabled={!canSubmit}>
            Apply
          </Button>
        </Box>
      </Box>
    </form>
  );
};
