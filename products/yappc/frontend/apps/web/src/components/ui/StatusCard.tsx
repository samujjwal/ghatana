import type React from 'react';

/**
 * Status card component.
 *
 * Displays an icon, a numeric count, and a label in a compact tile.
 */
export function StatusCard({
  icon,
  label,
  count,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  count: number;
  color: string;
}) {
  return (
    <div className="text-center">
      <div className="flex justify-center mb-2">{icon}</div>
      <div className={`text-2xl font-bold ${color}`}>{count}</div>
      <div className="text-xs text-gray-600">{label}</div>
    </div>
  );
}
