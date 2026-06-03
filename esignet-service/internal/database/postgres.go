// Package database wraps pgx connection-pool setup. The wrapper exposes a
// single constructor that opens, pings, and returns a pool ready for use.
// Callers own the lifecycle and must defer Close.
package database

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	applog "github.com/mosip/esignet/internal/log"
)

// Config bundles every operator-controlled knob for the Postgres pool.
// Values originate from envconfig in the consumer package; this struct
// stays free of env-tag annotations so it remains library-shaped.
type Config struct {
	URL             string
	MaxConns        int32
	MinConns        int32
	MaxConnLifetime time.Duration
	MaxConnIdleTime time.Duration
	HealthTimeout   time.Duration
}

// NewPool opens a pgxpool, pings it once, and returns it ready for use.
// Caller owns the lifecycle; defer pool.Close() in main.
func NewPool(ctx context.Context, cfg Config, log *applog.Logger) (*pgxpool.Pool, error) {
	pc, err := pgxpool.ParseConfig(cfg.URL)
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

	log.Info("postgres pool ready",
		applog.Int("max_conns", int(pc.MaxConns)),
		applog.Int("min_conns", int(pc.MinConns)),
	)
	return pool, nil
}
