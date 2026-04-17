import type { FastifyPluginAsync } from "fastify";
import { TeacherServiceImpl } from "./service";
import { z } from "zod";
import {
  getTenantId,
  getUserId,
  requireRole,
} from "../../../core/http/requestContext.js";
import type {
  TenantId,
  UserId,
  ClassroomId,
  ModuleId,
} from "@tutorputor/contracts/v1/types";

type TeacherRoutesService = Pick<
  TeacherServiceImpl,
  | "getTeacherDashboard"
  | "createClassroom"
  | "getClassroom"
  | "addStudentToClassroom"
  | "removeStudentFromClassroom"
  | "assignModule"
  | "getClassroomProgress"
  | "checkHealth"
>;

type TeacherRoutesOptions = {
  service?: TeacherRoutesService;
};

const ClassroomParamsSchema = z.object({
  classroomId: z.string().min(1),
});

const ClassroomStudentParamsSchema = z.object({
  classroomId: z.string().min(1),
  studentId: z.string().min(1),
});

const CreateClassroomBodySchema = z.object({
  name: z.string().min(1),
  description: z.string().min(1).optional(),
});

const AddStudentBodySchema = z.object({
  studentId: z.string().min(1),
  displayName: z.string().min(1),
  email: z.string().email().optional(),
});

const AssignModuleBodySchema = z.object({
  moduleId: z.string().min(1),
  dueAt: z
    .string()
    .refine((value) => !Number.isNaN(Date.parse(value)), {
      message: "Invalid date format",
    })
    .optional(),
});

function createValidationErrorResponse(error: z.ZodError) {
  const primaryIssue = error.issues[0];
  return {
    error: "Validation Error",
    message: primaryIssue?.message ?? "Invalid request payload",
  };
}

/**
 * Teacher routes - classroom management and student progress tracking.
 *
 * @doc.type routes
 * @doc.purpose HTTP endpoints for teacher operations
 * @doc.layer product
 * @doc.pattern REST API
 */
export const teacherRoutes: FastifyPluginAsync<TeacherRoutesOptions> = async (
  app,
  options,
) => {
  const teacherService = options.service ?? new TeacherServiceImpl(app.prisma);
  const teacherRoles = ["teacher", "admin", "superadmin"];

  /**
   * GET /teacher/dashboard
   * Get teacher dashboard with classrooms, students, and recent activity
   */
  app.get("/dashboard", async (request, reply) => {
    const tenantId = getTenantId(request) as TenantId;
    const teacherId = getUserId(request) as UserId;
    requireRole(request, teacherRoles);

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
    const tenantId = getTenantId(request) as TenantId;
    const teacherId = getUserId(request) as UserId;
    requireRole(request, teacherRoles);

    const parseResult = CreateClassroomBodySchema.safeParse(request.body);
    if (!parseResult.success) {
      return reply.code(400).send(createValidationErrorResponse(parseResult.error));
    }

    const { name, description } = parseResult.data;

    try {
      const classroom = await teacherService.createClassroom({
        tenantId,
        teacherId,
        name,
        ...(description ? { description } : {}),
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
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, teacherRoles);

    const paramsParseResult = ClassroomParamsSchema.safeParse(request.params);
    if (!paramsParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(paramsParseResult.error));
    }

    const { classroomId } = paramsParseResult.data;

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
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, teacherRoles);

    const paramsParseResult = ClassroomParamsSchema.safeParse(request.params);
    if (!paramsParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(paramsParseResult.error));
    }

    const bodyParseResult = AddStudentBodySchema.safeParse(request.body);
    if (!bodyParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(bodyParseResult.error));
    }

    const { classroomId } = paramsParseResult.data;
    const { studentId, displayName, email } = bodyParseResult.data;

    try {
      const classroom = await teacherService.addStudentToClassroom({
        tenantId,
        classroomId,
        studentId,
        displayName,
        ...(email ? { email } : {}),
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
      const tenantId = getTenantId(request) as TenantId;
      requireRole(request, teacherRoles);

      const paramsParseResult = ClassroomStudentParamsSchema.safeParse(
        request.params,
      );
      if (!paramsParseResult.success) {
        return reply
          .code(400)
          .send(createValidationErrorResponse(paramsParseResult.error));
      }

      const { classroomId, studentId } = paramsParseResult.data;

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
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, teacherRoles);

    const paramsParseResult = ClassroomParamsSchema.safeParse(request.params);
    if (!paramsParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(paramsParseResult.error));
    }

    const bodyParseResult = AssignModuleBodySchema.safeParse(request.body);
    if (!bodyParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(bodyParseResult.error));
    }

    const { classroomId } = paramsParseResult.data;
    const { moduleId, dueAt } = bodyParseResult.data;

    try {
      const dueAtIso = dueAt ? new Date(dueAt).toISOString() : undefined;
      const classroom = await teacherService.assignModule({
        tenantId,
        classroomId,
        moduleId,
        ...(dueAtIso ? { dueAt: dueAtIso } : {}),
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
    const tenantId = getTenantId(request) as TenantId;
    requireRole(request, teacherRoles);

    const paramsParseResult = ClassroomParamsSchema.safeParse(request.params);
    if (!paramsParseResult.success) {
      return reply
        .code(400)
        .send(createValidationErrorResponse(paramsParseResult.error));
    }

    const { classroomId } = paramsParseResult.data;

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
  app.get("/health", async (_request, reply) => {
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
