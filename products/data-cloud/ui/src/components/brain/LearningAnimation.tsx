/**
 * Learning Animation Component
 *
 * Visualizes learning signals being processed by the AI Brain.
 * Part of Journey 3: Teaching the Brain (Feedback & Learning)
 *
 * @doc.type component
 * @doc.purpose Learning signal visualization
 * @doc.layer frontend
 */

import React, { useEffect, useState } from 'react';
import { Brain, Zap, CheckCircle } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { brainService, LearningSignal } from '../../api/brain.service';

interface LearningAnimationProps {
  signalId?: string;
  autoClose?: boolean;
  duration?: number;
  onComplete?: () => void;
}

export function LearningAnimation({
  signalId,
  autoClose = true,
  duration = 3000,
  onComplete,
}: LearningAnimationProps) {
  const [phase, setPhase] = useState<'sending' | 'processing' | 'complete'>('sending');
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    // Phase 1: Sending (0-1s)
    const sendingTimer = setTimeout(() => {
      setPhase('processing');
    }, 1000);

    // Phase 2: Processing (1-2.5s)
    const processingTimer = setTimeout(() => {
      setPhase('complete');
    }, 2500);

    // Phase 3: Complete (2.5-3s)
    const completeTimer = setTimeout(() => {
      if (autoClose) {
        onComplete?.();
      }
    }, duration);

    // Progress animation
    const progressInterval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) return 100;
        return prev + (100 / (duration / 50));
      });
    }, 50);

    return () => {
      clearTimeout(sendingTimer);
      clearTimeout(processingTimer);
      clearTimeout(completeTimer);
      clearInterval(progressInterval);
    };
  }, [autoClose, duration, onComplete]);

  return (
    <div className="fixed bottom-8 right-8 z-50">
      <div className="bg-white rounded-lg shadow-2xl border border-gray-200 p-6 min-w-[320px]">
        {/* Header */}
        <div className="flex items-center gap-3 mb-4">
          <div className="relative">
            <div
              className={`w-12 h-12 rounded-full flex items-center justify-center transition-all duration-500 ${
                phase === 'sending'
                  ? 'bg-blue-100'
                  : phase === 'processing'
                  ? 'bg-purple-100 animate-pulse'
                  : 'bg-green-100'
              }`}
            >
              {phase === 'complete' ? (
                <CheckCircle className="h-6 w-6 text-green-600" />
              ) : (
                <Brain
                  className={`h-6 w-6 ${
                    phase === 'sending'
                      ? 'text-blue-600'
                      : 'text-purple-600'
                  }`}
                />
              )}
            </div>

            {/* Particle animation during sending */}
            {phase === 'sending' && (
              <div className="absolute inset-0">
                {[...Array(3)].map((_, i) => (
                  <div
                    key={i}
                    className="absolute w-2 h-2 bg-blue-500 rounded-full animate-ping"
                    style={{
                      animationDelay: `${i * 0.3}s`,
                      top: '50%',
                      left: '50%',
                    }}
                  />
                ))}
              </div>
            )}
          </div>

          <div className="flex-1">
            <h3 className="text-sm font-semibold text-gray-900">
              {phase === 'sending' && 'Sending Learning Signal...'}
              {phase === 'processing' && 'Processing Feedback...'}
              {phase === 'complete' && 'Learning Complete!'}
            </h3>
            <p className="text-xs text-gray-600">
              {phase === 'sending' && 'Transmitting to Brain'}
              {phase === 'processing' && 'Updating neural weights'}
              {phase === 'complete' && 'System has learned from your feedback'}
            </p>
          </div>
        </div>

        {/* Progress Bar */}
        <div className="mb-4">
          <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className={`h-full transition-all duration-300 ${
                phase === 'complete' ? 'bg-green-500' : 'bg-blue-500'
              }`}
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
        </div>

        {/* Details */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="text-gray-600">Reflex Adjustment</span>
            <span
              className={`font-semibold ${
                phase === 'complete' ? 'text-green-600' : 'text-gray-900'
              }`}
            >
              {phase === 'complete' ? 'Applied' : 'Pending'}
            </span>
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-gray-600">Pattern Recognition</span>
            <span
              className={`font-semibold ${
                phase === 'complete' ? 'text-green-600' : 'text-gray-900'
              }`}
            >
              {phase === 'complete' ? 'Updated' : 'Processing'}
            </span>
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-gray-600">Confidence Score</span>
            <span
              className={`font-semibold ${
                phase === 'complete' ? 'text-green-600' : 'text-gray-900'
              }`}
            >
              {phase === 'complete' ? 'Adjusted' : 'Calculating'}
            </span>
          </div>
        </div>

        {/* Energy particles for processing phase */}
        {phase === 'processing' && (
          <div className="absolute inset-0 pointer-events-none overflow-hidden rounded-lg">
            {[...Array(8)].map((_, i) => (
              <Zap
                key={i}
                className="absolute text-purple-400 animate-pulse"
                style={{
                  width: '12px',
                  height: '12px',
                  top: `${Math.random() * 100}%`,
                  left: `${Math.random() * 100}%`,
                  animationDelay: `${Math.random() * 1}s`,
                  opacity: 0.3,
                }}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Learning Signals List Component
 *
 * Displays recent learning signals with their status
 */
export function LearningSignalsList({ limit = 10 }: { limit?: number }) {
  const { data: signals, isLoading } = useQuery({
    queryKey: ['learning-signals', limit],
    queryFn: () => brainService.getLearningSignals(limit),
    refetchInterval: 10000,
  });

  if (isLoading) {
    return (
      <div className="space-y-2">
        {[1, 2, 3].map((i) => (
          <div key={i} className="h-16 bg-gray-200 rounded animate-pulse" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {signals?.map((signal) => (
        <div
          key={signal.id}
          className="flex items-center gap-3 p-3 bg-white border border-gray-200 rounded-lg hover:border-primary-300 transition-colors"
        >
          <div
            className={`w-2 h-2 rounded-full ${
              signal.status === 'APPLIED'
                ? 'bg-green-500'
                : signal.status === 'PROCESSED'
                ? 'bg-blue-500'
                : 'bg-yellow-500'
            }`}
          />
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-900">
                {signal.signalType}
              </span>
              <span
                className={`px-2 py-0.5 rounded text-xs font-semibold ${
                  signal.status === 'APPLIED'
                    ? 'bg-green-100 text-green-700'
                    : signal.status === 'PROCESSED'
                    ? 'bg-blue-100 text-blue-700'
                    : 'bg-yellow-100 text-yellow-700'
                }`}
              >
                {signal.status}
              </span>
            </div>
            <div className="text-xs text-gray-500 mt-1">
              Impact: {(signal.impact * 100).toFixed(1)}% •
              {signal.affectedComponents.length} components affected
            </div>
          </div>
          <div className="text-xs text-gray-500">
            {new Date(signal.timestamp).toLocaleTimeString()}
          </div>
        </div>
      ))}

      {(!signals || signals.length === 0) && (
        <div className="text-center py-8 text-gray-500">
          <Brain className="h-12 w-12 mx-auto mb-2 opacity-50" />
          <p>No learning signals yet</p>
        </div>
      )}
    </div>
  );
}

export default LearningAnimation;

