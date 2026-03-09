import { Box } from "@/components/ui";

/**
 * KPI Grid Component - Layout for multiple KPI cards
 *
 * <p><b>Purpose</b><br>
 * Renders a responsive grid of KPI cards with proper spacing and alignment.
 *
 * <p><b>Features</b><br>
 * - Responsive grid (3 cols on desktop, 2 on tablet, 1 on mobile)
 * - Automatic card sizing and spacing
 * - Support for different card states
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <KpiGrid>
 *   <KpiCard title="Deployments" value={156} />
 *   <KpiCard title="Lead Time" value="3.2h" />
 * </KpiGrid>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose KPI grid layout container
 * @doc.layer product
 * @doc.pattern Organism
 */
interface KpiGridProps {
    children: React.ReactNode;
}

export function KpiGrid({ children }: KpiGridProps) {
    return (
        <Box className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {children}
        </Box>
    );
}

export default KpiGrid;
