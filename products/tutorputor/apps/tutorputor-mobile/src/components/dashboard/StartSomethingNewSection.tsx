/**
 * Start Something New Section
 *
 * Shows AI-suggested modules for new learning.
 *
 * @doc.type component
 * @doc.purpose AI-suggested content discovery
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
} from 'react-native';

interface RecommendedModule {
  id: string;
  title: string;
  slug: string;
  description?: string;
  tags: string[];
  estimatedMinutes?: number;
  difficulty?: string;
  domain?: string;
}

interface StartSomethingNewSectionProps {
  recommendations: RecommendedModule[];
  isLoading: boolean;
  onExplore: () => void;
  onModulePress: (moduleId: string) => void;
}

export function StartSomethingNewSection({
  recommendations,
  isLoading,
  onExplore,
  onModulePress,
}: StartSomethingNewSectionProps): React.ReactElement {
  const [showAll, setShowAll] = useState(false);
  
  const displayModules = showAll ? recommendations : recommendations.slice(0, 3);
  const hasMore = recommendations.length > 3;

  if (isLoading) {
    return (
      <View style={styles.container}>
        <Text style={styles.sectionTitle}>Start Something New</Text>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="small" color="#4F46E5" />
          <Text style={styles.loadingText}>Finding recommendations...</Text>
        </View>
      </View>
    );
  }

  if (!recommendations || recommendations.length === 0) {
    return (
      <View style={styles.container}>
        <Text style={styles.sectionTitle}>Start Something New</Text>
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>🎯</Text>
          <Text style={styles.emptyTitle}>Explore our catalog</Text>
          <Text style={styles.emptyDescription}>
            Discover modules across science, technology, engineering, arts, and math.
          </Text>
          <TouchableOpacity style={styles.exploreButton} onPress={onExplore}>
            <Text style={styles.exploreButtonText}>Browse All Modules</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.sectionTitle}>Start Something New</Text>
        <TouchableOpacity onPress={onExplore}>
          <Text style={styles.exploreLink}>Explore →</Text>
        </TouchableOpacity>
      </View>
      
      <Text style={styles.subtitle}>
        AI-suggested based on your interests
      </Text>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
      >
        {displayModules.map((module) => (
          <TouchableOpacity
            key={module.id}
            style={styles.moduleCard}
            onPress={() => onModulePress(module.id)}
            activeOpacity={0.8}
          >
            <View style={styles.cardHeader}>
              <View style={styles.domainBadge}>
                <Text style={styles.domainText}>{module.domain || 'General'}</Text>
              </View>
              {module.difficulty && (
                <View style={[styles.difficultyBadge, getDifficultyStyle(module.difficulty)]}>
                  <Text style={styles.difficultyText}>{module.difficulty}</Text>
                </View>
              )}
            </View>

            <Text style={styles.moduleTitle} numberOfLines={2}>
              {module.title}
            </Text>
            
            <Text style={styles.moduleDescription} numberOfLines={2}>
              {module.description || 'Learn more about this topic'}
            </Text>

            <View style={styles.cardFooter}>
              <Text style={styles.durationText}>
                ⏱️ {module.estimatedMinutes || 30} min
              </Text>
              {module.tags && module.tags.length > 0 && (
                <View style={styles.tagContainer}>
                  <Text style={styles.tagText}>{module.tags[0]}</Text>
                </View>
              )}
            </View>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {hasMore && !showAll && (
        <TouchableOpacity 
          style={styles.showMoreButton} 
          onPress={() => setShowAll(true)}
        >
          <Text style={styles.showMoreText}>
            Show {recommendations.length - 3} more recommendations
          </Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

function getDifficultyStyle(difficulty: string): { backgroundColor: string } {
  switch (difficulty.toLowerCase()) {
    case 'beginner':
      return { backgroundColor: '#D1FAE5' };
    case 'intermediate':
      return { backgroundColor: '#FEF3C7' };
    case 'advanced':
      return { backgroundColor: '#FEE2E2' };
    default:
      return { backgroundColor: '#F3F4F6' };
  }
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
  },
  subtitle: {
    fontSize: 13,
    color: '#6B7280',
    marginBottom: 12,
  },
  exploreLink: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '500',
  },
  scrollContent: {
    paddingRight: 16,
    gap: 12,
  },
  moduleCard: {
    width: 280,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginRight: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  domainBadge: {
    backgroundColor: '#EEF2FF',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  domainText: {
    fontSize: 11,
    color: '#4F46E5',
    fontWeight: '500',
    textTransform: 'uppercase',
  },
  difficultyBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  difficultyText: {
    fontSize: 11,
    color: '#374151',
    fontWeight: '500',
    textTransform: 'capitalize',
  },
  moduleTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  moduleDescription: {
    fontSize: 13,
    color: '#6B7280',
    marginBottom: 12,
    lineHeight: 18,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  durationText: {
    fontSize: 12,
    color: '#6B7280',
  },
  tagContainer: {
    backgroundColor: '#F3F4F6',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  tagText: {
    fontSize: 11,
    color: '#4B5563',
  },
  loadingContainer: {
    padding: 24,
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 8,
    fontSize: 13,
    color: '#6B7280',
  },
  emptyContainer: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 24,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  emptyIcon: {
    fontSize: 40,
    marginBottom: 12,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  emptyDescription: {
    fontSize: 13,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 16,
    lineHeight: 18,
  },
  exploreButton: {
    backgroundColor: '#4F46E5',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
  },
  exploreButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
  showMoreButton: {
    marginTop: 12,
    alignItems: 'center',
  },
  showMoreText: {
    fontSize: 13,
    color: '#4F46E5',
    fontWeight: '500',
  },
});
