/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package captcha provides a concrete CaptchaValidationProvider that verifies
// captcha tokens against an external HTTP validation service.
package captcha

import (
	"os"
	"strconv"
	"strings"
)

const (
	envCaptchaValidatorURL = "CAPTCHA_VALIDATOR_URL"
	envCaptchaModuleName   = "CAPTCHA_MODULE_NAME"
	envCaptchaTimeoutSecs  = "CAPTCHA_TIMEOUT_SECS"

	defaultTimeoutSecs = 30
)

// Config holds the settings required by the captcha validation provider.
type Config struct {
	// ValidatorURL is the URL of the external captcha validation service
	// (e.g. POST /v1/captcha/validatecaptcha). Required; an empty value
	// disables the provider.
	ValidatorURL string

	// ModuleName is sent with every verification request so the captcha
	// service can apply module-specific policies. Optional.
	ModuleName string

	// TimeoutSecs is the HTTP client timeout in seconds. Defaults to 30.
	TimeoutSecs int
}

// IsEnabled reports whether the provider is configured and should be active.
// Returns false when ValidatorURL is empty.
func (c Config) IsEnabled() bool {
	return strings.TrimSpace(c.ValidatorURL) != ""
}

// LoadConfig reads captcha provider settings from environment variables.
func LoadConfig() Config {
	timeoutSecs := defaultTimeoutSecs
	if raw := os.Getenv(envCaptchaTimeoutSecs); raw != "" {
		if secs, err := strconv.Atoi(raw); err == nil && secs > 0 {
			timeoutSecs = secs
		}
	}

	return Config{
		ValidatorURL: strings.TrimSpace(os.Getenv(envCaptchaValidatorURL)),
		ModuleName:   strings.TrimSpace(os.Getenv(envCaptchaModuleName)),
		TimeoutSecs:  timeoutSecs,
	}
}
