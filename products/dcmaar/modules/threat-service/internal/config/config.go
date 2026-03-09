package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config holds the application configuration.
type Config struct {
	// Server configuration
	GRPCAddr          string        `mapstructure:"GRPC_ADDR"`
	HTTPAddr          string        `mapstructure:"HTTP_ADDR"`
	TLSCertFile       string        `mapstructure:"TLS_CERT_FILE"`
	TLSKeyFile        string        `mapstructure:"TLS_KEY_FILE"`
	MaxReceiveMsgSize int           `mapstructure:"MAX_RECEIVE_MSG_SIZE"`
	ShutdownTimeout   time.Duration `mapstructure:"SHUTDOWN_TIMEOUT"`

	// Storage configuration
	ClickHouseDSN string `mapstructure:"CLICKHOUSE_DSN"`
	PostgresDSN   string `mapstructure:"POSTGRES_DSN"`
	MaxOpenConns  int    `mapstructure:"MAX_OPEN_CONNS"`
	MaxIdleConns  int    `mapstructure:"MAX_IDLE_CONNS"`

	// Rate limiting
	RateLimitRPS    float64       `mapstructure:"RATE_LIMIT_RPS"`
	RateLimitBurst  int           `mapstructure:"RATE_LIMIT_BURST"`
	RateLimitWindow time.Duration `mapstructure:"RATE_LIMIT_WINDOW"`

	// Logging
	LogLevel string `mapstructure:"LOG_LEVEL"`
	LogJSON  bool   `mapstructure:"LOG_JSON"`

	// Telemetry
	MetricsAddr     string `mapstructure:"METRICS_ADDR"`
	MetricsEnabled  bool   `mapstructure:"METRICS_ENABLED"`
	TracingEnabled  bool   `mapstructure:"TRACING_ENABLED"`
	TracingEndpoint string `mapstructure:"TRACING_ENDPOINT"`
}

// DefaultConfig returns a configuration with default values.
func DefaultConfig() *Config {
	return &Config{
		GRPCAddr:          ":50051",
		HTTPAddr:          ":8080",
		MaxReceiveMsgSize: 10 * 1024 * 1024, // 10MB
		ShutdownTimeout:   30 * time.Second,

		ClickHouseDSN: "clickhouse://localhost:9000/default",
		PostgresDSN:   "postgres://postgres:postgres@localhost:5432/dcmaar?sslmode=disable",
		MaxOpenConns:  10,
		MaxIdleConns:  5,

		RateLimitRPS:    100,
		RateLimitBurst:  50,
		RateLimitWindow: time.Minute,

		LogLevel: "info",
		LogJSON:  true,

		MetricsAddr:     ":9090",
		MetricsEnabled:  true,
		TracingEnabled:  true,
		TracingEndpoint: "localhost:4317",
	}
}

