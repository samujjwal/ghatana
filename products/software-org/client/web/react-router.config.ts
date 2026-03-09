/**
 * React Router Framework Mode configuration
 *
 * <p><b>Purpose</b><br>
 * Configures the React Router CLI for Framework Mode development and building.
 * This file is used by `react-router dev` and `react-router build` commands
 * to locate routes and configure the application.
 *
 * <p><b>App Directory</b><br>
 * Points to the directory containing:
 * - root.tsx: Root route module (entry point for all routes)
 * - routes.ts: Route configuration with all route definitions
 *
 * @doc.type configuration
 * @doc.purpose React Router Framework Mode CLI configuration
 * @doc.layer product
 * @doc.pattern Configuration
 */
export default {
    appDirectory: "src/app",
};
