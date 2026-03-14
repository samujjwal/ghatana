import React, { ReactNode, useState, useRef, useEffect } from 'react';

/**
 * Generic Application Header Component
 * 
 * Provides a flexible, responsive header with logo, navigation links, actions, and user menu.
 * Supports mobile menu, notifications, search, and custom action buttons.
 * 
 * @example
 * ```tsx
 * <AppHeader
 *   logo={<YourLogo />}
 *   title="My App"
 *   subtitle="Dashboard"
 *   navLinks={[
 *     { label: 'Dashboard', href: '/', active: true },
 *     { label: 'Analytics', href: '/analytics' },
 *   ]}
 *   actions={<SearchBar />}
 *   userMenu={{
 *     userName: 'John Doe',
 *     userEmail: 'john@example.com',
 *     menuItems: [
 *       { label: 'Profile', onClick: () => {} },
 *       { label: 'Logout', onClick: () => {} },
 *     ]
 *   }}
 *   onMenuClick={() => toggleSidebar()}
 * />
 * ```
 */

export interface NavLinkConfig {
  /** Link label */
  label: string;
  
  /** Link href (optional if using onClick) */
  href?: string;
  
  /** Click handler (optional if using href) */
  onClick?: () => void;
  
  /** Whether this link is active */
  active?: boolean;
  
  /** Icon to display before label */
  icon?: ReactNode;
  
  /** Badge to display (e.g., "New", "3") */
  badge?: string | number;
}

export interface UserMenuItemConfig {
  /** Menu item label */
  label: string;
  
  /** Click handler */
  onClick: () => void;
  
  /** Icon to display before label */
  icon?: ReactNode;
  
  /** Whether this is a destructive action (e.g., logout) */
  destructive?: boolean;
  
  /** Whether to show a separator after this item */
  separator?: boolean;
}

export interface UserMenuConfig {
  /** User's display name */
  userName?: string;
  
  /** User's email or secondary info */
  userEmail?: string;
  
  /** User avatar URL or component */
  avatar?: string | ReactNode;
  
  /** Menu items */
  menuItems: UserMenuItemConfig[];
}

export interface StatusIndicatorConfig {
  /** Status label */
  label: string;
  
  /** Status type */
  status: 'online' | 'offline' | 'warning' | 'error' | 'success';
  
  /** Click handler */
  onClick?: () => void;
}

export interface AppHeaderProps {
  /** Logo component or image */
  logo?: ReactNode;
  
  /** Application title */
  title?: string;
  
  /** Subtitle or page name */
  subtitle?: string;
  
  /** Navigation links (desktop only) */
  navLinks?: NavLinkConfig[];
  
  /** Action buttons/components (search, notifications, etc.) */
  actions?: ReactNode;
  
  /** User menu configuration */
  userMenu?: UserMenuConfig;
  
  /** Status indicator (connection, sync, etc.) */
  statusIndicator?: StatusIndicatorConfig;
  
  /** Callback when menu (hamburger) is clicked */
  onMenuClick?: () => void;
  
  /** Whether to show menu button */
  showMenuButton?: boolean;
  
  /** Additional CSS classes */
  className?: string;
  
  /** Height variant */
  height?: 'sm' | 'md' | 'lg';
}

const HEIGHT_MAP = {
  sm: 'h-12',
  md: 'h-16',
  lg: 'h-20',
};

const STATUS_COLORS = {
  online: 'bg-green-500',
  offline: 'bg-red-500',
  warning: 'bg-yellow-500',
  error: 'bg-red-600',
  success: 'bg-emerald-500',
};

/**
 * MenuIcon - Hamburger menu icon
 */
const MenuIcon: React.FC = () => (
  <svg
    className="h-6 w-6"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    viewBox="0 0 24 24"
  >
    <path d="M4 6h16M4 12h16M4 18h16" />
  </svg>
);

/**
 * ChevronDownIcon - Dropdown indicator
 */
const ChevronDownIcon: React.FC = () => (
  <svg
    className="h-4 w-4"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
    viewBox="0 0 24 24"
  >
    <path d="m6 9 6 6 6-6" />
  </svg>
);

/**
 * AppHeader - Generic application header with navigation and user menu
 * 
 * Features:
 * - Responsive design (mobile menu button)
 * - Navigation links with active state
 * - User menu with dropdown
 * - Status indicator
 * - Custom actions (search, notifications)
 * - Flexible logo and branding
 */
