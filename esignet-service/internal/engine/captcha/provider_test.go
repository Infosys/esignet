/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package captcha

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
)

// newTestProvider builds a Provider pointed at the given server URL.
func newTestProvider(t *testing.T, serverURL string) *Provider {
	t.Helper()
	p, err := New(Config{
		ValidatorURL: serverURL,
		ModuleName:   "esignet",
		TimeoutSecs:  5,
	})
	require.NoError(t, err)
	return p
}

// writeJSON writes a JSON body with the given status code.
func writeJSON(w http.ResponseWriter, code int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(body)
}

func TestNew_EmptyURL(t *testing.T) {
	_, err := New(Config{ValidatorURL: "", ModuleName: "esignet", TimeoutSecs: 5})
	require.Error(t, err, "New must fail when ValidatorURL is empty")
}

func TestNew_ValidURL(t *testing.T) {
	p, err := New(Config{ValidatorURL: "http://example.com", ModuleName: "esignet", TimeoutSecs: 5})
	require.NoError(t, err)
	assert.NotNil(t, p)
}

func TestVerify_EmptyToken(t *testing.T) {
	// No server should be contacted — an empty token short-circuits to Success: false.
	p, err := New(Config{ValidatorURL: "http://should-not-be-called.invalid", TimeoutSecs: 5})
	require.NoError(t, err)

	for _, tok := range []string{"", "   ", "\t"} {
		result, svcErr := p.Verify(context.Background(), tok)
		require.Nil(t, svcErr, "empty token must return nil service error")
		require.NotNil(t, result)
		assert.False(t, result.Success, "empty token must produce Success: false")
	}
}

func TestVerify_ValidToken_ServiceApproves(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, http.MethodPost, r.Method)
		assert.Equal(t, "application/json", r.Header.Get("Content-Type"))

		var req captchaRequest
		require.NoError(t, json.NewDecoder(r.Body).Decode(&req))
		assert.Equal(t, "esignet", req.ModuleName)
		assert.Equal(t, "valid-token-123", req.CaptchaToken)

		writeJSON(w, http.StatusOK, captchaResponse{
			Errors:   []captchaServiceError{},
			Response: &captchaVerdict{Success: true},
		})
	}))
	defer srv.Close()

	p := newTestProvider(t, srv.URL)
	result, svcErr := p.Verify(context.Background(), "valid-token-123")

	require.Nil(t, svcErr)
	require.NotNil(t, result)
	assert.True(t, result.Success)
}

func TestVerify_InvalidToken_ServiceRejectsWithErrors(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, captchaResponse{
			Errors: []captchaServiceError{
				{ErrorCode: "CAP-001", Message: "token expired"},
			},
		})
	}))
	defer srv.Close()

	p := newTestProvider(t, srv.URL)
	result, svcErr := p.Verify(context.Background(), "expired-token")

	require.Nil(t, svcErr, "a rejected token is a negative verdict, not a server error")
	require.NotNil(t, result)
	assert.False(t, result.Success)
}

func TestVerify_ServiceReturnsSuccessFalse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, captchaResponse{
			Errors:   []captchaServiceError{},
			Response: &captchaVerdict{Success: false},
		})
	}))
	defer srv.Close()

	p := newTestProvider(t, srv.URL)
	result, svcErr := p.Verify(context.Background(), "bad-token")

	require.Nil(t, svcErr)
	require.NotNil(t, result)
	assert.False(t, result.Success)
}

func TestVerify_2xx_NilResponseField(t *testing.T) {
	// 2xx body with no "response" key — treated as negative verdict.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, captchaResponse{
			Errors: []captchaServiceError{},
			// Response field omitted → nil after unmarshal
		})
	}))
	defer srv.Close()

	p := newTestProvider(t, srv.URL)
	result, svcErr := p.Verify(context.Background(), "some-token")

	require.Nil(t, svcErr)
	require.NotNil(t, result)
	assert.False(t, result.Success)
}

func TestVerify_Non2xxResponse(t *testing.T) {
	for _, code := range []int{http.StatusInternalServerError, http.StatusBadGateway, http.StatusUnauthorized} {
		code := code
		t.Run(http.StatusText(code), func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				w.WriteHeader(code)
			}))
			defer srv.Close()

			p := newTestProvider(t, srv.URL)
			result, svcErr := p.Verify(context.Background(), "some-token")

			assert.Nil(t, result, "non-2xx must return nil result")
			require.NotNil(t, svcErr)
			assert.Equal(t, common.ServerErrorType, svcErr.Type)
		})
	}
}

func TestVerify_TransportError(t *testing.T) {
	// Start and immediately close the server so any connection attempt fails.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {}))
	srv.Close()

	p := newTestProvider(t, srv.URL)
	result, svcErr := p.Verify(context.Background(), "some-token")

	assert.Nil(t, result)
	require.NotNil(t, svcErr)
	assert.Equal(t, common.ServerErrorType, svcErr.Type)
}

func TestVerify_MalformedJSONResponse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`not-valid-json{`))
	}))
	defer srv.Close()

	p := newTestProvider(t, srv.URL)
	result, svcErr := p.Verify(context.Background(), "some-token")

	assert.Nil(t, result)
	require.NotNil(t, svcErr)
	assert.Equal(t, common.ServerErrorType, svcErr.Type)
}

func TestConfig_IsEnabled(t *testing.T) {
	assert.False(t, Config{}.IsEnabled(), "zero-value Config must not be enabled")
	assert.False(t, Config{ValidatorURL: "   "}.IsEnabled(), "blank URL must not be enabled")
	assert.True(t, Config{ValidatorURL: "http://example.com"}.IsEnabled(), "non-empty URL must be enabled")
}

func TestLoadConfig_Defaults(t *testing.T) {
	t.Setenv(envCaptchaValidatorURL, "")
	t.Setenv(envCaptchaModuleName, "")
	t.Setenv(envCaptchaTimeoutSecs, "")

	cfg := LoadConfig()
	assert.Equal(t, "", cfg.ValidatorURL)
	assert.Equal(t, "", cfg.ModuleName)
	assert.Equal(t, defaultTimeoutSecs, cfg.TimeoutSecs)
}

func TestLoadConfig_EnvOverrides(t *testing.T) {
	t.Setenv(envCaptchaValidatorURL, "http://captcha.example.com/validate")
	t.Setenv(envCaptchaModuleName, "esignet")
	t.Setenv(envCaptchaTimeoutSecs, "15")

	cfg := LoadConfig()
	assert.Equal(t, "http://captcha.example.com/validate", cfg.ValidatorURL)
	assert.Equal(t, "esignet", cfg.ModuleName)
	assert.Equal(t, 15, cfg.TimeoutSecs)
}

func TestLoadConfig_InvalidTimeout_UsesDefault(t *testing.T) {
	t.Setenv(envCaptchaValidatorURL, "http://captcha.example.com/validate")
	t.Setenv(envCaptchaTimeoutSecs, "not-a-number")

	cfg := LoadConfig()
	assert.Equal(t, defaultTimeoutSecs, cfg.TimeoutSecs)
}
