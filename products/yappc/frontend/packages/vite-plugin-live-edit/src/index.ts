/**
 * Vite plugin for YAPPC live component editing.
 *
 * <p><b>Purpose</b><br>
 * Provides hot module replacement and component tracking for live editing
 * of React components in the YAPPC editor.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import liveEditPlugin from '@ghatana/yappc-vite-plugin-live-edit';
 *
 * export default {
 *   plugins: [
 *     liveEditPlugin({
 *       enabled: true,
 *       port: 5173,
 *     }),
 *   ],
 * };
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Vite plugin for live editing
 * @doc.layer product
 * @doc.pattern Plugin
 */

import type { Plugin } from 'vite';
import { isComponentFile, injectTracking, extractMetadata } from './utils';
import type { LiveEditOptions, ComponentMetadata } from './types';

/**
 * Creates a Vite plugin for live component editing.
 *
 * <p><b>Purpose</b><br>
 * Enables hot module replacement and real-time component tracking for
 * live editing capabilities in the YAPPC editor.
 *
 * @param options - Plugin configuration options
 * @returns Vite plugin instance
 *
 * @doc.type function
 * @doc.purpose Create live edit plugin
 * @doc.layer product
 * @doc.pattern Plugin Factory
 */
export default function liveEditPlugin(options: LiveEditOptions = {}): Plugin {
  const {
    enabled = true,
    port = 5173,
    include = ['**/*.tsx', '**/*.jsx'],
    exclude = ['node_modules', 'dist'],
  } = options;

  if (!enabled) {
    return {
      name: 'vite-plugin-yappc-live-edit',
      apply: 'serve',
    };
  }

  const componentMetadata = new Map<string, ComponentMetadata>();

  return {
    name: 'vite-plugin-yappc-live-edit',
    apply: 'serve',

    /**
     * Transform component files to inject tracking code.
     *
     * <p><b>Purpose</b><br>
     * Intercepts component files and injects metadata tracking code
     * for live editing support.
     *
     * @param code - Source code
     * @param id - Module ID
     * @returns Transformed code
     *
     * @doc.type method
     * @doc.purpose Transform component code
     * @doc.layer product
     * @doc.pattern Plugin
     */
    transform(code: string, id: string) {
      // Skip non-component files
      if (!isComponentFile(id, include, exclude)) {
        return null;
      }

      try {
        // Extract component metadata
        const metadata = extractMetadata(code, id);
        componentMetadata.set(id, metadata);

        // Inject tracking code
        const transformed = injectTracking(code, metadata);

        return {
          code: transformed,
          map: null,
        };
      } catch (error) {
        console.error(`[vite-plugin-yappc-live-edit] Error transforming ${id}:`, error);
        return null;
      }
    },

    /**
     * Handle hot module updates.
     *
     * <p><b>Purpose</b><br>
     * Notifies clients when components are updated, enabling
     * real-time preview updates.
     *
     * @param context - HMR context
     *
     * @doc.type method
     * @doc.purpose Handle HMR updates
     * @doc.layer product
     * @doc.pattern Plugin
     */
    handleHotUpdate({ file, server, modules }) {
      if (!isComponentFile(file, include, exclude)) {
        return;
      }

      // Extract updated metadata
      const metadata = componentMetadata.get(file);

      if (metadata) {
        // Notify clients of component update
        server.ws.send({
          type: 'custom',
          event: 'yappc:component-updated',
          data: {
            file,
            metadata,
            timestamp: Date.now(),
          },
        });
      }
    },

    /**
     * Configure Vite server.
     *
     * <p><b>Purpose</b><br>
     * Configures the Vite dev server for live editing support.
     *
     * @param server - Vite server instance
     *
     * @doc.type method
     * @doc.purpose Configure server
     * @doc.layer product
     * @doc.pattern Plugin
     */
    configureServer(server) {
      // Add custom middleware for component metadata
      return () => {
        server.middlewares.use('/__yappc/metadata', (req, res) => {
          const metadata = Array.from(componentMetadata.entries()).map(([file, meta]) => ({
            file,
            ...meta,
          }));

          res.end(JSON.stringify(metadata));
        });
      };
    },
  };
}

export type { LiveEditOptions, ComponentMetadata } from './types';
export { isComponentFile, injectTracking, extractMetadata } from './utils';
