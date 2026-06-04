package client

import (
	"context"
	"fmt"
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"

	applog "github.com/mosip/esignet/internal/log"
)

// Module is the constructed client feature, ready to mount on a router.
type Module struct {
	service   *Service
	validator *Validator
	log       *applog.Logger
}

// NewModule wires the validator and service against a repository built
// from the supplied pool. Supported-list enum values (claims, ACRs,
// grant types, auth methods) are taken from cfg — these are operator
// policy, sourced via env vars matching Java esignet's property pattern.
//
// Fails fast on validator-load errors.
func NewModule(
	ctx context.Context,
	cfg Config,
	pool *pgxpool.Pool,
	log *applog.Logger,
) (*Module, error) {
	validatorCfg := ClientValidatorConfig{
		SupportedUserClaims:        cfg.SupportedUserClaims,
		SupportedACRValues:         cfg.SupportedACRValues,
		SupportedGrantTypes:        cfg.SupportedGrantTypes,
		SupportedClientAuthMethods: cfg.SupportedClientAuthMethods,
		SupportedIDRegex:           cfg.SupportedIDRegex,
	}

	val, err := buildValidatorForConfig(ctx, validatorCfg, cfg.AdditionalConfigSchemaURL, log)
	if err != nil {
		return nil, fmt.Errorf("client module: %w", err)
	}
	repo := NewRepository(pool)
	return &Module{
		service:   NewService(repo, log),
		validator: val,
		log:       log,
	}, nil
}

// Initialize registers the client-management routes on the supplied mux.
// Routes are open; auth middleware will be added here when introduced.
func (m *Module) Initialize(mux *http.ServeMux) {
	create := ClientMgmtCreate(m.service, m.validator, m.log)
	mux.HandleFunc("POST /v1/esignet/client-mgmt/client", create)
}

// buildValidatorForConfig picks the embedded additionalConfig schema or
// the override URL, and logs which path was taken.
func buildValidatorForConfig(
	ctx context.Context,
	vcfg ClientValidatorConfig,
	additionalConfigSchemaURL string,
	log *applog.Logger,
) (*Validator, error) {
	if additionalConfigSchemaURL == "" {
		val, err := NewValidator(vcfg)
		if err != nil {
			return nil, err
		}
		log.Info("additionalConfig schema loaded (embedded)")
		return val, nil
	}
	val, err := NewValidatorWithSchema(ctx, vcfg, additionalConfigSchemaURL)
	if err != nil {
		return nil, err
	}
	log.Info("additionalConfig schema loaded (override)",
		applog.String("url", additionalConfigSchemaURL))
	return val, nil
}
