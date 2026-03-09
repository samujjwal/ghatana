import { act, renderHook } from '@testing-library/react-hooks';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { apiMock } from '../mocks/api.mock';
import { deviceListFixture } from '../fixtures/device.fixtures';
import { alertsFixture } from '../fixtures/alert.fixtures';
import { policiesFixture } from '../fixtures/policy.fixtures';
import {
  useDevices,
  useAlerts,
  usePolicies,
  useUsageData,
} from '@/hooks/useApi';

type WrapperProps = { children: React.ReactNode };

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        // Keep cached data fresh in tests to avoid automatic refetches
        // between unmount/mount boundaries which can make call counts flaky.
        staleTime: Infinity,
      },
    },
  });

const createWrapper = () => {
  const client = createQueryClient();
  const Wrapper: React.FC<WrapperProps> = ({ children }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { Wrapper, client };
};

describe('useApi hooks', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('useDevices', () => {
    it('fetches device list', async () => {
      apiMock.getDevices.mockResolvedValueOnce(deviceListFixture);
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => useDevices(), { wrapper: Wrapper });

      await waitFor(() => expect(result.current.data).toEqual(deviceListFixture));
      expect(apiMock.getDevices).toHaveBeenCalledTimes(1);
    });

    it('reuses cache on subsequent renders', async () => {
      apiMock.getDevices.mockResolvedValue(deviceListFixture);
      const { Wrapper, client } = createWrapper();

      const first = renderHook(() => useDevices(), { wrapper: Wrapper });
      await first.waitFor(() => first.result.current.isSuccess);
      expect(apiMock.getDevices).toHaveBeenCalledTimes(1);

      first.unmount();

      const second = renderHook(() => useDevices(), {
        wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
      });

      await second.waitFor(() => second.result.current.isSuccess);
      expect(apiMock.getDevices).toHaveBeenCalledTimes(1);
    });

    it('refetches on demand', async () => {
      apiMock.getDevices.mockResolvedValue(deviceListFixture);
      const { Wrapper } = createWrapper();
      const { result, waitFor } = renderHook(() => useDevices(), { wrapper: Wrapper });

      await waitFor(() => result.current.isSuccess);
      expect(apiMock.getDevices).toHaveBeenCalledTimes(1);

      apiMock.getDevices.mockResolvedValueOnce(deviceListFixture.slice(0, 1));

      await act(async () => {
        await result.current.refetch();
      });

      expect(apiMock.getDevices).toHaveBeenCalledTimes(2);
    });

    it('handles errors', async () => {
      apiMock.getDevices.mockRejectedValueOnce(new Error('Network'));
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => useDevices(), { wrapper: Wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error).toBeInstanceOf(Error);
    });
  });

  describe('useAlerts', () => {
    it('fetches alerts data', async () => {
      apiMock.getAlerts.mockResolvedValueOnce(alertsFixture);
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => useAlerts(), { wrapper: Wrapper });

      await waitFor(() => expect(result.current.data).toEqual(alertsFixture));
      expect(apiMock.getAlerts).toHaveBeenCalledTimes(1);
    });

    it('supports manual refetch', async () => {
      apiMock.getAlerts.mockResolvedValue(alertsFixture);
      const { Wrapper } = createWrapper();
      const { result, waitFor } = renderHook(() => useAlerts(), { wrapper: Wrapper });

      await waitFor(() => result.current.isSuccess);

      await act(async () => {
        await result.current.refetch();
      });

      expect(apiMock.getAlerts).toHaveBeenCalledTimes(2);
    });

    it('exposes error state', async () => {
      apiMock.getAlerts.mockRejectedValueOnce(new Error('timeout'));
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => useAlerts(), { wrapper: Wrapper });

      await waitFor(() => result.current.isError);
      expect(result.current.error).toBeInstanceOf(Error);
    });
  });

  describe('usePolicies', () => {
    it('fetches all policies', async () => {
      apiMock.getPolicies.mockResolvedValueOnce(policiesFixture);
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => usePolicies(), { wrapper: Wrapper });

      await waitFor(() => expect(result.current.data).toEqual(policiesFixture));
      expect(apiMock.getPolicies).toHaveBeenCalledWith(undefined);
    });

    it('fetches policies for device', async () => {
      apiMock.getPolicies.mockResolvedValueOnce(policiesFixture.slice(0, 1));
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => usePolicies('device-1'), {
        wrapper: Wrapper,
      });

      await waitFor(() => result.current.isSuccess);
      expect(apiMock.getPolicies).toHaveBeenCalledWith('device-1');
    });

    it('reports fetch errors', async () => {
      apiMock.getPolicies.mockRejectedValueOnce(new Error('Server error'));
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => usePolicies(), { wrapper: Wrapper });

      await waitFor(() => result.current.isError);
      expect(result.current.error).toBeInstanceOf(Error);
    });
  });

  describe('useUsageData', () => {
    it('fetches usage data when device id provided', async () => {
      const usage = [
        { date: '2024-01-01', screenTime: 120, appUsage: [], websiteVisits: [] },
      ];
      apiMock.getUsageData.mockResolvedValueOnce(usage);
      const { Wrapper } = createWrapper();

      const { result, waitFor } = renderHook(() => useUsageData('device-1'), {
        wrapper: Wrapper,
      });

      await waitFor(() => expect(result.current.data).toEqual(usage));
    });

    it('disables query when device id missing', () => {
      const { Wrapper } = createWrapper();
      const { result } = renderHook(() => useUsageData(''), { wrapper: Wrapper });

      expect(result.current.isFetching).toBe(false);
      expect(apiMock.getUsageData).not.toHaveBeenCalled();
    });
  });
});
