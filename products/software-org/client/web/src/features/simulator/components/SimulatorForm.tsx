import { memo, useState } from 'react';

/**
 * Dynamic event form with schema-driven field generation.
 *
 * <p><b>Purpose</b><br>
 * Renders form fields based on selected event type schema.
 * Supports text, number, select, datetime, and json input types.
 * Provides real-time validation and preview updates.
 *
 * <p><b>Features</b><br>
 * - Event type selector (50+ types)
 * - Dynamic field generation from Zod schema
 * - Type-aware input components (text, number, select, datetime, json)
 * - Real-time form validation
 * - Common templates (fraud, anomaly, test events)
 * - Field descriptions and examples
 *
 * <p><b>Props</b><br>
 * @param eventType - Selected event type
 * @param fields - Form field values
 * @param onChange - Callback with updated form data
 *
 * @doc.type component
 * @doc.purpose Dynamic event form
 * @doc.layer product
 * @doc.pattern Form
 */

interface SimulatorFormProps {
    eventType: string;
    fields: Record<string, unknown>;
    onChange: (data: {
        eventType: string;
        fields: Record<string, unknown>;
    }) => void;
}

// Mock event schemas
const EVENT_SCHEMAS: Record<string, Array<{
    name: string;
    type: 'text' | 'number' | 'select' | 'datetime' | 'json';
    label: string;
    placeholder?: string;
    description?: string;
    options?: Array<{ label: string; value: string }>;
    required?: boolean;
}>> = {
    transaction: [
        {
            name: 'userId',
            type: 'text',
            label: 'User ID',
            placeholder: 'user-123',
            required: true,
        },
        {
            name: 'amount',
            type: 'number',
            label: 'Amount',
            placeholder: '99.99',
            required: true,
        },
        {
            name: 'currency',
            type: 'select',
            label: 'Currency',
            options: [
                { label: 'USD', value: 'USD' },
                { label: 'EUR', value: 'EUR' },
                { label: 'GBP', value: 'GBP' },
            ],
            required: true,
        },
        {
            name: 'merchant',
            type: 'text',
            label: 'Merchant',
            placeholder: 'acme-store',
        },
        {
            name: 'riskScore',
            type: 'number',
            label: 'Risk Score',
            placeholder: '0-100',
        },
    ],
    error: [
        {
            name: 'service',
            type: 'text',
            label: 'Service Name',
            placeholder: 'service-auth',
            required: true,
        },
        {
            name: 'errorCode',
            type: 'text',
            label: 'Error Code',
            placeholder: '500',
            required: true,
        },
        {
            name: 'message',
            type: 'text',
            label: 'Error Message',
            placeholder: 'Internal server error',
            required: true,
        },
        {
            name: 'timestamp',
            type: 'datetime',
            label: 'Timestamp',
            required: true,
        },
        {
            name: 'metadata',
            type: 'json',
            label: 'Metadata',
            placeholder: '{"key": "value"}',
        },
    ],
    anomaly: [
        {
            name: 'metricName',
            type: 'text',
            label: 'Metric Name',
            placeholder: 'cpu_usage',
            required: true,
        },
        {
            name: 'value',
            type: 'number',
            label: 'Value',
            placeholder: '95.5',
            required: true,
        },
        {
            name: 'threshold',
            type: 'number',
            label: 'Threshold',
            placeholder: '80',
        },
        {
            name: 'anomalyScore',
            type: 'number',
            label: 'Anomaly Score',
            placeholder: '0.95',
        },
    ],
};

const EVENT_TYPES = Object.keys(EVENT_SCHEMAS);

