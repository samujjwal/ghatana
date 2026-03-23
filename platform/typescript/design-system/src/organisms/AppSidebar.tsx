import React, { ReactNode } from 'react';

/**
 * Generic Application Sidebar Component
 * 
 * Provides a flexible, responsive sidebar navigation with icons, labels, badges, and sections.
 * Supports collapsible state, active item highlighting, and grouped navigation.
 * 
 * @example
 * ```tsx
 * <AppSidebar
 *   logo={<YourLogo />}
 *   title="My App"
 *   subtitle="v1.0"
 *   collapsed={isCollapsed}
 *   sections={[
 *     {
 *       title: 'Main',
 *       items: [
 *         { id: 'dashboard', label: 'Dashboard', icon: <DashboardIcon />, active: true },
 *         { id: 'analytics', label: 'Analytics', icon: <AnalyticsIcon /> },
 *       ]
 *     }
 *   ]}
 *   onNavigate={(itemId) => navigate(itemId)}
 * />
 * ```
 */

export interface SidebarItemConfig {
  /** Unique item identifier */
  id: string;
  
  /** Item label */
  label: string;
  
  /** Icon to display */
  icon?: ReactNode;
  
  /** Whether this item is active */
  active?: boolean;
  
  /** Badge to display (e.g., "New", "3") */
  badge?: string | number;
  
  /** Whether item is disabled */
  disabled?: boolean;
  
  /** Tooltip text (shown when collapsed) */
  tooltip?: string;
  
  /** Item href (optional) */
  href?: string;
}

export interface SidebarSectionConfig {
  /** Section title (hidden when collapsed) */
  title?: string;
  
  /** Section items */
  items: SidebarItemConfig[];
  
  /** Whether section is collapsible */
  collapsible?: boolean;
  
  /** Whether section is initially collapsed */
  defaultCollapsed?: boolean;
}

export interface AppSidebarProps {
  /** Logo component or image */
  logo?: ReactNode;
  
  /** Application title */
  title?: string;
  
  /** Subtitle (version, environment, etc.) */
  subtitle?: string;
  
  /** Navigation sections */
  sections: SidebarSectionConfig[];
  
  /** Whether sidebar is collapsed */
  collapsed?: boolean;
  
  /** Callback when navigation item is clicked */
  onNavigate: (itemId: string) => void;
  
  /** Footer content */
  footer?: ReactNode;
  
  /** Additional CSS classes */
  className?: string;
  
  /** Background color */
  backgroundColor?: string;
  
  /** Border style */
  borderStyle?: 'none' | 'light' | 'medium';
}

const BORDER_STYLES = {
  none: '',
  light: 'border-r border-gray-100',
  medium: 'border-r border-gray-200',
};

/**
 * AppSidebar - Generic application sidebar with navigation
 * 
 * Features:
 * - Collapsible state (icon-only mode)
 * - Grouped navigation sections
 * - Active item highlighting
 * - Badge support
 * - Tooltips when collapsed
 * - Responsive transitions
 */
export const AppSidebar: React.FC<AppSidebarProps> = ({
  logo,
  title,
  subtitle,
  sections,
  collapsed = false,
  onNavigate,
  footer,
  className = '',
  backgroundColor = 'bg-white',
  borderStyle = 'medium',
}) => {
  const borderClass = BORDER_STYLES[borderStyle];

  return (
    <aside
      className={`
        ${collapsed ? 'w-16' : 'w-64'}
        ${backgroundColor} ${borderClass}
        transition-all duration-300 ease-in-out
        flex flex-col h-full shadow-sm
        ${className}
      `}
      aria-label="Sidebar navigation"
    >
      {/* Header */}
      {(logo || title) && (
        <div
          className={`
            px-4 py-3 border-b border-gray-200 flex-shrink-0
            ${collapsed ? 'justify-center' : ''}
          `}
        >
          <div className="flex items-center gap-3">
            {logo && (
              <div className="flex-shrink-0">
                {logo}
              </div>
            )}
            {!collapsed && (title || subtitle) && (
              <div className="flex-1 min-w-0">
                {title && (
                  <h1 className="text-lg font-bold text-gray-900 truncate">
                    {title}
                  </h1>
                )}
                {subtitle && (
                  <p className="text-xs text-gray-500 truncate">{subtitle}</p>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-4 px-2" aria-label="Main navigation">
        <div className="space-y-6">
          {sections.map((section, sectionIndex) => (
            <div key={sectionIndex}>
              {/* Section Title */}
              {!collapsed && section.title && (
                <h2 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                  {section.title}
                </h2>
              )}

              {/* Section Items */}
              <ul className="space-y-1">
                {section.items.map((item) => {
                  const ItemContent = (
                    <>
                      {/* Icon */}
                      {item.icon && (
                        <span
                          className={`
                            flex-shrink-0 transition-colors
                            ${
                              item.active
                                ? 'text-indigo-600'
                                : 'text-gray-500 group-hover:text-gray-700'
                            }
                          `}
                        >
                          {item.icon}
                        </span>
                      )}

                      {/* Label */}
                      {!collapsed && (
                        <span className="flex-1 text-sm truncate">{item.label}</span>
                      )}

                      {/* Badge */}
                      {!collapsed && item.badge && (
                        <span
                          className={`
                            ml-auto inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium
                            ${
                              item.active
                                ? 'bg-indigo-100 text-indigo-800'
                                : 'bg-gray-100 text-gray-700'
                            }
                          `}
                        >
                          {item.badge}
                        </span>
                      )}

                      {/* Active Indicator */}
                      {item.active && (
                        <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-indigo-600 rounded-r-full" />
                      )}
                    </>
                  );

                  const itemClasses = `
                    w-full flex items-center relative
                    ${collapsed ? 'justify-center px-2' : 'px-3'}
                    py-3 rounded-lg transition-all group
                    ${
                      item.active
                        ? 'bg-indigo-50 text-indigo-700 font-medium shadow-sm'
                        : 'text-gray-700 hover:bg-gray-50 hover:text-gray-900'
                    }
                    ${item.disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
                  `;

                  return (
                    <li key={item.id}>
                      {item.href ? (
                        <a
                          href={item.href}
                          onClick={(e) => {
                            if (item.disabled) {
                              e.preventDefault();
                              return;
                            }
                            onNavigate(item.id);
                          }}
                          className={itemClasses}
                          title={collapsed ? item.tooltip || item.label : undefined}
                          aria-current={item.active ? 'page' : undefined}
                          aria-disabled={item.disabled}
                        >
                          {ItemContent}
                        </a>
                      ) : (
                        <button
                          onClick={() => {
                            if (!item.disabled) {
                              onNavigate(item.id);
                            }
                          }}
                          className={itemClasses}
                          title={collapsed ? item.tooltip || item.label : undefined}
                          aria-current={item.active ? 'page' : undefined}
                          disabled={item.disabled}
                        >
                          {ItemContent}
                        </button>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </div>
      </nav>

      {/* Footer */}
      {footer && (
        <div className="flex-shrink-0 border-t border-gray-200 p-4">
          {footer}
        </div>
      )}
    </aside>
  );
};

export default AppSidebar;
