import React from 'react';
import { clsx } from 'clsx';

export interface AccordionItemProps {
    /**
     * Unique item ID
     */
    id: string;
    /**
     * Accordion item title
     */
    title: string;
    /**
     * Accordion item content
     */
    children: React.ReactNode;
    /**
     * Whether item is disabled
     */
    disabled?: boolean;
}

export interface AccordionProps {
    /**
     * Accordion items
     */
    items: AccordionItemProps[];
    /**
     * Allow multiple items to be open
     * @default false
     */
    allowMultiple?: boolean;
    /**
     * Initially expanded item IDs
     */
    defaultExpanded?: string[];
    /**
     * Controlled expanded item IDs
     */
    expanded?: string[];
    /**
     * Expansion change handler
     */
    onChange?: (expandedIds: string[]) => void;
    /**
     * Visual variant
     * @default 'default'
     */
    variant?: 'default' | 'bordered' | 'separated';
    /**
     * Additional CSS classes
     */
    className?: string;
}

/**
 * Accordion component for collapsible content sections.
 *
 * @example
 * ```tsx
 * <Accordion
 *   items={[
 *     { id: '1', title: 'Section 1', children: <p>Content 1</p> },
 *     { id: '2', title: 'Section 2', children: <p>Content 2</p> },
 *   ]}
 *   allowMultiple={false}
 *   variant="bordered"
 * />
 * ```
 */
export const Accordion: React.FC<AccordionProps> = ({
    items,
    allowMultiple = false,
    defaultExpanded = [],
    expanded: controlledExpanded,
    onChange,
    variant = 'default',
    className,
}) => {
    const [uncontrolledExpanded, setUncontrolledExpanded] =
        React.useState<string[]>(defaultExpanded);

    const expanded = controlledExpanded ?? uncontrolledExpanded;

    const handleToggle = (itemId: string) => {
        let newExpanded: string[];

        if (expanded.includes(itemId)) {
            // Close item
            newExpanded = expanded.filter((id) => id !== itemId);
        } else {
            // Open item
            newExpanded = allowMultiple ? [...expanded, itemId] : [itemId];
        }

        if (onChange) {
            onChange(newExpanded);
        } else {
            setUncontrolledExpanded(newExpanded);
        }
    };

    const variantClasses = {
        default: 'space-y-1',
        bordered: 'border border-gray-200 rounded-lg divide-y divide-gray-200',
        separated: 'space-y-3',
    };

    const itemVariantClasses = {
        default: 'bg-gray-50 hover:bg-gray-100 rounded-md',
        bordered: 'hover:bg-gray-50',
        separated: 'bg-white border border-gray-200 rounded-lg',
    };

    return (
        <div className={clsx(variantClasses[variant], className)}>
            {items.map((item) => {
                const isExpanded = expanded.includes(item.id);

                return (
                    <div key={item.id} className={clsx(variant === 'separated' && 'overflow-hidden')}>
                        <button
                            onClick={() => !item.disabled && handleToggle(item.id)}
                            disabled={item.disabled}
                            className={clsx(
                                'w-full flex items-center justify-between px-4 py-3 text-left transition-colors',
                                itemVariantClasses[variant],
                                item.disabled && 'opacity-50 cursor-not-allowed'
                            )}
                            aria-expanded={isExpanded}
                            aria-controls={`accordion-content-${item.id}`}
                            id={`accordion-button-${item.id}`}
                        >
                            <span className="font-medium text-gray-900">{item.title}</span>
                            <svg
                                className={clsx(
                                    'h-5 w-5 text-gray-500 transition-transform',
                                    isExpanded && 'rotate-180'
                                )}
                                fill="none"
                                viewBox="0 0 24 24"
                                stroke="currentColor"
                            >
                                <path
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                    strokeWidth={2}
                                    d="M19 9l-7 7-7-7"
                                />
                            </svg>
                        </button>
                        {isExpanded && (
                            <div
                                id={`accordion-content-${item.id}`}
                                role="region"
                                aria-labelledby={`accordion-button-${item.id}`}
                                className={clsx(
                                    'px-4 pb-3',
                                    variant === 'bordered' && 'pt-1'
                                )}
                            >
                                {item.children}
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
};

Accordion.displayName = 'Accordion';
