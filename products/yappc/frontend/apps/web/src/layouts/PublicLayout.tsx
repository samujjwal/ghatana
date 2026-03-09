/**
 * Public Layout
 *
 * @description Layout for public pages (landing, pricing, etc.)
 * with marketing header and footer.
 */

import React, { useState } from 'react';
import { Outlet, NavLink } from 'react-router';
import { AnimatePresence, motion } from 'framer-motion';
import { Menu, X, ChevronDown, Github, Twitter, Linkedin } from 'lucide-react';

import { cn } from '../utils/cn';
import { ROUTES } from '../router/paths';

// =============================================================================
// Navigation Configuration
// =============================================================================

interface NavItem {
  label: string;
  path?: string;
  children?: { label: string; path: string; description: string }[];
}

const navItems: NavItem[] = [
  {
    label: 'Product',
    children: [
      { label: 'Features', path: '/features', description: 'Explore all capabilities' },
      { label: 'Integrations', path: '/integrations', description: 'Connect your tools' },
      { label: 'Security', path: '/security', description: 'Enterprise-grade security' },
      { label: 'Roadmap', path: '/roadmap', description: 'See what\'s coming' },
    ],
  },
  {
    label: 'Solutions',
    children: [
      { label: 'Startups', path: '/startups', description: 'Ship fast, scale smart' },
      { label: 'Enterprise', path: '/enterprise', description: 'Scale with confidence' },
      { label: 'Agencies', path: '/agencies', description: 'Deliver for clients' },
    ],
  },
  {
    label: 'Resources',
    children: [
      { label: 'Documentation', path: '/docs', description: 'Learn how to use YAPPC' },
      { label: 'Blog', path: '/blog', description: 'Tips and best practices' },
      { label: 'Changelog', path: '/changelog', description: 'Latest updates' },
      { label: 'API Reference', path: '/api', description: 'Build on YAPPC' },
    ],
  },
  { label: 'Dashboard', path: ROUTES.DASHBOARD },
];

// =============================================================================
// Header Component
// =============================================================================

