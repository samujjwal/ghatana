import React, { useState } from 'react';
import { Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { DashboardLayout } from './ui/components/layout/DashboardLayout';
import DashboardPage from './ui/pages/DashboardPage';
import { MetricsPage } from './ui/pages/MetricsPage';
import { PageUsagePage } from './ui/pages/PageUsagePage';
import { ConfigurationPage } from './ui/pages/ConfigurationPage';
import { HelpPage } from './ui/pages/HelpPage';
import { SettingsPage } from './ui/pages/SettingsPage';
import { AccessibilityPage } from './ui/pages/AccessibilityPage';
import { DetailsPage } from './ui/pages/DetailsPage';
import { AnalyticsProvider } from './ui/context/AnalyticsContext';
import { GuidanceProvider } from './ui/context/GuidanceContext';
import './styles/globals.css';

export const Dashboard: React.FC = () => {
    const navigate = useNavigate();
    const location = useLocation();

    const handlePageChange = (page: string) => {
        navigate(page);
    };

    return (
        <AnalyticsProvider>
            <GuidanceProvider>
                <DashboardLayout
                    currentPage={location.pathname}
                    onPageChange={handlePageChange}
                >
                    <Routes>
                        <Route path="/" element={<DashboardPage />} />
                        <Route path="/details" element={<DetailsPage />} />
                        <Route path="/analytics" element={<PageUsagePage />} />
                        <Route path="/events" element={<MetricsPage />} />
                        <Route path="/monitoring" element={<ConfigurationPage />} />
                        <Route path="/settings" element={<SettingsPage />} />
                        <Route path="/help" element={<HelpPage />} />
                        <Route path="/accessibility" element={<AccessibilityPage />} />
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </DashboardLayout>
            </GuidanceProvider>
        </AnalyticsProvider>
    );
};
