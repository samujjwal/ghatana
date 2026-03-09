/**
 * @ghatana/dcmaar-plugin-extension
 * Device monitoring plugin implementations
 * 
 * Provides production-ready device monitors for system metrics collection:
 * - CPUMonitor: CPU usage and time allocation
 * - MemoryMonitor: System memory usage
 * - BatteryMonitor: Battery status and health
 * 
 * All monitors implement IDataCollector interface and support:
 * - Async metrics collection with retry logic
 * - Configuration via environment or constructor
 * - Proper error handling and type safety
 */

export { BaseDeviceMonitor, type DeviceMonitorConfig } from './BaseDeviceMonitor';
export { CPUMonitor, type CPUMetrics } from './CPUMonitor';
export { MemoryMonitor, type MemoryMetrics } from './MemoryMonitor';
export { BatteryMonitor, type BatteryMetrics } from './BatteryMonitor';
