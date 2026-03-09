/**
 * Main Layout Component
 * Provides navigation and page structure
 */

import { Link, useLocation } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { currentUserAtom } from '../store/atoms';
import { useLogout } from '../hooks/use-api';
import { Home, PlusCircle, FileText, Layers, LogOut, Search, BarChart3, Brain, Settings } from 'lucide-react';
import SkipLink from './SkipLink';

interface LayoutProps {
  children: React.ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  const location = useLocation();
  const currentUser = useAtomValue(currentUserAtom);
  const logout = useLogout();

  const navItems = [
    { path: '/', label: 'Dashboard', icon: Home },
    { path: '/capture', label: 'Capture', icon: PlusCircle },
    { path: '/moments', label: 'Moments', icon: FileText },
    { path: '/spheres', label: 'Spheres', icon: Layers },
    { path: '/search', label: 'Search', icon: Search },
    { path: '/analytics', label: 'Analytics', icon: BarChart3 },
    { path: '/reflection', label: 'Reflection', icon: Brain },
    { path: '/settings', label: 'Settings', icon: Settings },
  ];

  return (
    <div className="min-h-screen bg-gray-50">
      <SkipLink />
      
      {/* Top Navigation */}
      <nav 
        className="bg-white border-b border-gray-200"
        role="navigation"
        aria-label="Main navigation"
      >
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex">
              <Link 
                to="/" 
                className="flex items-center"
                aria-label="Flashit home"
              >
                <h1 className="text-2xl font-bold text-primary-600">Flashit</h1>
              </Link>

              <div className="hidden sm:ml-10 sm:flex sm:space-x-8">
                {navItems.map(({ path, label, icon: Icon }) => (
                  <Link
                    key={path}
                    to={path}
                    className={`inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium ${
                      location.pathname === path
                        ? 'border-primary-500 text-gray-900'
                        : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'
                    }`}
                    aria-label={label}
                    aria-current={location.pathname === path ? 'page' : undefined}
                  >
                    <Icon className="w-4 h-4 mr-2" aria-hidden="true" />
                    {label}
                  </Link>
                ))}
              </div>
            </div>

            <div className="flex items-center">
              <span 
                className="text-sm text-gray-700 mr-4"
                aria-label={`Logged in as ${currentUser?.displayName || currentUser?.email}`}
              >
                {currentUser?.displayName || currentUser?.email}
              </span>
              <button
                onClick={logout}
                className="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
                aria-label="Logout"
              >
                <LogOut className="w-4 h-4 mr-2" aria-hidden="true" />
                Logout
              </button>
            </div>
          </div>
        </div>

        {/* Mobile Navigation */}
        <div 
          className="sm:hidden border-t border-gray-200"
          role="navigation"
          aria-label="Mobile navigation"
        >
          <div className="grid grid-cols-4 gap-1 p-2">
            {navItems.map(({ path, label, icon: Icon }) => (
              <Link
                key={path}
                to={path}
                className={`flex flex-col items-center py-2 text-xs font-medium rounded-md ${
                  location.pathname === path
                    ? 'bg-primary-50 text-primary-600'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
                aria-label={label}
                aria-current={location.pathname === path ? 'page' : undefined}
              >
                <Icon className="w-5 h-5 mb-1" aria-hidden="true" />
                {label}
              </Link>
            ))}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main 
        id="main-content"
        className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8"
        role="main"
        tabIndex={-1}
      >
        {children}
      </main>
    </div>
  );
}


