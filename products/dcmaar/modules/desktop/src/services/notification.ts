import { enqueueSnackbar } from 'notistack';

type NotificationOptions = {
  title?: string;
  message: string;
  type?: 'error' | 'warning' | 'info' | 'success';
  persist?: boolean;
};

export const showNotification = (options: NotificationOptions) => {
  enqueueSnackbar(options.message, {
    variant: options.type || 'info',
    persist: options.persist !== undefined ? options.persist : options.type === 'error',
    anchorOrigin: {
      vertical: 'bottom',
      horizontal: 'right',
    },
  });
};

export const showApiError = (error: any) => {
  const message = error?.message || 'An unknown error occurred';
  const title = error?.response?.status
    ? `API Error (${error.response.status})`
    : 'API Error';

  showNotification({
    title,
    message,
    type: 'error',
    persist: true
  });
};