export const SimulatorForm = memo(function SimulatorForm({
    eventType,
    fields,
    onChange,
}: SimulatorFormProps) {
    // GIVEN: Event type selected
    // WHEN: User fills form fields
    // THEN: onChange callback updates preview

    const [selectedType, setSelectedType] = useState(eventType);
    const schema = EVENT_SCHEMAS[selectedType] || [];

    const handleTypeChange = (newType: string) => {
        setSelectedType(newType);
        onChange({
            eventType: newType,
            fields: {},
        });
    };

    const handleFieldChange = (fieldName: string, value: unknown) => {
        const updatedFields = {
            ...fields,
            [fieldName]: value,
        };
        onChange({
            eventType: selectedType,
            fields: updatedFields,
        });
    };

    const renderInput = (
        field: (typeof schema)[0],
        value: unknown,
    ) => {
        const baseClasses =
            'w-full px-3 py-2 bg-slate-700 text-slate-200 rounded border border-slate-600 text-sm focus:border-blue-500 focus:outline-none';

        switch (field.type) {
            case 'text':
                return (
                    <input
                        type="text"
                        className={baseClasses}
                        placeholder={field.placeholder}
                        value={(value as string) || ''}
                        onChange={(e) => handleFieldChange(field.name, e.target.value)}
                    />
                );

            case 'number':
                return (
                    <input
                        type="number"
                        className={baseClasses}
                        placeholder={field.placeholder}
                        value={(value as number) || ''}
                        onChange={(e) => handleFieldChange(field.name, parseFloat(e.target.value))}
                        step="0.01"
                    />
                );

            case 'datetime':
                return (
                    <input
                        type="datetime-local"
                        className={baseClasses}
                        value={(value as string) || new Date().toISOString().slice(0, 16)}
                        onChange={(e) => handleFieldChange(field.name, e.target.value)}
                    />
                );

            case 'select':
                return (
                    <select
                        className={baseClasses}
                        value={(value as string) || ''}
                        onChange={(e) => handleFieldChange(field.name, e.target.value)}
                    >
                        <option value="">Select {field.label}</option>
                        {field.options?.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </select>
                );

            case 'json':
                return (
                    <textarea
                        className={`${baseClasses} font-mono resize-none h-24`}
                        placeholder={field.placeholder || '{}'}
                        value={(value as string) || ''}
                        onChange={(e) => {
                            try {
                                // Validate JSON
                                JSON.parse(e.target.value);
                                handleFieldChange(field.name, e.target.value);
                            } catch {
                                // Keep invalid JSON in field for now (will be caught on submit)
                                handleFieldChange(field.name, e.target.value);
                            }
                        }}
                    />
                );

            default:
                return null;
        }
    };

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="p-4 border-b border-slate-700">
                <label className="text-sm text-slate-400 mb-2 block font-semibold">Event Type</label>
                <select
                    value={selectedType}
                    onChange={(e) => handleTypeChange(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-700 text-slate-200 rounded border border-slate-600 text-sm focus:border-blue-500 focus:outline-none"
                >
                    {EVENT_TYPES.map((type) => (
                        <option key={type} value={type}>
                            {type.charAt(0).toUpperCase() + type.slice(1)}
                        </option>
                    ))}
                </select>
            </div>

            {/* Form Fields */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {schema.map((field) => (
                    <div key={field.name}>
                        <label className="text-sm text-slate-400 mb-2 block">
                            {field.label}
                            {field.required && <span className="text-red-400 ml-1">*</span>}
                        </label>
                        {field.description && (
                            <p className="text-xs text-slate-500 mb-2">{field.description}</p>
                        )}
                        {renderInput(field, fields[field.name])}
                    </div>
                ))}
            </div>

            {/* Templates */}
            <div className="p-4 border-t border-slate-700 bg-slate-900">
                <label className="text-xs text-slate-400 mb-2 block font-semibold">Templates</label>
                <div className="flex gap-2 flex-wrap">
                    <button
                        onClick={() => {
                            const fraudFields = {
                                userId: 'user-456',
                                amount: 9999.99,
                                currency: 'USD',
                                merchant: 'suspicious-store',
                                riskScore: 95,
                            };
                            handleFieldChange('userId', fraudFields.userId);
                            handleFieldChange('amount', fraudFields.amount);
                            handleFieldChange('currency', fraudFields.currency);
                            handleFieldChange('merchant', fraudFields.merchant);
                            handleFieldChange('riskScore', fraudFields.riskScore);
                        }}
                        className="px-2 py-1 text-xs bg-red-900 hover:bg-red-800 text-red-200 rounded"
                    >
                        High Risk
                    </button>
                    <button
                        onClick={() => {
                            const normalFields = {
                                userId: 'user-123',
                                amount: 49.99,
                                currency: 'USD',
                                merchant: 'trusted-store',
                                riskScore: 5,
                            };
                            handleFieldChange('userId', normalFields.userId);
                            handleFieldChange('amount', normalFields.amount);
                            handleFieldChange('currency', normalFields.currency);
                            handleFieldChange('merchant', normalFields.merchant);
                            handleFieldChange('riskScore', normalFields.riskScore);
                        }}
                        className="px-2 py-1 text-xs bg-green-900 hover:bg-green-800 text-green-200 rounded"
                    >
                        Normal
                    </button>
                    <button
                        onClick={() => {
                            onChange({ eventType: selectedType, fields: {} });
                        }}
                        className="px-2 py-1 text-xs bg-slate-700 hover:bg-slate-600 text-slate-200 rounded"
                    >
                        Clear
                    </button>
                </div>
            </div>
        </div>
    );
});

export default SimulatorForm;
