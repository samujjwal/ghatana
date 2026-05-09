/**
 * ADR Panel Component
 *
 * Architecture Decision Record editor with options comparison.
 * Supports AI-assisted pros/cons generation.
 *
 * @doc.type component
 * @doc.purpose SHAPE phase ADR editor
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useState, useCallback } from 'react';
import { Plus as Add, Minus as Remove, Sparkles as AutoAwesome, Save, ThumbsUp as ThumbUp, ThumbsDown as ThumbDown } from 'lucide-react';
import type { AdrPayload } from '@/shared/types/lifecycle-artifacts';
import { Button } from '../../ui/Button';
import { Input } from '../../ui/Input';
import { Textarea } from '../../ui/Textarea';
import { useI18n } from '../../../i18n/I18nProvider';

export interface AdrPanelProps {
    data?: AdrPayload;
    onSave: (data: AdrPayload) => Promise<void>;
    onAIAssist?: (context: { adr?: AdrPayload }) => Promise<Partial<AdrPayload> | null>;
    onClose: () => void;
    isLoading?: boolean;
}

interface Option {
    name: string;
    pros: string[];
    cons: string[];
}

const STATUS_COLORS = {
    proposed: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
    accepted: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
    superseded: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
    deprecated: 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted',
};

const defaultOption = (): Option => ({
    name: '',
    pros: [''],
    cons: [''],
});

const defaultData: AdrPayload = {
    context: '',
    decision: '',
    options: [defaultOption()],
    consequences: '',
    status: 'proposed',
};

/**
 * ADR Panel for SHAPE phase.
 */
