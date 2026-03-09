/**
 * Canvas Templates Module
 *
 * Pre-built templates for various canvas workflows including
 * persona-specific journey templates.
 *
 * @doc.type module
 * @doc.purpose Template exports
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Gallery template management
export {
    templateManager,
    type GalleryTemplate,
    type TemplateParameter,
    type TemplateCategory,
} from './templateManager';

// Journey templates for persona workflows
export {
    journeyTemplates,
    pmBrainstormingTemplate,
    architectDesignTemplate,
    engineerImplementationTemplate,
    type JourneyTemplate,
    type JourneyNode,
    type JourneyEdge,
} from './journeyTemplates';
