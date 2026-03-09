/**
 * Auth Layout
 *
 * @description Layout for authentication pages (login, register, etc.)
 * with branding and background effects.
 */

import React from 'react';
import { Outlet, NavLink } from 'react-router';
import { motion } from 'framer-motion';

import { cn } from '../utils/cn';
import { ROUTES } from '../router/paths';

// =============================================================================
// Auth Layout Component
// =============================================================================

const AuthLayout: React.FC = () => {
  return (
    <div className="min-h-screen bg-zinc-950 flex">
      {/* Left Panel - Branding */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden">
        {/* Gradient Background */}
        <div className="absolute inset-0 bg-gradient-to-br from-violet-900/30 via-zinc-950 to-fuchsia-900/30" />
        
        {/* Animated Grid */}
        <div className="absolute inset-0 opacity-20">
          <div
            className="absolute inset-0"
            style={{
              backgroundImage: `
                linear-gradient(rgba(139, 92, 246, 0.1) 1px, transparent 1px),
                linear-gradient(90deg, rgba(139, 92, 246, 0.1) 1px, transparent 1px)
              `,
              backgroundSize: '40px 40px',
            }}
          />
        </div>

        {/* Floating Orbs */}
        <motion.div
          className="absolute w-96 h-96 rounded-full bg-violet-500/20 blur-3xl"
          animate={{
            x: [0, 100, 0],
            y: [0, 50, 0],
          }}
          transition={{
            duration: 20,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
          style={{ top: '10%', left: '10%' }}
        />
        <motion.div
          className="absolute w-80 h-80 rounded-full bg-fuchsia-500/20 blur-3xl"
          animate={{
            x: [0, -80, 0],
            y: [0, 80, 0],
          }}
          transition={{
            duration: 25,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
          style={{ bottom: '10%', right: '10%' }}
        />

        {/* Content */}
        <div className="relative z-10 flex flex-col justify-between p-12 w-full">
          {/* Logo */}
          <NavLink to={ROUTES.HOME} className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center">
              <span className="text-white text-xl font-bold">Y</span>
            </div>
            <span className="text-2xl font-bold bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
              YAPPC
            </span>
          </NavLink>

          {/* Hero Content */}
          <div className="max-w-md">
            <h1 className="text-4xl font-bold text-white mb-6">
              Build Production-Ready Software at{' '}
              <span className="bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
                Lightning Speed
              </span>
            </h1>
            <p className="text-lg text-zinc-400 mb-8">
              YAPPC is your AI-powered project partner. From ideation to deployment,
              we guide you through the entire software development lifecycle.
            </p>

            {/* Feature List */}
            <div className="space-y-4">
              {[
                'AI-assisted project scaffolding',
                'Automated infrastructure setup',
                'Intelligent sprint planning',
                'Real-time monitoring & alerts',
                'Security scanning & compliance',
              ].map((feature, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="w-5 h-5 rounded-full bg-violet-500/20 flex items-center justify-center">
                    <svg
                      className="w-3 h-3 text-violet-400"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M5 13l4 4L19 7"
                      />
                    </svg>
                  </div>
                  <span className="text-zinc-300">{feature}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Testimonial */}
          <div className="max-w-md">
            <blockquote className="border-l-2 border-violet-500 pl-4">
              <p className="text-zinc-400 italic mb-4">
                "YAPPC helped us ship our MVP in 2 weeks instead of 2 months.
                The AI assistance is incredibly intuitive."
              </p>
              <footer className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-400 to-cyan-400" />
                <div>
                  <div className="text-sm font-medium text-white">Sarah Chen</div>
                  <div className="text-xs text-zinc-500">CTO, TechStartup Inc.</div>
                </div>
              </footer>
            </blockquote>
          </div>
        </div>
      </div>

      {/* Right Panel - Auth Form */}
      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-md">
          {/* Mobile Logo */}
          <div className="lg:hidden flex justify-center mb-8">
            <NavLink to={ROUTES.HOME} className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center">
                <span className="text-white text-xl font-bold">Y</span>
              </div>
              <span className="text-2xl font-bold bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
                YAPPC
              </span>
            </NavLink>
          </div>

          {/* Auth Content */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <Outlet />
          </motion.div>

          {/* Footer */}
          <div className="mt-8 text-center text-sm text-zinc-500">
            <p>
              By continuing, you agree to our{' '}
              <a href="/terms" className="text-violet-400 hover:underline">
                Terms of Service
              </a>{' '}
              and{' '}
              <a href="/privacy" className="text-violet-400 hover:underline">
                Privacy Policy
              </a>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AuthLayout;
