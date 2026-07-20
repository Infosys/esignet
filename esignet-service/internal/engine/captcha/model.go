/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package captcha

// captchaRequest is the payload sent to the external captcha validation service.
// Modelled on the MOSIP CaptchaHelper POST /v1/captcha/validatecaptcha contract.
type captchaRequest struct {
	ModuleName   string `json:"moduleName"`
	CaptchaToken string `json:"captchaToken"`
}

// captchaResponse is the response returned by the external captcha validation service.
type captchaResponse struct {
	// Errors contains service-level error entries when validation could not
	// succeed (e.g. token rejected, module unknown). A non-empty slice means
	// the token was not accepted, which is a negative verdict — not a server
	// failure.
	Errors []captchaServiceError `json:"errors"`

	// Response carries the verification outcome on a successful 2xx with no
	// errors. It is nil when the service returns errors or an unexpected body.
	Response *captchaVerdict `json:"response"`
}

type captchaVerdict struct {
	Success bool `json:"success"`
}

type captchaServiceError struct {
	ErrorCode string `json:"errorCode"`
	Message   string `json:"message"`
}
