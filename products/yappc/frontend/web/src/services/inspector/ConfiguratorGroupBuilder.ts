/**
 * Configurator Group Builder
 *
 * Builds configurator groups from component registry metadata.
 *
 * @doc.type service
 * @doc.purpose Configurator group builder
 * @doc.layer product
 */

export interface ComponentRegistryMetadata {
  /** Component ID */
  componentId: string;
  /** Component name */
  name: string;
  /** Category */
  category: string;
  /** Prop definitions */
  props: PropMetadata[];
  /** Slot definitions */
  slots?: SlotMetadata[];
  /** Event definitions */
  events?: EventMetadata[];
}

export interface PropMetadata {
  /** Prop name */
  name: string;
  /** Prop type */
  type: 'string' | 'number' | 'boolean' | 'object' | 'array' | 'enum' | 'component' | 'slot' | 'action' | 'data-binding';
  /** Description */
  description?: string;
  /** Default value */
  defaultValue?: unknown;
  /** Required flag */
  required?: boolean;
  /** Enum values (if type is enum) */
  enumValues?: string[];
  /** Group assignment */
  group?: string;
  /** Order in group */
  order?: number;
}

export interface SlotMetadata {
  /** Slot name */
  name: string;
  /** Description */
  description?: string;
  /** Required flag */
  required?: boolean;
  /** Default content */
  defaultContent?: string;
}

export interface EventMetadata {
  /** Event name */
  name: string;
  /** Description */
  description?: string;
  /** Payload type */
  payloadType?: string;
}

export interface ConfiguratorGroup {
  /** Group ID */
  id: string;
  /** Group name */
  name: string;
  /** Group description */
  description?: string;
  /** Group icon */
  icon?: string;
  /** Props in this group */
  props: PropMetadata[];
  /** Collapsed flag */
  collapsed?: boolean;
  /** Order */
  order?: number;
}

export interface ConfiguratorGroups {
  /** All groups */
  groups: ConfiguratorGroup[];
  /** Ungrouped props */
  ungrouped: PropMetadata[];
  /** Slots section */
  slots?: SlotMetadata[];
  /** Events section */
  events?: EventMetadata[];
}

/**
 * Build configurator groups from registry metadata
 */
export function buildConfiguratorGroups(
  metadata: ComponentRegistryMetadata,
  customGroups?: Partial<ConfiguratorGroup>[]
): ConfiguratorGroups {
  const groupsMap = new Map<string, ConfiguratorGroup>();
  const ungrouped: PropMetadata[] = [];

  // Initialize custom groups if provided
  if (customGroups) {
    for (const customGroup of customGroups) {
      if (customGroup.id) {
        groupsMap.set(customGroup.id, {
          id: customGroup.id,
          name: customGroup.name || customGroup.id,
          description: customGroup.description,
          icon: customGroup.icon,
          props: [],
          collapsed: customGroup.collapsed,
          order: customGroup.order,
        });
      }
    }
  }

  // Group props by their group assignment
  for (const prop of metadata.props) {
    if (prop.group) {
      if (!groupsMap.has(prop.group)) {
        groupsMap.set(prop.group, {
          id: prop.group,
          name: formatGroupName(prop.group),
          props: [],
          order: getGroupOrder(prop.group),
        });
      }
      groupsMap.get(prop.group)!.props.push(prop);
    } else {
      ungrouped.push(prop);
    }
  }

  // Sort props within groups by order
  for (const group of groupsMap.values()) {
    group.props.sort((a, b) => (a.order || 0) - (b.order || 0));
  }

  // Sort groups by order
  const groups = Array.from(groupsMap.values()).sort((a, b) => (a.order || 0) - (b.order || 0));

  return {
    groups,
    ungrouped,
    slots: metadata.slots,
    events: metadata.events,
  };
}

/**
 * Format group name from ID
 */
