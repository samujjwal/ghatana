import React, { createContext, useContext, ReactNode } from 'react';
import { Alert, AlertTitle, Button, Box, Slide, IconButton } from '@mui/material';
import { Close } from '@mui/icons-material';
import { useNotifications, type Notification } from '../hooks/useNotifications';

interface NotificationContextType {
  showNotification: (notification: Omit<Notification, 'id' | 'timestamp'>) => string;
  dismissNotification: (id: string) => void;
  clearAll: () => void;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export const useNotificationContext = () => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotificationContext must be used within NotificationProvider');
  }
  return context;
};

interface NotificationProviderProps {
  children: ReactNode;
}

export const NotificationProvider: React.FC<NotificationProviderProps> = ({ children }) => {
  const { notifications, showNotification, dismissNotification, clearAll } = useNotifications();

  return (
    <NotificationContext.Provider value={{ showNotification, dismissNotification, clearAll }}>
      {children}
      
      {/* Render notifications */}
      <Box sx={{ position: 'fixed', top: 16, right: 16, zIndex: 9999, width: 400 }}>
        {notifications.map((notification) => (
          <Slide
            key={notification.id}
            direction="left"
            in={true}
            mountOnEnter
            unmountOnExit
          >
            <Box sx={{ mb: 1 }}>
              <Alert
                severity={notification.type}
                action={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {notification.actions?.map((action, index) => (
                      <Button
                        key={index}
                        size="small"
                        variant={action.primary ? 'contained' : 'text'}
                        onClick={action.onClick}
                      >
                        {action.label}
                      </Button>
                    ))}
                    <IconButton
                      size="small"
                      onClick={() => dismissNotification(notification.id)}
                      color="inherit"
                    >
                      <Close fontSize="small" />
                    </IconButton>
                  </Box>
                }
                sx={{
                  boxShadow: 3,
                  borderRadius: 1,
                }}
              >
                {notification.title && <AlertTitle>{notification.title}</AlertTitle>}
                {notification.message}
              </Alert>
            </Box>
          </Slide>
        ))}
      </Box>
    </NotificationContext.Provider>
  );
};