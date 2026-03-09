/**
 * Viewport Utilities
 *
 * Coordinate transformations, viewport management, and minimap state utilities
 * for infinite canvas operations.
 *
 * @module canvas/viewport
 */

// Infinite Space utilities (Feature 2.5)
export {
    shouldShiftOrigin,
    computeOriginShiftDelta,
    getViewportBounds,
    screenToWorld,
    worldToScreen,
    isPointVisible,
    isRectVisible,
    getTiledBackgroundOffset,
    clampZoom,
    fitElementsInView,
    zoomAtPoint,
    testCoordinateAccuracy,
    validateOriginShiftConfig,
    type Viewport,
    type Point,
    type OriginShiftConfig,
} from './infiniteSpace';

// Minimap State utilities (Feature 2.9)
export {
    calculateCanvasBounds,
    worldToMinimapCoordinates,
    calculateMinimapViewport,
    handleMinimapClick,
    isPointInMinimapViewport,
    createMinimapConfig,
    zoomToSelection,
    applyKeyboardZoom,
    createZoomConfig,
    type MinimapNode,
    type MinimapConfig,
    type MinimapViewport,
    type ZoomConfig,
    type CanvasBounds,
} from './minimapState';
