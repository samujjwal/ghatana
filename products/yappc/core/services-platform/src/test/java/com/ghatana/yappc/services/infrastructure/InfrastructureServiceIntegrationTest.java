/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.infrastructure;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for InfrastructureService.
 */
class InfrastructureServiceIntegrationTest extends EventloopTestBase {

    private InfrastructureService infrastructureService;

    @BeforeEach
    void setUp() {
        infrastructureService = new MockInfrastructureService();
    }

    @Test
    @DisplayName("Integration: Should provision compute resources")
    void testProvisionCompute() throws Exception {
        ResourceRequest request = ResourceRequest.builder()
                .resourceType("COMPUTE")
                .specification(Map.of(
                    "cpu", "4",
                    "memory", "16GB",
                    "region", "us-west-2"
                ))
                .tenantId("test-tenant")
                .build();

        Promise<Resource> promise = infrastructureService.provision(request);
        Resource resource = runPromise(() -> promise);

        assertThat(resource).isNotNull();
        assertThat(resource.id()).isNotNull();
        assertThat(resource.type()).isEqualTo("COMPUTE");
        assertThat(resource.status()).isEqualTo("PROVISIONED");
    }

    @Test
    @DisplayName("Integration: Should provision database")
    void testProvisionDatabase() throws Exception {
        ResourceRequest request = ResourceRequest.builder()
                .resourceType("DATABASE")
                .specification(Map.of(
                    "engine", "postgresql",
                    "version", "15",
                    "instanceClass", "db.r5.xlarge"
                ))
                .tenantId("test-tenant")
                .build();

        Promise<Resource> promise = infrastructureService.provision(request);
        Resource resource = runPromise(() -> promise);

        assertThat(resource.type()).isEqualTo("DATABASE");
        assertThat(resource.endpoints()).containsKey("jdbc");
    }

    @Test
    @DisplayName("Integration: Should configure networking")
    void testConfigureNetworking() throws Exception {
        NetworkRequest request = NetworkRequest.builder()
                .vpcName("test-vpc")
                .cidrBlock("10.0.0.0/16")
                .subnets(Map.of(
                    "public-1a", "10.0.1.0/24",
                    "private-1a", "10.0.2.0/24"
                ))
                .tenantId("test-tenant")
                .build();

        Promise<NetworkConfig> promise = infrastructureService.configureNetwork(request);
        NetworkConfig config = runPromise(() -> promise);

        assertThat(config).isNotNull();
        assertThat(config.vpcId()).isNotNull();
        assertThat(config.subnets()).hasSize(2);
    }

    @Test
    @DisplayName("Integration: Should set up load balancer")
    void testSetupLoadBalancer() throws Exception {
        LoadBalancerRequest request = LoadBalancerRequest.builder()
                .name("test-lb")
                .type("ALB")
                .listeners(Map.of("80", "HTTP", "443", "HTTPS"))
                .targetGroups(Map.of("web", "80", "api", "8080"))
                .tenantId("test-tenant")
                .build();

        Promise<LoadBalancer> promise = infrastructureService.setupLoadBalancer(request);
        LoadBalancer lb = runPromise(() -> promise);

        assertThat(lb.arn()).isNotNull();
        assertThat(lb.dnsName()).isNotNull();
        assertThat(lb.listeners()).hasSize(2);
    }

    @Test
    @DisplayName("Integration: Should configure security groups")
    void testConfigureSecurityGroups() throws Exception {
        SecurityGroupRequest request = SecurityGroupRequest.builder()
                .name("test-sg")
                .vpcId("vpc-12345")
                .rules(java.util.List.of(
                    Rule.ingress("0.0.0.0/0", "tcp", 443),
                    Rule.ingress("10.0.0.0/8", "tcp", 8080),
                    Rule.egress("0.0.0.0/0", "all", 0)
                ))
                .tenantId("test-tenant")
                .build();

        Promise<SecurityGroup> promise = infrastructureService.configureSecurityGroup(request);
        SecurityGroup sg = runPromise(() -> promise);

        assertThat(sg.id()).isNotNull();
        assertThat(sg.rules()).hasSize(3);
    }

    @Test
    @DisplayName("Integration: Should deploy container to Kubernetes")
    void testDeployContainer() throws Exception {
        ContainerDeploymentRequest request = ContainerDeploymentRequest.builder()
                .image("yappc/api:latest")
                .replicas(3)
                .resources(Map.of("cpu", "500m", "memory", "1Gi"))
                .environment(Map.of("ENV", "production", "LOG_LEVEL", "info"))
                .tenantId("test-tenant")
                .build();

        Promise<Deployment> promise = infrastructureService.deployContainer(request);
        Deployment deployment = runPromise(() -> promise);

        assertThat(deployment.id()).isNotNull();
        assertThat(deployment.status()).isEqualTo("RUNNING");
        assertThat(deployment.endpoints()).isNotEmpty();
    }

