/**
 * Not Found Page (404)
 *
 * @description Page displayed when a route doesn't exist.
 */

import { NavLink } from 'react-router';
import { motion } from 'framer-motion';
import { Home, ArrowLeft, Search, HelpCircle } from 'lucide-react';

import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';
import { Button } from '../../components/ui/Button';

function NotFoundPage(): React.ReactElement {
  return (
    <div className="min-h-screen bg-surface flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center max-w-md"
      >
        {/* 404 Illustration */}
        <div className="relative mb-8">
          <div className="text-[12rem] font-bold leading-none bg-gradient-to-b from-zinc-700 to-zinc-900 bg-clip-text text-transparent select-none">
            404
          </div>
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-24 h-24 rounded-full bg-gradient-to-br from-violet-500/20 to-fuchsia-500/20 flex items-center justify-center">
              <Search className="w-12 h-12 text-violet-400" />
            </div>
          </div>
        </div>

        {/* Message */}
        <h1 className="text-2xl font-bold text-white mb-4">Page Not Found</h1>
        <p className="text-fg-muted mb-8">
          The page you're looking for doesn't exist or has been moved. Let's get you back on track.
        </p>

        {/* Actions */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <NavLink
            to={ROUTES.DASHBOARD}
            className={cn(
              'flex items-center gap-2 px-6 py-3 rounded-lg font-medium',
              'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
            )}
          >
            <Home className="w-4 h-4" />
            Go to Dashboard
          </NavLink>
          <Button
            onClick={() => window.history.back()}
            variant="ghost"
            className={cn(
              'flex items-center gap-2 px-6 py-3 rounded-lg font-medium',
              'bg-surface text-fg-muted hover:bg-surface-muted transition-colors'
            )}
          >
            <ArrowLeft className="w-4 h-4" />
            Go Back
          </Button>
        </div>

        {/* Help Link */}
        <div className="mt-8">
          <a
            href="/help"
            className="inline-flex items-center gap-2 text-sm text-fg-muted hover:text-fg-muted transition-colors"
          >
            <HelpCircle className="w-4 h-4" />
            Need help? Contact support
          </a>
        </div>
      </motion.div>
    </div>
  );
}

export default NotFoundPage;