export const AdrPanel: React.FC<AdrPanelProps> = ({
    data,
    onSave,
    onAIAssist,
    onClose,
    isLoading = false,
}) => {
    const { t } = useI18n();
    const [adr, setAdr] = useState<AdrPayload>({
        ...defaultData,
        ...data,
        options: data?.options?.length ? data.options : defaultData.options,
    });
    const [isSaving, setIsSaving] = useState(false);
    const [isAILoading, setIsAILoading] = useState(false);

    const updateField = useCallback(<K extends keyof AdrPayload>(field: K, value: AdrPayload[K]) => {
        setAdr((prev) => ({ ...prev, [field]: value }));
    }, []);

    const updateOption = useCallback((index: number, updates: Partial<Option>) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => (i === index ? { ...opt, ...updates } : opt)),
        }));
    }, []);

    const addOption = useCallback(() => {
        setAdr((prev) => ({
            ...prev,
            options: [...prev.options, defaultOption()],
        }));
    }, []);

    const removeOption = useCallback((index: number) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.filter((_, i) => i !== index),
        }));
    }, []);

    const updateProCon = useCallback((
        optionIndex: number,
        type: 'pros' | 'cons',
        itemIndex: number,
        value: string
    ) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => {
                if (i !== optionIndex) return opt;
                const items = [...opt[type]];
                items[itemIndex] = value;
                return { ...opt, [type]: items };
            }),
        }));
    }, []);

    const addProCon = useCallback((optionIndex: number, type: 'pros' | 'cons') => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => {
                if (i !== optionIndex) return opt;
                return { ...opt, [type]: [...opt[type], ''] };
            }),
        }));
    }, []);

    const removeProCon = useCallback((optionIndex: number, type: 'pros' | 'cons', itemIndex: number) => {
        setAdr((prev) => ({
            ...prev,
            options: prev.options.map((opt, i) => {
                if (i !== optionIndex) return opt;
                return { ...opt, [type]: opt[type].filter((_, j) => j !== itemIndex) };
            }),
        }));
    }, []);

    const handleAIAssist = useCallback(async () => {
        if (!onAIAssist) return;
        setIsAILoading(true);
        try {
            const result = await onAIAssist({ adr });
            if (result) {
                setAdr((prev) => ({
                    ...prev,
                    ...result,
                    options: result.options?.length ? result.options : prev.options,
                }));
            }
        } finally {
            setIsAILoading(false);
        }
    }, [onAIAssist, adr]);

    const handleSave = useCallback(async () => {
        setIsSaving(true);
        try {
            // Clean up empty items
            const cleanAdr: AdrPayload = {
                ...adr,
                options: adr.options
                    .filter((opt) => opt.name.trim())
                    .map((opt) => ({
                        ...opt,
                        pros: opt.pros.filter((p) => p.trim()),
                        cons: opt.cons.filter((c) => c.trim()),
                    })),
            };
            await onSave(cleanAdr);
        } finally {
            setIsSaving(false);
        }
    }, [adr, onSave]);

    return (
        <div className="flex flex-col h-full">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-divider">
                <div>
                    <h3 className="font-semibold text-text-primary">Architecture Decision Record</h3>
                    <p className="text-xs text-text-secondary">
                        Document key architectural decisions
                    </p>
                </div>
                <div className="flex gap-2">
                    {onAIAssist && (
                        <Button
                            onClick={handleAIAssist}
                            disabled={isAILoading || isSaving}
                            variant="ghost"
                            size="sm"
                            className="flex items-center gap-1 px-3 py-1.5 text-sm text-info-color hover:bg-info-bg dark:hover:bg-info-bg/20 rounded-lg transition-colors disabled:opacity-50"
                        >
                            <AutoAwesome className="w-4 h-4" />
                            {isAILoading ? 'Analyzing...' : 'Guided Assist'}
                        </Button>
                    )}
                    <Button
                        onClick={handleSave}
                        disabled={isSaving || isLoading}
                        variant="solid"
                        size="sm"
                        className="flex items-center gap-1 px-3 py-1.5 text-sm bg-info-color text-white rounded-lg hover:bg-info-color/90 transition-colors disabled:opacity-50"
                    >
                        <Save className="w-4 h-4" />
                        {isSaving ? 'Saving...' : 'Save'}
                    </Button>
                </div>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4 space-y-6">
                {/* Status */}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-2">Status</label>
                    <div className="flex gap-2">
                        {(['proposed', 'accepted', 'superseded', 'deprecated'] as const).map((status) => (
                            <Button
                                key={status}
                                onClick={() => updateField('status', status)}
                                variant="ghost"
                                size="sm"
                                className={`px-3 py-1.5 text-sm rounded-full transition-colors ${adr.status === status
                                        ? STATUS_COLORS[status]
                                        : 'bg-surface-muted text-fg-muted dark:bg-surface-muted dark:text-fg-muted hover:bg-surface-muted dark:hover:bg-surface-muted'
                                    }`}
                            >
                                {status.charAt(0).toUpperCase() + status.slice(1)}
                            </Button>
                        ))}
                    </div>
                </div>

                {/* Context */}
                <div>
                    <label
                        htmlFor="adr-context"
                        className="block text-sm font-medium text-text-primary mb-1"
                    >
                        Context
                    </label>
                    <Textarea
                        id="adr-context"
                        value={adr.context}
                        onChange={(e) => updateField('context', e.target.value)}
                        placeholder={t('canvas.adr.contextPlaceholder')}
                        rows={4}
                        fullWidth
                        resize="none"
                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-info-border resize-none"
                    />
                </div>

                {/* Options */}
                <div>
                    <label className="block text-sm font-medium text-text-primary mb-2">
                        Options Considered
                    </label>
                    <div className="space-y-4">
                        {adr.options.map((option, optIdx) => (
                            <div key={optIdx} className="border border-divider rounded-lg bg-bg-paper">
                                <div className="flex items-center gap-2 p-3 border-b border-divider">
                                    <span className="text-sm font-medium text-text-secondary">
                                        Option {optIdx + 1}
                                    </span>
                                    <Input
                                        type="text"
                                        value={option.name}
                                        onChange={(e) => updateOption(optIdx, { name: e.target.value })}
                                        placeholder={t('canvas.adr.optionNamePlaceholder')}
                                        fullWidth
                                        size="sm"
                                        className="flex-1 px-2 py-1 border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border"
                                    />
                                    {adr.options.length > 1 && (
                                        <Button
                                            onClick={() => removeOption(optIdx)}
                                            variant="ghost"
                                            size="sm"
                                            className="p-1 text-text-secondary hover:text-error-color transition-colors"
                                            aria-label={t('canvas.adr.removeOption')}
                                        >
                                            <Remove className="w-4 h-4" />
                                        </Button>
                                    )}
                                </div>
                                <div className="grid grid-cols-2 divide-x divide-divider">
                                    {/* Pros */}
                                    <div className="p-3 space-y-2">
                                        <div className="flex items-center gap-1 text-sm font-medium text-success-600 dark:text-success-400">
                                            <ThumbUp className="w-4 h-4" />
                                            Pros
                                        </div>
                                        {option.pros.map((pro, proIdx) => (
                                            <div key={proIdx} className="flex items-center gap-1">
                                                <Input
                                                    type="text"
                                                    value={pro}
                                                    onChange={(e) =>
                                                        updateProCon(optIdx, 'pros', proIdx, e.target.value)
                                                    }
                                                    placeholder={t('canvas.adr.proPlaceholder')}
                                                    fullWidth
                                                    size="sm"
                                                    className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border"
                                                />
                                                {option.pros.length > 1 && (
                                                    <Button
                                                        onClick={() => removeProCon(optIdx, 'pros', proIdx)}
                                                        variant="ghost"
                                                        size="sm"
                                                        aria-label={`${t('canvas.adr.removePro')} ${proIdx + 1}`}
                                                        className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                    >
                                                        <Remove className="w-3 h-3" />
                                                    </Button>
                                                )}
                                            </div>
                                        ))}
                                        <Button
                                            onClick={() => addProCon(optIdx, 'pros')}
                                            variant="link"
                                            size="sm"
                                            className="flex items-center gap-1 text-xs text-info-color hover:text-info-color transition-colors"
                                        >
                                            <Add className="w-3 h-3" /> Add pro
                                        </Button>
                                    </div>
                                    {/* Cons */}
                                    <div className="p-3 space-y-2">
                                        <div className="flex items-center gap-1 text-sm font-medium text-error-color">
                                            <ThumbDown className="w-4 h-4" />
                                            Cons
                                        </div>
                                        {option.cons.map((con, conIdx) => (
                                            <div key={conIdx} className="flex items-center gap-1">
                                                <Input
                                                    type="text"
                                                    value={con}
                                                    onChange={(e) =>
                                                        updateProCon(optIdx, 'cons', conIdx, e.target.value)
                                                    }
                                                    placeholder={t('canvas.adr.conPlaceholder')}
                                                    fullWidth
                                                    size="sm"
                                                    className="flex-1 px-2 py-1 text-sm border border-divider rounded bg-bg-default text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-1 focus:ring-info-border"
                                                />
                                                {option.cons.length > 1 && (
                                                    <Button
                                                        onClick={() => removeProCon(optIdx, 'cons', conIdx)}
                                                        variant="ghost"
                                                        size="sm"
                                                        aria-label={`${t('canvas.adr.removeCon')} ${conIdx + 1}`}
                                                        className="p-0.5 text-text-secondary hover:text-error-color transition-colors"
                                                    >
                                                        <Remove className="w-3 h-3" />
                                                    </Button>
                                                )}
                                            </div>
                                        ))}
                                        <Button
                                            onClick={() => addProCon(optIdx, 'cons')}
                                            variant="link"
                                            size="sm"
                                            className="flex items-center gap-1 text-xs text-info-color hover:text-info-color transition-colors"
                                        >
                                            <Add className="w-3 h-3" /> Add con
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        ))}
                        <Button
                            onClick={addOption}
                            variant="ghost"
                            size="sm"
                            fullWidth
                            className="w-full flex items-center justify-center gap-2 p-3 border-2 border-dashed border-divider rounded-lg text-text-secondary hover:text-info-color hover:border-info-border transition-colors"
                        >
                            <Add className="w-5 h-5" /> Add Option
                        </Button>
                    </div>
                </div>

                {/* Decision */}
                <div>
                    <label
                        htmlFor="adr-decision"
                        className="block text-sm font-medium text-text-primary mb-1"
                    >
                        Decision
                    </label>
                    <Textarea
                        id="adr-decision"
                        value={adr.decision}
                        onChange={(e) => updateField('decision', e.target.value)}
                        placeholder={t('canvas.adr.decisionPlaceholder')}
                        rows={4}
                        fullWidth
                        resize="none"
                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-info-border resize-none"
                    />
                </div>

                {/* Consequences */}
                <div>
                    <label
                        htmlFor="adr-consequences"
                        className="block text-sm font-medium text-text-primary mb-1"
                    >
                        Consequences
                    </label>
                    <Textarea
                        id="adr-consequences"
                        value={adr.consequences}
                        onChange={(e) => updateField('consequences', e.target.value)}
                        placeholder={t('canvas.adr.consequencesPlaceholder')}
                        rows={4}
                        fullWidth
                        resize="none"
                        className="w-full px-3 py-2 border border-divider rounded-lg bg-bg-paper text-text-primary placeholder:text-text-secondary focus:outline-none focus:ring-2 focus:ring-info-border resize-none"
                    />
                </div>
            </div>
        </div>
    );
};
