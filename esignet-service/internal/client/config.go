package client

import (
	"fmt"
	"time"

	"github.com/kelseyhightower/envconfig"

	"github.com/mosip/esignet/internal/database"
)

// Config is the operator-controlled configuration for the client-mgmt
// feature. Populated from environment via envconfig.
//
// The four supported-list fields (UserClaims, ACRValues, GrantTypes,
// ClientAuthMethods) are sourced from operator config — same pattern as
// Java esignet's application-default.properties. The engine's
// /.well-known describes engine capabilities; this list describes the
// MOSIP deployment policy for what RPs may register against.
type Config struct {
	// Postgres knobs. Discrete fields (not a single URL) so they can be
	// sourced from separate K8s ConfigMap + Secret references — matching
	// the existing MOSIP helm chart's postgres-config / db-common-secrets
	// objects.
	Postgres database.Config

	SupportedUserClaims        []string
	SupportedACRValues         []string
	SupportedGrantTypes        []string
	SupportedClientAuthMethods []string

	// SupportedIDRegex bounds the shape of clientId / relyingPartyId.
	SupportedIDRegex string

	// AdditionalConfigSchemaURL — empty uses the embedded schema; an
	// http / https / file URL is fetched once at startup.
	AdditionalConfigSchemaURL string
}

// envConfig is the flat envconfig-tagged representation. Translated to
// the structured Config by LoadConfig so callers see a clean shape and
// envconfig stays an implementation detail.
//
// Env var names for the DB block mirror the MOSIP helm chart's
// extraEnvVars block exactly (see deploy/esignet/values.yaml). The
// CLIENT_SUPPORTED_* names mirror Java esignet's property pattern
// (mosip.esignet.supported.grant.types, etc.). DO NOT rename without
// coordinating with ops — these names are referenced by ConfigMap and
// Secret bindings already in place on MOSIP clusters.
type envConfig struct {
	DatabaseHost     string `envconfig:"DATABASE_HOST" default:"localhost"`
	DatabasePort     string `envconfig:"DATABASE_PORT" default:"5432"`
	DatabaseName     string `envconfig:"DATABASE_NAME" default:"mosip_esignet"`
	DatabaseUsername string `envconfig:"DATABASE_USERNAME" default:"esignetuser"`
	// Password is REQUIRED. No default — refusing to start with an empty
	// password is safer than silently connecting with one.
	DBDBUserPassword string `envconfig:"DB_DBUSER_PASSWORD" required:"true"`
	DatabaseSSLMode  string `envconfig:"DATABASE_SSLMODE" default:"disable"`
	DatabaseSchema   string `envconfig:"DATABASE_SCHEMA" default:"esignet"`

	DBMaxConns        int32         `envconfig:"DB_MAX_CONNS" default:"10"`
	DBMinConns        int32         `envconfig:"DB_MIN_CONNS" default:"2"`
	DBMaxConnLifetime time.Duration `envconfig:"DB_MAX_CONN_LIFETIME" default:"1h"`
	DBMaxConnIdleTime time.Duration `envconfig:"DB_MAX_CONN_IDLE_TIME" default:"30m"`
	DBHealthTimeout   time.Duration `envconfig:"DB_HEALTH_TIMEOUT" default:"5s"`

	// Supported-list env vars. Comma-separated; envconfig parses them
	// into []string natively. All four are REQUIRED — the validator
	// can't compile a schema without them, and silent defaults would
	// drift from MOSIP policy.
	ClientSupportedUserClaims        []string `envconfig:"CLIENT_SUPPORTED_USER_CLAIMS" required:"true"`
	ClientSupportedACRValues         []string `envconfig:"CLIENT_SUPPORTED_ACR_VALUES" required:"true"`
	ClientSupportedGrantTypes        []string `envconfig:"CLIENT_SUPPORTED_GRANT_TYPES" required:"true"`
	ClientSupportedClientAuthMethods []string `envconfig:"CLIENT_SUPPORTED_CLIENT_AUTH_METHODS" required:"true"`

	ClientSupportedIDRegex    string `envconfig:"CLIENT_SUPPORTED_ID_REGEX" default:""`
	AdditionalConfigSchemaURL string `envconfig:"CLIENT_ADDITIONAL_CONFIG_SCHEMA_URL" default:""`
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
			Host:            raw.DatabaseHost,
			Port:            raw.DatabasePort,
			Name:            raw.DatabaseName,
			Username:        raw.DatabaseUsername,
			Password:        raw.DBDBUserPassword,
			SSLMode:         raw.DatabaseSSLMode,
			Schema:          raw.DatabaseSchema,
			MaxConns:        raw.DBMaxConns,
			MinConns:        raw.DBMinConns,
			MaxConnLifetime: raw.DBMaxConnLifetime,
			MaxConnIdleTime: raw.DBMaxConnIdleTime,
			HealthTimeout:   raw.DBHealthTimeout,
		},
		SupportedUserClaims:        raw.ClientSupportedUserClaims,
		SupportedACRValues:         raw.ClientSupportedACRValues,
		SupportedGrantTypes:        raw.ClientSupportedGrantTypes,
		SupportedClientAuthMethods: raw.ClientSupportedClientAuthMethods,
		SupportedIDRegex:           raw.ClientSupportedIDRegex,
		AdditionalConfigSchemaURL:  raw.AdditionalConfigSchemaURL,
	}, nil
}
