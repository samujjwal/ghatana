package com.ghatana.aep.connector.strategy.rabbitmq;

import java.util.Objects;

/**
 * Configuration for RabbitMQ consumer/producer strategy.
 * 
 * @doc.type class
 * @doc.purpose RabbitMQ configuration
 * @doc.layer infrastructure
 * @doc.pattern Builder
 */
public class RabbitMQConfig {
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String virtualHost;
    private final String queueName;
    private final String exchangeName;
    private final String routingKey;
    private final boolean durable;
    private final boolean exclusive;
    private final boolean autoDelete;
    private final int prefetchCount;
    private final int batchSize;
    
    private RabbitMQConfig(Builder builder) {
        this.host = builder.host != null ? builder.host : "localhost";
        this.port = builder.port > 0 ? builder.port : 5672;
        this.username = builder.username != null ? builder.username : "guest";
        this.password = builder.password != null ? builder.password : "guest";
        this.virtualHost = builder.virtualHost != null ? builder.virtualHost : "/";
        this.queueName = Objects.requireNonNull(builder.queueName, "queueName required");
        this.exchangeName = builder.exchangeName != null ? builder.exchangeName : "";
        this.routingKey = builder.routingKey != null ? builder.routingKey : "";
        this.durable = builder.durable;
        this.exclusive = builder.exclusive;
        this.autoDelete = builder.autoDelete;
        this.prefetchCount = builder.prefetchCount > 0 ? builder.prefetchCount : 10;
        this.batchSize = builder.batchSize > 0 ? builder.batchSize : 10;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getVirtualHost() { return virtualHost; }
    public String getQueueName() { return queueName; }
    public String getExchangeName() { return exchangeName; }
    public String getRoutingKey() { return routingKey; }
    public boolean isDurable() { return durable; }
    public boolean isExclusive() { return exclusive; }
    public boolean isAutoDelete() { return autoDelete; }
    public int getPrefetchCount() { return prefetchCount; }
    public int getBatchSize() { return batchSize; }
    
    public static class Builder {
        private String host = "localhost";
        private int port = 5672;
        private String username = "guest";
        private String password = "guest";
        private String virtualHost = "/";
        private String queueName;
        private String exchangeName = "";
        private String routingKey = "";
        private boolean durable = true;
        private boolean exclusive = false;
        private boolean autoDelete = false;
        private int prefetchCount = 10;
        private int batchSize = 10;
        
        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder virtualHost(String virtualHost) { this.virtualHost = virtualHost; return this; }
        public Builder queueName(String queueName) { this.queueName = queueName; return this; }
        public Builder exchangeName(String exchangeName) { this.exchangeName = exchangeName; return this; }
        public Builder routingKey(String routingKey) { this.routingKey = routingKey; return this; }
        public Builder durable(boolean durable) { this.durable = durable; return this; }
        public Builder exclusive(boolean exclusive) { this.exclusive = exclusive; return this; }
        public Builder autoDelete(boolean autoDelete) { this.autoDelete = autoDelete; return this; }
        public Builder prefetchCount(int prefetchCount) { this.prefetchCount = prefetchCount; return this; }
        public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
        
        public RabbitMQConfig build() {
            return new RabbitMQConfig(this);
        }
    }
}
