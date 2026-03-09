/**
 * DraggableBox Component
 *
 * A container that can be dragged around the canvas via mouse events.
 * Used for floating toolbar positioning.
 *
 * @doc.type component
 * @doc.purpose Draggable container for floating UI elements
 * @doc.layer product
 * @doc.pattern UI Component
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { Box } from '@ghatana/ui';

interface DraggableBoxProps {
  children: React.ReactNode;
  initialX?: number;
  initialY?: number;
  sx?: Record<string, unknown>;
}

export const DraggableBox = ({
  children,
  initialX = 0,
  initialY = 0,
  sx = {},
}: DraggableBoxProps) => {
  const [position, setPosition] = useState({ x: initialX, y: initialY });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartRef = useRef<{ x: number; y: number } | null>(null);
  const startPosRef = useRef<{ x: number; y: number }>({
    x: initialX,
    y: initialY,
  });

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (e.button !== 0) return;

      const target = e.target as HTMLElement;
      if (
        target.tagName === 'BUTTON' ||
        target.closest('button') ||
        target.tagName === 'INPUT' ||
        target.closest('.MuiSlider-root')
      ) {
        return;
      }

      setIsDragging(true);
      dragStartRef.current = { x: e.clientX, y: e.clientY };
      startPosRef.current = { ...position };
    },
    [position]
  );

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging || !dragStartRef.current) return;

      const dx = e.clientX - dragStartRef.current.x;
      const dy = e.clientY - dragStartRef.current.y;

      setPosition({
        x: startPosRef.current.x + dx,
        y: startPosRef.current.y + dy,
      });
    };

    const handleMouseUp = () => {
      setIsDragging(false);
      dragStartRef.current = null;
    };

    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isDragging]);

  return (
    <Box
      onMouseDown={handleMouseDown}
      style={{
        cursor: isDragging ? 'grabbing' : 'grab',
        transform: `translate(${position.x}px, ${position.y}px)`,
        ...sx,
      }}
    >
      {children}
    </Box>
  );
};