// Load loads configuration from environment variables.
func Load() (*Config, error) {
	cfg := DefaultConfig()

	// Helper function to get environment variable with default
	getEnv := func(key, def string) string {
		if v := os.Getenv(key); v != "" {
			return v
		}
		return def
	}

	// Helper function to get environment variable as int with default
	getEnvInt := func(key string, def int) int {
		if v := os.Getenv(key); v != "" {
			if i, err := strconv.Atoi(v); err == nil {
				return i
			}
		}
		return def
	}

	// Helper function to get environment variable as bool with default
	getEnvBool := func(key string, def bool) bool {
		if v := os.Getenv(key); v != "" {
			b, _ := strconv.ParseBool(v)
			return b
		}
		return def
	}

	// Helper function to get environment variable as duration with default
	getEnvDuration := func(key string, def time.Duration) time.Duration {
		if v := os.Getenv(key); v != "" {
			d, err := time.ParseDuration(v)
			if err == nil {
				return d
			}
		}
		return def
	}

	// Load configuration from environment variables
	cfg.GRPCAddr = getEnv("GRPC_ADDR", cfg.GRPCAddr)
	cfg.HTTPAddr = getEnv("HTTP_ADDR", cfg.HTTPAddr)
	cfg.TLSCertFile = getEnv("TLS_CERT_FILE", cfg.TLSCertFile)
	cfg.TLSKeyFile = getEnv("TLS_KEY_FILE", cfg.TLSKeyFile)
	cfg.MaxReceiveMsgSize = getEnvInt("MAX_RECEIVE_MSG_SIZE", cfg.MaxReceiveMsgSize)
	cfg.ShutdownTimeout = getEnvDuration("SHUTDOWN_TIMEOUT", cfg.ShutdownTimeout)

	cfg.ClickHouseDSN = getEnv("CLICKHOUSE_DSN", cfg.ClickHouseDSN)
	cfg.PostgresDSN = getEnv("POSTGRES_DSN", cfg.PostgresDSN)
	cfg.MaxOpenConns = getEnvInt("MAX_OPEN_CONNS", cfg.MaxOpenConns)
	cfg.MaxIdleConns = getEnvInt("MAX_IDLE_CONNS", cfg.MaxIdleConns)

	cfg.RateLimitRPS = float64(getEnvInt("RATE_LIMIT_RPS", int(cfg.RateLimitRPS)))
	cfg.RateLimitBurst = getEnvInt("RATE_LIMIT_BURST", cfg.RateLimitBurst)
	cfg.RateLimitWindow = getEnvDuration("RATE_LIMIT_WINDOW", cfg.RateLimitWindow)

	cfg.LogLevel = getEnv("LOG_LEVEL", cfg.LogLevel)
	cfg.LogJSON = getEnvBool("LOG_JSON", cfg.LogJSON)

	cfg.MetricsAddr = getEnv("METRICS_ADDR", cfg.MetricsAddr)
	cfg.MetricsEnabled = getEnvBool("METRICS_ENABLED", cfg.MetricsEnabled)
	cfg.TracingEnabled = getEnvBool("TRACING_ENABLED", cfg.TracingEnabled)
	cfg.TracingEndpoint = getEnv("TRACING_ENDPOINT", cfg.TracingEndpoint)

	// Validate configuration
	if err := cfg.Validate(); err != nil {
		return nil, fmt.Errorf("invalid configuration: %w", err)
	}

	return cfg, nil
}

// Validate validates the configuration.
func (c *Config) Validate() error {
	if c.GRPCAddr == "" {
		return fmt.Errorf("GRPC_ADDR is required")
	}

	if c.HTTPAddr == "" {
		return fmt.Errorf("HTTP_ADDR is required")
	}

	if c.ClickHouseDSN == "" {
		return fmt.Errorf("CLICKHOUSE_DSN is required")
	}

	if c.RateLimitRPS <= 0 {
		return fmt.Errorf("RATE_LIMIT_RPS must be greater than 0")
	}

	if c.RateLimitBurst <= 0 {
		return fmt.Errorf("RATE_LIMIT_BURST must be greater than 0")
	}

	if c.RateLimitWindow <= 0 {
		return fmt.Errorf("RATE_LIMIT_WINDOW must be greater than 0")
	}

	return nil
}

// String returns a string representation of the configuration (with sensitive data redacted).
func (c *Config) String() string {
	return fmt.Sprintf(`Config{
  GRPCAddr: %q,
  HTTPAddr: %q,
  TLSCertFile: %q,
  TLSKeyFile: %q,
  MaxReceiveMsgSize: %d,
  ShutdownTimeout: %s,
  ClickHouseDSN: %q,
  PostgresDSN: %q,
  MaxOpenConns: %d,
  MaxIdleConns: %d,
  RateLimitRPS: %f,
  RateLimitBurst: %d,
  RateLimitWindow: %s,
  LogLevel: %q,
  LogJSON: %t,
  MetricsAddr: %q,
  MetricsEnabled: %t,
  TracingEnabled: %t,
  TracingEndpoint: %q
}`,
		c.GRPCAddr,
		c.HTTPAddr,
		c.TLSCertFile,
		c.TLSKeyFile,
		c.MaxReceiveMsgSize,
		c.ShutdownTimeout,
		c.ClickHouseDSN,
		"[REDACTED]", // Redact DSN
		c.MaxOpenConns,
		c.MaxIdleConns,
		c.RateLimitRPS,
		c.RateLimitBurst,
		c.RateLimitWindow,
		c.LogLevel,
		c.LogJSON,
		c.MetricsAddr,
		c.MetricsEnabled,
		c.TracingEnabled,
		c.TracingEndpoint,
	)
}
