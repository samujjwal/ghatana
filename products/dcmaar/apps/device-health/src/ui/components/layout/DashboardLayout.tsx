import React, { useState } from 'react';
import { Header } from './Header';
import Sidebar from './Sidebar';

interface DashboardLayoutProps {
    children: React.ReactNode;
    currentPage: string;
    onPageChange: (page: string) => void;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
    children,
    currentPage,
    onPageChange,
}) => {
    const [sidebarOpen, setSidebarOpen] = useState(() =>
        typeof window === 'undefined' ? true : window.innerWidth >= 1280
    );

    return (
        <div className="h-screen flex bg-gray-50 overflow-hidden">
            <Sidebar
                isOpen={sidebarOpen}
                currentPage={currentPage}
                onPageChange={onPageChange}
            />

            <div className="flex flex-1 flex-col overflow-hidden">
                <Header onMenuClick={() => setSidebarOpen(!sidebarOpen)} onPageChange={onPageChange} />

                <main className="relative flex-1 overflow-auto bg-gray-50 px-4 py-4">
                    <div className="mx-auto w-full max-w-6xl">
                        {children}
                    </div>
                </main>
            </div>
        </div>
    );
};
