package config

import (
	"time"

	"github.com/spf13/viper"
	"github.com/samujjwal/dcmaar/apps/server/internal/query"
)

type Config struct {
    Server   ServerConfig   `mapstructure:"server"`
    Database DatabaseConfig `mapstructure:"database"`
    Cache    CacheConfig    `mapstructure:"cache"`
    Logging  LoggingConfig  `mapstructure:"logging"`
    Tracing  TracingConfig  `mapstructure:"tracing"`
    Metrics  MetricsConfig  `mapstructure:"metrics"`
    Identity IdentityConfig `mapstructure:"identity"`
    Policy   PolicyConfig   `mapstructure:"policy"`
    Features FeaturesConfig `mapstructure:"features"`
}

type ServerConfig struct {
	Host            string        `mapstructure:"host"`
	Port            int           `mapstructure:"port"`
	ShutdownTimeout time.Duration `mapstructure:"shutdown_timeout"`
}

type DatabaseConfig struct {
	Host            string        `mapstructure:"host"`
	Port            int           `mapstructure:"port"`
	User            string        `mapstructure:"user"`
	Password        string        `mapstructure:"password"`
	Name            string        `mapstructure:"name"`
	MaxOpenConns    int           `mapstructure:"max_open_conns"`
	MaxIdleConns    int           `mapstructure:"max_idle_conns"`
	ConnMaxLifetime time.Duration `mapstructure:"conn_max_lifetime"`
}

type CacheConfig struct {
	Enabled     bool          `mapstructure:"enabled"`
	Backend     string        `mapstructure:"backend"`
	TTL         time.Duration `mapstructure:"ttl"`
	Redis       RedisConfig   `mapstructure:"redis"`
	MaxItemSize int           `mapstructure:"max_item_size"` // in bytes
}

type RedisConfig struct {
	Addr     string `mapstructure:"addr"`
	Password string `mapstructure:"password"`
	DB       int    `mapstructure:"db"`
}

type LoggingConfig struct {
	Level  string `mapstructure:"level"`
	Format string `mapstructure:"format"`
}

type TracingConfig struct {
	Enabled            bool              `mapstructure:"enabled"`
	ServiceName        string            `mapstructure:"service_name"`
	SamplingRate       float64           `mapstructure:"sampling_rate"`
	Endpoint           string            `mapstructure:"endpoint"`
	Insecure           bool              `mapstructure:"insecure"`
	ResourceAttributes map[string]string `mapstructure:"resource_attributes"`
	Batch              BatchConfig       `mapstructure:"batch"`
}

type MetricsConfig struct {
	Enabled      bool          `mapstructure:"enabled"`
	Port         int           `mapstructure:"port"`
	Path         string        `mapstructure:"path"`
	Namespace    string        `mapstructure:"namespace"`
	Subsystem    string        `mapstructure:"subsystem"`
	ReadTimeout  time.Duration `mapstructure:"read_timeout"`
	WriteTimeout time.Duration `mapstructure:"write_timeout"`
}

type IdentityConfig struct {
    TTL time.Duration `mapstructure:"ttl"`
}

type PolicyConfig struct {
    TTL            time.Duration `mapstructure:"ttl"`
    DenyByDefault  bool          `mapstructure:"deny_by_default"`
    UseRego        bool          `mapstructure:"use_rego"`
    RegoPolicyFile string        `mapstructure:"rego_policy_file"`
}

type FeaturesConfig struct {
    Debug       bool `mapstructure:"debug"`
    DevHelpers  bool `mapstructure:"dev_helpers"`
    Experimental bool `mapstructure:"experimental"`
    Pprof       bool `mapstructure:"pprof"`
}

type BatchConfig struct {
	MaxExportBatchSize int           `mapstructure:"max_export_batch_size"`
	MaxQueueSize       int           `mapstructure:"max_queue_size"`
	Timeout            time.Duration `mapstructure:"timeout"`
}

func LoadConfig(path string) (*Config, error) {
	viper.SetConfigFile(path)
	viper.AutomaticEnv()

	setDefaults()

	if err := viper.ReadInConfig(); err != nil {
		return nil, err
	}

	var config Config
	if err := viper.Unmarshal(&config); err != nil {
		return nil, err
	}

	return &config, nil
}

func setDefaults() {
	// Server defaults
	viper.SetDefault("server.host", "0.0.0.0")
	viper.SetDefault("server.port", 8080)
	viper.SetDefault("server.shutdown_timeout", "30s")

	// Database defaults
	viper.SetDefault("database.host", "localhost")
	viper.SetDefault("database.port", 5432)
	viper.SetDefault("database.user", "postgres")
	viper.SetDefault("database.name", "dcmaar")
	viper.SetDefault("database.max_open_conns", 10)
	viper.SetDefault("database.max_idle_conns", 5)
	viper.SetDefault("database.conn_max_lifetime", "30m")

	// Cache defaults
	viper.SetDefault("cache.enabled", true)
	viper.SetDefault("cache.backend", "memory")
	viper.SetDefault("cache.ttl", "5m")
	viper.SetDefault("cache.max_item_size", 1048576) // 1MB
	viper.SetDefault("cache.redis.addr", "localhost:6379")
	viper.SetDefault("cache.redis.db", 0)

	// Logging defaults
	viper.SetDefault("logging.level", "info")
	viper.SetDefault("logging.format", "json")

	// Tracing defaults
	viper.SetDefault("tracing.enabled", false)
	viper.SetDefault("tracing.service_name", "dcmaar-server")
	viper.SetDefault("tracing.sampling_rate", 0.1)
	viper.SetDefault("tracing.endpoint", "localhost:4317")
	viper.SetDefault("tracing.insecure", true)
	viper.SetDefault("tracing.batch.max_export_batch_size", 1000)
	viper.SetDefault("tracing.batch.max_queue_size", 10000)
	viper.SetDefault("tracing.batch.timeout", "5s")

    // Metrics defaults
    viper.SetDefault("metrics.enabled", true)
	viper.SetDefault("metrics.port", 8081)
	viper.SetDefault("metrics.path", "/metrics")
	viper.SetDefault("metrics.namespace", "dcmaar")
	viper.SetDefault("metrics.subsystem", "server")
	viper.SetDefault("metrics.read_timeout", "10s")
    viper.SetDefault("metrics.write_timeout", "10s")

    // Identity defaults
    viper.SetDefault("identity.ttl", "2m")

    // Policy defaults
    viper.SetDefault("policy.ttl", "1m")
    viper.SetDefault("policy.deny_by_default", false)
    viper.SetDefault("policy.use_rego", false)
    viper.SetDefault("policy.rego_policy_file", "ops/opa-policies/actions.rego")

    // Features defaults
    viper.SetDefault("features.debug", false)
    viper.SetDefault("features.dev_helpers", false)
    viper.SetDefault("features.experimental", false)
    viper.SetDefault("features.pprof", false)
}

// GetCacheConfig returns the cache configuration
func (c *Config) GetCacheConfig() *query.CacheConfig {
	if !c.Cache.Enabled {
		return nil
	}

	return &query.CacheConfig{
		Enabled: c.Cache.Enabled,
		Backend: c.Cache.Backend,
		TTL:     c.Cache.TTL,
		RedisConfig: &query.RedisConfig{
			Addr:     c.Cache.Redis.Addr,
			Password: c.Cache.Redis.Password,
			DB:       c.Cache.Redis.DB,
		},
	}
}
