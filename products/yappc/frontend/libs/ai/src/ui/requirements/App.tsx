import { Routes, Route } from 'react-router-dom'
import { Box } from '@ghatana/ui';import Header from './components/common/Header'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import WorkspacePage from './pages/WorkspacePage'
import ProjectPage from './pages/ProjectPage'

function App() {
  const isAuthenticated = !!localStorage.getItem('authToken')

  return (
    <Box className="min-h-screen">
      {isAuthenticated && <Header />}
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<DashboardPage />} />
        <Route path="/workspace/:workspaceId" element={<WorkspacePage />} />
        <Route path="/project/:projectId" element={<ProjectPage />} />
      </Routes>
    </Box>
  )
}

export default App