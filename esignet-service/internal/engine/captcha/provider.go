/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package captcha

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

// errCaptchaProviderServer is the server-side ServiceError returned for all
// operational failures (transport, non-2xx, malformed response). It signals
// that verification could not be completed and the request should be rejected
// with a server error rather than a client-level captcha-invalid response.
var errCaptchaProviderServer = &common.ServiceError{
	Code: "captcha_provider_error",
	Type: common.ServerErrorType,
	Error: common.I18nMessage{
		Key:          "error.captcha.provider_error",
		DefaultValue: "Captcha service error",
	},
	ErrorDescription: common.I18nMessage{
		Key:          "error.captcha.provider_error_description",
		DefaultValue: "The captcha validation service could not be reached or returned an unexpected response",
	},
}

// Provider is a concrete CaptchaValidationProvider that verifies tokens by
// calling an external HTTP captcha validation service.
type Provider struct {
	cfg    Config
	client *http.Client
	logger *applog.Logger
}

// New constructs a Provider from cfg. Returns an error when ValidatorURL is
// empty; callers should check cfg.IsEnabled() before calling New to avoid
// constructing a disabled provider.
func New(cfg Config) (*Provider, error) {
	if !cfg.IsEnabled() {
		return nil, errors.New("captcha: ValidatorURL must not be empty")
	}

	timeout := time.Duration(cfg.TimeoutSecs) * time.Second
	if timeout <= 0 {
		timeout = defaultTimeoutSecs * time.Second
	}

	return &Provider{
		cfg:    cfg,
		client: &http.Client{Timeout: timeout},
		logger: applog.GetLogger(),
	}, nil
}

// Verify implements providers.CaptchaValidationProvider.
//
// Outcome mapping:
//   - Empty/blank token          → {Success: false}, nil  (no HTTP call)
//   - 2xx + no errors + response → {Success: result.Success}, nil
//   - 2xx + errors or nil response → {Success: false}, nil
//   - Transport error / non-2xx / malformed body → nil, server ServiceError
func (p *Provider) Verify(ctx context.Context, token string) (*providers.CaptchaVerificationResult, *common.ServiceError) {
	if strings.TrimSpace(token) == "" {
		return &providers.CaptchaVerificationResult{Success: false}, nil
	}

	payload, err := json.Marshal(captchaRequest{
		ModuleName:   p.cfg.ModuleName,
		CaptchaToken: token,
	})
	if err != nil {
		p.logger.Error("captcha: failed to marshal request", applog.Error(err))
		return nil, errCaptchaProviderServer
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.cfg.ValidatorURL, bytes.NewReader(payload))
	if err != nil {
		p.logger.Error("captcha: failed to build HTTP request", applog.Error(err))
		return nil, errCaptchaProviderServer
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := p.client.Do(req)
	if err != nil {
		p.logger.Error("captcha: HTTP request to validation service failed",
			applog.String("url", p.cfg.ValidatorURL),
			applog.Error(err),
		)
		return nil, errCaptchaProviderServer
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		p.logger.Error("captcha: validation service returned non-2xx status",
			applog.String("url", p.cfg.ValidatorURL),
			applog.String("status", fmt.Sprintf("%d", resp.StatusCode)),
		)
		return nil, errCaptchaProviderServer
	}

	var result captchaResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		p.logger.Error("captcha: failed to decode response from validation service",
			applog.String("url", p.cfg.ValidatorURL),
			applog.Error(err),
		)
		return nil, errCaptchaProviderServer
	}

	// Service returned errors or omitted the response body — negative verdict.
	if len(result.Errors) > 0 || result.Response == nil {
		return &providers.CaptchaVerificationResult{Success: false}, nil
	}

	return &providers.CaptchaVerificationResult{Success: result.Response.Success}, nil
}
