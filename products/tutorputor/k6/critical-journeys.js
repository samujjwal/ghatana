import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    dashboard_journey: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: 10 },
        { duration: '3m', target: 25 },
        { duration: '1m', target: 0 },
      ],
      exec: 'dashboardJourney',
    },
    ai_tutor_journey: {
      executor: 'ramping-vus',
      startTime: '30s',
      stages: [
        { duration: '1m', target: 8 },
        { duration: '3m', target: 20 },
        { duration: '1m', target: 0 },
      ],
      exec: 'aiTutorJourney',
    },
    content_generation_journey: {
      executor: 'constant-vus',
      startTime: '1m',
      vus: 5,
      duration: '4m',
      exec: 'contentGenerationJourney',
    },
    simulation_start_journey: {
      executor: 'constant-vus',
      startTime: '1m',
      vus: 5,
      duration: '4m',
      exec: 'simulationJourney',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1200'],
    'http_req_duration{journey:dashboard}': ['p(95)<400'],
    'http_req_duration{journey:ai_tutor}': ['p(95)<2000'],
    'http_req_duration{journey:content_generation}': ['p(95)<4000'],
    'http_req_duration{journey:simulation_start}': ['p(95)<1200'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:3200';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
const TENANT_ID = __ENV.TENANT_ID || '';

function authHeaders() {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (AUTH_TOKEN) {
    headers.Authorization = `Bearer ${AUTH_TOKEN}`;
  }

  if (TENANT_ID) {
    headers['X-Tenant-ID'] = TENANT_ID;
  }

  return headers;
}

export function dashboardJourney() {
  const res = http.get(`${BASE_URL}/api/v1/learning/dashboard`, {
    headers: authHeaders(),
    tags: { journey: 'dashboard' },
  });

  check(res, {
    'dashboard status is 200': (r) => r.status === 200,
  });

  sleep(1);
}

export function aiTutorJourney() {
  const payload = JSON.stringify({
    prompt: 'Explain Newton\'s second law with a practical example.',
    context: 'physics_grade_9',
  });

  const res = http.post(`${BASE_URL}/api/v1/ai/tutor`, payload, {
    headers: authHeaders(),
    tags: { journey: 'ai_tutor' },
  });

  check(res, {
    'ai tutor status is 200': (r) => r.status === 200,
  });

  sleep(1);
}

export function contentGenerationJourney() {
  const payload = JSON.stringify({
    moduleId: __ENV.MODULE_ID || 'module-load-test',
    topic: 'Linear equations and graph interpretation',
    domain: 'math',
    gradeBand: 'middle-school',
  });

  const res = http.post(`${BASE_URL}/api/content-studio/generation/jobs`, payload, {
    headers: authHeaders(),
    tags: { journey: 'content_generation' },
  });

  check(res, {
    'content generation enqueue status is 200/202': (r) => r.status === 200 || r.status === 202,
  });

  sleep(2);
}

export function simulationJourney() {
  const payload = JSON.stringify({
    prompt: 'Pendulum motion under gravity with adjustable length and damping',
    domain: 'physics',
  });

  const res = http.post(`${BASE_URL}/api/sim-author/generate`, payload, {
    headers: authHeaders(),
    tags: { journey: 'simulation_start' },
  });

  check(res, {
    'simulation generation status is 200': (r) => r.status === 200,
  });

  sleep(2);
}
