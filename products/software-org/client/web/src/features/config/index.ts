/**
 * Configuration Feature Exports
 *
 * @doc.type module
 * @doc.purpose Configuration feature barrel exports
 * @doc.layer product
 */

// Dashboard
export { ConfigDashboardPage } from './ConfigDashboardPage';

// Entity Registry
export {
    ENTITY_TYPES,
    getEntityType,
    getAllEntityTypes,
    getCreatableEntityTypes,
    getEntityTypeByRoutePath,
    type EntityTypeDefinition,
    type EntityField,
    type FieldType,
    type FieldOption,
} from './entity-registry';

// Unified Components
export { EntityForm, type EntityFormProps } from './EntityForm';
export { EntityList, type EntityListProps, type EntityItem } from './EntityList';
export { EntityListPage, type EntityListPageProps } from './EntityListPage';
export { EntityDetailPage, type EntityDetailPageProps } from './EntityDetailPage';
export { EntityEditPage, type EntityEditPageProps } from './EntityEditPage';
