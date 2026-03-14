import * as React from 'react';
import * as ReactDOM from 'react-dom';

import { sxToStyle, type SxProps } from '../utils/sx';

export type PopperPlacement =
    | 'bottom'
    | 'bottom-start'
    | 'bottom-end'
    | 'top'
    | 'top-start'
    | 'top-end'
    | 'left'
    | 'left-start'
    | 'left-end'
    | 'right'
    | 'right-start'
    | 'right-end';

export interface PopperRenderProps {
    placement: PopperPlacement;
}

export interface PopperProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'children'> {
    open?: boolean;
    anchorEl?: HTMLElement | null | (() => HTMLElement | null);
    placement?: PopperPlacement;
    disablePortal?: boolean;
    sx?: SxProps;
    children?: React.ReactNode | ((props: PopperRenderProps) => React.ReactNode);
}

function resolveAnchor(anchorEl: PopperProps['anchorEl']): HTMLElement | null {
    if (typeof anchorEl === 'function') return anchorEl();
    return anchorEl ?? null;
}

function computePosition(anchor: DOMRect, placement: PopperPlacement) {
    const scrollX = window.scrollX ?? 0;
    const scrollY = window.scrollY ?? 0;

    switch (placement) {
        case 'top':
            return { top: anchor.top + scrollY, left: anchor.left + scrollX };
        case 'top-start':
            return { top: anchor.top + scrollY, left: anchor.left + scrollX };
        case 'top-end':
            return { top: anchor.top + scrollY, left: anchor.right + scrollX };
        case 'bottom':
            return { top: anchor.bottom + scrollY, left: anchor.left + scrollX };
        case 'bottom-end':
            return { top: anchor.bottom + scrollY, left: anchor.right + scrollX };
        case 'left':
            return { top: anchor.top + scrollY, left: anchor.left + scrollX };
        case 'left-start':
            return { top: anchor.top + scrollY, left: anchor.left + scrollX };
        case 'left-end':
            return { top: anchor.bottom + scrollY, left: anchor.left + scrollX };
        case 'right':
            return { top: anchor.top + scrollY, left: anchor.right + scrollX };
        case 'right-start':
            return { top: anchor.top + scrollY, left: anchor.right + scrollX };
        case 'right-end':
            return { top: anchor.bottom + scrollY, left: anchor.right + scrollX };
        case 'bottom-start':
        default:
            return { top: anchor.bottom + scrollY, left: anchor.left + scrollX };
    }
}

export function Popper({
    open = false,
    anchorEl,
    placement = 'bottom-start',
    disablePortal = false,
    sx,
    style,
    children,
    ...rest
}: PopperProps) {
    const [coords, setCoords] = React.useState<{ top: number; left: number } | null>(null);

    React.useLayoutEffect(() => {
        if (!open) return;
        const anchor = resolveAnchor(anchorEl);
        if (!anchor) {
            setCoords(null);
            return;
        }

        const update = () => {
            const rect = anchor.getBoundingClientRect();
            setCoords(computePosition(rect, placement));
        };

        update();
        window.addEventListener('scroll', update, true);
        window.addEventListener('resize', update);
        return () => {
            window.removeEventListener('scroll', update, true);
            window.removeEventListener('resize', update);
        };
    }, [open, anchorEl, placement]);

    if (!open) return null;

    const renderedChildren =
        typeof children === 'function' ? (children as (p: PopperRenderProps) => React.ReactNode)({ placement }) : children;

    const content = (
        <div
            {...rest}
            style={{
                position: 'absolute',
                zIndex: 1300,
                top: coords?.top,
                left: coords?.left,
                ...style,
                ...sxToStyle(sx),
            }}
            data-popper
            data-placement={placement}
        >
            {renderedChildren}
        </div>
    );

    if (disablePortal) return content;
    return ReactDOM.createPortal(content, document.body);
}

Popper.displayName = 'Popper';
