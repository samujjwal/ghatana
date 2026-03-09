import type { FastifyPluginAsync } from "fastify";
import { TeacherServiceImpl } from "./service";
import type {
  TenantId,
  UserId,
  ClassroomId,
  ModuleId,
} from "@ghatana/tutorputor-contracts/v1/types";

/**
 * Teacher routes - classroom management and student progress tracking.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for teacher operations
 * @doc.layer product
 * @doc.pattern REST API
 */
export const teacherRoutes: FastifyPluginAsync = async (app) => {
  const teacherService = new TeacherServiceImpl(app.prisma);

  /**
   * GET /teacher/dashboard
   * Get teacher dashboard with classrooms, students, and recent activity
   */
  app.get("/dashboard", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const teacherId = request.headers["x-user-id"] as UserId;

    if (!tenantId || !teacherId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const dashboard = await teacherService.getTeacherDashboard({
        tenantId,
        teacherId,
      });
      return reply.code(200).send(dashboard);
    } catch (error) {
      app.log.error(error, "Failed to get teacher dashboard");
      return reply.code(500).send({
        error: "Failed to get dashboard",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /teacher/classrooms
   * Create a new classroom
   */
  app.post("/classrooms", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const teacherId = request.headers["x-user-id"] as UserId;

    if (!tenantId || !teacherId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    const { name, description } = request.body as {
      name: string;
      description?: string;
    };

    if (!name) {
      return reply.code(400).send({ error: "Classroom name is required" });
    }

    try {
      const classroom = await teacherService.createClassroom({
        tenantId,
        teacherId,
        name,
        description,
      });
      return reply.code(201).send(classroom);
    } catch (error) {
      app.log.error(error, "Failed to create classroom");
      return reply.code(500).send({
        error: "Failed to create classroom",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /teacher/classrooms/:classroomId
   * Get classroom details with roster and assignments
   */
  app.get("/classrooms/:classroomId", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { classroomId } = request.params as { classroomId: ClassroomId };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const classroom = await teacherService.getClassroom({
        tenantId,
        classroomId,
      });
      return reply.code(200).send(classroom);
    } catch (error) {
      app.log.error(error, "Failed to get classroom");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to get classroom",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * POST /teacher/classrooms/:classroomId/students
   * Add a student to a classroom
   */
  app.post("/classrooms/:classroomId/students", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { classroomId } = request.params as { classroomId: ClassroomId };
    const { studentId, displayName, email } = request.body as {
      studentId: UserId;
      displayName: string;
      email?: string;
    };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!studentId || !displayName) {
      return reply
        .code(400)
        .send({ error: "Student ID and display name are required" });
    }

    try {
      const classroom = await teacherService.addStudentToClassroom({
        tenantId,
        classroomId,
        studentId,
        displayName,
        email,
      });
      return reply.code(200).send(classroom);
    } catch (error) {
      app.log.error(error, "Failed to add student to classroom");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to add student",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * DELETE /teacher/classrooms/:classroomId/students/:studentId
   * Remove a student from a classroom
   */
  app.delete(
    "/classrooms/:classroomId/students/:studentId",
    async (request, reply) => {
      const tenantId = request.headers["x-tenant-id"] as TenantId;
      const { classroomId, studentId } = request.params as {
        classroomId: ClassroomId;
        studentId: UserId;
      };

      if (!tenantId) {
        return reply.code(401).send({ error: "Authentication required" });
      }

      try {
        const classroom = await teacherService.removeStudentFromClassroom({
          tenantId,
          classroomId,
          studentId,
        });
        return reply.code(200).send(classroom);
      } catch (error) {
        app.log.error(error, "Failed to remove student from classroom");
        if (error instanceof Error && error.message.includes("not found")) {
          return reply.code(404).send({ error: error.message });
        }
        return reply.code(500).send({
          error: "Failed to remove student",
          message: error instanceof Error ? error.message : "Unknown error",
        });
      }
    },
  );

  /**
   * POST /teacher/classrooms/:classroomId/assignments
   * Assign a module to a classroom
   */
  app.post("/classrooms/:classroomId/assignments", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { classroomId } = request.params as { classroomId: ClassroomId };
    const { moduleId, dueAt } = request.body as {
      moduleId: ModuleId;
      dueAt?: string;
    };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    if (!moduleId) {
      return reply.code(400).send({ error: "Module ID is required" });
    }

    try {
      const classroom = await teacherService.assignModule({
        tenantId,
        classroomId,
        moduleId,
        dueAt,
      });
      return reply.code(200).send(classroom);
    } catch (error) {
      app.log.error(error, "Failed to assign module");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to assign module",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /teacher/classrooms/:classroomId/progress
   * Get progress report for all students in a classroom
   */
  app.get("/classrooms/:classroomId/progress", async (request, reply) => {
    const tenantId = request.headers["x-tenant-id"] as TenantId;
    const { classroomId } = request.params as { classroomId: ClassroomId };

    if (!tenantId) {
      return reply.code(401).send({ error: "Authentication required" });
    }

    try {
      const progress = await teacherService.getClassroomProgress({
        tenantId,
        classroomId,
      });
      return reply.code(200).send(progress);
    } catch (error) {
      app.log.error(error, "Failed to get classroom progress");
      if (error instanceof Error && error.message.includes("not found")) {
        return reply.code(404).send({ error: error.message });
      }
      return reply.code(500).send({
        error: "Failed to get progress",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

  /**
   * GET /teacher/health
   * Health check endpoint
   */
  app.get("/health", async (request, reply) => {
    try {
      const healthy = await teacherService.checkHealth();
      return reply
        .code(200)
        .send({ status: healthy ? "healthy" : "unhealthy" });
    } catch (error) {
      return reply.code(503).send({ status: "unhealthy" });
    }
  });

  app.log.info("Teacher routes registered");
};
