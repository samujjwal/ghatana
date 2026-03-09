// This is a re-export of the original MetricChart component from dcmaar-extension
// This allows us to maintain a single source of truth while providing a unified API

// Import the original component
import { MetricChart as OriginalMetricChart } from '../../../../apps/dcmaar-extension/src/ui/components/metrics/MetricChart';

export { OriginalMetricChart as MetricChart };
export type { MetricChartProps } from '../../../../apps/dcmaar-extension/src/ui/components/metrics/MetricChart';

export default OriginalMetricChart;
