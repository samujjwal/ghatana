import React, { useEffect, useState } from 'react';
import { Button, LinearProgress, Dialog, DialogTitle, DialogContent, DialogActions, Snackbar, Alert, Box, Typography, CircularProgress } from '@mui/material';
// Mock Tauri functions
const invoke = async (cmd: string, args?: unknown) => {
  console.log('Mock Tauri invoke:', cmd, args);
  return Promise.resolve({ message: 'Mock response' });
};

const listen = async (event: string, _handler: (payload: unknown) => void) => {
  console.log('Mock Tauri listen:', event);
  return Promise.resolve(() => {}); // Return unsubscribe function
};

const _appWindow = { 
  hide: () => console.log('Mock appWindow.hide()'),
  show: () => console.log('Mock appWindow.show()'),
  close: () => console.log('Mock appWindow.close()')
};

interface UpdateStatus {
  status: 'idle' | 'checking' | 'downloading' | 'installing' | 'error' | 'success';
  progress?: number;
  message?: string;
  updateInfo?: {
    version: string;
    date: string;
    body: string;
  };
}

const UpdateManager: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' | 'info' }>({
    open: false,
    message: '',
    severity: 'info',
  });
  const [status, setStatus] = useState<UpdateStatus>({ status: 'idle' });
  const [_updateAvailable, setUpdateAvailable] = useState(false);
  // mark mocks as intentionally unused in this environment
  void _appWindow;
  void _updateAvailable;

  // Listen for update events
  useEffect(() => {
    // subscribe to update events (mock listeners). Handlers intentionally unused in this mock implementation.
    const unlisten = async () => {
      const unlisteners = await Promise.all([
        listen('update-available', (_event) => {
          // reserved for production
          setUpdateAvailable(true);
        }),
        listen('update-status', (_event) => {
          // reserved for production
        }),
        listen('download-progress', (_event) => {
          // reserved for production
        }),
        listen('update-downloaded', () => {
          // reserved for production
        }),
        listen('app-updated', () => {
          // reserved for production
          setSnackbar({
            open: true,
            message: 'Application has been updated to the latest version!',
            severity: 'success',
          });
        }),
        listen('update-error', (_event) => {
          // reserved for production
        }),
        listen('no-update-available', (_event) => {
          // reserved for production
        }),
      ]);

      return () => {
        unlisteners.forEach((unlistenFn) => unlistenFn());
      };
    };

    const cleanup = unlisten();
    return () => {
      cleanup.then(fn => fn());
    };
  }, []);

  const checkForUpdates = async () => {
    try {
      setStatus({ status: 'checking', message: 'Checking for updates...' });
      await invoke('check_for_updates');
    } catch (error) {
      console.error('Failed to check for updates:', error);
      setStatus({
        status: 'error',
        message: 'Failed to check for updates',
      });
      setSnackbar({
        open: true,
        message: 'Failed to check for updates',
        severity: 'error',
      });
    }
  };

  const handleInstallUpdate = async () => {
    try {
      setStatus({ ...status, status: 'downloading', progress: 0 });
      await invoke('install_update');
    } catch (error) {
      console.error('Failed to install update:', error);
      setStatus({
        status: 'error',
        message: 'Failed to install update',
      });
      setSnackbar({
        open: true,
        message: 'Failed to install update',
        severity: 'error',
      });
    }
  };

  const handleClose = () => {
    setOpen(false);
    // Reset status after a short delay to allow the dialog to close
    setTimeout(() => {
      setStatus({ status: 'idle' });
    }, 300);
  };

  const handleSnackbarClose = () => {
    setSnackbar(prev => ({ ...prev, open: false }));
  };

  return (
    <>
      <Button 
        variant="outlined" 
        onClick={checkForUpdates}
        disabled={status.status === 'checking' || status.status === 'downloading'}
        startIcon={status.status === 'checking' ? <CircularProgress size={16} /> : null}
      >
        {status.status === 'checking' ? 'Checking...' : 'Check for Updates'}
      </Button>

      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle>Update Available</DialogTitle>
        <DialogContent>
          {status.updateInfo && (
            <Box mb={2}>
              <Typography variant="h6">Version {status.updateInfo.version}</Typography>
              <Typography variant="body2" color="textSecondary" gutterBottom>
                Released on {new Date(status.updateInfo.date).toLocaleDateString()}
              </Typography>
              <Typography variant="body1" style={{ whiteSpace: 'pre-line', margin: '16px 0' }}>
                {status.updateInfo.body}
              </Typography>
            </Box>
          )}

          {status.status === 'downloading' && (
            <Box mt={2}>
              <Typography variant="body2" gutterBottom>
                {status.message || 'Downloading update...'}
              </Typography>
              <LinearProgress 
                variant={typeof status.progress === 'number' ? 'determinate' : 'indeterminate'} 
                value={status.progress}
                style={{ marginTop: 8 }}
              />
              {typeof status.progress === 'number' && (
                <Typography variant="caption" display="block" align="right">
                  {Math.round(status.progress)}%
                </Typography>
              )}
            </Box>
          )}

          {status.status === 'installing' && (
            <Box display="flex" alignItems="center" mt={2}>
              <CircularProgress size={24} style={{ marginRight: 16 }} />
              <Typography>{status.message || 'Installing update...'}</Typography>
            </Box>
          )}

          {status.status === 'error' && (
            <Alert severity="error" style={{ marginTop: 16 }}>
              {status.message || 'An error occurred during the update process.'}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button 
            onClick={handleClose} 
            disabled={status.status === 'downloading' || status.status === 'installing'}
          >
            Remind Me Later
          </Button>
          <Button 
            onClick={handleInstallUpdate} 
            color="primary" 
            variant="contained"
            disabled={status.status === 'downloading' || status.status === 'installing'}
          >
            {status.status === 'downloading' ? 'Downloading...' : 'Install Update'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleSnackbarClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert onClose={handleSnackbarClose} severity={snackbar.severity}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};

export default UpdateManager;
