export type ValidationIssueSeverity = 'error' | 'warning' | 'info';

export interface ValidationIssue {
  id?: string;
  severity: ValidationIssueSeverity;
  message: string;
  field?: string;
}

export interface ValidationIssueListProps {
  issues: ValidationIssue[];
  className?: string;
}

export function ValidationIssueList({ issues, className }: ValidationIssueListProps) {
  if (!issues.length) return null;
  return (
    <ul className={className}>
      {issues.map((issue, i) => (
        <li key={issue.id ?? i}>{issue.message}</li>
      ))}
    </ul>
  );
}
