/**
 * YAPPC Actions
 * 
 * YAPPC-specific action definitions.
 * Imports generic actions and adds YAPPC-specific implementations.
 * 
 * @doc.type yappc
 * @doc.purpose YAPPC action definitions
 * @doc.layer application
 */

import { GenericActionDefinition } from '../core/canvas-config';
import { YAPPCActionContext, YAPPCLayer, YAPPCPhase, YAPPCRole } from './yappc-config';

// Re-export the existing action definitions with YAPPC types
export {
    ARCHITECTURE_ACTIONS,
    DESIGN_ACTIONS,
    COMPONENT_ACTIONS,
    IMPLEMENTATION_ACTIONS,
    DETAIL_ACTIONS,
    UNIVERSAL_LAYER_ACTIONS,
    getAllLayerActions,
    getLayerActions,
} from '../actions/layer-actions';

export {
    INTENT_ACTIONS,
    SHAPE_ACTIONS,
    VALIDATE_ACTIONS,
    GENERATE_ACTIONS,
    RUN_ACTIONS,
    OBSERVE_ACTIONS,
    IMPROVE_ACTIONS,
    getAllPhaseActions,
    getPhaseActions,
} from '../actions/phase-actions';

export {
    PRODUCT_OWNER_ACTIONS,
    ARCHITECT_ACTIONS,
    DEVELOPER_ACTIONS,
    QA_ENGINEER_ACTIONS,
    DEVOPS_ENGINEER_ACTIONS,
    SECURITY_ENGINEER_ACTIONS,
    UX_DESIGNER_ACTIONS,
    DATA_ENGINEER_ACTIONS,
    BUSINESS_ANALYST_ACTIONS,
    getAllRoleActions,
    getRoleActions,
} from '../actions/role-actions';

/**
 * Get all YAPPC layer actions
 */
export function getYAPPCLayerActions(): Record<YAPPCLayer, GenericActionDefinition<YAPPCActionContext>[]> {
    return {
        architecture: ARCHITECTURE_ACTIONS as unknown,
        design: DESIGN_ACTIONS as unknown,
        component: COMPONENT_ACTIONS as unknown,
        implementation: IMPLEMENTATION_ACTIONS as unknown,
        detail: DETAIL_ACTIONS as unknown,
    };
}

/**
 * Get all YAPPC phase actions
 */
export function getYAPPCPhaseActions(): Record<YAPPCPhase, GenericActionDefinition<YAPPCActionContext>[]> {
    return {
        INTENT: INTENT_ACTIONS as unknown,
        SHAPE: SHAPE_ACTIONS as unknown,
        VALIDATE: VALIDATE_ACTIONS as unknown,
        GENERATE: GENERATE_ACTIONS as unknown,
        RUN: RUN_ACTIONS as unknown,
        OBSERVE: OBSERVE_ACTIONS as unknown,
        IMPROVE: IMPROVE_ACTIONS as unknown,
    };
}

/**
 * Get all YAPPC role actions
 */
export function getYAPPCRoleActions(): Record<YAPPCRole, GenericActionDefinition<YAPPCActionContext>[]> {
    return {
        product_owner: PRODUCT_OWNER_ACTIONS as unknown,
        architect: ARCHITECT_ACTIONS as unknown,
        developer: DEVELOPER_ACTIONS as unknown,
        qa_engineer: QA_ENGINEER_ACTIONS as unknown,
        devops_engineer: DEVOPS_ENGINEER_ACTIONS as unknown,
        security_engineer: SECURITY_ENGINEER_ACTIONS as unknown,
        ux_designer: UX_DESIGNER_ACTIONS as unknown,
        data_engineer: DATA_ENGINEER_ACTIONS as unknown,
        business_analyst: BUSINESS_ANALYST_ACTIONS as unknown,
    };
}

/**
 * Get YAPPC universal actions
 */
export function getYAPPCUniversalActions(): GenericActionDefinition<YAPPCActionContext>[] {
    return UNIVERSAL_LAYER_ACTIONS as unknown;
}
