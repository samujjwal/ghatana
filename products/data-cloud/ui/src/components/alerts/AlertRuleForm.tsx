/**
 * Alert Rule Form Component
 * 
 * Form for creating and editing alert rules.
 * Supports various condition types, thresholds, and notification channels.
 * 
 * @doc.type component
 * @doc.purpose Alert rule creation/editing
 * @doc.layer frontend
 * @doc.pattern Form Component
 */

import React, { useState } from 'react';
import { X, Plus, Trash2, Bell, Mail, MessageSquare } from 'lucide-react';
import {
    cn,
    cardStyles,
    textStyles,
    buttonStyles,
    inputStyles,
    modalStyles,
} from '../../lib/theme';

/**
 * Alert condition type
 */
export type ConditionType =
    | 'threshold'
    | 'anomaly'
    | 'pattern'
    | 'absence';

/**
 * Alert severity
 */
export type AlertSeverity = 'critical' | 'warning' | 'info';

/**
 * Notification channel
 */
export type NotificationChannel = 'email' | 'slack' | 'webhook' | 'pagerduty';

/**
 * Alert rule interface
 */
export interface AlertRule {
    id?: string;
    name: string;
    description?: string;
    enabled: boolean;
    severity: AlertSeverity;
    conditionType: ConditionType;
    metric: string;
    operator: 'gt' | 'lt' | 'eq' | 'gte' | 'lte';
    threshold: number;
    duration: number; // in minutes
    channels: NotificationChannel[];
    recipients?: string[];
    webhookUrl?: string;
}

/**
 * Default alert rule
 */
const defaultRule: AlertRule = {
    name: '',
    description: '',
    enabled: true,
    severity: 'warning',
    conditionType: 'threshold',
    metric: '',
    operator: 'gt',
    threshold: 0,
    duration: 5,
    channels: ['email'],
    recipients: [],
};

/**
 * Metric options
 */
const metricOptions = [
    { value: 'cpu_usage', label: 'CPU Usage (%)' },
    { value: 'memory_usage', label: 'Memory Usage (%)' },
    { value: 'error_rate', label: 'Error Rate (%)' },
    { value: 'latency_p99', label: 'P99 Latency (ms)' },
    { value: 'throughput', label: 'Throughput (req/s)' },
    { value: 'queue_depth', label: 'Queue Depth' },
    { value: 'data_quality_score', label: 'Data Quality Score' },
    { value: 'workflow_failures', label: 'Workflow Failures' },
];

/**
 * Operator options
 */
const operatorOptions = [
    { value: 'gt', label: 'Greater than (>)' },
    { value: 'gte', label: 'Greater than or equal (>=)' },
    { value: 'lt', label: 'Less than (<)' },
    { value: 'lte', label: 'Less than or equal (<=)' },
    { value: 'eq', label: 'Equal to (=)' },
];

interface AlertRuleFormProps {
    rule?: AlertRule;
    isOpen: boolean;
    onClose: () => void;
    onSave: (rule: AlertRule) => void;
}

/**
 * Alert Rule Form Component
 */
