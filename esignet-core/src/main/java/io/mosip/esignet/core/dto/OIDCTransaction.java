/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.util.LinkCodeQueue;
import lombok.Data;

import java.util.List;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

@Data
public class OIDCTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    String transactionId;

    String clientId;
    String relyingPartyId;
    String redirectUri;
    Claims resolvedClaims;
    List<String> essentialClaims;
    List<String> voluntaryClaims;
    List<String> requestedAuthorizeScopes;
    String[] claimsLocales;
    String authTransactionId;

    Set<List<String>> providedAuthFactors;
    String kycToken;
    String partnerSpecificUserToken;
    long authTimeInSeconds;
    String codeHash;

    List<String> acceptedClaims;
    List<String> permittedScopes;
    String encryptedKyc;
    String aHash;

    LinkCodeQueue linkCodeQueue;
    int currentLinkCodeLimit;

    String linkedCodeHash;
    String linkedTransactionId;

    String nonce;
    String state;

    String individualId;
    String individualIdHash;

    String oauthDetailsHash;
    ConsentAction consentAction;

    //signup redirect secret
    String serverNonce;

    //PKCE support
    ProofKeyCodeExchange proofKeyCodeExchange;
    List<String> requestedCredentialScopes;

    boolean isInternalAuthSuccess;
    Map<String, List<JsonNode>> claimMetadata;
    Map<String, JsonNode> requestedClaimDetails;

    String verificationStatus;
    String verificationErrorCode;
    String userInfoResponseType;

    String[] prompt;
    int consentExpireMinutes;
}
