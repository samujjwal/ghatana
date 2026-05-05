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
    <div className={`w-full bg-surface-muted rounded-full overflow-hidden ${height}`}>
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
  if (percentage >= 90) return 'bg-success-bg';
  if (percentage >= 70) return 'bg-warning-bg';
  return 'bg-destructive-bg';
}