const Header: React.FC = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [activeDropdown, setActiveDropdown] = useState<string | null>(null);

  return (
    <header className="fixed top-0 left-0 right-0 z-50">
      <div className="bg-zinc-950/80 backdrop-blur-lg border-b border-zinc-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            {/* Logo */}
            <NavLink to={ROUTES.HOME} className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center">
                <span className="text-white text-lg font-bold">Y</span>
              </div>
              <span className="text-xl font-bold bg-gradient-to-r from-violet-400 to-fuchsia-400 bg-clip-text text-transparent">
                YAPPC
              </span>
            </NavLink>

            {/* Desktop Navigation */}
            <nav className="hidden md:flex items-center gap-1">
              {navItems.map((item) => (
                <div
                  key={item.label}
                  className="relative"
                  onMouseEnter={() => item.children && setActiveDropdown(item.label)}
                  onMouseLeave={() => setActiveDropdown(null)}
                >
                  {item.path ? (
                    <NavLink
                      to={item.path}
                      className="px-4 py-2 text-sm text-zinc-400 hover:text-white transition-colors"
                    >
                      {item.label}
                    </NavLink>
                  ) : (
                    <button
                      className={cn(
                        'flex items-center gap-1 px-4 py-2 text-sm transition-colors',
                        activeDropdown === item.label ? 'text-white' : 'text-zinc-400 hover:text-white'
                      )}
                    >
                      {item.label}
                      <ChevronDown className="w-4 h-4" />
                    </button>
                  )}

                  {/* Dropdown */}
                  <AnimatePresence>
                    {item.children && activeDropdown === item.label && (
                      <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: 10 }}
                        transition={{ duration: 0.15 }}
                        className="absolute top-full left-0 mt-2 w-64 bg-zinc-900 border border-zinc-800 rounded-xl shadow-xl overflow-hidden"
                      >
                        <div className="p-2">
                          {item.children.map((child) => (
                            <NavLink
                              key={child.path}
                              to={child.path}
                              className="block p-3 rounded-lg hover:bg-zinc-800 transition-colors"
                            >
                              <div className="text-sm font-medium text-white">{child.label}</div>
                              <div className="text-xs text-zinc-500 mt-0.5">{child.description}</div>
                            </NavLink>
                          ))}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              ))}
            </nav>

            {/* CTA Buttons */}
            <div className="hidden md:flex items-center gap-3">
              <NavLink
                to={ROUTES.SSO_CALLBACK}
                className="px-4 py-2 text-sm text-zinc-400 hover:text-white transition-colors"
              >
                Log in
              </NavLink>
              <NavLink
                to={ROUTES.SSO_CALLBACK}
                className={cn(
                  'px-4 py-2 rounded-lg text-sm font-medium',
                  'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
                )}
              >
                Get Started Free
              </NavLink>
            </div>

            {/* Mobile Menu Button */}
            <button
              className="md:hidden p-2 text-zinc-400 hover:text-white transition-colors"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Toggle menu"
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>

        {/* Mobile Menu */}
        <AnimatePresence>
          {mobileMenuOpen && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="md:hidden border-t border-zinc-800 overflow-hidden"
            >
              <div className="px-4 py-4 space-y-4">
                {navItems.map((item) => (
                  <div key={item.label}>
                    {item.path ? (
                      <NavLink
                        to={item.path}
                        className="block py-2 text-zinc-400 hover:text-white transition-colors"
                        onClick={() => setMobileMenuOpen(false)}
                      >
                        {item.label}
                      </NavLink>
                    ) : (
                      <div>
                        <div className="py-2 text-zinc-300 font-medium">{item.label}</div>
                        <div className="pl-4 space-y-2">
                          {item.children?.map((child) => (
                            <NavLink
                              key={child.path}
                              to={child.path}
                              className="block py-1 text-sm text-zinc-500 hover:text-white transition-colors"
                              onClick={() => setMobileMenuOpen(false)}
                            >
                              {child.label}
                            </NavLink>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
                <div className="pt-4 border-t border-zinc-800 space-y-3">
                  <NavLink
                    to={ROUTES.SSO_CALLBACK}
                    className="block w-full py-2 text-center text-zinc-400 hover:text-white transition-colors"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    Log in
                  </NavLink>
                  <NavLink
                    to={ROUTES.SSO_CALLBACK}
                    className={cn(
                      'block w-full py-2 rounded-lg text-center font-medium',
                      'bg-violet-500 text-white hover:bg-violet-600 transition-colors'
                    )}
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    Get Started Free
                  </NavLink>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </header>
  );
};

// =============================================================================
// Footer Component
// =============================================================================

const footerLinks = {
  Product: [
    { label: 'Features', path: '/features' },
    { label: 'Integrations', path: '/integrations' },
    { label: 'Dashboard', path: ROUTES.DASHBOARD },
    { label: 'Changelog', path: '/changelog' },
  ],
  Resources: [
    { label: 'Documentation', path: '/docs' },
    { label: 'API Reference', path: '/api' },
    { label: 'Blog', path: '/blog' },
    { label: 'Community', path: '/community' },
  ],
  Company: [
    { label: 'About', path: '/about' },
    { label: 'Careers', path: '/careers' },
    { label: 'Contact', path: '/contact' },
    { label: 'Press', path: '/press' },
  ],
  Legal: [
    { label: 'Terms', path: '/terms' },
    { label: 'Privacy', path: '/privacy' },
    { label: 'Security', path: '/security' },
    { label: 'Cookies', path: '/cookies' },
  ],
};

const Footer: React.FC = () => {
  return (
    <footer className="bg-zinc-900 border-t border-zinc-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-2 md:grid-cols-5 gap-8">
          {/* Brand */}
          <div className="col-span-2 md:col-span-1">
            <NavLink to={ROUTES.HOME} className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center">
                <span className="text-white text-sm font-bold">Y</span>
              </div>
              <span className="text-lg font-bold text-white">YAPPC</span>
            </NavLink>
            <p className="text-sm text-zinc-500 mb-4">
              AI-powered project lifecycle management for modern teams.
            </p>
            <div className="flex items-center gap-3">
              <a
                href="https://github.com/yappc"
                target="_blank"
                rel="noopener noreferrer"
                className="p-2 rounded-lg bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
              >
                <Github className="w-4 h-4" />
              </a>
              <a
                href="https://twitter.com/yappc"
                target="_blank"
                rel="noopener noreferrer"
                className="p-2 rounded-lg bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
              >
                <Twitter className="w-4 h-4" />
              </a>
              <a
                href="https://linkedin.com/company/yappc"
                target="_blank"
                rel="noopener noreferrer"
                className="p-2 rounded-lg bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
              >
                <Linkedin className="w-4 h-4" />
              </a>
            </div>
          </div>

          {/* Links */}
          {Object.entries(footerLinks).map(([category, links]) => (
            <div key={category}>
              <h3 className="text-sm font-semibold text-white mb-4">{category}</h3>
              <ul className="space-y-2">
                {links.map((link) => (
                  <li key={link.path}>
                    <NavLink
                      to={link.path}
                      className="text-sm text-zinc-500 hover:text-white transition-colors"
                    >
                      {link.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* Bottom */}
        <div className="mt-12 pt-8 border-t border-zinc-800 flex flex-col md:flex-row items-center justify-between gap-4">
          <p className="text-sm text-zinc-500">
            © {new Date().getFullYear()} YAPPC. All rights reserved.
          </p>
          <div className="flex items-center gap-4">
            <span className="text-xs text-zinc-600">Status: All systems operational</span>
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
          </div>
        </div>
      </div>
    </footer>
  );
};

// =============================================================================
// Public Layout Component
// =============================================================================

const PublicLayout: React.FC = () => {
  return (
    <div className="min-h-screen bg-zinc-950 flex flex-col">
      {/* Header */}
      <Header />

      {/* Main Content */}
      <main className="flex-1 pt-16">
        <Outlet />
      </main>

      {/* Footer */}
      <Footer />
    </div>
  );
};

export default PublicLayout;
