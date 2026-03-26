/**
 * Capability interfaces for Data-Cloud plugins.
 *
 * <p>This package defines standard capabilities that plugins can implement:
 * <ul>
 *   <li>{@link com.ghatana.datacloud.spi.capability.StorageCapability} - Storage operations</li>
 *   <li>{@link com.ghatana.datacloud.spi.capability.StreamingCapability} - Streaming operations</li>
 *   <li>{@link com.ghatana.datacloud.spi.capability.QueryCapability} - Query operations</li>
 * </ul>
 *
 * <p>Plugins declare their capabilities via {@link com.ghatana.datacloud.spi.PluginMetadata#capabilities()}
 * and implement the corresponding interfaces.
 *
 * @see com.ghatana.datacloud.spi.Plugin
 */
package com.ghatana.datacloud.spi.capability;
