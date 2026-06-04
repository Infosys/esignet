// Package database wraps pgx connection-pool setup. The wrapper exposes a
// single constructor that opens, pings, and returns a pool ready for use.
// Callers own the lifecycle and must defer Close.
package database

import (
	"context"
	"fmt"
	"net/url"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	applog "github.com/mosip/esignet/internal/log"
)

// Config bundles every operator-controlled knob for the Postgres pool.
// Fields mirror the MOSIP helm chart's existing env vars
// (DATABASE_HOST / DATABASE_PORT / DATABASE_NAME / DATABASE_USERNAME /
// DB_DBUSER_PASSWORD) so the chart works for this service with no
// changes; the existing postgres-config ConfigMap and db-common-secrets
// Secret on every MOSIP cluster supply the values.
type Config struct {
	Host     string
	Port     string
	Name     string
	Username string
	Password string
	SSLMode  string // disable | require | verify-ca | verify-full
	Schema   string // optional Postgres search_path

	MaxConns        int32
	MinConns        int32
	MaxConnLifetime time.Duration
	MaxConnIdleTime time.Duration
	HealthTimeout   time.Duration
}

// ConnString assembles the libpq-style connection URL from the discrete
// fields. Password is URL-escaped so special characters survive (`@`,
// `#`, `%`, etc. in production-rotated passwords would otherwise break
// the URL parser).
//
// The returned string includes the password and MUST NOT be logged. Use
// (Config).Redacted for that.
func (c Config) ConnString() string {
	u := url.URL{
		Scheme: "postgres",
		User:   url.UserPassword(c.Username, c.Password),
		Host:   fmt.Sprintf("%s:%s", c.Host, c.Port),
		Path:   "/" + c.Name,
	}
	q := url.Values{}
	if c.SSLMode != "" {
		q.Set("sslmode", c.SSLMode)
	}
	if c.Schema != "" {
		q.Set("search_path", c.Schema)
	}
	u.RawQuery = q.Encode()
	return u.String()
}

// Redacted returns a log-safe representation of the config with the
// password masked. Use this in startup logs instead of ConnString.
func (c Config) Redacted() string {
	return fmt.Sprintf("postgres://%s:****@%s:%s/%s?sslmode=%s&search_path=%s",
		c.Username, c.Host, c.Port, c.Name, c.SSLMode, c.Schema)
}

// NewPool opens a pgxpool, pings it once, and returns it ready for use.
// Caller owns the lifecycle; defer pool.Close() in main.
func NewPool(ctx context.Context, cfg Config, log *applog.Logger) (*pgxpool.Pool, error) {
	log.Info("postgres: opening pool", applog.String("conn", cfg.Redacted()))

	pc, err := pgxpool.ParseConfig(cfg.ConnString())
	if err != nil {
		return nil, fmt.Errorf("parse db url: %w", err)
	}
	if cfg.MaxConns > 0 {
		pc.MaxConns = cfg.MaxConns
	}
	if cfg.MinConns > 0 {
		pc.MinConns = cfg.MinConns
	}
	if cfg.MaxConnLifetime > 0 {
		pc.MaxConnLifetime = cfg.MaxConnLifetime
	}
	if cfg.MaxConnIdleTime > 0 {
		pc.MaxConnIdleTime = cfg.MaxConnIdleTime
	}

	pool, err := pgxpool.NewWithConfig(ctx, pc)
	if err != nil {
		return nil, fmt.Errorf("open db pool: %w", err)
	}

	pingCtx, cancel := context.WithTimeout(ctx, cfg.HealthTimeout)
	defer cancel()
	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("db ping: %w", err)
	}

	log.Info("postgres: pool ready",
		applog.Int("max_conns", int(pc.MaxConns)),
		applog.Int("min_conns", int(pc.MinConns)),
	)
	return pool, nil
}
