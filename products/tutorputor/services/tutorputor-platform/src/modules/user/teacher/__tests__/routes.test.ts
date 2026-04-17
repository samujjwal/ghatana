import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { teacherRoutes } from "../routes.js";

describe("teacherRoutes", () => {
  const service = {
    getTeacherDashboard: vi.fn(),
    createClassroom: vi.fn(),
    getClassroom: vi.fn(),
    addStudentToClassroom: vi.fn(),
    removeStudentFromClassroom: vi.fn(),
    assignModule: vi.fn(),
    getClassroomProgress: vi.fn(),
    checkHealth: vi.fn(),
  };

  let app: ReturnType<typeof Fastify>;

  beforeEach(async () => {
    vi.clearAllMocks();
    service.createClassroom.mockResolvedValue({ id: "classroom-1" });
    service.assignModule.mockResolvedValue({ id: "classroom-1" });

    app = Fastify();
    await app.register(teacherRoutes, { service: service as never });
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it("rejects classroom creation when name is missing", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/classrooms",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        description: "Physics lab",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.createClassroom).not.toHaveBeenCalled();
  });

  it("forwards valid classroom creation payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/classrooms",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-id": "teacher-1",
        "x-user-role": "teacher",
      },
      payload: {
        name: "Physics 101",
        description: "Foundational mechanics",
      },
    });

    expect(response.statusCode).toBe(201);
    expect(service.createClassroom).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      teacherId: "teacher-1",
      name: "Physics 101",
      description: "Foundational mechanics",
    });
  });

  it("rejects invalid student enrollment payloads", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/classrooms/classroom-1/students",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        studentId: "",
        displayName: "",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.addStudentToClassroom).not.toHaveBeenCalled();
  });

  it("rejects assignment payloads with invalid dueAt dates", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/classrooms/classroom-1/assignments",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        moduleId: "module-1",
        dueAt: "not-a-date",
      },
    });

    expect(response.statusCode).toBe(400);
    expect(service.assignModule).not.toHaveBeenCalled();
  });

  it("forwards valid module assignments", async () => {
    const response = await app.inject({
      method: "POST",
      url: "/classrooms/classroom-1/assignments",
      headers: {
        "x-tenant-id": "tenant-1",
        "x-user-role": "teacher",
      },
      payload: {
        moduleId: "module-1",
        dueAt: "2026-04-20T00:00:00.000Z",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(service.assignModule).toHaveBeenCalledWith({
      tenantId: "tenant-1",
      classroomId: "classroom-1",
      moduleId: "module-1",
      dueAt: "2026-04-20T00:00:00.000Z",
    });
  });
});
