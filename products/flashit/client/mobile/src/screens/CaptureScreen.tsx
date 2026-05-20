import { emitFlashItMobileDiagnostic } from '@/diagnostics';
/**
 * Capture Screen for Flashit Mobile
 * Full text capture with emotions, tags, importance, and sphere selection
 *
 * @doc.type screen
 * @doc.purpose Primary interface for capturing text Moments on mobile
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue, useSetAtom } from 'jotai';
import { RootStackParamList } from '../navigation';
import { useApi } from '../contexts/ApiContext';
import { mobileAtoms } from '../state/localAtoms';
import { EMOTION_OPTIONS, TAG_SUGGESTIONS } from '@flashit/shared';
import { flashitMobileColors } from '@/styles/designTokens';

type Props = {
  navigation: NativeStackNavigationProp<RootStackParamList>;
};

export default function CaptureScreen({ navigation }: Props) {
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const selectedSphereId = useAtomValue(mobileAtoms.selectedSphereIdAtom);
  const setSelectedSphereId = useSetAtom(mobileAtoms.selectedSphereIdAtom);

  const handleGoBack = () => {
    try {
      if (navigation && typeof navigation.goBack === 'function') {
        navigation.goBack();
      }
    } catch (error) {
      emitFlashItMobileDiagnostic({ level: 'error', component: 'CaptureScreen', message: 'Navigation error', error });
    }
  };

  // Form state
  const [text, setText] = useState('');
  const [selectedEmotions, setSelectedEmotions] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [customTag, setCustomTag] = useState('');
  const [importance, setImportance] = useState(3);

  // Fetch spheres
  const { data: spheresData, isLoading: spheresLoading } = useQuery({
    queryKey: ['spheres'],
    queryFn: () => apiClient.getSpheres(),
  });

  // Create moment mutation
  const createMoment = useMutation({
    mutationFn: (data: Parameters<typeof apiClient.createMoment>[0]) =>
      apiClient.createMoment(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['moments'] });
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
      Alert.alert('Success', 'Moment captured!', [
        { text: 'OK', onPress: handleGoBack },
      ]);
    },
    onError: (error: any) => {
      Alert.alert('Error', error.response?.data?.message || 'Failed to capture moment');
    },
  });

  const toggleEmotion = (emotion: string) => {
    setSelectedEmotions((prev) =>
      prev.includes(emotion) ? prev.filter((e) => e !== emotion) : [...prev, emotion]
    );
  };

  const toggleTag = (tag: string) => {
    setSelectedTags((prev) =>
      prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]
    );
  };

  const addCustomTag = () => {
    if (customTag.trim() && !selectedTags.includes(customTag.trim())) {
      setSelectedTags((prev) => [...prev, customTag.trim()]);
      setCustomTag('');
    }
  };

  const handleSubmit = () => {
    if (!text.trim()) {
      Alert.alert('Error', 'Please enter some text for your moment');
      return;
    }

    if (!selectedSphereId) {
      Alert.alert('Error', 'Please select a Sphere');
      return;
    }

    createMoment.mutate({
      sphereId: selectedSphereId,
      content: {
        text: text.trim(),
        type: 'TEXT',
      },
      signals: {
        emotions: selectedEmotions,
        tags: selectedTags,
        importance,
      },
      capturedAt: new Date().toISOString(),
    });
  };

  if (spheresLoading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={flashitMobileColors.sky500} />
      </View>
    );
  }

  const spheres = spheresData || [];
  const selectedSphere = spheres.find((s) => s.id === selectedSphereId);

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView style={styles.scrollView} keyboardShouldPersistTaps="handled">
        {/* Sphere Selector */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Sphere</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View style={styles.sphereList}>
              {spheres.map((sphere) => (
                <TouchableOpacity
                  key={sphere.id}
                  style={[
                    styles.sphereChip,
                    selectedSphereId === sphere.id && styles.sphereChipSelected,
                  ]}
                  onPress={() => setSelectedSphereId(sphere.id)}
                  accessible={true}
                  accessibilityRole="button"
                  accessibilityLabel={`Select sphere ${sphere.name}`}
                  accessibilityState={{ selected: selectedSphereId === sphere.id }}
                >
                  <Text
                    style={[
                      styles.sphereChipText,
                      selectedSphereId === sphere.id && styles.sphereChipTextSelected,
                    ]}
                  >
                    {sphere.name}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </ScrollView>
          {selectedSphere && (
            <Text style={styles.sphereInfo}>
              {selectedSphere.type} • {selectedSphere.momentCount ?? 0} moments
            </Text>
          )}
        </View>

        {/* Text Input */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>What's happening? *</Text>
          <TextInput
            style={styles.textInput}
            placeholder="Write your moment here..."
            value={text}
            onChangeText={setText}
            multiline
            numberOfLines={6}
            textAlignVertical="top"
            editable={!createMoment.isPending}
            accessible={true}
            accessibilityLabel="Moment text"
            accessibilityHint="Type what's happening"
          />
          <Text style={styles.charCount}>{text.length} characters</Text>
        </View>

        {/* Emotions */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>How are you feeling?</Text>
          <View style={styles.chipGrid}>
            {EMOTION_OPTIONS.map((emotion) => (
              <TouchableOpacity
                key={emotion}
                style={[
                  styles.emotionChip,
                  selectedEmotions.includes(emotion) && styles.emotionChipSelected,
                ]}
                onPress={() => toggleEmotion(emotion)}
                disabled={createMoment.isPending}
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel={`Toggle emotion ${emotion}`}
                accessibilityState={{ selected: selectedEmotions.includes(emotion) }}
              >
                <Text
                  style={[
                    styles.emotionChipText,
                    selectedEmotions.includes(emotion) && styles.emotionChipTextSelected,
                  ]}
                >
                  {emotion}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Tags */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Add tags</Text>
          <View style={styles.chipGrid}>
            {TAG_SUGGESTIONS.map((tag) => (
              <TouchableOpacity
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel={`Toggle tag ${tag}`}
                accessibilityState={{ selected: selectedTags.includes(tag) }}
                key={tag}
                style={[styles.tagChip, selectedTags.includes(tag) && styles.tagChipSelected]}
                onPress={() => toggleTag(tag)}
                disabled={createMoment.isPending}
              >
                <Text
                  style={[
                    styles.tagChipText,
                    selectedTags.includes(tag) && styles.tagChipTextSelected,
                  ]}
                >
                  #{tag}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          {/* Custom tag input */}
          <View style={styles.customTagRow}>
            <TextInput
              style={styles.customTagInput}
              placeholder="Add custom tag..."
              value={customTag}
              onChangeText={setCustomTag}
              onSubmitEditing={addCustomTag}
              editable={!createMoment.isPending}
            />
            <TouchableOpacity
              style={styles.addTagButton}
              onPress={addCustomTag}
              disabled={createMoment.isPending}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Add custom tag"
            >
              <Text style={styles.addTagButtonText}>Add</Text>
            </TouchableOpacity>
          </View>

          {/* Selected tags */}
          {selectedTags.length > 0 && (
            <View style={styles.selectedTagsContainer}>
              <Text style={styles.selectedTagsLabel}>Selected:</Text>
              <View style={styles.selectedTagsList}>
                {selectedTags.map((tag) => (
                  <TouchableOpacity
                    key={tag}
                    style={styles.selectedTag}
                    onPress={() => toggleTag(tag)}
                    accessible={true}
                    accessibilityRole="button"
                    accessibilityLabel={`Remove tag ${tag}`}
                  >
                    <Text style={styles.selectedTagText}>#{tag} ×</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}
        </View>

        {/* Importance */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Importance (1-5)</Text>
          <View style={styles.importanceContainer}>
            {[1, 2, 3, 4, 5].map((value) => (
              <TouchableOpacity
                key={value}
                style={[
                  styles.importanceButton,
                  importance === value && styles.importanceButtonSelected,
                ]}
                onPress={() => setImportance(value)}
                disabled={createMoment.isPending}
                accessible={true}
                accessibilityRole="button"
                accessibilityLabel={`Set importance to ${value}`}
                accessibilityState={{ selected: importance === value }}
              >
                <Text
                  style={[
                    styles.importanceButtonText,
                    importance === value && styles.importanceButtonTextSelected,
                  ]}
                >
                  {value}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          <View style={styles.importanceLabels}>
            <Text style={styles.importanceLabelText}>Low</Text>
            <Text style={styles.importanceLabelText}>High</Text>
          </View>
        </View>

        {/* Submit Button */}
        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={[styles.cancelButton]}
            onPress={handleGoBack}
            disabled={createMoment.isPending}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Cancel capture"
          >
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[
              styles.submitButton,
              (createMoment.isPending || !selectedSphereId) && styles.submitButtonDisabled,
            ]}
            onPress={handleSubmit}
            disabled={createMoment.isPending || !selectedSphereId}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Submit moment"
            accessibilityState={{ disabled: createMoment.isPending || !selectedSphereId, busy: createMoment.isPending }}
          >
            {createMoment.isPending ? (
              <ActivityIndicator color={flashitMobileColors.white} size="small" />
            ) : (
              <Text style={styles.submitButtonText}>Capture Moment</Text>
            )}
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: flashitMobileColors.slate50,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollView: {
    flex: 1,
    padding: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: flashitMobileColors.slate800,
    marginBottom: 12,
  },
  sphereList: {
    flexDirection: 'row',
    gap: 8,
  },
  sphereChip: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: flashitMobileColors.white,
    borderWidth: 1,
    borderColor: flashitMobileColors.slate200,
  },
  sphereChipSelected: {
    backgroundColor: flashitMobileColors.sky500,
    borderColor: flashitMobileColors.sky500,
  },
  sphereChipText: {
    fontSize: 14,
    fontWeight: '500',
    color: flashitMobileColors.slate600,
  },
  sphereChipTextSelected: {
    color: flashitMobileColors.white,
  },
  sphereInfo: {
    fontSize: 12,
    color: flashitMobileColors.slate500,
    marginTop: 8,
  },
  textInput: {
    backgroundColor: flashitMobileColors.white,
    borderRadius: 12,
    padding: 16,
    fontSize: 16,
    minHeight: 150,
    borderWidth: 1,
    borderColor: flashitMobileColors.slate200,
  },
  charCount: {
    fontSize: 12,
    color: flashitMobileColors.slate400,
    marginTop: 8,
    textAlign: 'right',
  },
  chipGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  emotionChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: flashitMobileColors.slate100,
  },
  emotionChipSelected: {
    backgroundColor: flashitMobileColors.sky500,
  },
  emotionChipText: {
    fontSize: 13,
    fontWeight: '500',
    color: flashitMobileColors.slate600,
  },
  emotionChipTextSelected: {
    color: flashitMobileColors.white,
  },
  tagChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    backgroundColor: flashitMobileColors.slate100,
  },
  tagChipSelected: {
    backgroundColor: flashitMobileColors.sky100,
    borderWidth: 1,
    borderColor: flashitMobileColors.sky500,
  },
  tagChipText: {
    fontSize: 13,
    color: flashitMobileColors.slate600,
  },
  tagChipTextSelected: {
    color: flashitMobileColors.sky600,
    fontWeight: '500',
  },
  customTagRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 12,
  },
  customTagInput: {
    flex: 1,
    backgroundColor: flashitMobileColors.white,
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    borderWidth: 1,
    borderColor: flashitMobileColors.slate200,
  },
  addTagButton: {
    backgroundColor: flashitMobileColors.slate200,
    borderRadius: 8,
    paddingHorizontal: 16,
    justifyContent: 'center',
  },
  addTagButtonText: {
    fontSize: 14,
    fontWeight: '500',
    color: flashitMobileColors.slate600,
  },
  selectedTagsContainer: {
    marginTop: 12,
  },
  selectedTagsLabel: {
    fontSize: 12,
    color: flashitMobileColors.slate500,
    marginBottom: 6,
  },
  selectedTagsList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  selectedTag: {
    backgroundColor: flashitMobileColors.sky500,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
  },
  selectedTagText: {
    fontSize: 12,
    color: flashitMobileColors.white,
    fontWeight: '500',
  },
  importanceContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 8,
  },
  importanceButton: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 8,
    backgroundColor: flashitMobileColors.slate100,
    alignItems: 'center',
  },
  importanceButtonSelected: {
    backgroundColor: flashitMobileColors.sky500,
  },
  importanceButtonText: {
    fontSize: 18,
    fontWeight: '600',
    color: flashitMobileColors.slate600,
  },
  importanceButtonTextSelected: {
    color: flashitMobileColors.white,
  },
  importanceLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 6,
  },
  importanceLabelText: {
    fontSize: 11,
    color: flashitMobileColors.slate400,
  },
  buttonContainer: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 32,
  },
  cancelButton: {
    flex: 1,
    backgroundColor: flashitMobileColors.white,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: flashitMobileColors.slate200,
  },
  cancelButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: flashitMobileColors.slate500,
  },
  submitButton: {
    flex: 2,
    backgroundColor: flashitMobileColors.sky500,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  submitButtonDisabled: {
    opacity: 0.6,
  },
  submitButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: flashitMobileColors.white,
  },
});

