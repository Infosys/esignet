package client

import (
	"net/http"
	"testing"
)

func TestDefaultSupportedLists_NonEmpty(t *testing.T) {
	d := DefaultSupportedLists()
	if len(d.UserClaims) == 0 {
		t.Error("UserClaims is empty")
	}
	if len(d.ACRValues) == 0 {
		t.Error("ACRValues is empty")
	}
	if len(d.GrantTypes) == 0 {
		t.Error("GrantTypes is empty")
	}
	if len(d.ClientAuthMethods) == 0 {
		t.Error("ClientAuthMethods is empty")
	}
}

func TestDiscoverSupportedLists_EngineSourced(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /.well-known/openid-configuration", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{
			"claims_supported": ["sub", "email", "custom_claim"],
			"acr_values_supported": ["urn:example:loa1"],
			"grant_types_supported": ["authorization_code"],
			"token_endpoint_auth_methods_supported": ["private_key_jwt", "client_secret_basic"]
		}`))
	})

	got := DiscoverSupportedLists(mux, testLogger())
	if len(got.UserClaims) != 3 || got.UserClaims[2] != "custom_claim" {
		t.Errorf("UserClaims: got %v", got.UserClaims)
	}
	if len(got.ACRValues) != 1 || got.ACRValues[0] != "urn:example:loa1" {
		t.Errorf("ACRValues: got %v", got.ACRValues)
	}
	if len(got.GrantTypes) != 1 || got.GrantTypes[0] != "authorization_code" {
		t.Errorf("GrantTypes: got %v", got.GrantTypes)
	}
	if len(got.ClientAuthMethods) != 2 {
		t.Errorf("ClientAuthMethods: got %v", got.ClientAuthMethods)
	}
}

func TestDiscoverSupportedLists_FallbackOnNon200(t *testing.T) {
	// Empty mux — /.well-known/openid-configuration is unregistered, so the
	// default mux returns 404. Must fall back to baked-in defaults.
	mux := http.NewServeMux()
	got := DiscoverSupportedLists(mux, testLogger())

	want := DefaultSupportedLists()
	if len(got.UserClaims) != len(want.UserClaims) {
		t.Errorf("fallback UserClaims length: got %d, want %d", len(got.UserClaims), len(want.UserClaims))
	}
}

func TestDiscoverSupportedLists_FallbackOnMalformedJSON(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /.well-known/openid-configuration", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("not-json"))
	})
	got := DiscoverSupportedLists(mux, testLogger())
	want := DefaultSupportedLists()
	if len(got.UserClaims) != len(want.UserClaims) {
		t.Errorf("malformed-body fallback didn't kick in")
	}
}

func TestDiscoverSupportedLists_FallbackOnEmptyArrays(t *testing.T) {
	// Valid JSON, but one or more empty arrays — must fall back, otherwise
	// every schema validation would fail.
	mux := http.NewServeMux()
	mux.HandleFunc("GET /.well-known/openid-configuration", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{
			"claims_supported": [],
			"acr_values_supported": ["x"],
			"grant_types_supported": ["y"],
			"token_endpoint_auth_methods_supported": ["z"]
		}`))
	})
	got := DiscoverSupportedLists(mux, testLogger())
	want := DefaultSupportedLists()
	if len(got.UserClaims) != len(want.UserClaims) {
		t.Errorf("empty-array fallback didn't kick in")
	}
}