function formatGroupName(groupId: string): string {
  return groupId
    .split(/[-_]/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/**
 * Get default group order
 */
function getGroupOrder(groupId: string): number {
  const defaultOrders: Record<string, number> = {
    'basic': 0,
    'appearance': 10,
    'layout': 20,
    'behavior': 30,
    'accessibility': 40,
    'advanced': 50,
    'data': 60,
    'actions': 70,
  };

  return defaultOrders[groupId] ?? 100;
}

/**
 * Auto-group props by type
 */
export function autoGroupProps(props: PropMetadata[]): Map<string, PropMetadata[]> {
  const groups = new Map<string, PropMetadata[]>();

  for (const prop of props) {
    let groupName = 'basic';

    // Auto-determine group based on prop name and type
    if (prop.type === 'component' || prop.type === 'slot') {
      groupName = 'layout';
    } else if (prop.type === 'action') {
      groupName = 'actions';
    } else if (prop.type === 'data-binding') {
      groupName = 'data';
    } else if (prop.name.startsWith('aria') || prop.name.includes('accessible')) {
      groupName = 'accessibility';
    } else if (prop.name.includes('style') || prop.name.includes('color') || prop.name.includes('size')) {
      groupName = 'appearance';
    } else if (prop.name.includes('on')) {
      groupName = 'behavior';
    } else if (prop.name === 'id' || prop.name === 'className' || prop.name === 'testId') {
      groupName = 'basic';
    }

    if (!groups.has(groupName)) {
      groups.set(groupName, []);
    }
    groups.get(groupName)!.push(prop);
  }

  return groups;
}

/**
 * Create default configurator groups for a component
 */
export function createDefaultConfiguratorGroups(
  metadata: ComponentRegistryMetadata
): ConfiguratorGroups {
  const autoGroups = autoGroupProps(metadata.props);
  const groups: ConfiguratorGroup[] = [];

  for (const [groupName, props] of autoGroups.entries()) {
    groups.push({
      id: groupName,
      name: formatGroupName(groupName),
      props,
      order: getGroupOrder(groupName),
    });
  }

  return {
    groups,
    ungrouped: [],
    slots: metadata.slots,
    events: metadata.events,
  };
}

/**
 * Merge custom groups with auto-generated groups
 */
export function mergeConfiguratorGroups(
  base: ConfiguratorGroups,
  custom: Partial<ConfiguratorGroup>[]
): ConfiguratorGroups {
  const groupsMap = new Map<string, ConfiguratorGroup>();

  // Add base groups
  for (const group of base.groups) {
    groupsMap.set(group.id, { ...group });
  }

  // Merge or add custom groups
  for (const customGroup of custom) {
    if (customGroup.id) {
      const existing = groupsMap.get(customGroup.id);
      if (existing) {
        // Merge custom properties
        groupsMap.set(customGroup.id, {
          ...existing,
          ...customGroup,
          props: existing.props, // Keep existing props
        });
      } else {
        // Add new group
        groupsMap.set(customGroup.id, {
          id: customGroup.id,
          name: customGroup.name || customGroup.id,
          description: customGroup.description,
          icon: customGroup.icon,
          props: [],
          collapsed: customGroup.collapsed,
          order: customGroup.order,
        });
      }
    }
  }

  return {
    groups: Array.from(groupsMap.values()).sort((a, b) => (a.order || 0) - (b.order || 0)),
    ungrouped: base.ungrouped,
    slots: base.slots,
    events: base.events,
  };
}

/**
 * Reassign prop to different group
 */
export function reassignProp(
  groups: ConfiguratorGroups,
  propId: string,
  targetGroupId: string
): ConfiguratorGroups {
  // Find prop in current group
  let sourceGroup: ConfiguratorGroup | null = null;
  let propIndex = -1;

  for (const group of groups.groups) {
    const idx = group.props.findIndex(p => p.name === propId);
    if (idx !== -1) {
      sourceGroup = group;
      propIndex = idx;
      break;
    }
  }

  // Check ungrouped
  if (!sourceGroup) {
    const idx = groups.ungrouped.findIndex(p => p.name === propId);
    if (idx !== -1) {
      const prop = groups.ungrouped.splice(idx, 1)[0];
      
      // Find or create target group
      let targetGroup = groups.groups.find(g => g.id === targetGroupId);
      if (!targetGroup) {
        targetGroup = {
          id: targetGroupId,
          name: formatGroupName(targetGroupId),
          props: [],
          order: getGroupOrder(targetGroupId),
        };
        groups.groups.push(targetGroup);
      }
      targetGroup.props.push(prop);
    }
    return groups;
  }

  if (sourceGroup && propIndex !== -1) {
    const prop = sourceGroup.props.splice(propIndex, 1)[0];
    
    // Find or create target group
    let targetGroup = groups.groups.find(g => g.id === targetGroupId);
    if (!targetGroup) {
      targetGroup = {
        id: targetGroupId,
        name: formatGroupName(targetGroupId),
        props: [],
        order: getGroupOrder(targetGroupId),
      };
      groups.groups.push(targetGroup);
    }
    targetGroup.props.push(prop);
  }

  return groups;
}

/**
 * Get group for a specific prop
 */
export function getPropGroup(
  groups: ConfiguratorGroups,
  propId: string
): ConfiguratorGroup | null {
  for (const group of groups.groups) {
    if (group.props.some(p => p.name === propId)) {
      return group;
    }
  }
  return null;
}

export default {
  buildConfiguratorGroups,
  autoGroupProps,
  createDefaultConfiguratorGroups,
  mergeConfiguratorGroups,
  reassignProp,
  getPropGroup,
};
