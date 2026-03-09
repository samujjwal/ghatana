/**
 * Progress bar component.
 *
 * Displays a horizontal bar where the filled portion represents the
 * percentage value between 0 and 100.
 */
export function ProgressBar({
  percentage,
  height = 'h-2',
}: {
  percentage: number;
  height?: string;
}) {
  const color = getProgressColor(percentage);

  return (
    <div className={`w-full bg-gray-200 rounded-full overflow-hidden ${height}`}>
      <div
        className={`h-full rounded-full transition-all duration-500 ${color}`}
        style={{ width: `${Math.min(100, Math.max(0, percentage))}%` }}
      />
    </div>
  );
}

/**
 * Get progress bar color.
 */
function getProgressColor(percentage: number): string {
  if (percentage >= 90) return 'bg-green-600';
  if (percentage >= 70) return 'bg-yellow-500';
  return 'bg-red-600';
}
