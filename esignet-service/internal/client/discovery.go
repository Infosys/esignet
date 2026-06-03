package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"

	applog "github.com/mosip/esignet/internal/log"
)

// wellKnownPath is the OIDC Discovery 1.0 spec-defined location for the
// authorization server's metadata document.
const wellKnownPath = "/.well-known/openid-configuration"

// SupportedLists is what the validator needs from the engine to enforce
// client-registration enum constraints. Populated at startup from the
// engine's /.well-known document or from baked-in defaults.
type SupportedLists struct {
	UserClaims        []string
	ACRValues         []string
	GrantTypes        []string
	ClientAuthMethods []string
}

// DefaultSupportedLists returns OIDC/RFC-baseline defaults used as a
// fallback when /.well-known is unreachable, returns non-200, or returns
// malformed JSON. These values keep the service bootable; operators can
// confirm via startup logs which source (engine vs defaults) was used.
func DefaultSupportedLists() SupportedLists {
	return SupportedLists{
		// OIDC Core 1.0 standard claims (Section 5.1).
		UserClaims: []string{
			"sub", "name", "given_name", "family_name", "middle_name",
			"nickname", "preferred_username", "profile", "picture", "website",
			"email", "email_verified", "gender", "birthdate", "zoneinfo",
			"locale", "phone_number", "phone_number_verified", "address",
			"updated_at",
		},
		// MOSIP eSignet ACR values (matches Java application-default.properties).
		ACRValues: []string{
			"mosip:idp:acr:generated-code",
			"mosip:idp:acr:static-code",
			"mosip:idp:acr:linked-wallet",
			"mosip:idp:acr:biometrics",
			"mosip:idp:acr:knowledge",
			"mosip:idp:acr:password",
		},
		// RFC 6749 + OIDC core grant types eSignet supports.
		GrantTypes: []string{"authorization_code", "refresh_token"},
		// RFC 7521/7523 — what eSignet enforces in Java.
		ClientAuthMethods: []string{"private_key_jwt"},
	}
}

// DiscoverSupportedLists dispatches an in-process GET to
// /.well-known/openid-configuration through the shared mux and returns
// the four enum lists the validator needs. On any failure (non-200,
// decode error, empty arrays) it logs a WARN and returns the baked-in
// defaults so the service stays bootable.
func DiscoverSupportedLists(mux *http.ServeMux, log *applog.Logger) SupportedLists {
	req := httptest.NewRequest(http.MethodGet, wellKnownPath, nil)
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		log.Warn("well-known discovery returned non-200, using defaults",
			applog.Int("status", w.Code))
		return DefaultSupportedLists()
	}

	var doc struct {
		ClaimsSupported                   []string `json:"claims_supported"`
		AcrValuesSupported                []string `json:"acr_values_supported"`
		GrantTypesSupported               []string `json:"grant_types_supported"`
		TokenEndpointAuthMethodsSupported []string `json:"token_endpoint_auth_methods_supported"`
	}
	if err := json.NewDecoder(w.Body).Decode(&doc); err != nil {
		log.Warn("well-known decode failed, using defaults", applog.Error(err))
		return DefaultSupportedLists()
	}

	// Valid JSON but empty enum lists would make every schema validation
	// fail (no claim/ACR/grant/auth-method would be acceptable). Treat as
	// a misconfiguration and fall back.
	if len(doc.ClaimsSupported) == 0 || len(doc.AcrValuesSupported) == 0 ||
		len(doc.GrantTypesSupported) == 0 || len(doc.TokenEndpointAuthMethodsSupported) == 0 {
		log.Warn("well-known returned empty supported lists, using defaults")
		return DefaultSupportedLists()
	}

	log.Info("supported lists loaded from engine /.well-known",
		applog.Int("user_claims", len(doc.ClaimsSupported)),
		applog.Int("acr_values", len(doc.AcrValuesSupported)),
		applog.Int("grant_types", len(doc.GrantTypesSupported)),
		applog.Int("auth_methods", len(doc.TokenEndpointAuthMethodsSupported)),
	)
	return SupportedLists{
		UserClaims:        doc.ClaimsSupported,
		ACRValues:         doc.AcrValuesSupported,
		GrantTypes:        doc.GrantTypesSupported,
		ClientAuthMethods: doc.TokenEndpointAuthMethodsSupported,
	}
}
