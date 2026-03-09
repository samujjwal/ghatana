/**
 * Spheres Screen for Flashit Mobile
 * Manage user's Spheres (privacy boundaries)
 *
 * @doc.type screen
 * @doc.purpose Display and manage Spheres on mobile
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  Alert,
  ActivityIndicator,
  RefreshControl,
  Modal,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { RootStackParamList } from '../navigation';
import { useApi } from '../contexts/ApiContext';
import { Sphere, SphereType, SphereVisibility } from '@ghatana/flashit-shared';

type Props = NativeStackScreenProps<RootStackParamList, 'Spheres'>;

const SPHERE_TYPES: SphereType[] = ['PERSONAL', 'WORK', 'FAMILY', 'PROJECT', 'CUSTOM'];
const VISIBILITY_OPTIONS: SphereVisibility[] = ['PRIVATE', 'INVITE_ONLY', 'LINK_SHARED'];

export default function SpheresScreen({ navigation }: Props) {
  const { apiClient } = useApi();
  const queryClient = useQueryClient();
  const [refreshing, setRefreshing] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);

  // Create form state
  const [newName, setNewName] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newType, setNewType] = useState<SphereType>('PERSONAL');
  const [newVisibility, setNewVisibility] = useState<SphereVisibility>('PRIVATE');

  // Fetch spheres
  const {
    data: spheresData,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ['spheres'],
    queryFn: () => apiClient.getSpheres(),
  });

  // Create sphere mutation
  const createSphere = useMutation({
    mutationFn: (data: Parameters<typeof apiClient.createSphere>[0]) =>
      apiClient.createSphere(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
      setModalVisible(false);
      resetForm();
      Alert.alert('Success', 'Sphere created!');
    },
    onError: (error: any) => {
      Alert.alert('Error', error.response?.data?.message || 'Failed to create sphere');
    },
  });

  // Delete sphere mutation
  const deleteSphere = useMutation({
    mutationFn: (id: string) => apiClient.deleteSphere(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
      queryClient.invalidateQueries({ queryKey: ['moments'] });
    },
    onError: (error: any) => {
      Alert.alert('Error', error.response?.data?.message || 'Failed to delete sphere');
    },
  });

  const resetForm = () => {
    setNewName('');
    setNewDescription('');
    setNewType('PERSONAL');
    setNewVisibility('PRIVATE');
  };

  const handleCreate = () => {
    if (!newName.trim()) {
      Alert.alert('Error', 'Please enter a sphere name');
      return;
    }
    createSphere.mutate({
      name: newName.trim(),
      description: newDescription.trim() || undefined,
      type: newType,
      visibility: newVisibility,
    });
  };

  const handleDelete = useCallback(
    (sphere: Sphere) => {
      if (sphere.userRole !== 'OWNER') {
        Alert.alert('Error', 'Only the owner can delete this sphere');
        return;
      }
      Alert.alert(
        'Delete Sphere',
        `Are you sure you want to delete "${sphere.name}"? This will also delete all moments in this sphere.`,
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Delete',
            style: 'destructive',
            onPress: () => deleteSphere.mutate(sphere.id),
          },
        ]
      );
    },
    [deleteSphere]
  );

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  const getVisibilityIcon = (visibility: string) => {
    switch (visibility) {
      case 'PRIVATE':
        return '🔒';
      case 'INVITE_ONLY':
        return '👥';
      default:
        return '🔗';
    }
  };

  const renderSphere = useCallback(
    ({ item }: { item: Sphere }) => (
      <View style={styles.sphereCard}>
        <View style={styles.sphereHeader}>
          <Text style={styles.sphereName}>{item.name}</Text>
          <View style={styles.roleBadge}>
            <Text style={styles.roleBadgeText}>{item.userRole}</Text>
          </View>
        </View>

        {item.description && (
          <Text style={styles.sphereDescription} numberOfLines={2}>
            {item.description}
          </Text>
        )}

        <View style={styles.sphereInfo}>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Type:</Text>
            <Text style={styles.infoValue}>{item.type}</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={styles.infoLabel}>Visibility:</Text>
            <Text style={styles.infoValue}>
              {getVisibilityIcon(item.visibility)} {item.visibility}
            </Text>
          </View>
        </View>

        <View style={styles.sphereFooter}>
          <View style={styles.momentCount}>
            <Text style={styles.momentCountNumber}>{item.momentCount}</Text>
            <Text style={styles.momentCountLabel}>moments</Text>
          </View>
          {item.userRole === 'OWNER' && (
            <TouchableOpacity
              style={styles.deleteButton}
              onPress={() => handleDelete(item)}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel={`Delete sphere ${item.name}`}
            >
              <Text style={styles.deleteButtonText}>Delete</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>
    ),
    [handleDelete]
  );

  const renderEmpty = useCallback(
    () => (
      <View 
        style={styles.emptyContainer}
        accessible={true}
        accessibilityRole="text"
        accessibilityLabel="No spheres yet. Create your first sphere to organize moments."
      >
        <Text style={styles.emptyText}>No spheres yet</Text>
        <Text style={styles.emptySubtext}>
          Create your first sphere to organize moments
        </Text>
      </View>
    ),
    []
  );

  if (isLoading && !refreshing) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#0ea5e9" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Your Spheres</Text>
        <TouchableOpacity
          style={styles.createButton}
          onPress={() => setModalVisible(true)}
          accessible={true}
          accessibilityRole="button"
          accessibilityLabel="Create new sphere"
        >
          <Text style={styles.createButtonText}>+ Create</Text>
        </TouchableOpacity>
      </View>

      {/* Spheres List */}
      <FlatList
        data={spheresData?.spheres || []}
        renderItem={renderSphere}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={renderEmpty}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor="#0ea5e9"
          />
        }
        showsVerticalScrollIndicator={false}
        numColumns={1}
      />

      {/* Create Sphere Modal */}
      <Modal
        visible={modalVisible}
        animationType="slide"
        presentationStyle="pag
              onPress={() => setModalVisible(false)}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Cancel sphere creation"
            >
              <Text style={styles.modalCancel}>Cancel</Text>
            </TouchableOpacity>
            <Text 
              style={styles.modalTitle}
              accessibilityRole="header"
            >
              Create Sphere
            </Text>
            <TouchableOpacity
              onPress={handleCreate}
              disabled={createSphere.isPending}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Create sphere"
              accessibilityState={{ disabled: createSphere.isPending, busy: createSphere.isPending }>Cancel</Text>
            </TouchableOpacity>
            <Text style={styles.modalTitle}>Create Sphere</Text>
            <TouchableOpacity
              onPress={handleCreate}
              disabled={createSphere.isPending}
            >
              <Text
                style={[
                  styles.modalSave,
                  createSphere.isPending && styles.modalSaveDisabled,
                ]}
              >
                {createSphere.isPending ? 'Creating...' : 'Create'}
              </Text>
            </TouchableOpacity>
          </View>

          <View style={styles.modalContent}>
            {/* Name */}
            <View style={styles.formGroup}>
              <Text style={styles.formLabel}>Name *</Text>
              <TextInput
                style={styles.formInput}
                placeholder="e.g., Work, Family, Personal Growth"
                value={newName}
                onChangeText={setNewName}
                accessible={true}
                accessibilityLabel="Sphere Name"
                accessibilityHint="Enter a name for your sphere"
              />
            </View>

            {/* Description */}
            <View style={styles.formGroup}>
              <Text style={styles.formLabel}>Description</Text>
              <TextInput
                style={[styles.formInput, styles.formTextArea]}
                placeholder="Optional description..."
                value={newDescription}
                onChangeText={setNewDescription}
                multiline
                numberOfLines={3}
              />
            </View>

            {/* Type */}
            <View style={styles.formGroup}>
              <Text style={styles.formLabel}>Type</Text>
              <View style={styles.optionsRow}>
                {SPHERE_TYPES.map((type) => (
                  <TouchableOpacity
                    key={type}
                    style={[
                      styles.optionChip,
                      newType === type && styles.optionChipSelected,
                    ]}
                    onPress={() => setNewType(type)}
                    accessible={true}
                    accessibilityRole="button"
                    accessibilityLabel={`Set type to ${type}`}
                    accessibilityState={{ selected: newType === type }}
                  >
                    <Text
                      style={[
                        styles.optionChipText,
                        newType === type && styles.optionChipTextSelected,
                      ]}
                    >
                      {type}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Visibility */}
            <View style={styles.formGroup}>
              <Text style={styles.formLabel}>Visibility</Text>
              <View style={styles.optionsRow}>
                {VISIBILITY_OPTIONS.map((vis) => (
                  <TouchableOpacity
                    key={vis}
                    style={[
                    accessible={true}
                    accessibilityRole="button"
                    accessibilityLabel={`Set visibility to ${vis}`}
                    accessibilityState={{ selected: newVisibility === vis }}
                      styles.optionChip,
                      newVisibility === vis && styles.optionChipSelected,
                    ]}
                    onPress={() => setNewVisibility(vis)}
                  >
                    <Text
                      style={[
                        styles.optionChipText,
                        newVisibility === vis && styles.optionChipTextSelected,
                      ]}
                    >
                      {getVisibilityIcon(vis)} {vis}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1e293b',
  },
  createButton: {
    backgroundColor: '#0ea5e9',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  createButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  listContent: {
    padding: 16,
    paddingBottom: 32,
  },
  sphereCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  sphereHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  sphereName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1e293b',
  },
  roleBadge: {
    backgroundColor: '#e0f2fe',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
  },
  roleBadgeText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#0284c7',
  },
  sphereDescription: {
    fontSize: 14,
    color: '#64748b',
    marginBottom: 12,
  },
  sphereInfo: {
    marginBottom: 12,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  infoLabel: {
    fontSize: 13,
    color: '#64748b',
  },
  infoValue: {
    fontSize: 13,
    fontWeight: '500',
    color: '#334155',
  },
  sphereFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 8,
  },
  momentCount: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 4,
  },
  momentCountNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#0ea5e9',
  },
  momentCountLabel: {
    fontSize: 13,
    color: '#64748b',
  },
  deleteButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  deleteButtonText: {
    fontSize: 13,
    color: '#ef4444',
    fontWeight: '500',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 60,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#475569',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#94a3b8',
  },
  // Modal styles
  modalContainer: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
  },
  modalCancel: {
    fontSize: 16,
    color: '#64748b',
  },
  modalTitle: {
    fontSize: 17,
    fontWeight: '600',
    color: '#1e293b',
  },
  modalSave: {
    fontSize: 16,
    fontWeight: '600',
    color: '#0ea5e9',
  },
  modalSaveDisabled: {
    opacity: 0.5,
  },
  modalContent: {
    padding: 16,
  },
  formGroup: {
    marginBottom: 20,
  },
  formLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 8,
  },
  formInput: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 14,
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  formTextArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  optionsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  optionChip: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: '#f1f5f9',
  },
  optionChipSelected: {
    backgroundColor: '#0ea5e9',
  },
  optionChipText: {
    fontSize: 13,
    fontWeight: '500',
    color: '#475569',
  },
  optionChipTextSelected: {
    color: '#fff',
  },
});

