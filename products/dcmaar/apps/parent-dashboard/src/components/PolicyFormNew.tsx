import { DynamicForm, type FieldConfig } from '@ghatana/ui';
import type { Policy } from './PolicyManagement';

/**
 * Policy form data interface.
 */
interface PolicyFormData {
  name: string;
  type: 'time-limit' | 'content-filter' | 'app-block' | 'schedule';
  maxUsageMinutes?: number;
  blockedCategories?: string[];
  blockedApps?: string[];
  allowedStart?: string;
  allowedEnd?: string;
  deviceIds: string[];
}

/**
 * Props for PolicyForm component.
 */
interface PolicyFormProps {
  policy?: Policy;
  onSubmit: (data: Omit<Policy, 'createdAt' | 'updatedAt'>) => void;
  onCancel: () => void;
}

/**
 * PolicyForm configuration using DynamicForm.
 *
 * Wraps the generic DynamicForm component with policy-specific
 * field configuration, validation, and data transformation.
 */
export function PolicyForm({ policy, onSubmit, onCancel }: PolicyFormProps) {
  const policyFields: FieldConfig<PolicyFormData>[] = [
    {
      name: 'name',
      type: 'text',
      label: 'Policy Name',
      required: true,
      placeholder: 'e.g., School Time Limits',
    },
    {
      name: 'type',
      type: 'select',
      label: 'Policy Type',
      required: true,
      options: [
        { label: 'Time Limit', value: 'time-limit' },
        { label: 'Content Filter', value: 'content-filter' },
        { label: 'App Block', value: 'app-block' },
        { label: 'Schedule', value: 'schedule' },
      ],
    },
    {
      name: 'maxUsageMinutes',
      type: 'number',
      label: 'Maximum Usage (minutes)',
      visible: (data) => data.type === 'time-limit',
      required: true,
      placeholder: '60',
      validation: (value, data) => {
        const numValue = typeof value === 'number' ? value : Number(value);
        if (data.type === 'time-limit' && (!numValue || numValue <= 0)) {
          return 'Valid max usage minutes required';
        }
      },
    },
    {
      name: 'blockedCategories',
      type: 'text',
      label: 'Blocked Categories (comma-separated)',
      visible: (data) => data.type === 'content-filter',
      required: true,
      placeholder: 'social media, gaming, adult content',
      transform: {
        toForm: (value: unknown) => Array.isArray(value) ? value.join(', ') : '',
        fromForm: (value: unknown) =>
          typeof value === 'string' ? value.split(',').map((s) => s.trim()).filter(Boolean) : [],
      },
      validation: (value, data) => {
        const strValue = String(value || '');
        if (data.type === 'content-filter' && !strValue.trim()) {
          return 'At least one blocked category required';
        }
      },
    },
    {
      name: 'blockedApps',
      type: 'text',
      label: 'Blocked Apps (comma-separated)',
      visible: (data) => data.type === 'app-block',
      required: true,
      placeholder: 'TikTok, Instagram, Snapchat',
      transform: {
        toForm: (value: unknown) => Array.isArray(value) ? value.join(', ') : '',
        fromForm: (value: unknown) =>
          typeof value === 'string' ? value.split(',').map((s) => s.trim()).filter(Boolean) : [],
      },
      validation: (value, data) => {
        const strValue = String(value || '');
        if (data.type === 'app-block' && !strValue.trim()) {
          return 'At least one blocked app required';
        }
      },
    },
    {
      name: 'allowedStart',
      type: 'time',
      label: 'Start Time',
      visible: (data) => data.type === 'schedule',
      required: true,
      validation: (value, data) => {
        if (data.type === 'schedule' && !value) {
          return 'Start time required';
        }
      },
    },
    {
      name: 'allowedEnd',
      type: 'time',
      label: 'End Time',
      visible: (data) => data.type === 'schedule',
      required: true,
      validation: (value, data) => {
        if (data.type === 'schedule' && !value) {
          return 'End time required';
        }
      },
    },
    {
      name: 'deviceIds',
      type: 'text',
      label: 'Device IDs (comma-separated)',
      required: true,
      placeholder: 'device-1, device-2',
      transform: {
        toForm: (value: unknown) => Array.isArray(value) ? value.join(', ') : '',
        fromForm: (value: unknown) =>
          typeof value === 'string' ? value.split(',').map((s) => s.trim()).filter(Boolean) : [],
      },
      validation: (value) => {
        const strValue = String(value || '');
        if (!strValue.trim()) {
          return 'At least one device ID required';
        }
      },
    },
  ];

  // Transform policy to initial data
  const initialData: Partial<PolicyFormData> = policy
    ? {
        name: policy.name,
        type: policy.type,
        maxUsageMinutes: policy.restrictions.maxUsageMinutes,
        blockedCategories: policy.restrictions.blockedCategories,
        blockedApps: policy.restrictions.blockedApps,
        allowedStart: policy.restrictions.allowedHours?.start,
        allowedEnd: policy.restrictions.allowedHours?.end,
        deviceIds: policy.deviceIds,
      }
    : {};

  // Handle submit with proper type transformation
  const handleSubmit = (data: PolicyFormData) => {
    // Build restrictions based on type
    const restrictions: Policy['restrictions'] = {};

    if (data.type === 'time-limit' && data.maxUsageMinutes) {
      restrictions.maxUsageMinutes = data.maxUsageMinutes;
    } else if (data.type === 'content-filter' && data.blockedCategories) {
      restrictions.blockedCategories = data.blockedCategories;
    } else if (data.type === 'app-block' && data.blockedApps) {
      restrictions.blockedApps = data.blockedApps;
    } else if (data.type === 'schedule' && data.allowedStart && data.allowedEnd) {
      restrictions.allowedHours = {
        start: data.allowedStart,
        end: data.allowedEnd,
      };
    }

    const policyData: Partial<Omit<Policy, 'createdAt' | 'updatedAt'>> & { id?: string } = {
      name: data.name,
      type: data.type,
      restrictions,
      deviceIds: data.deviceIds,
    };

    if (policy?.id) {
      policyData.id = policy.id;
    }

    // Cast to the expected onSubmit type; when creating new policy the id may be omitted
    onSubmit(policyData as Omit<Policy, 'createdAt' | 'updatedAt'>);
  };

  return (
    <DynamicForm
      fields={policyFields}
      initialData={initialData}
      onSubmit={handleSubmit}
      onCancel={onCancel}
      submitText={policy ? 'Update Policy' : 'Create Policy'}
      cancelText="Cancel"
    />
  );
}
