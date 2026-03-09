import { ReactNode } from 'react';
import { Link } from 'react-router';

interface DashboardCardProps {
  title: string;
  viewAllLink: string;
  children: ReactNode;
  className?: string;
}

export function DashboardCard({ title, viewAllLink, children, className = '' }: DashboardCardProps) {
  return (
    <div className={`bg-white rounded-lg shadow-sm hover:shadow-md transition-shadow duration-200 ${className}`}>
      <div className="p-6 border-b">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold text-gray-900">{title}</h2>
          <Link to={viewAllLink} className="text-sm font-medium text-primary-600 hover:text-primary-500">
            View all
          </Link>
        </div>
      </div>
      <div className="p-6">
        {children}
      </div>
    </div>
  );
}

interface DashboardKPIProps {
  icon: ReactNode;
  title: string;
  value: string | number;
  subtitle?: string;
  secondaryValue?: string;
  link?: string;
  color?: 'blue' | 'green' | 'yellow' | 'orange' | 'red' | 'purple' | 'gray';
  trend?: {
    value: number;
    direction: 'up' | 'down' | 'neutral';
  };
  className?: string;
}

const COLOR_STYLES: Record<string, string> = {
  blue: 'bg-blue-50 border-blue-200',
  green: 'bg-green-50 border-green-200',
  yellow: 'bg-yellow-50 border-yellow-200',
  orange: 'bg-orange-50 border-orange-200',
  red: 'bg-red-50 border-red-200',
  purple: 'bg-purple-50 border-purple-200',
  gray: 'bg-gray-50 border-gray-200',
};

export function DashboardKPI({
  icon,
  title,
  value,
  subtitle,
  secondaryValue,
  link,
  color,
  trend,
  className = '',
}: DashboardKPIProps) {
  const colorClass = color ? COLOR_STYLES[color] : '';

  const content = (
    <div className={`flex items-center justify-between ${colorClass} ${className}`}>
      <div className="flex items-center">
        {icon}
        <div className="ml-4">
          <p className="text-sm font-medium text-gray-500">{title}</p>
          <p className="text-2xl font-semibold text-gray-900">
            {value}
          </p>
          {subtitle ? <p className="text-xs text-gray-500">{subtitle}</p> : null}
          {trend ? (
            <p className="text-xs text-gray-500">
              {trend.direction === 'up' ? '↑' : trend.direction === 'down' ? '↓' : '→'} {Math.abs(trend.value)}%
            </p>
          ) : null}
        </div>
      </div>
      {secondaryValue && (
        <div className="text-right">
          <p className="text-sm text-gray-900">
            {secondaryValue}
          </p>
        </div>
      )}
    </div>
  );

  if (link) {
    return (
      <Link to={link} className="block hover:bg-gray-50 transition-colors duration-150 -mx-6 px-6 py-4">
        {content}
      </Link>
    );
  }

  return <div className="-mx-6 px-6 py-4">{content}</div>;
}
