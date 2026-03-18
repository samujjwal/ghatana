import * as React from 'react';

export function useMergeRefs<T>(...refs: Array<React.Ref<T> | undefined>): React.RefCallback<T> {
    return React.useCallback(
        (value: T) => {
            for (const ref of refs) {
                if (!ref) continue;
                if (typeof ref === 'function') {
                    ref(value);
                } else {
                    try {
                        (ref as React.MutableRefObject<T | null>).current = value;
                    } catch {
                        // ignore readonly refs
                    }
                }
            }
        },
        [refs],
    );
}
