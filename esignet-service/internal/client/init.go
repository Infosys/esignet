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
// from the supplied pool. The supplied mux is used in read-only mode for
// in-process discovery of the engine's /.well-known document; the actual
// route registration happens later in (*Module).Initialize.
//
// Fails fast on validator-load errors. Discovery itself never fails — it
// falls back to baked-in OIDC/RFC defaults when /.well-known is unreachable.
func NewModule(
	ctx context.Context,
	cfg Config,
	pool *pgxpool.Pool,
	mux *http.ServeMux,
	log *applog.Logger,
) (*Module, error) {
	supported := DiscoverSupportedLists(mux, log)
	validatorCfg := ClientValidatorConfig{
		SupportedUserClaims:        supported.UserClaims,
		SupportedACRValues:         supported.ACRValues,
		SupportedGrantTypes:        supported.GrantTypes,
		SupportedClientAuthMethods: supported.ClientAuthMethods,
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
