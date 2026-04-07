import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../services/auth/AuthService', () => ({
  authService: {
    login: vi.fn(),
  },
  isDemoLoginEnabled: vi.fn(() => false),
}));

import { authService } from '../../services/auth/AuthService';
import { clientAction } from '../../routes/login';

function createRequest(form: Record<string, string>): Request {
  const formData = new FormData();
  Object.entries(form).forEach(([key, value]) => formData.set(key, value));

  return new Request('http://localhost/login', {
    method: 'POST',
    body: formData,
  });
}

describe('login clientAction', () => {
  beforeEach(() => {
    vi.mocked(authService.login).mockReset();
  });

  it('validates missing credentials', async () => {
    const result = await clientAction({ request: createRequest({ email: '', password: '' }) } as never);

    expect(result).toEqual({ error: 'Email and password are required' });
    expect(authService.login).not.toHaveBeenCalled();
  });

  it('logs in with email and redirects to workspaces by default', async () => {
    vi.mocked(authService.login).mockResolvedValue({ success: true, token: 'access-token-1' });

    const response = await clientAction({
      request: createRequest({ email: 'sam@yappc.local', password: 'secret' }),
    } as never);

    expect(authService.login).toHaveBeenCalledWith({
      email: 'sam@yappc.local',
      password: 'secret',
    });
    expect(response).toBeInstanceOf(Response);
    expect((response as Response).headers.get('Location')).toBe('/workspaces');
  });

  it('preserves an explicit redirect target', async () => {
    vi.mocked(authService.login).mockResolvedValue({ success: true, token: 'access-token-1' });

    const response = await clientAction({
      request: createRequest({
        email: 'sam@yappc.local',
        password: 'secret',
        redirectTo: '/projects',
      }),
    } as never);

    expect((response as Response).headers.get('Location')).toBe('/projects');
  });

  it('returns the auth failure to the route', async () => {
    vi.mocked(authService.login).mockResolvedValue({ success: false, error: 'Invalid email or password' });

    const result = await clientAction({
      request: createRequest({ email: 'sam@yappc.local', password: 'wrong' }),
    } as never);

    expect(result).toEqual({ error: 'Invalid email or password' });
  });
});