export const AppHeader: React.FC<AppHeaderProps> = ({
  logo,
  title,
  subtitle,
  navLinks = [],
  actions,
  userMenu,
  statusIndicator,
  onMenuClick,
  showMenuButton = true,
  className = '',
  height = 'md',
}) => {
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  // Close user menu on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(event.target as Node)) {
        setUserMenuOpen(false);
      }
    };

    if (userMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [userMenuOpen]);

  const heightClass = HEIGHT_MAP[height];

  return (
    <header className={`bg-white shadow-sm ${className}`}>
      <div className={`flex items-center justify-between px-4 sm:px-6 lg:px-8 ${heightClass}`}>
        {/* Left Section: Menu + Logo/Title */}
        <div className="flex items-center space-x-4">
          {/* Menu Button (Mobile) */}
          {showMenuButton && onMenuClick && (
            <button
              onClick={onMenuClick}
              className="inline-flex items-center justify-center rounded-md p-2 text-gray-600 hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
              aria-label="Toggle menu"
            >
              <MenuIcon />
            </button>
          )}

          {/* Logo */}
          {logo && <div className="flex-shrink-0">{logo}</div>}

          {/* Title & Subtitle */}
          <div className="flex flex-col">
            {title && (
              <h1 className="text-lg font-semibold text-gray-900 leading-tight">
                {title}
              </h1>
            )}
            {subtitle && (
              <p className="text-sm text-gray-500 leading-tight">{subtitle}</p>
            )}
          </div>
        </div>

        {/* Center Section: Navigation Links (Desktop) */}
        {navLinks.length > 0 && (
          <nav className="hidden md:flex items-center space-x-1">
            {navLinks.map((link, index) => (
              <a
                key={index}
                href={link.href}
                onClick={link.onClick}
                className={`
                  flex items-center space-x-2 px-3 py-2 rounded-md text-sm font-medium transition-colors
                  ${
                    link.active
                      ? 'bg-indigo-50 text-indigo-700'
                      : 'text-gray-700 hover:bg-gray-100 hover:text-gray-900'
                  }
                `}
              >
                {link.icon && <span>{link.icon}</span>}
                <span>{link.label}</span>
                {link.badge && (
                  <span className="ml-1 inline-flex items-center rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-800">
                    {link.badge}
                  </span>
                )}
              </a>
            ))}
          </nav>
        )}

        {/* Right Section: Actions + Status + User Menu */}
        <div className="flex items-center space-x-4">
          {/* Custom Actions */}
          {actions && <div className="flex items-center space-x-2">{actions}</div>}

          {/* Status Indicator */}
          {statusIndicator && (
            <button
              onClick={statusIndicator.onClick}
              className="flex items-center space-x-2 text-sm text-gray-600 hover:text-gray-900"
              disabled={!statusIndicator.onClick}
            >
              <span
                className={`h-2 w-2 rounded-full ${STATUS_COLORS[statusIndicator.status]}`}
              />
              <span className="hidden sm:inline">{statusIndicator.label}</span>
            </button>
          )}

          {/* User Menu */}
          {userMenu && (
            <div className="relative" ref={userMenuRef}>
              <button
                onClick={() => setUserMenuOpen(!userMenuOpen)}
                className="flex items-center space-x-2 rounded-md px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                aria-expanded={userMenuOpen}
                aria-haspopup="true"
              >
                {/* Avatar */}
                {typeof userMenu.avatar === 'string' ? (
                  <img
                    src={userMenu.avatar}
                    alt={userMenu.userName}
                    className="h-8 w-8 rounded-full"
                  />
                ) : userMenu.avatar ? (
                  userMenu.avatar
                ) : (
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-600 text-white text-sm font-medium">
                    {userMenu.userName?.charAt(0).toUpperCase() || 'U'}
                  </div>
                )}

                {/* User Info (Desktop) */}
                <div className="hidden md:block text-left">
                  {userMenu.userName && (
                    <div className="text-sm font-medium text-gray-900">
                      {userMenu.userName}
                    </div>
                  )}
                  {userMenu.userEmail && (
                    <div className="text-xs text-gray-500">{userMenu.userEmail}</div>
                  )}
                </div>

                <ChevronDownIcon />
              </button>

              {/* Dropdown Menu */}
              {userMenuOpen && (
                <div className="absolute right-0 mt-2 w-56 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none z-50">
                  <div className="py-1">
                    {userMenu.menuItems.map((item, index) => (
                      <React.Fragment key={index}>
                        <button
                          onClick={() => {
                            item.onClick();
                            setUserMenuOpen(false);
                          }}
                          className={`
                            w-full flex items-center space-x-2 px-4 py-2 text-sm text-left
                            ${
                              item.destructive
                                ? 'text-red-700 hover:bg-red-50'
                                : 'text-gray-700 hover:bg-gray-100'
                            }
                          `}
                        >
                          {item.icon && <span>{item.icon}</span>}
                          <span>{item.label}</span>
                        </button>
                        {item.separator && <div className="border-t border-gray-100" />}
                      </React.Fragment>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default AppHeader;
