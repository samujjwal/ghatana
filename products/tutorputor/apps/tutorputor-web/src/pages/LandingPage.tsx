/**
 * Landing Page
 * 
 * Marketing landing page for unauthenticated users.
 * Authenticated users are redirected to dashboard.
 * 
 * @doc.type page
 * @doc.purpose Marketing landing page for unauthenticated users
 * @doc.layer product
 * @doc.pattern Page
 */

import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useEffect } from 'react';
import { Box, Button, Card } from '@ghatana/design-system';

export function LandingPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, isLoading, navigate]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-lg">Loading...</div>
      </div>
    );
  }

  if (isAuthenticated) {
    return null; // Redirecting
  }

  return (
    <Box className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
      {/* Hero Section */}
      <Box className="container mx-auto px-4 py-16">
        <Box className="text-center mb-12">
          <h1 className="text-5xl font-bold text-gray-900 mb-4">
            TutorPutor
          </h1>
          <p className="text-xl text-gray-600 mb-8">
            Interactive educational simulations and personalized learning experiences
          </p>
          <Box className="flex justify-center gap-4">
            <Button
              onClick={() => navigate('/login')}
              className="px-8 py-3 text-lg"
            >
              Get Started
            </Button>
            <Button
              variant="outline"
              onClick={() => navigate('/marketplace')}
              className="px-8 py-3 text-lg"
            >
              Explore Content
            </Button>
          </Box>
        </Box>

        {/* Features Section */}
        <Box className="grid md:grid-cols-3 gap-8 mt-16">
          <Card className="p-6">
            <h3 className="text-xl font-semibold mb-3">Interactive Simulations</h3>
            <p className="text-gray-600">
              Hands-on learning with physics, chemistry, and biology simulations
            </p>
          </Card>
          <Card className="p-6">
            <h3 className="text-xl font-semibold mb-3">Personalized Pathways</h3>
            <p className="text-gray-600">
              AI-powered learning paths adapted to your pace and understanding
            </p>
          </Card>
          <Card className="p-6">
            <h3 className="text-xl font-semibold mb-3">CBM Assessments</h3>
            <p className="text-gray-600">
              Confidence-based marking for accurate self-assessment
            </p>
          </Card>
        </Box>

        {/* CTA Section */}
        <Box className="text-center mt-16">
          <h2 className="text-3xl font-bold text-gray-900 mb-4">
            Start Learning Today
          </h2>
          <p className="text-gray-600 mb-8">
            Join thousands of learners mastering STEM concepts
          </p>
          <Button
            onClick={() => navigate('/login')}
            className="px-8 py-3 text-lg"
          >
            Create Free Account
          </Button>
        </Box>
      </Box>
    </Box>
  );
}
