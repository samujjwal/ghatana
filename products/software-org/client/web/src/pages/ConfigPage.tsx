/**
 * Configuration Page
 *
 * <p><b>Purpose</b><br>
 * Wrapper page component that renders the ConfigDashboardPage feature.
 * This follows the pattern of other pages in the application.
 *
 * @doc.type page
 * @doc.purpose Configuration management page wrapper
 * @doc.layer product
 * @doc.pattern Page Component
 */

import { ConfigDashboardPage } from '@/features/config';

export function ConfigPage() {
    return <ConfigDashboardPage />;
}

export default ConfigPage;
