import {
  Box,
  Button,
  FormControlLabel,
  Switch,
  TextField,
  Typography,
} from '@ghatana/design-system';
import { Select } from '../../ui/Select';
import { Textarea } from '../../ui/Textarea';
import type { Binding, ComponentInstance, ResponsiveVariant, StateVariant } from '@ghatana/ui-builder';
import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from '@ghatana/i18n';

import {
  getConfiguratorGroups,
  getContractGovernanceProfile,
  getRegistryFields,
  type RegistryFieldDescriptor,
} from './registry';

export interface PropertyFormUpdatePayload {
  readonly props: Record<string, unknown>;
  readonly name?: string;
  readonly responsiveVariant?: ResponsiveVariant;
  readonly stateVariant?: StateVariant;
  readonly dataBinding?: Binding;
  readonly actionBinding?: Binding;
  readonly privacyClassification?: PrivacyClassification;
}

export interface PropertyFormProps {
  readonly contractName: string;
  readonly instanceName?: string;
  readonly initialProps: Record<string, unknown>;
  readonly initialBindings?: readonly Binding[];
  readonly initialResponsiveVariants?: readonly ResponsiveVariant[];
  readonly initialStateVariants?: readonly StateVariant[];
  readonly initialDataClassification?: PrivacyClassification;
  readonly onUpdate: (payload: PropertyFormUpdatePayload) => void;
  readonly onCancel?: () => void;
  readonly readOnly?: boolean;
  readonly readOnlyReason?: string;
}

type PrimitiveValue = string | number | boolean;
type DraftProps = Record<string, unknown>;
type EditableStateVariant = StateVariant['state'];
type PrivacyClassification = NonNullable<ComponentInstance['metadata']['dataClassification']>;

const STATE_VARIANT_OPTIONS: readonly EditableStateVariant[] = [
  'hover',
  'focus',
  'active',
  'disabled',
  'error',
  'loading',
  'selected',
];

const PRIVACY_CLASSIFICATION_OPTIONS: readonly PrivacyClassification[] = [
  'PUBLIC',
  'INTERNAL',
  'SENSITIVE',
  'CREDENTIALS',
  'REGULATED',
];

const DEFAULT_ACTION_EVENT_OPTIONS = ['onClick', 'onChange', 'onSubmit', 'onFocus', 'onBlur'] as const;

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

function isComplexField(field: RegistryFieldDescriptor): boolean {
  return field.control === 'json' || field.valueType === 'object';
}

function isTokenCollectionField(field: RegistryFieldDescriptor): boolean {
  return field.control === 'multiselect' || (field.valueType === 'array' && Boolean(field.tokenTypes?.length));
}

function isTokenReferenceField(field: RegistryFieldDescriptor): boolean {
  return field.control === 'token-select' || field.valueType === 'token-ref';
}

function getInitialFieldValue(field: RegistryFieldDescriptor, props: Record<string, unknown>): unknown {
  if (Object.prototype.hasOwnProperty.call(props, field.name)) {
    return props[field.name];
  }

  if (field.defaultValue !== undefined) {
    return field.defaultValue;
  }

  if (isComplexField(field)) {
    return field.valueType === 'array' ? [] : {};
  }

  return toPrimitiveValue(undefined);
}

function createInitialDraftProps(
  fields: readonly RegistryFieldDescriptor[],
  props: Record<string, unknown>,
): DraftProps {
  const fieldEntries = fields.flatMap((field) => {
    const hasInitialValue = Object.prototype.hasOwnProperty.call(props, field.name);
    if (!hasInitialValue && field.defaultValue === undefined) {
      return [];
    }

    const value = getInitialFieldValue(field, props);

    if (isComplexField(field) || isTokenCollectionField(field)) {
      return [[field.name, value] as const];
    }

    return [[field.name, toPrimitiveValue(value)] as const];
  });
  const fieldNames = new Set(fieldEntries.map(([name]) => name));
  const passthroughEntries = Object.entries(props).filter(([name]) => !fieldNames.has(name));

  return Object.fromEntries([...passthroughEntries, ...fieldEntries]);
}

function hasMeaningfulValue(value: unknown): boolean {
  if (typeof value === 'string') {
    return value.trim().length > 0;
  }

  return value !== null && value !== undefined && value !== false;
}

function getPrivacyLevelLabel(privacyLevel: unknown): string | undefined {
  if (!privacyLevel) {
    return undefined;
  }
  if (typeof privacyLevel === 'string') {
    return privacyLevel;
  }
  if (typeof privacyLevel === 'object' && 'privacyGuidance' in privacyLevel) {
    const guidance = (privacyLevel as { readonly privacyGuidance?: unknown }).privacyGuidance;
    if (typeof guidance === 'string' && guidance.trim()) {
      return guidance;
    }
  }

  return 'governed';
}

