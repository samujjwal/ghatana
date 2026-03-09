import { ReactNode } from 'react';

export interface SidebarProps {
  isOpen: boolean;
  currentPage: string;
  onPageChange: (page: string) => void;
}

export interface HeaderProps {
  onMenuClick: () => void;
  onPageChange: (page: string) => void;
}

export interface LayoutProps {
  children: ReactNode;
  isOpen: boolean;
  currentPage: string;
  onPageChange: (page: string) => void;
  onMenuClick: () => void;
}
