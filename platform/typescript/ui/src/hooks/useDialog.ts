/**
 * useDialog Hook for @ghatana/ui
 *
 * Unified dialog/modal state management hook.
 * Migrated from yappc/app-creator for shared use across all products.
 *
 * @doc.type hook
 * @doc.purpose Unified dialog state management
 * @doc.layer platform
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect, useRef } from 'react';

export interface UseDialogOptions<TData = unknown, TResult = unknown> {
    /** Callback when dialog is confirmed */
    onConfirm?: (data: TData) => void | Promise<TResult>;

    /** Callback when dialog is cancelled/closed */
    onCancel?: () => void;

    /** Initial open state */
    defaultOpen?: boolean;

    /** Whether to close on Escape key */
    closeOnEscape?: boolean;

    /** Whether to close on overlay/backdrop click */
    closeOnOverlayClick?: boolean;

    /** Whether to prevent closing while loading */
    preventCloseWhileLoading?: boolean;
}

export interface UseDialogReturn<TData = unknown, TResult = unknown> {
    isOpen: boolean;
    open: (data?: TData) => void;
    close: () => void;
    toggle: () => void;
    confirm: () => Promise<void>;
    cancel: () => void;
    data: TData | undefined;
    setData: (data: TData | ((prev: TData | undefined) => TData)) => void;
    isLoading: boolean;
    error: Error | null;
    result: TResult | null;
    props: {
        isOpen: boolean;
        onClose: () => void;
        isLoading: boolean;
        error: Error | null;
    };
}

export function useDialog<TData = unknown, TResult = unknown>(
    options: UseDialogOptions<TData, TResult> = {}
): UseDialogReturn<TData, TResult> {
    const {
        onConfirm,
        onCancel,
        defaultOpen = false,
        closeOnEscape = true,
        closeOnOverlayClick = true,
        preventCloseWhileLoading = true,
    } = options;

    const [isOpen, setIsOpen] = useState(defaultOpen);
    const [data, setData] = useState<TData | undefined>(undefined);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const [result, setResult] = useState<TResult | null>(null);

    const isMountedRef = useRef(true);
    useEffect(() => {
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    const open = useCallback((newData?: TData) => {
        setIsOpen(true);
        if (newData !== undefined) {
            setData(newData);
        }
        setError(null);
        setResult(null);
    }, []);

    const close = useCallback(() => {
        if (preventCloseWhileLoading && isLoading) {
            return;
        }
        setIsOpen(false);
        setTimeout(() => {
            if (isMountedRef.current) {
                setData(undefined);
                setError(null);
            }
        }, 300);
    }, [isLoading, preventCloseWhileLoading]);

    const toggle = useCallback(() => {
        if (isOpen) {
            close();
        } else {
            open();
        }
    }, [isOpen, open, close]);

    const confirm = useCallback(async () => {
        if (!onConfirm) {
            close();
            return;
        }

        setIsLoading(true);
        setError(null);

        try {
            const confirmResult = await onConfirm(data as TData);
            if (isMountedRef.current) {
                setResult((confirmResult as TResult) ?? null);
                close();
            }
        } catch (err) {
            if (isMountedRef.current) {
                setError(err instanceof Error ? err : new Error(String(err)));
            }
        } finally {
            if (isMountedRef.current) {
                setIsLoading(false);
            }
        }
    }, [data, onConfirm, close]);

    const cancel = useCallback(() => {
        onCancel?.();
        close();
    }, [onCancel, close]);

    // Keyboard handling
    useEffect(() => {
        if (!isOpen || !closeOnEscape) return;

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && !isLoading) {
                cancel();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, closeOnEscape, isLoading, cancel]);

    const props = {
        isOpen,
        onClose: closeOnOverlayClick ? close : () => { },
        isLoading,
        error,
    };

    return {
        isOpen,
        open,
        close,
        toggle,
        confirm,
        cancel,
        data,
        setData,
        isLoading,
        error,
        result,
        props,
    };
}
