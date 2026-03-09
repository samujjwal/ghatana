import { Box } from '@ghatana/ui';
import React from 'react';

import type { AlignmentGuide } from '../../../utils/spatialIndex';

/**
 *
 */
interface AlignmentGuidesProps {
  guides: AlignmentGuide[];
  canvasWidth: number;
  canvasHeight: number;
  viewport: { x: number; y: number; zoom: number };
}

export const AlignmentGuides: React.FC<AlignmentGuidesProps> = ({
  guides,
  canvasWidth,
  canvasHeight,
  viewport,
}) => {
  if (guides.length === 0) {
    return null;
  }

  return (
    <Box
      className="absolute w-full h-full pointer-events-none top-[0px] left-[0px] z-[1000]"
    >
      {guides.map((guide, index) => {
        if (guide.type === 'vertical') {
          const x = guide.position * viewport.zoom + viewport.x;
          
          return (
            <Box
              key={`${guide.type}-${guide.position}-${index}`}
              className="absolute top-[0px] w-[1px] h-full bg-[#ff4081] opacity-[0.8]" style={{ left: x }}
            />
          );
        } else {
          const y = guide.position * viewport.zoom + viewport.y;
          
          return (
            <Box
              key={`${guide.type}-${guide.position}-${index}`}
              className="absolute left-[0px] w-full h-[1px] bg-[#ff4081] opacity-[0.8]" style={{ top: y }}
            />
          );
        }
      })}
    </Box>
  );
};