export function AlertRuleForm({
    rule,
    isOpen,
    onClose,
    onSave
}: AlertRuleFormProps): React.ReactElement | null {
    const [formData, setFormData] = useState<AlertRule>(rule || defaultRule);
    const [newRecipient, setNewRecipient] = useState('');

    const isEditing = !!rule?.id;

    const handleChange = (field: keyof AlertRule, value: unknown) => {
        setFormData((prev) => ({ ...prev, [field]: value }));
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        onSave(formData);
        onClose();
    };

    const handleToggleChannel = (channel: NotificationChannel) => {
        setFormData((prev) => {
            const exists = prev.channels.includes(channel);
            const channels = exists
                ? prev.channels.filter((c) => c !== channel)
                : [...prev.channels, channel];

            return {
                ...prev,
                channels,
                recipients: channels.includes('email') ? prev.recipients : [],
                webhookUrl: channels.includes('webhook') ? prev.webhookUrl : undefined,
            };
        });
    };

    const handleAddRecipient = () => {
        const email = newRecipient.trim();
        if (!email) return;

        setFormData((prev) => ({
            ...prev,
            recipients: [...(prev.recipients ?? []), email],
        }));
        setNewRecipient('');
    };

    const handleRemoveRecipient = (email: string) => {
        setFormData((prev) => ({
            ...prev,
            recipients: (prev.recipients ?? []).filter((recipient) => recipient !== email),
        }));
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 overflow-y-auto">
            {/* Backdrop */}
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="relative min-h-screen flex items-center justify-center p-4">
                <div className={cn(modalStyles.container, 'w-full max-w-2xl')}>
                    {/* Header */}
                    <div className="flex items-center justify-between mb-4">
                        <h2 className={textStyles.h2}>
                            {isEditing ? 'Edit Alert Rule' : 'Create Alert Rule'}
                        </h2>
                        <button onClick={onClose} className={modalStyles.closeButton}>
                            <X className="h-5 w-5" />
                        </button>
                    </div>

                    {/* Form */}
                    <form onSubmit={handleSubmit}>
                        <div className="space-y-6">
                            {/* Basic Info */}
                            <div className="space-y-4">
                                <h3 className={textStyles.h4}>Basic Information</h3>

                                <div>
                                    <label className={cn(textStyles.label, 'block mb-1')}>Rule Name *</label>
                                    <input
                                        type="text"
                                        value={formData.name}
                                        onChange={(e) => handleChange('name', e.target.value)}
                                        placeholder="e.g., High CPU Alert"
                                        className={inputStyles.base}
                                        required
                                    />
                                </div>

                                <div>
                                    <label className={cn(textStyles.label, 'block mb-1')}>Description</label>
                                    <textarea
                                        value={formData.description}
                                        onChange={(e) => handleChange('description', e.target.value)}
                                        placeholder="Describe when this alert should trigger..."
                                        className={cn(inputStyles.base, 'h-20 resize-none')}
                                    />
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Severity *</label>
                                        <select
                                            value={formData.severity}
                                            onChange={(e) => handleChange('severity', e.target.value)}
                                            className={inputStyles.select}
                                        >
                                            <option value="critical">Critical</option>
                                            <option value="warning">Warning</option>
                                            <option value="info">Info</option>
                                        </select>
                                    </div>
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Status</label>
                                        <label className="flex items-center gap-2 mt-2">
                                            <input
                                                type="checkbox"
                                                checked={formData.enabled}
                                                onChange={(e) => handleChange('enabled', e.target.checked)}
                                                className="w-4 h-4 rounded border-gray-300"
                                            />
                                            <span className={textStyles.body}>Enabled</span>
                                        </label>
                                    </div>
                                </div>
                            </div>

                            {/* Condition */}
                            <div className="space-y-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                                <h3 className={textStyles.h4}>Condition</h3>

                                <div>
                                    <label className={cn(textStyles.label, 'block mb-1')}>Metric *</label>
                                    <select
                                        value={formData.metric}
                                        onChange={(e) => handleChange('metric', e.target.value)}
                                        className={inputStyles.select}
                                        required
                                    >
                                        <option value="">Select a metric...</option>
                                        {metricOptions.map((opt) => (
                                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                                        ))}
                                    </select>
                                </div>

                                <div className="grid grid-cols-3 gap-4">
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Operator</label>
                                        <select
                                            value={formData.operator}
                                            onChange={(e) => handleChange('operator', e.target.value)}
                                            className={inputStyles.select}
                                        >
                                            {operatorOptions.map((opt) => (
                                                <option key={opt.value} value={opt.value}>{opt.label}</option>
                                            ))}
                                        </select>
                                    </div>
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Threshold</label>
                                        <input
                                            type="number"
                                            value={formData.threshold}
                                            onChange={(e) => handleChange('threshold', parseFloat(e.target.value))}
                                            className={inputStyles.base}
                                        />
                                    </div>
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Duration (min)</label>
                                        <input
                                            type="number"
                                            value={formData.duration}
                                            onChange={(e) => handleChange('duration', parseInt(e.target.value))}
                                            min={1}
                                            className={inputStyles.base}
                                        />
                                    </div>
                                </div>

                                <p className={textStyles.xs}>
                                    Alert triggers when {formData.metric || '[metric]'} is {' '}
                                    {operatorOptions.find((o) => o.value === formData.operator)?.label.toLowerCase() || ''} {' '}
                                    {formData.threshold} for {formData.duration} minutes.
                                </p>
                            </div>

                            {/* Notifications */}
                            <div className="space-y-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                                <h3 className={textStyles.h4}>Notifications</h3>

                                <div>
                                    <label className={cn(textStyles.label, 'block mb-2')}>Channels</label>
                                    <div className="flex flex-wrap gap-2">
                                        {[
                                            { id: 'email' as const, label: 'Email', icon: <Mail className="h-4 w-4" /> },
                                            { id: 'slack' as const, label: 'Slack', icon: <MessageSquare className="h-4 w-4" /> },
                                            { id: 'webhook' as const, label: 'Webhook', icon: <Bell className="h-4 w-4" /> },
                                        ].map((channel) => (
                                            <button
                                                key={channel.id}
                                                type="button"
                                                onClick={() => handleToggleChannel(channel.id)}
                                                className={cn(
                                                    'flex items-center gap-2 px-3 py-2 rounded-lg border transition-colors',
                                                    formData.channels.includes(channel.id)
                                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                                                        : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
                                                )}
                                            >
                                                {channel.icon}
                                                <span className="text-sm">{channel.label}</span>
                                            </button>
                                        ))}
                                    </div>
                                </div>

                                {formData.channels.includes('email') && (
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Email Recipients</label>
                                        <div className="flex gap-2 mb-2">
                                            <input
                                                type="email"
                                                value={newRecipient}
                                                onChange={(e) => setNewRecipient(e.target.value)}
                                                placeholder="email@example.com"
                                                className={cn(inputStyles.base, 'flex-1')}
                                                onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddRecipient())}
                                            />
                                            <button
                                                type="button"
                                                onClick={handleAddRecipient}
                                                className={cn(buttonStyles.secondary, 'px-3')}
                                            >
                                                <Plus className="h-4 w-4" />
                                            </button>
                                        </div>
                                        <div className="flex flex-wrap gap-2">
                                            {formData.recipients?.map((email) => (
                                                <span
                                                    key={email}
                                                    className="flex items-center gap-1 px-2 py-1 bg-gray-100 dark:bg-gray-700 rounded text-sm"
                                                >
                                                    {email}
                                                    <button
                                                        type="button"
                                                        onClick={() => handleRemoveRecipient(email)}
                                                        className="p-0.5 hover:text-red-500"
                                                    >
                                                        <X className="h-3 w-3" />
                                                    </button>
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {formData.channels.includes('webhook') && (
                                    <div>
                                        <label className={cn(textStyles.label, 'block mb-1')}>Webhook URL</label>
                                        <input
                                            type="url"
                                            value={formData.webhookUrl || ''}
                                            onChange={(e) => handleChange('webhookUrl', e.target.value)}
                                            placeholder="https://..."
                                            className={inputStyles.base}
                                        />
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Footer */}
                        <div className="flex items-center justify-end gap-3 pt-4 mt-4 border-t border-gray-200 dark:border-gray-700">
                            <button type="button" onClick={onClose} className={buttonStyles.secondary}>
                                Cancel
                            </button>
                            <button type="submit" className={buttonStyles.primary}>
                                {isEditing ? 'Save Changes' : 'Create Rule'}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}

export default AlertRuleForm;
