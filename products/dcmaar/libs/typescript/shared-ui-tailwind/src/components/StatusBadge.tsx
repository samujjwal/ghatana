import React from 'react';
import type { StatusBadgeProps } from '@ghatana/dcmaar-shared-ui-core';

const STATUS_CONFIG = {
  online: {
    color: 'bg-green-500',
    text: 'Online',
    textColor: 'text-green-700',
    bgLight: 'bg-green-50',
  },
  offline: {
    color: 'bg-gray-400',
    text: 'Offline',
    textColor: 'text-gray-700',
    bgLight: 'bg-gray-50',
  },
  connecting: {
    color: 'bg-blue-500',
    text: 'Connecting',
    textColor: 'text-blue-700',
    bgLight: 'bg-blue-50',
  },
  error: {
    color: 'bg-red-500',
    text: 'Error',
    textColor: 'text-red-700',
    bgLight: 'bg-red-50',
  },
  warning: {
    color: 'bg-yellow-500',
    text: 'Warning',
    textColor: 'text-yellow-700',
    bgLight: 'bg-yellow-50',
  },
  success: {
    color: 'bg-green-500',
    text: 'Success',
    textColor: 'text-green-700',
    bgLight: 'bg-green-50',
  },
  info: {
    color: 'bg-blue-500',
    text: 'Info',
    textColor: 'text-blue-700',
    bgLight: 'bg-blue-50',
  },
} as const;

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  label,
  pulse = false,
  size = 'md',
  className = '',
}) => {
  const config = STATUS_CONFIG[status];

  const sizeClasses = {
    sm: {
      text: 'text-xs',
      dot: 'w-2 h-2',
      padding: 'px-2 py-1',
      gap: 'gap-1.5',
    },
    md: {
      text: 'text-sm',
      dot: 'w-3 h-3',
      padding: 'px-3 py-1.5',
      gap: 'gap-2',
    },
    lg: {
      text: 'text-base',
      dot: 'w-4 h-4',
      padding: 'px-4 py-2',
      gap: 'gap-2.5',
    },
  };

  const sizeConfig = sizeClasses[size];

  return (
    <div
      className={`inline-flex items-center ${sizeConfig.gap} ${sizeConfig.padding} rounded-full ${config.bgLight} border border-${status}-200 ${className}`}
    >
      <div
        className={`${sizeConfig.dot} rounded-full ${config.color} ${
          pulse ? 'animate-pulse' : ''
        }`}
      />
      <span className={`${sizeConfig.text} font-medium ${config.textColor}`}>
        {label || config.text}
      </span>
    </div>
  );
};
