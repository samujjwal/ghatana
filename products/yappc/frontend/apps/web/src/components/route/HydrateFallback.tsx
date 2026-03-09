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
import {
  Box,
  Typography,
  Stack,
  Spinner as CircularProgress,
} from '@ghatana/ui';

/**
 * HydrateFallback - Displayed while loading route modules
 *
 * This replaces the generic "Loading application..." message with a
 * polished loading experience that provides visual feedback to users.
 */
export const HydrateFallback: React.FC = () => {
  return (
    <Box className="relative flex min-h-screen items-center justify-center overflow-hidden">
      {/* Animated background gradient */}
      <Box
        className="absolute inset-0"
        style={{
          background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
        }}
      />
      <Box
        className="absolute inset-0"
        style={{
          background:
            'radial-gradient(circle at 20% 50%, rgba(59,130,246,0.14) 0%, transparent 55%), radial-gradient(circle at 80% 80%, rgba(147,197,253,0.16) 0%, transparent 55%)',
          animation: 'pulse 4s ease-in-out infinite',
        }}
      />

      {/* Loading content */}
      <Stack
        spacing={3}
        alignItems="center"
        className="relative z-[1]"
      >
        {/* Animated spinner */}
        <Box className="relative flex h-[80px] w-[80px] items-center justify-center">
          {/* Outer rotating ring */}
          <Box
            className="absolute inset-0 rounded-full border-[3px] border-solid border-transparent"
            style={{
              borderTopColor: '#3b82f6',
              animation: 'spin 2s linear infinite',
            }}
          />

          {/* Inner rotating ring (opposite direction) */}
          <Box
            className="absolute inset-[8px] rounded-full border-[2px] border-solid border-transparent"
            style={{
              borderBottomColor: '#93c5fd',
              animation: 'spin 3s linear reverse infinite',
            }}
          />

          {/* Center icon */}
          <CircularProgress
            size={40}
            thickness={4}
            className="text-blue-600 opacity-[0.4]"
          />
        </Box>

        {/* Loading text */}
        <Stack spacing={1} alignItems="center">
          <Typography
            variant="h6"
            className="font-semibold text-gray-900 dark:text-gray-100 tracking-wider" >
            Preparing Canvas
          </Typography>
          <Typography
            variant="body2"
            className="text-center text-sm text-gray-500 dark:text-gray-400 max-w-[300px] opacity-[0.8]"
          >
            Loading modules and initializing your workspace...
          </Typography>
        </Stack>

        {/* Loading dots animation */}
        <Stack
          direction="row"
          spacing={1}
          className="mt-4"
        >
          {[0, 1, 2].map((i) => (
            <Box
              key={i}
              className="h-[8px] w-[8px] rounded-full bg-blue-600"
              style={{
                animation: 'bounce 1.4s infinite ease-in-out',
                animationDelay: `${i * 0.16}s`,
              }}
            />
          ))}
        </Stack>
      </Stack>

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
    </Box>
  );
};

export default HydrateFallback;
