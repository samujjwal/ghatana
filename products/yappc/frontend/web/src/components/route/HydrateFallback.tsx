/**
 * HydrateFallback Component
 *
 * Provides better UX during React Router module loading and clientLoader execution.
 * Displays a sophisticated loading state with animations and context-aware messaging.
 *
 * @doc.type class
 * @doc.purpose Loading UI during route module hydration
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';

/**
 * HydrateFallback - Displayed while loading route modules
 *
 * This replaces the generic "Loading application..." message with a
 * polished loading experience that provides visual feedback to users.
 */
export const HydrateFallback: React.FC = () => {
  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden">
      {/* Animated background gradient */}
      <div
        className="absolute inset-0"
        style={{
          background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
        }}
      />
      <div
        className="absolute inset-0"
        style={{
          background:
            'radial-gradient(circle at 20% 50%, rgba(59,130,246,0.14) 0%, transparent 55%), radial-gradient(circle at 80% 80%, rgba(147,197,253,0.16) 0%, transparent 55%)',
          animation: 'pulse 4s ease-in-out infinite',
        }}
      />

      {/* Loading content */}
      <div className="relative z-[1] flex flex-col items-center gap-6">
        {/* Animated spinner */}
        <div className="relative flex h-[80px] w-[80px] items-center justify-center">
          {/* Outer rotating ring */}
          <div
            className="absolute inset-0 rounded-full border-[3px] border-solid border-transparent"
            style={{
              borderTopColor: '#3b82f6',
              animation: 'spin 2s linear infinite',
            }}
          />

          {/* Inner rotating ring (opposite direction) */}
          <div
            className="absolute inset-[8px] rounded-full border-[2px] border-solid border-transparent"
            style={{
              borderBottomColor: '#93c5fd',
              animation: 'spin 3s linear reverse infinite',
            }}
          />

          {/* Center icon */}
          <div
            className="text-blue-600 opacity-[0.4]"
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '9999px',
              border: '4px solid rgba(59,130,246,0.15)',
              borderTopColor: '#2563eb',
              animation: 'spin 1s linear infinite',
            }}
          />
        </div>

        {/* Loading text */}
        <div className="flex flex-col items-center gap-1">
          <h1 className="font-semibold text-gray-900 dark:text-gray-100 tracking-wider text-xl">
            Preparing Canvas
          </h1>
          <p className="text-center text-sm text-gray-500 dark:text-gray-400 max-w-[300px] opacity-[0.8]">
            Loading modules and initializing your workspace...
          </p>
        </div>

        {/* Loading dots animation */}
        <div className="mt-4 flex flex-row gap-2">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="h-[8px] w-[8px] rounded-full bg-blue-600"
              style={{
                animation: 'bounce 1.4s infinite ease-in-out',
                animationDelay: `${i * 0.16}s`,
              }}
            />
          ))}
        </div>
      </div>

      {/* CSS animations */}
      <style>
        {`
          @keyframes spin {
            from {
              transform: rotate(0deg);
            }
            to {
              transform: rotate(360deg);
            }
          }

          @keyframes pulse {
            0%, 100% {
              opacity: 0.5;
            }
            50% {
              opacity: 1;
            }
          }

          @keyframes bounce {
            0%, 80%, 100% {
              transform: scale(0);
              opacity: 0.5;
            }
            40% {
              transform: scale(1);
              opacity: 1;
            }
          }
        `}
      </style>
    </div>
  );
};

export default HydrateFallback;
