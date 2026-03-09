import { useAtomValue } from 'jotai';
import { useViewport } from '@xyflow/react';
import { alignmentGuidesAtom } from './workspace/canvasAtoms';

export const AlignmentGuides = () => {
    const guides = useAtomValue(alignmentGuidesAtom);
    const viewport = useViewport();

    if (guides.horizontal === null && guides.vertical === null) return null;

    const vPosition = guides.vertical !== null ? (guides.vertical * viewport.zoom) + viewport.x : null;
    const hPosition = guides.horizontal !== null ? (guides.horizontal * viewport.zoom) + viewport.y : null;

    return (
        <svg
            style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                pointerEvents: 'none',
                zIndex: 1000,
            }}
        >
            {vPosition !== null && (
                <line
                    x1={vPosition}
                    y1={0}
                    x2={vPosition}
                    y2="100%"
                    stroke="#FF007F"
                    strokeWidth={1}
                    strokeDasharray="4 4"
                />
            )}
            {hPosition !== null && (
                <line
                    x1={0}
                    y1={hPosition}
                    x2="100%"
                    y2={hPosition}
                    stroke="#FF007F"
                    strokeWidth={1}
                    strokeDasharray="4 4"
                />
            )}
        </svg>
    );
};