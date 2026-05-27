import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { t } from '../i18n/phrMobileI18n';
import type { MobileNotificationItem } from '../types';

interface NotificationsScreenProps {
  notifications: MobileNotificationItem[];
  onEnablePush: () => void;
}

export function NotificationsScreen({ notifications, onEnablePush }: NotificationsScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <Pressable 
        onPress={onEnablePush} 
        style={styles.button}
        accessibilityRole="button"
        accessibilityLabel={t('notifications.enablePush')}
      >
        <Text style={styles.buttonText}>{t('notifications.enablePush')}</Text>
      </Pressable>
      {notifications.map((notification) => (
        <View key={notification.id} style={styles.card} accessibilityRole="text" accessibilityLabel={`${notification.title}. ${notification.detail}`}>
          <Text style={styles.title}>{notification.title}</Text>
          <Text style={styles.detail}>{notification.detail}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  button: { backgroundColor: '#123c84', borderRadius: 16, padding: 14, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: '700' },
  card: { backgroundColor: '#fff', borderRadius: 16, padding: 14, borderWidth: 1, borderColor: '#d5dded' },
  title: { fontWeight: '700', color: '#102243' },
  detail: { marginTop: 6, color: '#4b5c77' },
});