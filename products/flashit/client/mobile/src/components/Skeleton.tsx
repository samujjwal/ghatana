/**
 * Skeleton Loading Components
 * 
 * Provides consistent skeleton loading states across the app
 * 
 * @doc.type component
 * @doc.purpose Skeleton loading states for better UX
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { View, StyleSheet, ViewStyle, DimensionValue } from 'react-native';

interface SkeletonProps {
  width?: DimensionValue;
  height?: DimensionValue;
  borderRadius?: number;
  style?: ViewStyle;
}

/**
 * Basic skeleton element
 */
export const Skeleton: React.FC<SkeletonProps> = ({
  width = '100%',
  height = 20,
  borderRadius = 4,
  style,
}) => {
  return (
    <View
      style={[
        styles.skeleton,
        {
          width,
          height,
          borderRadius,
        },
        style,
      ]}
    />
  );
};

interface SkeletonTextProps {
  lines?: number;
  width?: DimensionValue | DimensionValue[]; // Single width or array of widths for each line
  style?: ViewStyle;
}

/**
 * Skeleton for text lines
 */
export const SkeletonText: React.FC<SkeletonTextProps> = ({
  lines = 3,
  width = '100%',
  style,
}) => {
  const widths = Array.isArray(width) ? width : Array(lines).fill(width);
  
  return (
    <View style={[styles.textContainer, style]}>
      {widths.slice(0, lines).map((w, index) => (
        <Skeleton
          key={index}
          width={w}
          height={16}
          borderRadius={4}
          style={index === lines - 1 ? styles.lastLine : {}}
        />
      ))}
    </View>
  );
};

interface SkeletonMomentProps {
  style?: ViewStyle;
}

/**
 * Skeleton for moment cards
 */
export const SkeletonMoment: React.FC<SkeletonMomentProps> = ({ style }) => {
  return (
    <View style={[styles.momentCard, style]}>
      <View style={styles.momentHeader}>
        <Skeleton width={40} height={40} borderRadius={20} />
        <View style={styles.momentHeaderText}>
          <Skeleton width={120} height={16} />
          <Skeleton width={80} height={12} style={styles.momentDate} />
        </View>
      </View>
      <View style={styles.momentContent}>
        <SkeletonText lines={3} width={['100%', '90%', '70%']} />
      </View>
      <View style={styles.momentFooter}>
        <Skeleton width={60} height={24} borderRadius={12} />
        <Skeleton width={80} height={24} borderRadius={12} />
      </View>
    </View>
  );
};

interface SkeletonSphereProps {
  style?: ViewStyle;
}

/**
 * Skeleton for sphere cards
 */
export const SkeletonSphere: React.FC<SkeletonSphereProps> = ({ style }) => {
  return (
    <View style={[styles.sphereCard, style]}>
      <Skeleton width={60} height={60} borderRadius={30} />
      <View style={styles.sphereContent}>
        <Skeleton width={100} height={18} />
        <Skeleton width={140} height={14} style={styles.sphereDescription} />
      </View>
    </View>
  );
};

interface SkeletonListProps {
  items?: number;
  component?: 'moment' | 'sphere';
  style?: ViewStyle;
}

/**
 * Skeleton for lists of items
 */
export const SkeletonList: React.FC<SkeletonListProps> = ({
  items = 5,
  component = 'moment',
  style,
}) => {
  const SkeletonComponent = component === 'moment' ? SkeletonMoment : SkeletonSphere;
  
  return (
    <View style={[styles.list, style]}>
      {Array.from({ length: items }).map((_, index) => (
        <SkeletonComponent key={index} style={styles.listItem} />
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  skeleton: {
    backgroundColor: '#e5e5e5',
    overflow: 'hidden',
  },
  textContainer: {
    gap: 8,
  },
  lastLine: {
    marginBottom: 0,
  },
  momentCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginHorizontal: 16,
    marginVertical: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    gap: 12,
  },
  momentHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  momentHeaderText: {
    flex: 1,
    gap: 4,
  },
  momentDate: {
    opacity: 0.7,
  },
  momentContent: {
    gap: 8,
  },
  momentFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  sphereCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginHorizontal: 16,
    marginVertical: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  sphereContent: {
    flex: 1,
    gap: 4,
  },
  sphereDescription: {
    opacity: 0.7,
  },
  list: {
    gap: 8,
  },
  listItem: {
    marginBottom: 8,
  },
});

export default {
  Skeleton,
  SkeletonText,
  SkeletonMoment,
  SkeletonSphere,
  SkeletonList,
};
