import type * as React from 'react';

export type SxProps = Record<string, unknown>;

const SPACING_UNIT_PX = 8;

const toPx = (value: unknown): string | undefined => {
    if (value === null || value === undefined) return undefined;
    if (typeof value === 'number') return `${value * SPACING_UNIT_PX}px`;
    if (typeof value === 'string') return value;
    return undefined;
};

/**
 * Minimal MUI-like `sx` support.
 *
 * Supports spacing shorthands commonly used in app code: p/px/py/pt/pr/pb/pl and m/mx/my/mt/mr/mb/ml.
 * Numeric values map to `value * 8px`.
 */
export function sxToStyle(sx?: unknown): React.CSSProperties | undefined {
    if (!sx || typeof sx !== 'object') return undefined;

    const record = sx as SxProps;
    const style: React.CSSProperties = {};

    const mapSpacing = (key: string, cssKey: keyof React.CSSProperties) => {
        const px = toPx(record[key]);
        if (px !== undefined) {
            (style as Record<string, string>)[cssKey] = px;
        }
    };

    mapSpacing('p', 'padding');
    mapSpacing('px', 'paddingInline');
    mapSpacing('py', 'paddingBlock');
    mapSpacing('pt', 'paddingTop');
    mapSpacing('pr', 'paddingRight');
    mapSpacing('pb', 'paddingBottom');
    mapSpacing('pl', 'paddingLeft');

    mapSpacing('m', 'margin');
    mapSpacing('mx', 'marginInline');
    mapSpacing('my', 'marginBlock');
    mapSpacing('mt', 'marginTop');
    mapSpacing('mr', 'marginRight');
    mapSpacing('mb', 'marginBottom');
    mapSpacing('ml', 'marginLeft');

    return Object.keys(style).length > 0 ? style : undefined;
}