function requiresPrivacyAcknowledgement(privacyLevel: unknown): boolean {
  const label = getPrivacyLevelLabel(privacyLevel);
  if (!label) {
    return false;
  }
  const normalized = label.trim().toLowerCase();
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

function formatFieldJson(value: unknown, field: RegistryFieldDescriptor): string {
  const fallback = field.valueType === 'array' ? [] : {};
  return JSON.stringify(value ?? field.defaultValue ?? fallback, null, 2);
}

function parseFieldJson(
  value: string,
  field: RegistryFieldDescriptor,
): { readonly value: unknown; readonly error?: string } {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return { value: field.valueType === 'array' ? [] : {} };
  }

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (field.valueType === 'array' && !Array.isArray(parsed)) {
      return { value: [], error: `${field.label} must be a JSON array.` };
    }
    if (field.valueType === 'object' && (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed))) {
      return { value: {}, error: `${field.label} must be a JSON object.` };
    }

    return { value: parsed };
  } catch {
    return { value: field.valueType === 'array' ? [] : {}, error: `${field.label} must be valid JSON.` };
  }
}

function formatTokenCollection(value: unknown): string {
  if (Array.isArray(value)) {
    return value
      .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      .join(', ');
  }

  return typeof value === 'string' ? value : '';
}

function parseTokenCollection(value: string): readonly string[] {
  return value
    .split(',')
    .map((token) => token.trim())
    .filter((token) => token.length > 0);
}

function stableStringify(value: unknown): string {
  return JSON.stringify(value ?? null);
}

function bindingIdFor(type: Binding['type'], source: string, target: string): string {
  const safeSource = source.trim().replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
  const safeTarget = target.trim().replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
  return `inspector-${type}-${safeSource}-${safeTarget}`;
}

function normalizePrivacyClassification(value: unknown): PrivacyClassification {
  if (typeof value === 'string') {
    const normalized = value.trim().toUpperCase();
    if (PRIVACY_CLASSIFICATION_OPTIONS.includes(normalized as PrivacyClassification)) {
      return normalized as PrivacyClassification;
    }
  }

  return 'INTERNAL';
}

function isActionEventProp(propName: string): boolean {
  return /^on[A-Z]/.test(propName);
}

function mergeUniqueOptions(...optionGroups: readonly (readonly string[])[]): readonly string[] {
  return Array.from(
    new Set(
      optionGroups
        .flat()
        .map((option) => option.trim())
        .filter((option) => option.length > 0),
    ),
  );
}

