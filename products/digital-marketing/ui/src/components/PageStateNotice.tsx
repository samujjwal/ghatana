/**
 * Shared page-level state notice.
 *
 * @doc.type component
 * @doc.purpose Reusable loading/error/empty/warning page state primitive
 * @doc.layer frontend
 */
import React from 'react';

export type PageStateTone = 'loading' | 'error' | 'empty' | 'warning';

interface PageStateNoticeProps {
  testId: string;
  tone: PageStateTone;
  message: string;
}

function stateClasses(tone: PageStateTone): string {
  if (tone === 'error') {
    return 'text-sm text-red-600';
  }
  if (tone === 'warning') {
    return 'text-sm text-yellow-700 bg-yellow-50 px-3 py-2 rounded';
  }
  if (tone === 'empty') {
    return 'text-sm text-gray-500';
  }
  return 'text-sm text-gray-600';
}

export function PageStateNotice({
  testId,
  tone,
  message,
}: PageStateNoticeProps): React.ReactElement {
  return (
    <p
      data-testid={testId}
      role={tone === 'error' || tone === 'warning' ? 'alert' : undefined}
      className={stateClasses(tone)}
    >
      {message}
    </p>
  );
}
