/**
 * Department API Integration Tests
 *
 * <p><b>Purpose</b><br>
 * Integration tests for Department API endpoints.
 * Tests all department operations and KPI tracking.
 *
 * <p><b>Coverage</b><br>
 * - List departments
 * - Get department details
 * - Get department agents
 * - Get department KPIs
 * - Get department workflows
 * - Error handling
 *
 * @doc.type test
 * @doc.purpose Department API integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */

import { describe, it, expect } from 'vitest';
import './setup';

const API_BASE_URL = 'http://localhost:8080';

describe('Department API Integration Tests', () => {
    describe('GET /api/v1/departments', () => {
        it('should list all departments', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments`);
            const departments = await response.json();

            expect(response.ok).toBe(true);
            expect(departments).toBeInstanceOf(Array);
            expect(departments.length).toBeGreaterThan(0);
        });

        it('should return departments with required properties', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments`);
            const departments = await response.json();

            departments.forEach((dept: any) => {
                expect(dept).toHaveProperty('id');
                expect(dept).toHaveProperty('name');
                expect(dept).toHaveProperty('type');
                expect(dept).toHaveProperty('agentCount');
                expect(dept).toHaveProperty('status');
            });
        });
    });

    describe('GET /api/v1/departments/:id', () => {
        it('should get department details', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1`);
            const department = await response.json();

            expect(response.ok).toBe(true);
            expect(department.id).toBe('dept-1');
            expect(department.name).toBe('Engineering');
        });

        it('should return 404 for non-existent department', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/non-existent`);

            expect(response.status).toBe(404);
        });

        it('should include agents and workflows', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1`);
            const department = await response.json();

            expect(department).toHaveProperty('agents');
            expect(department).toHaveProperty('workflows');
            expect(department.agents).toBeInstanceOf(Array);
            expect(department.workflows).toBeInstanceOf(Array);
        });
    });

    describe('GET /api/v1/departments/:id/agents', () => {
        it('should list department agents', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/agents`);
            const agents = await response.json();

            expect(response.ok).toBe(true);
            expect(agents).toBeInstanceOf(Array);
        });

        it('should return agents with required properties', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/agents`);
            const agents = await response.json();

            agents.forEach((agent: any) => {
                expect(agent).toHaveProperty('id');
                expect(agent).toHaveProperty('name');
                expect(agent).toHaveProperty('role');
                expect(agent).toHaveProperty('status');
            });
        });
    });

    describe('GET /api/v1/departments/:id/kpis', () => {
        it('should get department KPIs', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/kpis`);
            const kpis = await response.json();

            expect(response.ok).toBe(true);
            expect(kpis).toBeDefined();
            expect(kpis.departmentId).toBe('dept-1');
        });

        it('should return KPIs with required metrics', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/kpis`);
            const kpis = await response.json();

            expect(kpis).toHaveProperty('velocity');
            expect(kpis).toHaveProperty('throughput');
            expect(kpis).toHaveProperty('quality');
            expect(kpis).toHaveProperty('efficiency');
            expect(kpis).toHaveProperty('timestamp');
        });

        it('should have valid KPI values', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/kpis`);
            const kpis = await response.json();

            expect(typeof kpis.velocity).toBe('number');
            expect(typeof kpis.throughput).toBe('number');
            expect(typeof kpis.quality).toBe('number');
            expect(typeof kpis.efficiency).toBe('number');
            expect(kpis.velocity).toBeGreaterThanOrEqual(0);
            expect(kpis.velocity).toBeLessThanOrEqual(100);
        });
    });

    describe('GET /api/v1/departments/:id/workflows', () => {
        it('should list department workflows', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/workflows`);
            const workflows = await response.json();

            expect(response.ok).toBe(true);
            expect(workflows).toBeInstanceOf(Array);
        });

        it('should return workflows with required properties', async () => {
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/dept-1/workflows`);
            const workflows = await response.json();

            workflows.forEach((workflow: any) => {
                expect(workflow).toHaveProperty('id');
                expect(workflow).toHaveProperty('name');
                expect(workflow).toHaveProperty('type');
                expect(workflow).toHaveProperty('status');
            });
        });
    });
});
