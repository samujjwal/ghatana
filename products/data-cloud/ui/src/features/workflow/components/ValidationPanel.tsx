import React from 'react';

/**
 * Minimal ValidationPanel component stub to satisfy imports during type-check.
 * Full implementation should render validation errors and auto-fix actions.
 */
export function ValidationPanel(props: { errors?: unknown[]; onErrorClick?: (e: unknown)=>void; onAutoFix?: (id:string)=>void }) {
  const { errors = [] } = props;
  return (
    <div className="p-2">
      <h4 className="text-sm font-medium">Validation</h4>
      {errors.length === 0 ? (
        <div className="text-xs text-gray-500">No validation issues</div>
      ) : (
        <ul className="text-xs list-disc pl-4">
          {errors.map((e, i) => (
            <li key={i}>{String(e)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default ValidationPanel;

