package client

import (
	"fmt"
	"time"

	"github.com/kelseyhightower/envconfig"

	"github.com/mosip/esignet/internal/database"
)

// Config is the operator-controlled configuration for the client-mgmt
// feature. Populated from environment via envconfig. Supported-list values
// (claims / ACRs / grant types / auth methods) are NOT here — those come
// from the engine's /.well-known document at startup, or from baked-in
// defaults if discovery fails.
type Config struct {
	// Postgres knobs. DATABASE_URL is required; the rest carry sensible
	// defaults for the local docker-compose dev environment.
	Postgres database.Config

	// SupportedIDRegex bounds the shape of clientId / relyingPartyId. Not
	// published by /.well-known, so it stays an operator knob.
	SupportedIDRegex string

	// AdditionalConfigSchemaURL — empty uses the embedded schema; an
	// http / https / file URL is fetched once at startup.
	AdditionalConfigSchemaURL string
}

// envConfig is the flat envconfig-tagged representation. Translated to
// the structured Config by LoadConfig so callers see a clean shape and
// envconfig stays an implementation detail.
type envConfig struct {
	DatabaseURL               string        `envconfig:"DATABASE_URL" required:"true"`
	DBMaxConns                int32         `envconfig:"DB_MAX_CONNS" default:"10"`
	DBMinConns                int32         `envconfig:"DB_MIN_CONNS" default:"2"`
	DBMaxConnLifetime         time.Duration `envconfig:"DB_MAX_CONN_LIFETIME" default:"1h"`
	DBMaxConnIdleTime         time.Duration `envconfig:"DB_MAX_CONN_IDLE_TIME" default:"30m"`
	DBHealthTimeout           time.Duration `envconfig:"DB_HEALTH_TIMEOUT" default:"5s"`
	ClientSupportedIDRegex    string        `envconfig:"CLIENT_SUPPORTED_ID_REGEX" default:""`
	AdditionalConfigSchemaURL string        `envconfig:"CLIENT_ADDITIONAL_CONFIG_SCHEMA_URL" default:""`
}

// LoadConfig reads the operator's environment and returns a populated
// Config. Returns an error when required vars are missing or values can't
// be parsed.
func LoadConfig() (Config, error) {
	var raw envConfig
	if err := envconfig.Process("", &raw); err != nil {
		return Config{}, fmt.Errorf("load client config: %w", err)
	}
	return Config{
		Postgres: database.Config{
			URL:             raw.DatabaseURL,
			MaxConns:        raw.DBMaxConns,
			MinConns:        raw.DBMinConns,
			MaxConnLifetime: raw.DBMaxConnLifetime,
			MaxConnIdleTime: raw.DBMaxConnIdleTime,
			HealthTimeout:   raw.DBHealthTimeout,
		},
		SupportedIDRegex:          raw.ClientSupportedIDRegex,
		AdditionalConfigSchemaURL: raw.AdditionalConfigSchemaURL,
	}, nil
}