export const PropertyForm: React.FC<PropertyFormProps> = ({
  contractName,
  instanceName,
  initialProps,
  initialBindings = [],
  initialResponsiveVariants = [],
  initialStateVariants = [],
  initialDataClassification,
  onUpdate,
  onCancel,
  readOnly = false,
  readOnlyReason,
}) => {
  const { t } = useTranslation('common');
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
  const [draftProps, setDraftProps] = useState<DraftProps>(() => createInitialDraftProps(fields, initialProps));
  const [complexDrafts, setComplexDrafts] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      fields
        .filter(isComplexField)
        .map((field) => [field.name, formatFieldJson(getInitialFieldValue(field, initialProps), field)]),
    ),
  );
  const [reviewAcknowledged, setReviewAcknowledged] = useState(false);
  const [telemetryConsentAcknowledged, setTelemetryConsentAcknowledged] = useState(false);
  const [privacyAcknowledged, setPrivacyAcknowledged] = useState(false);
  const initialResponsiveVariant = initialResponsiveVariants[0];
  const initialStateVariant = initialStateVariants[0];
  const initialDataBinding = getFirstBinding(initialBindings, 'data');
  const initialActionBinding = getFirstBinding(initialBindings, 'event');
  const initialPrivacyClassification = normalizePrivacyClassification(initialDataClassification);
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
  const [privacyClassification, setPrivacyClassification] = useState(initialPrivacyClassification);
  const dataBindingTargetOptions = useMemo(
    () => mergeUniqueOptions(fields.map((field) => field.name), dataBindingTarget ? [dataBindingTarget] : []),
    [dataBindingTarget, fields],
  );
  const actionBindingEventOptions = useMemo(
    () =>
      mergeUniqueOptions(
        fields.map((field) => field.name).filter(isActionEventProp),
        DEFAULT_ACTION_EVENT_OPTIONS,
        actionBindingEvent ? [actionBindingEvent] : [],
      ),
    [actionBindingEvent, fields],
  );

  useEffect(() => {
    setDraftName(instanceName ?? '');
    setDraftProps(
      createInitialDraftProps(fields, initialProps),
    );
    setComplexDrafts(
      Object.fromEntries(
        fields
          .filter(isComplexField)
          .map((field) => [field.name, formatFieldJson(getInitialFieldValue(field, initialProps), field)]),
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
    setPrivacyClassification(initialPrivacyClassification);
  }, [
    initialActionBinding,
    initialDataBinding,
    initialPrivacyClassification,
    initialProps,
    initialResponsiveVariant,
    initialStateVariant,
    instanceName,
    fields,
  ]);

  const initialPrimitiveProps = useMemo(
    () => createInitialDraftProps(fields, initialProps),
    [fields, initialProps],
  );

  const complexFieldErrors = fields
    .filter(isComplexField)
    .map((field) => parseFieldJson(complexDrafts[field.name] ?? '', field).error)
    .filter((error): error is string => typeof error === 'string');
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
    stableStringify(actionBinding) !== stableStringify(initialActionBinding) ||
    privacyClassification !== initialPrivacyClassification;
  const isDirty =
    draftName !== (instanceName ?? '') ||
    JSON.stringify(draftProps) !==
      JSON.stringify(initialPrimitiveProps) ||
    variantOrBindingDirty;

  const changedReviewRequiredProps = governance.reviewRequiredProps.filter(
    (propName) => draftProps[propName] !== initialPrimitiveProps[propName],
  );
  const inspectorReviewRequiredReasons = [
    stableStringify(responsiveVariant) !== stableStringify(initialResponsiveVariant)
      ? 'responsive variant'
      : null,
    stableStringify(stateVariant) !== stableStringify(initialStateVariant)
      ? 'state variant'
      : null,
    stableStringify(dataBinding) !== stableStringify(initialDataBinding)
      ? 'data binding'
      : null,
    stableStringify(actionBinding) !== stableStringify(initialActionBinding)
      ? 'action binding'
      : null,
  ].filter((reason): reason is string => reason !== null);
  const reviewAcknowledgementRequired =
    changedReviewRequiredProps.length > 0 || inspectorReviewRequiredReasons.length > 0;
  const missingA11yProps = governance.requiredA11yProps.filter(
    (propName) => !hasMeaningfulValue(draftProps[propName] ?? initialProps[propName]),
  );
  const privacyLevelLabel = getPrivacyLevelLabel(governance.privacyLevel);
  const privacyAcknowledgementRequired =
    requiresPrivacyAcknowledgement(governance.privacyLevel) ||
    requiresPrivacyAcknowledgement(privacyClassification);
  const canSubmit =
    !readOnly &&
    isDirty &&
    complexFieldErrors.length === 0 &&
    !responsivePropsParse.error &&
    !stateVariantPropsParse.error &&
    missingA11yProps.length === 0 &&
    (!reviewAcknowledgementRequired || reviewAcknowledged) &&
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
      ...(privacyClassification !== initialPrivacyClassification
        ? { privacyClassification }
        : {}),
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
                    <span aria-label={t('canvas.property.reviewRequiredAria')}> {' '}(review required)</span>
                  )}
                </Typography>
                <Select
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
                </Select>
              </Box>
            );
          }

          if (isComplexField(field)) {
            const jsonValue = complexDrafts[field.name] ?? formatFieldJson(value, field);
            const parseResult = parseFieldJson(jsonValue, field);

            return (
              <Box key={field.name} className="flex flex-col gap-1">
                <Typography variant="caption" color="subtle">
                  {field.label}
                  {field.required && <span aria-hidden="true"> *</span>}
                  {reviewRequiredProps.has(field.name) && (
                    <span aria-label={t('canvas.property.reviewRequiredAria')}> {' '}(review required)</span>
                  )}
                </Typography>
                {field.description ? (
                  <Typography variant="caption" color="muted">
                    {field.description}
                  </Typography>
                ) : null}
                <Textarea
                  aria-label={`${field.label} JSON`}
                  value={jsonValue}
                  required={field.required}
                  disabled={readOnly}
                  rows={field.valueType === 'array' ? 5 : 6}
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    const nextParse = parseFieldJson(nextValue, field);
                    setComplexDrafts((current) => ({
                      ...current,
                      [field.name]: nextValue,
                    }));
                    if (!nextParse.error) {
                      setDraftProps((current) => ({
                        ...current,
                        [field.name]: nextParse.value,
                      }));
                    }
                  }}
                  style={{ width: '100%', fontFamily: 'monospace', fontSize: 12 }}
                />
                {parseResult.error ? (
                  <Typography
                    variant="caption"
                    color="danger"
                    data-testid={`property-complex-json-error-${field.name}`}
                  >
                    {parseResult.error}
                  </Typography>
                ) : null}
              </Box>
            );
          }

          if (isTokenCollectionField(field)) {
            return (
              <TextField
                key={field.name}
                label={`${field.label}${field.tokenTypes?.length ? ` (${field.tokenTypes.join(', ')} tokens)` : ''}`}
                value={formatTokenCollection(value)}
                fullWidth
                size="sm"
                required={field.required}
                disabled={readOnly}
                onChange={(event) => {
                  setDraftProps((current) => ({
                    ...current,
                    [field.name]: parseTokenCollection(event.target.value),
                  }));
                }}
              />
            );
          }

          if (isTokenReferenceField(field)) {
            return (
              <TextField
                key={field.name}
                label={`${field.label}${field.tokenTypes?.length ? ` (${field.tokenTypes.join(', ')} token)` : ''}`}
                value={String(value ?? '')}
                fullWidth
                size="sm"
                required={field.required}
                disabled={readOnly}
                onChange={(event) => {
                  setDraftProps((current) => ({
                    ...current,
                    [field.name]: event.target.value,
                  }));
                }}
              />
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
          placeholder={t('canvas.property.responsiveBreakpointPlaceholder')}
          value={responsiveBreakpoint}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setResponsiveBreakpoint(event.target.value)}
        />
        <label style={{ display: 'block', marginTop: 8 }}>
          <Typography variant="caption" color="subtle">Responsive props JSON</Typography>
          <Textarea
            aria-label={t('canvas.property.responsivePropsJsonAria')}
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
          <Select
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
          </Select>
        </Box>
        <label style={{ display: 'block', marginTop: 8 }}>
          <Typography variant="caption" color="subtle">State props JSON</Typography>
          <Textarea
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
          placeholder={t('canvas.property.dataSourcePlaceholder')}
          value={dataBindingSource}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setDataBindingSource(event.target.value)}
        />
        <Box className="flex flex-col gap-1">
          <Typography variant="caption" color="subtle">Data target prop</Typography>
          <Select
            aria-label="Data target prop"
            value={dataBindingTarget}
            disabled={readOnly}
            onChange={(event) => setDataBindingTarget(event.target.value)}
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
            <option value="">Select prop...</option>
            {dataBindingTargetOptions.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </Select>
        </Box>
        <TextField
          label="Data transform"
          placeholder={t('canvas.property.dataTransformPlaceholder')}
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
        <Box className="flex flex-col gap-1">
          <Typography variant="caption" color="subtle">Action event</Typography>
          <Select
            aria-label="Action event"
            value={actionBindingEvent}
            disabled={readOnly}
            onChange={(event) => setActionBindingEvent(event.target.value)}
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
            <option value="">Select event...</option>
            {actionBindingEventOptions.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </Select>
        </Box>
        <TextField
          label="Action target"
          placeholder={t('canvas.property.actionTargetPlaceholder')}
          value={actionBindingTarget}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setActionBindingTarget(event.target.value)}
        />
        <TextField
          label="Action payload"
          placeholder={t('canvas.property.actionPayloadPlaceholder')}
          value={actionBindingPayload}
          fullWidth
          size="sm"
          disabled={readOnly}
          onChange={(event) => setActionBindingPayload(event.target.value)}
        />
      </Box>

      <Box className="rounded-md border border-neutral-border bg-neutral-bg p-3">
        <Typography variant="body2" style={{ fontWeight: 600, display: 'block', marginBottom: 8 }}>
          Privacy classification
        </Typography>
        <Select
          aria-label="Privacy classification"
          value={privacyClassification}
          disabled={readOnly}
          onChange={(event) =>
            setPrivacyClassification(normalizePrivacyClassification(event.target.value))
          }
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
          {PRIVACY_CLASSIFICATION_OPTIONS.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </Select>
        <Typography variant="caption" color="muted">
          Classification is stored on the selected builder node and participates in privacy review gates.
        </Typography>
      </Box>

      {(governance.requiredA11yProps.length > 0 || governance.telemetryEventNames.length > 0) && (
        <Typography variant="caption" color="muted">
          A11y required: {governance.requiredA11yProps.join(', ') || 'none'} | Telemetry: {governance.telemetryEventNames.join(', ') || 'none'} | Privacy: {privacyLevelLabel ?? 'none'}
        </Typography>
      )}

      {missingA11yProps.length > 0 && (
        <Typography variant="caption" color="danger" data-testid="property-policy-a11y-blocker">
          Required accessibility props missing: {missingA11yProps.join(', ')}
        </Typography>
      )}

      {reviewAcknowledgementRequired && (
        <Box className="rounded-md border border-warning-border bg-warning-bg p-3">
          <Typography variant="caption" color="warning" style={{ display: 'block', marginBottom: 8 }}>
            Review required before applying governed inspector changes: {[...changedReviewRequiredProps, ...inspectorReviewRequiredReasons].join(', ')}
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
            This component handles {privacyLevelLabel} data. Confirm the privacy classification and data handling policy before applying changes.
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