    @Test
    @DisplayName("Integration: Should configure auto-scaling")
    void testConfigureAutoScaling() throws Exception {
        AutoScalingRequest request = AutoScalingRequest.builder()
                .resourceId("deployment-123")
                .minCapacity(2)
                .maxCapacity(10)
                .targetCpuUtilization(70.0)
                .scaleOutCooldown(300)
                .scaleInCooldown(600)
                .tenantId("test-tenant")
                .build();

        Promise<AutoScalingConfig> promise = infrastructureService.configureAutoScaling(request);
        AutoScalingConfig config = runPromise(() -> promise);

        assertThat(config.policyArn()).isNotNull();
        assertThat(config.minCapacity()).isEqualTo(2);
        assertThat(config.maxCapacity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Integration: Should retrieve resource metrics")
    void testGetResourceMetrics() throws Exception {
        Promise<ResourceMetrics> promise = infrastructureService.getMetrics("deployment-123");
        ResourceMetrics metrics = runPromise(() -> promise);

        assertThat(metrics).isNotNull();
        assertThat(metrics.cpuUtilization()).isGreaterThanOrEqualTo(0.0);
        assertThat(metrics.memoryUtilization()).isGreaterThanOrEqualTo(0.0);
        assertThat(metrics.requestCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Integration: Should decommission resources")
    void testDecommissionResources() throws Exception {
        Promise<Boolean> promise = infrastructureService.decommission("resource-123");
        Boolean result = runPromise(() -> promise);

        assertThat(result).isTrue();
    }

    // Mock implementations

    interface InfrastructureService {
        Promise<Resource> provision(ResourceRequest request);
        Promise<NetworkConfig> configureNetwork(NetworkRequest request);
        Promise<LoadBalancer> setupLoadBalancer(LoadBalancerRequest request);
        Promise<SecurityGroup> configureSecurityGroup(SecurityGroupRequest request);
        Promise<Deployment> deployContainer(ContainerDeploymentRequest request);
        Promise<AutoScalingConfig> configureAutoScaling(AutoScalingRequest request);
        Promise<ResourceMetrics> getMetrics(String resourceId);
        Promise<Boolean> decommission(String resourceId);
    }

    record ResourceRequest(String resourceType, Map<String, String> specification, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String resourceType, tenantId;
            private Map<String, String> specification;
            Builder resourceType(String v) { resourceType = v; return this; }
            Builder specification(Map<String, String> v) { specification = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            ResourceRequest build() { return new ResourceRequest(resourceType, specification, tenantId); }
        }
    }

    record Resource(String id, String type, String status, Map<String, String> endpoints, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String id, type, status, tenantId;
            private Map<String, String> endpoints;
            Builder id(String v) { id = v; return this; }
            Builder type(String v) { type = v; return this; }
            Builder status(String v) { status = v; return this; }
            Builder endpoints(Map<String, String> v) { endpoints = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            Resource build() { return new Resource(id, type, status, endpoints, tenantId); }
        }
    }

    record NetworkRequest(String vpcName, String cidrBlock, Map<String, String> subnets, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String vpcName, cidrBlock, tenantId;
            private Map<String, String> subnets;
            Builder vpcName(String v) { vpcName = v; return this; }
            Builder cidrBlock(String v) { cidrBlock = v; return this; }
            Builder subnets(Map<String, String> v) { subnets = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            NetworkRequest build() { return new NetworkRequest(vpcName, cidrBlock, subnets, tenantId); }
        }
    }

    record NetworkConfig(String vpcId, Map<String, String> subnets, String tenantId) {}

    record LoadBalancerRequest(String name, String type, Map<String, String> listeners, Map<String, String> targetGroups, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String name, type, tenantId;
            private Map<String, String> listeners, targetGroups;
            Builder name(String v) { name = v; return this; }
            Builder type(String v) { type = v; return this; }
            Builder listeners(Map<String, String> v) { listeners = v; return this; }
            Builder targetGroups(Map<String, String> v) { targetGroups = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            LoadBalancerRequest build() { return new LoadBalancerRequest(name, type, listeners, targetGroups, tenantId); }
        }
    }

    record LoadBalancer(String arn, String dnsName, Map<String, String> listeners, String tenantId) {}

    record SecurityGroupRequest(String name, String vpcId, java.util.List<Rule> rules, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String name, vpcId, tenantId;
            private java.util.List<Rule> rules;
            Builder name(String v) { name = v; return this; }
            Builder vpcId(String v) { vpcId = v; return this; }
            Builder rules(java.util.List<Rule> v) { rules = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            SecurityGroupRequest build() { return new SecurityGroupRequest(name, vpcId, rules, tenantId); }
        }
    }

    record Rule(String direction, String cidr, String protocol, int port) {
        static Rule ingress(String cidr, String protocol, int port) {
            return new Rule("ingress", cidr, protocol, port);
        }
        static Rule egress(String cidr, String protocol, int port) {
            return new Rule("egress", cidr, protocol, port);
        }
    }

    record SecurityGroup(String id, java.util.List<Rule> rules, String tenantId) {}

    record ContainerDeploymentRequest(String image, int replicas, Map<String, String> resources, Map<String, String> environment, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String image, tenantId;
            private int replicas;
            private Map<String, String> resources, environment;
            Builder image(String v) { image = v; return this; }
            Builder replicas(int v) { replicas = v; return this; }
            Builder resources(Map<String, String> v) { resources = v; return this; }
            Builder environment(Map<String, String> v) { environment = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            ContainerDeploymentRequest build() { return new ContainerDeploymentRequest(image, replicas, resources, environment, tenantId); }
        }
    }

    record Deployment(String id, String status, Map<String, String> endpoints, String tenantId) {}

    record AutoScalingRequest(String resourceId, int minCapacity, int maxCapacity, double targetCpuUtilization, int scaleOutCooldown, int scaleInCooldown, String tenantId) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String resourceId, tenantId;
            private int minCapacity, maxCapacity, scaleOutCooldown, scaleInCooldown;
            private double targetCpuUtilization;
            Builder resourceId(String v) { resourceId = v; return this; }
            Builder minCapacity(int v) { minCapacity = v; return this; }
            Builder maxCapacity(int v) { maxCapacity = v; return this; }
            Builder targetCpuUtilization(double v) { targetCpuUtilization = v; return this; }
            Builder scaleOutCooldown(int v) { scaleOutCooldown = v; return this; }
            Builder scaleInCooldown(int v) { scaleInCooldown = v; return this; }
            Builder tenantId(String v) { tenantId = v; return this; }
            AutoScalingRequest build() { return new AutoScalingRequest(resourceId, minCapacity, maxCapacity, targetCpuUtilization, scaleOutCooldown, scaleInCooldown, tenantId); }
        }
    }

    record AutoScalingConfig(String policyArn, int minCapacity, int maxCapacity, String tenantId) {}

    record ResourceMetrics(double cpuUtilization, double memoryUtilization, int requestCount) {}

    static class MockInfrastructureService implements InfrastructureService {

        @Override
        public Promise<Resource> provision(ResourceRequest request) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                Map<String, String> endpoints = new java.util.HashMap<>();
                if (request.resourceType().equals("DATABASE")) {
                    endpoints.put("jdbc", "jdbc:postgresql://localhost:5432/test");
                }

                return Resource.builder()
                    .id("resource-" + java.util.UUID.randomUUID())
                    .type(request.resourceType())
                    .status("PROVISIONED")
                    .endpoints(endpoints)
                    .tenantId(request.tenantId())
                    .build();
            });
        }

