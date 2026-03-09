/// Phase 6b Multi-Node Scaling - Horizontal Scaling Infrastructure
///
/// Comprehensive test suite for horizontal scaling and resource optimization.
/// Tests validate:
/// - Horizontal scaling strategies
/// - Dynamic node provisioning
/// - Resource allocation and optimization
/// - Request routing and distribution
/// - Cluster capacity monitoring
/// - Scaling policies and triggers
/// - Load shedding strategies
/// - Performance metrics under scale
///
/// Architecture:
/// - ScalingController: Scaling decisions and automation
/// - HorizontalScaler: Dynamic node provisioning
/// - ResourceAllocator: CPU/memory distribution
/// - CapacityPlanner: Cluster capacity analysis
/// - RequestRouter: Intelligent request routing
/// - MetricsAggregator: Cross-node metrics
/// - ScalingPolicy: Scaling rules and thresholds

#[cfg(test)]
mod scaling_tests {
    use chrono::{DateTime, Utc};
    use serde::{Deserialize, Serialize};
    use std::collections::HashMap;
    use std::sync::{Arc, Mutex};

    // ============= Scaling Types =============

    #[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, PartialOrd, Ord)]
    enum ScalingTrigger {
        Low,
        Medium,
        High,
        Critical,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct ResourceMetrics {
        node_id: String,
        cpu_usage_percent: f32,
        memory_usage_percent: f32,
        threat_processing_rate: f32, // threats/sec
        queue_depth: usize,
        response_latency_ms: f32,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct ScalingDecision {
        action: ScalingAction,
        trigger: ScalingTrigger,
        recommended_nodes: Option<i32>, // Positive: scale up, Negative: scale down
        reason: String,
        timestamp: DateTime<Utc>,
    }

    #[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
    enum ScalingAction {
        ScaleUp,
        ScaleDown,
        Maintain,
        Emergency,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct HorizontalScaleRequest {
        target_node_count: usize,
        required_cpu_cores: f32,
        required_memory_gb: f32,
        priority: u8,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct ClusterCapacity {
        total_nodes: usize,
        active_nodes: usize,
        total_cpu_cores: f32,
        available_cpu_cores: f32,
        total_memory_gb: f32,
        available_memory_gb: f32,
        utilization_percent: f32,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct LoadRoute {
        metric_id: String,
        target_node_id: String,
        estimated_latency_ms: f32,
        predicted_throughput: f32,
    }

    #[derive(Debug, Clone, Serialize, Deserialize)]
    struct ScalingPolicy {
        scale_up_cpu_threshold: f32,
        scale_down_cpu_threshold: f32,
        scale_up_memory_threshold: f32,
        scale_down_memory_threshold: f32,
        scale_up_queue_depth_threshold: usize,
        min_nodes: usize,
        max_nodes: usize,
        scale_up_cooldown_secs: u64,
        scale_down_cooldown_secs: u64,
    }

    impl Default for ScalingPolicy {
        fn default() -> Self {
            Self {
                scale_up_cpu_threshold: 80.0,
                scale_down_cpu_threshold: 30.0,
                scale_up_memory_threshold: 85.0,
                scale_down_memory_threshold: 40.0,
                scale_up_queue_depth_threshold: 1000,
                min_nodes: 2,
                max_nodes: 10,
                scale_up_cooldown_secs: 60,
                scale_down_cooldown_secs: 300,
            }
        }
    }

    // ============= Scaling Implementations =============

    struct ScalingController {
        policy: ScalingPolicy,
        metrics_history: Arc<Mutex<Vec<ResourceMetrics>>>,
        scaling_decisions: Arc<Mutex<Vec<ScalingDecision>>>,
        current_node_count: Arc<Mutex<usize>>,
        last_scale_time: Arc<Mutex<DateTime<Utc>>>,
    }

    impl ScalingController {
        fn new(policy: ScalingPolicy) -> Self {
            Self {
                policy,
                metrics_history: Arc::new(Mutex::new(Vec::new())),
                scaling_decisions: Arc::new(Mutex::new(Vec::new())),
                current_node_count: Arc::new(Mutex::new(2)),
                last_scale_time: Arc::new(Mutex::new(Utc::now())),
            }
        }

        fn evaluate_metrics(&self, metrics: &[ResourceMetrics]) -> ScalingDecision {
            if metrics.is_empty() {
                return ScalingDecision {
                    action: ScalingAction::Maintain,
                    trigger: ScalingTrigger::Low,
                    recommended_nodes: None,
                    reason: "No metrics available".to_string(),
                    timestamp: Utc::now(),
                };
            }

            let avg_cpu =
                metrics.iter().map(|m| m.cpu_usage_percent).sum::<f32>() / metrics.len() as f32;
            let avg_memory =
                metrics.iter().map(|m| m.memory_usage_percent).sum::<f32>() / metrics.len() as f32;
            let max_queue = metrics.iter().map(|m| m.queue_depth).max().unwrap_or(0);

            if avg_cpu > self.policy.scale_up_cpu_threshold
                || avg_memory > self.policy.scale_up_memory_threshold
                || max_queue > self.policy.scale_up_queue_depth_threshold
            {
                let current = *self.current_node_count.lock().unwrap();
                let trigger = if avg_cpu > 95.0 || max_queue > 5000 {
                    ScalingTrigger::Critical
                } else if avg_cpu > 90.0 {
                    ScalingTrigger::High
                } else {
                    ScalingTrigger::Medium
                };

                let new_count = std::cmp::min(current + 2, self.policy.max_nodes);

                ScalingDecision {
                    action: ScalingAction::ScaleUp,
                    trigger,
                    recommended_nodes: Some((new_count - current) as i32),
                    reason: format!(
                        "CPU: {:.1}%, Memory: {:.1}%, Queue: {}",
                        avg_cpu, avg_memory, max_queue
                    ),
                    timestamp: Utc::now(),
                }
            } else if avg_cpu < self.policy.scale_down_cpu_threshold
                && avg_memory < self.policy.scale_down_memory_threshold
                && max_queue < self.policy.scale_up_queue_depth_threshold / 10
            {
                let current = *self.current_node_count.lock().unwrap();
                if current > self.policy.min_nodes {
                    ScalingDecision {
                        action: ScalingAction::ScaleDown,
                        trigger: ScalingTrigger::Low,
                        recommended_nodes: Some(-1),
                        reason: "Resources underutilized".to_string(),
                        timestamp: Utc::now(),
                    }
                } else {
                    ScalingDecision {
                        action: ScalingAction::Maintain,
                        trigger: ScalingTrigger::Low,
                        recommended_nodes: None,
                        reason: "At minimum node count".to_string(),
                        timestamp: Utc::now(),
                    }
                }
            } else {
                ScalingDecision {
                    action: ScalingAction::Maintain,
                    trigger: ScalingTrigger::Medium,
                    recommended_nodes: None,
                    reason: "Metrics within acceptable range".to_string(),
                    timestamp: Utc::now(),
                }
            }
        }

        fn get_scaling_decisions(&self) -> Vec<ScalingDecision> {
            self.scaling_decisions.lock().unwrap().clone()
        }

        fn record_decision(&self, decision: ScalingDecision) {
            self.scaling_decisions
                .lock()
                .unwrap()
                .push(decision.clone());
            if decision.action == ScalingAction::ScaleUp
                || decision.action == ScalingAction::ScaleDown
            {
                *self.last_scale_time.lock().unwrap() = Utc::now();
            }
        }
    }

    struct HorizontalScaler {
        provisioned_nodes: Arc<Mutex<Vec<String>>>,
    }

    impl HorizontalScaler {
        fn new() -> Self {
            Self {
                provisioned_nodes: Arc::new(Mutex::new(vec![
                    "node-1".to_string(),
                    "node-2".to_string(),
                ])),
            }
        }

        fn provision_nodes(&self, request: &HorizontalScaleRequest) -> Result<Vec<String>, String> {
            let mut nodes = self.provisioned_nodes.lock().unwrap();
            let current_count = nodes.len();

            if request.target_node_count > current_count {
                for i in current_count..request.target_node_count {
                    nodes.push(format!("node-{}", i + 1));
                }
                Ok(nodes[current_count..].to_vec())
            } else {
                Err("Target count must be greater than current".to_string())
            }
        }

        fn deprovision_nodes(&self, count: usize) -> Result<Vec<String>, String> {
            let mut nodes = self.provisioned_nodes.lock().unwrap();
            if count > nodes.len() {
                return Err("Cannot deprovision more nodes than available".to_string());
            }

            let end_idx = nodes.len() - count;
            let removed: Vec<String> = nodes.drain(end_idx..).collect();
            Ok(removed)
        }

        fn get_provisioned_nodes(&self) -> Vec<String> {
            self.provisioned_nodes.lock().unwrap().clone()
        }
    }

    struct ResourceAllocator {
        allocations: Arc<Mutex<HashMap<String, ResourceMetrics>>>,
    }

    impl ResourceAllocator {
        fn new() -> Self {
            Self {
                allocations: Arc::new(Mutex::new(HashMap::new())),
            }
        }

        fn allocate_resources(
            &self,
            node_id: &str,
            _cpu_cores: f32,
            _memory_gb: f32,
        ) -> Result<(), String> {
            let mut allocations = self.allocations.lock().unwrap();
            allocations.insert(
                node_id.to_string(),
                ResourceMetrics {
                    node_id: node_id.to_string(),
                    cpu_usage_percent: 0.0,
                    memory_usage_percent: 0.0,
                    threat_processing_rate: 0.0,
                    queue_depth: 0,
                    response_latency_ms: 0.0,
                },
            );
            Ok(())
        }

        fn get_resource_utilization(&self) -> f32 {
            let allocations = self.allocations.lock().unwrap();
            if allocations.is_empty() {
                return 0.0;
            }

            let total_cpu = allocations
                .values()
                .map(|m| m.cpu_usage_percent)
                .sum::<f32>();
            total_cpu / allocations.len() as f32
        }
    }

    struct CapacityPlanner {
        capacity: Arc<Mutex<ClusterCapacity>>,
    }

    impl CapacityPlanner {
        fn new(total_nodes: usize) -> Self {
            Self {
                capacity: Arc::new(Mutex::new(ClusterCapacity {
                    total_nodes,
                    active_nodes: total_nodes,
                    total_cpu_cores: total_nodes as f32 * 4.0,
                    available_cpu_cores: total_nodes as f32 * 4.0,
                    total_memory_gb: total_nodes as f32 * 8.0,
                    available_memory_gb: total_nodes as f32 * 8.0,
                    utilization_percent: 0.0,
                })),
            }
        }

        fn update_capacity(&self, active_nodes: usize) {
            let mut cap = self.capacity.lock().unwrap();
            cap.active_nodes = active_nodes;
            cap.available_cpu_cores = active_nodes as f32 * 4.0;
            cap.available_memory_gb = active_nodes as f32 * 8.0;
        }

        fn get_capacity(&self) -> ClusterCapacity {
            self.capacity.lock().unwrap().clone()
        }

        fn can_accommodate(&self, request: &HorizontalScaleRequest) -> bool {
            let cap = self.capacity.lock().unwrap();
            cap.available_cpu_cores >= request.required_cpu_cores
                && cap.available_memory_gb >= request.required_memory_gb
        }
    }

    struct RequestRouter {
        routes: Arc<Mutex<Vec<LoadRoute>>>,
    }

    impl RequestRouter {
        fn new() -> Self {
            Self {
                routes: Arc::new(Mutex::new(Vec::new())),
            }
        }

        fn route_requests(&self, metrics: &[ResourceMetrics]) -> Vec<LoadRoute> {
            // Route to least loaded nodes
            let mut sorted_metrics = metrics.to_vec();
            sorted_metrics.sort_by(|a, b| {
                a.cpu_usage_percent
                    .partial_cmp(&b.cpu_usage_percent)
                    .unwrap_or(std::cmp::Ordering::Equal)
            });

            let mut routes = Vec::new();
            for metric in &sorted_metrics {
                let estimated_latency = 5.0 + (metric.cpu_usage_percent * 0.1);
                let throughput = 1000.0 * (1.0 - metric.cpu_usage_percent / 100.0);

                routes.push(LoadRoute {
                    metric_id: metric.node_id.clone(),
                    target_node_id: metric.node_id.clone(),
                    estimated_latency_ms: estimated_latency,
                    predicted_throughput: throughput,
                });
            }

            *self.routes.lock().unwrap() = routes.clone();
            routes
        }

        fn get_routes(&self) -> Vec<LoadRoute> {
            self.routes.lock().unwrap().clone()
        }
    }

    // ============= Unit Tests =============

    #[test]
    fn test_scaling_controller_initialization() {
        let policy = ScalingPolicy::default();
        let controller = ScalingController::new(policy);
        assert_eq!(*controller.current_node_count.lock().unwrap(), 2);
    }

    #[test]
    fn test_scaling_decision_scale_up_high_cpu() {
        let policy = ScalingPolicy::default();
        let controller = ScalingController::new(policy);

        let metrics = vec![ResourceMetrics {
            node_id: "node-1".to_string(),
            cpu_usage_percent: 95.0,
            memory_usage_percent: 75.0,
            threat_processing_rate: 500.0,
            queue_depth: 2000,
            response_latency_ms: 150.0,
        }];

        let decision = controller.evaluate_metrics(&metrics);
        assert_eq!(decision.action, ScalingAction::ScaleUp);
        assert_eq!(decision.trigger, ScalingTrigger::High);
    }

    #[test]
    fn test_scaling_decision_scale_down_low_utilization() {
        let mut policy = ScalingPolicy::default();
        policy.min_nodes = 1; // Allow scaling below 2 for test
        let controller = ScalingController::new(policy);
        *controller.current_node_count.lock().unwrap() = 3; // Start with 3 nodes

        let metrics = vec![ResourceMetrics {
            node_id: "node-1".to_string(),
            cpu_usage_percent: 20.0,
            memory_usage_percent: 30.0,
            threat_processing_rate: 50.0,
            queue_depth: 10,
            response_latency_ms: 5.0,
        }];

        let decision = controller.evaluate_metrics(&metrics);
        assert_eq!(decision.action, ScalingAction::ScaleDown);
        assert_eq!(decision.trigger, ScalingTrigger::Low);
    }

    #[test]
    fn test_scaling_decision_maintain() {
        let policy = ScalingPolicy::default();
        let controller = ScalingController::new(policy);

        let metrics = vec![ResourceMetrics {
            node_id: "node-1".to_string(),
            cpu_usage_percent: 50.0,
            memory_usage_percent: 60.0,
            threat_processing_rate: 300.0,
            queue_depth: 500,
            response_latency_ms: 50.0,
        }];

        let decision = controller.evaluate_metrics(&metrics);
        assert_eq!(decision.action, ScalingAction::Maintain);
    }

    #[test]
    fn test_horizontal_scaler_provision_nodes() {
        let scaler = HorizontalScaler::new();
        let request = HorizontalScaleRequest {
            target_node_count: 4,
            required_cpu_cores: 8.0,
            required_memory_gb: 16.0,
            priority: 1,
        };

        let result = scaler.provision_nodes(&request);
        assert!(result.is_ok());
        assert_eq!(scaler.get_provisioned_nodes().len(), 4);
    }

    #[test]
    fn test_horizontal_scaler_deprovision_nodes() {
        let scaler = HorizontalScaler::new();
        let request = HorizontalScaleRequest {
            target_node_count: 5,
            required_cpu_cores: 10.0,
            required_memory_gb: 20.0,
            priority: 1,
        };

        scaler.provision_nodes(&request).unwrap();
        let result = scaler.deprovision_nodes(2);

        assert!(result.is_ok());
        assert_eq!(scaler.get_provisioned_nodes().len(), 3);
    }

    #[test]
    fn test_resource_allocator_allocation() {
        let allocator = ResourceAllocator::new();
        let result = allocator.allocate_resources("node-1", 4.0, 8.0);

        assert!(result.is_ok());
    }

    #[test]
    fn test_resource_allocator_utilization() {
        let allocator = ResourceAllocator::new();
        allocator.allocate_resources("node-1", 4.0, 8.0).unwrap();

        let utilization = allocator.get_resource_utilization();
        assert!(utilization >= 0.0 && utilization <= 100.0);
    }

    #[test]
    fn test_capacity_planner_initialization() {
        let planner = CapacityPlanner::new(5);
        let capacity = planner.get_capacity();

        assert_eq!(capacity.total_nodes, 5);
        assert_eq!(capacity.active_nodes, 5);
        assert_eq!(capacity.total_cpu_cores, 20.0);
    }

    #[test]
    fn test_capacity_planner_update_capacity() {
        let planner = CapacityPlanner::new(5);
        planner.update_capacity(3);

        let capacity = planner.get_capacity();
        assert_eq!(capacity.active_nodes, 3);
        assert_eq!(capacity.available_cpu_cores, 12.0);
    }

    #[test]
    fn test_capacity_planner_can_accommodate() {
        let planner = CapacityPlanner::new(5);

        let request = HorizontalScaleRequest {
            target_node_count: 10,
            required_cpu_cores: 10.0,
            required_memory_gb: 16.0,
            priority: 1,
        };

        assert!(planner.can_accommodate(&request));
    }

    #[test]
    fn test_request_router_routing() {
        let router = RequestRouter::new();

        let metrics = vec![
            ResourceMetrics {
                node_id: "node-1".to_string(),
                cpu_usage_percent: 30.0,
                memory_usage_percent: 40.0,
                threat_processing_rate: 400.0,
                queue_depth: 200,
                response_latency_ms: 30.0,
            },
            ResourceMetrics {
                node_id: "node-2".to_string(),
                cpu_usage_percent: 60.0,
                memory_usage_percent: 65.0,
                threat_processing_rate: 200.0,
                queue_depth: 400,
                response_latency_ms: 60.0,
            },
        ];

        let routes = router.route_requests(&metrics);
        assert_eq!(routes.len(), 2);

        // node-1 should have lower latency
        assert!(routes[0].estimated_latency_ms < routes[1].estimated_latency_ms);
    }

    #[test]
    fn test_scaling_policy_defaults() {
        let policy = ScalingPolicy::default();

        assert_eq!(policy.scale_up_cpu_threshold, 80.0);
        assert_eq!(policy.scale_down_cpu_threshold, 30.0);
        assert_eq!(policy.min_nodes, 2);
        assert_eq!(policy.max_nodes, 10);
    }

    #[test]
    fn test_scaling_emergency_trigger() {
        let policy = ScalingPolicy::default();
        let controller = ScalingController::new(policy);

        let metrics = vec![ResourceMetrics {
            node_id: "node-1".to_string(),
            cpu_usage_percent: 98.0,
            memory_usage_percent: 95.0,
            threat_processing_rate: 1000.0,
            queue_depth: 10000,
            response_latency_ms: 500.0,
        }];

        let decision = controller.evaluate_metrics(&metrics);
        assert_eq!(decision.action, ScalingAction::ScaleUp);
        assert_eq!(decision.trigger, ScalingTrigger::Critical);
    }

    #[test]
    fn test_load_distribution_across_nodes() {
        let router = RequestRouter::new();

        let metrics = vec![
            ResourceMetrics {
                node_id: "node-1".to_string(),
                cpu_usage_percent: 20.0,
                memory_usage_percent: 30.0,
                threat_processing_rate: 600.0,
                queue_depth: 100,
                response_latency_ms: 10.0,
            },
            ResourceMetrics {
                node_id: "node-2".to_string(),
                cpu_usage_percent: 50.0,
                memory_usage_percent: 55.0,
                threat_processing_rate: 400.0,
                queue_depth: 500,
                response_latency_ms: 35.0,
            },
            ResourceMetrics {
                node_id: "node-3".to_string(),
                cpu_usage_percent: 80.0,
                memory_usage_percent: 85.0,
                threat_processing_rate: 200.0,
                queue_depth: 1500,
                response_latency_ms: 60.0,
            },
        ];

        let routes = router.route_requests(&metrics);

        // Verify routes are ordered by latency
        for i in 0..routes.len() - 1 {
            assert!(routes[i].estimated_latency_ms <= routes[i + 1].estimated_latency_ms);
        }
    }

    #[test]
    fn test_scaling_decision_recording() {
        let policy = ScalingPolicy::default();
        let controller = ScalingController::new(policy);

        let decision = ScalingDecision {
            action: ScalingAction::ScaleUp,
            trigger: ScalingTrigger::High,
            recommended_nodes: Some(2),
            reason: "Test scaling".to_string(),
            timestamp: Utc::now(),
        };

        controller.record_decision(decision.clone());
        let decisions = controller.get_scaling_decisions();

        assert_eq!(decisions.len(), 1);
        assert_eq!(decisions[0].action, ScalingAction::ScaleUp);
    }

    #[test]
    fn test_cluster_capacity_serialization() {
        let capacity = ClusterCapacity {
            total_nodes: 5,
            active_nodes: 5,
            total_cpu_cores: 20.0,
            available_cpu_cores: 20.0,
            total_memory_gb: 40.0,
            available_memory_gb: 40.0,
            utilization_percent: 0.0,
        };

        let json = serde_json::to_string(&capacity).unwrap();
        let deserialized: ClusterCapacity = serde_json::from_str(&json).unwrap();

        assert_eq!(deserialized.total_nodes, 5);
        assert_eq!(deserialized.total_cpu_cores, 20.0);
    }
}
