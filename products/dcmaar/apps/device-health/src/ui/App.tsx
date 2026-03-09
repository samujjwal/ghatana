import React, { useState } from 'react';
import { Routes, Route, useLocation, useNavigate } from 'react-router-dom';
import { DashboardPageWrapper } from './pages/DashboardPageWrapper';
import { SettingsPage } from './pages/SettingsPage';
import { DetailsPage } from './pages/DetailsPage';
import { Layout } from './components/layout/Layout';

// This component ensures that all hooks are called within the Router context
const AppRouter = () => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const location = useLocation();
  const navigate = useNavigate();

  const handleMenuClick = () => {
    setIsSidebarOpen(!isSidebarOpen);
  };

  const handlePageChange = (page: string) => {
    navigate(page);
  };

  return (
    <Layout 
      isOpen={isSidebarOpen}
      currentPage={location.pathname}
      onPageChange={handlePageChange}
      onMenuClick={handleMenuClick}
    >
      <Routes>
        <Route path="/" element={<DashboardPageWrapper />} />
        <Route path="/dashboard" element={<DashboardPageWrapper />} />
        <Route path="/details" element={<DetailsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>
    </Layout>
  );
};

// Main App component that will be wrapped by Providers
export const App = () => {
  return <AppRouter />;
};

export default App;
