/**
 * Error Page
 *
 * @description Generic error page for unexpected errors.
 */

import React from 'react';
import { NavLink, useRouteError } from 'react-router';
import { motion } from 'framer-motion';
import { Home, RefreshCw, AlertTriangle, Copy, ChevronDown, ChevronUp } from 'lucide-react';

import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

const ErrorPage: React.FC = () => {
  const error = useRouteError() as Error | null;
  const [showDetails, setShowDetails] = React.useState(false);
  const [copied, setCopied] = React.useState(false);

  const handleCopyError = async () => {
    if (error) {
      await navigator.clipboard.writeText(
        `Error: ${error.message}\n\nStack:\n${error.stack || 'No stack trace available'}`
      );
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center max-w-lg"
      >
        {/* Error Icon */}
        <div className="mb-8">
          <div className="w-24 h-24 rounded-full bg-red-500/10 flex items-center justify-center mx-auto">
            <AlertTriangle className="w-12 h-12 text-red-400" />
          </div>
        </div>

        {/* Message */}
        <h1 className="text-2xl font-bold text-white mb-4">Something Went Wrong</h1>
        <p className="text-zinc-400 mb-8">
          An unexpected error occurred. Our team has been notified and is working on a fix.
        </p>

        {/* Error Details (collapsible) */}
        {error && (
          <div className="mb-8 text-left">
            <button
              onClick={() => setShowDetails(!showDetails)}
              className="flex items-center gap-2 text-sm text-zinc-500 hover:text-zinc-300 transition-colors w-full justify-center"
            >
              {showDetails ? (
                <ChevronUp className="w-4 h-4" />
              ) : (
                <ChevronDown className="w-4 h-4" />
              )}
              {showDetails ? 'Hide' : 'Show'} Error Details
            </button>

            {showDetails && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="mt-4 p-4 rounded-lg bg-zinc-900 border border-zinc-800"
              >
                <div className="flex items-start justify-between mb-2">
                  <span className="text-xs text-zinc-500 uppercase tracking-wider">
                    Error Message
                  </span>
                  <button
                    onClick={handleCopyError}
                    className="text-xs text-violet-400 hover:text-violet-300 flex items-center gap-1"
                  >
                    <Copy className="w-3 h-3" />
                    {copied ? 'Copied!' : 'Copy'}
                  </button>
                </div>
                <p className="text-sm text-red-400 font-mono mb-4">{error.message}</p>

                {error.stack && (
                  <>
                    <span className="text-xs text-zinc-500 uppercase tracking-wider">
                      Stack Trace
                    </span>
                    <pre className="mt-2 text-xs text-zinc-500 overflow-x-auto max-h-40 overflow-y-auto font-mono whitespace-pre-wrap">
                      {error.stack}
                    </pre>
                  </>
                )}
              </motion.div>
            )}
          </div>
        )}

        {/* Actions */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <button
            onClick={() => window.location.reload()}
            className={cn(
              'flex items-center gap-2 px-6 py-3 rounded-lg font-medium',
              'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
            )}
          >
            <RefreshCw className="w-4 h-4" />
            Refresh Page
          </button>
          <NavLink
            to={ROUTES.DASHBOARD}
            className={cn(
              'flex items-center gap-2 px-6 py-3 rounded-lg font-medium',
              'bg-zinc-800 text-zinc-300 hover:bg-zinc-700 transition-colors'
            )}
          >
            <Home className="w-4 h-4" />
            Go to Dashboard
          </NavLink>
        </div>

        {/* Support */}
        <div className="mt-8 text-sm text-zinc-500">
          <p>
            If this problem persists, please{' '}
            <a href="/support" className="text-violet-400 hover:underline">
              contact support
            </a>
          </p>
        </div>
      </motion.div>
    </div>
  );
};

export default ErrorPage;
