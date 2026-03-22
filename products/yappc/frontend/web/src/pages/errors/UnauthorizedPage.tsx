/**
 * Unauthorized Page (403)
 *
 * @description Page displayed when user lacks permission.
 */

import React from 'react';
import { NavLink } from 'react-router';
import { motion } from 'framer-motion';
import { ShieldOff, Home, ArrowLeft, Lock, Mail } from 'lucide-react';

import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

const UnauthorizedPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center max-w-md"
      >
        {/* Icon */}
        <div className="mb-8">
          <div className="w-24 h-24 rounded-full bg-amber-500/10 flex items-center justify-center mx-auto">
            <ShieldOff className="w-12 h-12 text-amber-400" />
          </div>
        </div>

        {/* Message */}
        <h1 className="text-2xl font-bold text-white mb-4">Access Denied</h1>
        <p className="text-zinc-400 mb-8">
          You don't have permission to access this page. If you believe this is a mistake, please
          contact your administrator.
        </p>

        {/* Info Box */}
        <div className="p-4 rounded-lg bg-zinc-900 border border-zinc-800 mb-8 text-left">
          <div className="flex items-start gap-3">
            <Lock className="w-5 h-5 text-zinc-500 mt-0.5" />
            <div>
              <div className="text-sm font-medium text-white">Why am I seeing this?</div>
              <ul className="mt-2 text-sm text-zinc-400 space-y-1">
                <li>• Your role may not have access to this feature</li>
                <li>• The resource may require elevated permissions</li>
                <li>• Your session may have expired</li>
              </ul>
            </div>
          </div>
        </div>

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
          <button
            onClick={() => window.history.back()}
            className={cn(
              'flex items-center gap-2 px-6 py-3 rounded-lg font-medium',
              'bg-zinc-800 text-zinc-300 hover:bg-zinc-700 transition-colors'
            )}
          >
            <ArrowLeft className="w-4 h-4" />
            Go Back
          </button>
        </div>

        {/* Contact Admin */}
        <div className="mt-8">
          <a
            href="mailto:admin@example.com"
            className="inline-flex items-center gap-2 text-sm text-zinc-500 hover:text-zinc-300 transition-colors"
          >
            <Mail className="w-4 h-4" />
            Contact Administrator
          </a>
        </div>
      </motion.div>
    </div>
  );
};

export default UnauthorizedPage;
