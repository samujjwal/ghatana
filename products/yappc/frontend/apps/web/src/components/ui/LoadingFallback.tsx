import React from 'react';

/**
 * Loading fallback component.
 *
 * Simple centered spinner and label used as a generic loading state.
 */
export default function LoadingFallback() {
  return (
    <div style={{ padding: '2rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ marginBottom: '1rem' }}>
          <svg width="48" height="48" viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg" stroke="#1976d2">
            <g fill="none" fillRule="evenodd">
              <g transform="translate(1 1)" strokeWidth="2">
                <circle strokeOpacity="0.5" cx="18" cy="18" r="18"></circle>
                <path d="M36 18c0-9.94-8.06-18-18-18">
                  <animateTransform attributeName="transform" type="rotate" from="0 18 18" to="360 18 18" dur="1s" repeatCount="indefinite" />
                </path>
              </g>
            </g>
          </svg>
        </div>
        <div style={{ fontSize: '0.95rem', color: 'var(--text-secondary)' }}>Loading…</div>
      </div>
    </div>
  );
}
