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
	log.Info("[discovery] STEP 1 — dispatching in-process request",
		applog.String("path", wellKnownPath),
		applog.String("method", http.MethodGet),
	)

	req := httptest.NewRequest(http.MethodGet, wellKnownPath, nil)
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)

	log.Info("[discovery] STEP 2 — got response from mux",
		applog.Int("status", w.Code),
		applog.Int("body_bytes", w.Body.Len()),
	)

	if w.Code != http.StatusOK {
		log.Warn("[discovery] DECISION — non-200 status, falling back to DefaultSupportedLists()",
			applog.Int("status", w.Code))
		return logAndReturnDefaults(log, "non-200")
	}

	var doc struct {
		ClaimsSupported                   []string `json:"claims_supported"`
		AcrValuesSupported                []string `json:"acr_values_supported"`
		GrantTypesSupported               []string `json:"grant_types_supported"`
		TokenEndpointAuthMethodsSupported []string `json:"token_endpoint_auth_methods_supported"`
	}
	if err := json.NewDecoder(w.Body).Decode(&doc); err != nil {
		log.Warn("[discovery] DECISION — JSON decode failed, falling back to DefaultSupportedLists()",
			applog.Error(err))
		return logAndReturnDefaults(log, "decode-failed")
	}

	log.Info("[discovery] STEP 3 — parsed well-known JSON, per-field presence:",
		applog.Int("claims_supported", len(doc.ClaimsSupported)),
		applog.Int("acr_values_supported", len(doc.AcrValuesSupported)),
		applog.Int("grant_types_supported", len(doc.GrantTypesSupported)),
		applog.Int("token_endpoint_auth_methods_supported", len(doc.TokenEndpointAuthMethodsSupported)),
	)

	// Valid JSON but empty enum lists would make every schema validation
	// fail (no claim/ACR/grant/auth-method would be acceptable). Treat as
	// a misconfiguration and fall back.
	if len(doc.ClaimsSupported) == 0 || len(doc.AcrValuesSupported) == 0 ||
		len(doc.GrantTypesSupported) == 0 || len(doc.TokenEndpointAuthMethodsSupported) == 0 {
		log.Warn("[discovery] DECISION — one or more fields missing/empty, falling back to DefaultSupportedLists()")
		return logAndReturnDefaults(log, "empty-fields")
	}

	log.Info("[discovery] DECISION — engine values accepted; using engine-sourced lists",
		applog.Any("user_claims", doc.ClaimsSupported),
		applog.Any("acr_values", doc.AcrValuesSupported),
		applog.Any("grant_types", doc.GrantTypesSupported),
		applog.Any("auth_methods", doc.TokenEndpointAuthMethodsSupported),
	)
	return SupportedLists{
		UserClaims:        doc.ClaimsSupported,
		ACRValues:         doc.AcrValuesSupported,
		GrantTypes:        doc.GrantTypesSupported,
		ClientAuthMethods: doc.TokenEndpointAuthMethodsSupported,
	}
}

// logAndReturnDefaults prints the actual default lists that get baked into
// the schema, so an operator can compare them against the engine's published
// values when a fallback kicks in.
func logAndReturnDefaults(log *applog.Logger, reason string) SupportedLists {
	d := DefaultSupportedLists()
	log.Info("[discovery] using baked-in DefaultSupportedLists()",
		applog.String("reason", reason),
		applog.Any("user_claims", d.UserClaims),
		applog.Any("acr_values", d.ACRValues),
		applog.Any("grant_types", d.GrantTypes),
		applog.Any("auth_methods", d.ClientAuthMethods),
	)
	return d
}
