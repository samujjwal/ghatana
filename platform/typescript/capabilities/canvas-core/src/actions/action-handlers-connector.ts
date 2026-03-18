/**
 * Action Handlers Connector
 * 
 * Connects action definitions to their concrete handler implementations.
 * Updates all action handlers to use the canvas state manager.
 * 
 * @doc.type integration
 * @doc.purpose Connect actions to handlers
 * @doc.layer core
 */

import { getActionRegistry } from '../core/action-registry';
import { CanvasHandlers } from '../handlers/canvas-handlers';

/**
 * Connect all action handlers to the registry
 */
export function connectActionHandlers(): void {
    const registry = getActionRegistry();

    // Architecture layer actions
    const archAddService = registry.getAction('arch-add-service');
    if (archAddService) {
        archAddService.handler = CanvasHandlers.addService;
    }

    const archAddDatabase = registry.getAction('arch-add-database');
    if (archAddDatabase) {
        archAddDatabase.handler = CanvasHandlers.addDatabase;
    }

    const archAddApiContract = registry.getAction('arch-add-api-contract');
    if (archAddApiContract) {
        archAddApiContract.handler = CanvasHandlers.addApiContract;
    }

    // Design layer actions
    const designAddComponent = registry.getAction('design-add-component');
    if (designAddComponent) {
        designAddComponent.handler = CanvasHandlers.addComponent;
    }

    const designAddScreen = registry.getAction('design-add-screen');
    if (designAddScreen) {
        designAddScreen.handler = CanvasHandlers.addScreen;
    }

    // Implementation layer actions
    const implAddCodeBlock = registry.getAction('impl-add-code-block');
    if (implAddCodeBlock) {
        implAddCodeBlock.handler = CanvasHandlers.addCodeBlock;
    }

    const implAddFunction = registry.getAction('impl-add-function');
    if (implAddFunction) {
        implAddFunction.handler = CanvasHandlers.addFunction;
    }

    // Universal actions
    const universalAddShape = registry.getAction('universal-add-shape');
    if (universalAddShape) {
        universalAddShape.handler = CanvasHandlers.addShape;
    }

    const universalAddText = registry.getAction('universal-add-text');
    if (universalAddText) {
        universalAddText.handler = CanvasHandlers.addText;
    }

    const universalAddFrame = registry.getAction('universal-add-frame');
    if (universalAddFrame) {
        universalAddFrame.handler = CanvasHandlers.addFrame;
    }

    console.log('✅ Action handlers connected');
}