        @Override
        public Promise<NetworkConfig> configureNetwork(NetworkRequest request) {
            return Promise.of(new NetworkConfig(
                "vpc-" + java.util.UUID.randomUUID(),
                request.subnets(),
                request.tenantId()
            ));
        }

        @Override
        public Promise<LoadBalancer> setupLoadBalancer(LoadBalancerRequest request) {
            return Promise.of(new LoadBalancer(
                "arn:aws:elasticloadbalancing::123456789:loadbalancer/app/" + request.name(),
                request.name() + "-123456789.us-west-2.elb.amazonaws.com",
                request.listeners(),
                request.tenantId()
            ));
        }

        @Override
        public Promise<SecurityGroup> configureSecurityGroup(SecurityGroupRequest request) {
            return Promise.of(new SecurityGroup(
                "sg-" + java.util.UUID.randomUUID(),
                request.rules(),
                request.tenantId()
            ));
        }

        @Override
        public Promise<Deployment> deployContainer(ContainerDeploymentRequest request) {
            return Promise.of(new Deployment(
                "deployment-" + java.util.UUID.randomUUID(),
                "RUNNING",
                Map.of("service", "http://service:8080"),
                request.tenantId()
            ));
        }

        @Override
        public Promise<AutoScalingConfig> configureAutoScaling(AutoScalingRequest request) {
            return Promise.of(new AutoScalingConfig(
                "arn:aws:autoscaling::123456789:scalingPolicy:...",
                request.minCapacity(),
                request.maxCapacity(),
                request.tenantId()
            ));
        }

        @Override
        public Promise<ResourceMetrics> getMetrics(String resourceId) {
            return Promise.of(new ResourceMetrics(
                45.5,
                62.3,
                1250
            ));
        }

        @Override
        public Promise<Boolean> decommission(String resourceId) {
            return Promise.of(true);
        }
    }
}
