import React from 'react';
import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { LayoutProps } from './types';

export const Layout: React.FC<LayoutProps> = ({
  children,
  isOpen,
  currentPage,
  onPageChange,
  onMenuClick,
}) => {
  return (
    <div className="flex h-screen bg-gray-50">
      <Sidebar 
        isOpen={isOpen}
        currentPage={currentPage}
        onPageChange={onPageChange}
      />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header 
          onMenuClick={onMenuClick} 
          onPageChange={onPageChange} 
        />
        <main className="flex-1 overflow-x-hidden overflow-y-auto bg-gray-50 p-4">
          {children || <Outlet />}
        </main>
      </div>
    </div>
  );
};

export default Layout;
