/**
 * Analytics API Routes
 *
 * API endpoints for analytics and data export.
 *
 * @doc.type routes
 * @doc.purpose Analytics and data export API endpoints
 * @doc.layer product
 * @doc.pattern REST API
 */

import { Router } from 'express';
import { DataExportService } from './DataExportService.js';
import { TeacherAnalyticsService } from './TeacherAnalyticsService.js';
import { EnhancedPredictiveAnalyticsService } from './EnhancedPredictiveAnalyticsService.js';
import type { TutorPrismaClient } from '@tutorputor/core/db';

export function createAnalyticsRoutes(prisma: TutorPrismaClient): Router {
  const router = Router();
  const dataExportService = new DataExportService(prisma);
  const teacherAnalyticsService = new TeacherAnalyticsService(prisma);
  const predictiveAnalyticsService = new EnhancedPredictiveAnalyticsService(prisma);

  /**
   * GET /analytics/export - Export analytics data
   */
  router.get('/export', async (req, res) => {
    try {
      const { tenantId, format, scope, scopeId, startDate, endDate, anonymize } = req.query;
      
      const result = await dataExportService.exportData({
        tenantId: tenantId as string,
        format: (format as 'csv' | 'excel' | 'json') || 'csv',
        scope: (scope as 'tenant' | 'classroom' | 'student' | 'assessment') || 'tenant',
        scopeId: scopeId as string,
        dateRange: startDate && endDate ? {
          startDate: startDate as string,
          endDate: endDate as string,
        } : undefined,
        anonymize: anonymize === 'true',
      });

      res.setHeader('Content-Type', format === 'json' ? 'application/json' : 'text/csv');
      res.setHeader('Content-Disposition', `attachment; filename="${result.filename}"`);
      res.send(result.data);
    } catch (error) {
      res.status(500).json({ error: 'Export failed', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * GET /analytics/classroom/:classroomId - Get classroom analytics
   */
  router.get('/classroom/:classroomId', async (req, res) => {
    try {
      const { tenantId } = req.query;
      const { classroomId } = req.params;

      const analytics = await teacherAnalyticsService.getClassroomAnalytics(
        tenantId as string,
        classroomId,
      );

      res.json(analytics);
    } catch (error) {
      res.status(500).json({ error: 'Failed to fetch classroom analytics', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * GET /analytics/student/:studentId - Get student analytics
   */
  router.get('/student/:studentId', async (req, res) => {
    try {
      const { tenantId } = req.query;
      const { studentId } = req.params;

      const analytics = await teacherAnalyticsService.getStudentAnalytics(
        tenantId as string,
        studentId,
      );

      res.json(analytics);
    } catch (error) {
      res.status(500).json({ error: 'Failed to fetch student analytics', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * GET /analytics/interventions/:classroomId - Get intervention recommendations
   */
  router.get('/interventions/:classroomId', async (req, res) => {
    try {
      const { tenantId } = req.query;
      const { classroomId } = req.params;

      const recommendations = await teacherAnalyticsService.getInterventionRecommendations(
        tenantId as string,
        classroomId,
      );

      res.json(recommendations);
    } catch (error) {
      res.status(500).json({ error: 'Failed to fetch intervention recommendations', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * POST /analytics/predict/path - Predict learning path
   */
  router.post('/predict/path', async (req, res) => {
    try {
      const { tenantId, userId, goal } = req.body;

      const prediction = await predictiveAnalyticsService.predictLearningPath(
        tenantId,
        userId,
        goal,
      );

      res.json(prediction);
    } catch (error) {
      res.status(500).json({ error: 'Failed to predict learning path', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * GET /analytics/predict/mastery/:conceptId - Predict mastery
   */
  router.get('/predict/mastery/:conceptId', async (req, res) => {
    try {
      const { tenantId, userId } = req.query;
      const { conceptId } = req.params;

      const prediction = await predictiveAnalyticsService.predictMastery(
        tenantId as string,
        userId as string,
        conceptId,
      );

      res.json(prediction);
    } catch (error) {
      res.status(500).json({ error: 'Failed to predict mastery', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * GET /analytics/predict/dropout/:userId - Predict dropout risk
   */
  router.get('/predict/dropout/:userId', async (req, res) => {
    try {
      const { tenantId } = req.query;
      const { userId } = req.params;

      const prediction = await predictiveAnalyticsService.predictDropout(
        tenantId as string,
        userId,
      );

      res.json(prediction);
    } catch (error) {
      res.status(500).json({ error: 'Failed to predict dropout risk', message: error instanceof Error ? error.message : String(error) });
    }
  });

  /**
   * GET /analytics/gaps - Analyze content gaps
   */
  router.get('/gaps', async (req, res) => {
    try {
      const { tenantId, classroomId } = req.query;

      const gaps = await predictiveAnalyticsService.analyzeContentGaps(
        tenantId as string,
        classroomId as string,
      );

      res.json(gaps);
    } catch (error) {
      res.status(500).json({ error: 'Failed to analyze content gaps', message: error instanceof Error ? error.message : String(error) });
    }
  });

  return router;
}
