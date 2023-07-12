/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
@Component
public class ConsentHelperService {
    @Autowired
    private ConsentService consentService;

    @Autowired
    private KeyBindingHelperService keyBindingHelperService;

    public void processConsent(OIDCTransaction transaction, boolean linked) {
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsuToken(transaction.getPartnerSpecificUserToken());
        Optional<ConsentDetail> consent = consentService.getUserConsent(userConsentRequest);

        ConsentAction consentAction = consent.isEmpty() ? ConsentAction.CAPTURE : evaluateConsentAction(transaction,consent.get(), linked);

        transaction.setConsentAction(consentAction);

        if(consentAction.equals(ConsentAction.NOCAPTURE)) {
            transaction.setAcceptedClaims(consent.get().getAcceptedClaims()); //NOSONAR consent is already evaluated to be not null
            transaction.setPermittedScopes(consent.get().getPermittedScopes()); //NOSONAR consent is already evaluated to be not null
        }
    }


    public void addUserConsent(OIDCTransaction transaction, boolean linked, String signature) {
        if (ConsentAction.CAPTURE.equals(transaction.getConsentAction())) {
            UserConsent userConsent = new UserConsent();
            userConsent.setClientId(transaction.getClientId());
            userConsent.setPsuToken(transaction.getPartnerSpecificUserToken());
            Claims claims = transaction.getRequestedClaims();
            List<String> acceptedClaims = transaction.getAcceptedClaims();
            Claims normalizedClaims = new Claims();
            normalizedClaims.setUserinfo(normalizeClaims(claims.getUserinfo()));
            normalizedClaims.setId_token(normalizeClaims(claims.getId_token()));

            userConsent.setClaims(normalizedClaims);
            userConsent.setSignature(signature);
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            Map<String, Boolean> authorizeScopes = permittedScopes != null ? permittedScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), authorizeScope::contains)) : Collections.emptyMap();
            userConsent.setAuthorizationScopes(authorizeScopes);
            userConsent.setAcceptedClaims(acceptedClaims);
            userConsent.setPermittedScopes(permittedScopes);
            try {
                userConsent.setHash(hashUserConsent(normalizedClaims, authorizeScopes));
            } catch (JsonProcessingException e) {
                throw new EsignetException(ErrorConstants.INVALID_CLAIM);
            }
            ConsentDetail consentDetail = new ConsentDetail();
            consentDetail.setPsuToken(userConsent.getPsuToken());
            consentDetail.setClientId(userConsent.getClientId());
            consentDetail.setClaims(userConsent.getClaims());
            consentDetail.setAuthorizationScopes(userConsent.getAuthorizationScopes());
            consentDetail.setExpiredtimes(userConsent.getExpirydtimes());
            consentDetail.setHash(userConsent.getHash());
            consentDetail.setSignature(userConsent.getSignature());
            consentDetail.setAcceptedClaims(userConsent.getAcceptedClaims());
            consentDetail.setPermittedScopes(userConsent.getPermittedScopes());
            if (linked && !verifyConsentSignature(consentDetail)) {
                throw new EsignetException(ErrorConstants.INVALID_CLAIM);
            }
            consentService.saveUserConsent(userConsent);
        }
    }

    public Map<String, ClaimDetail> normalizeClaims(Map<String, ClaimDetail> claims){
        return claims.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            ClaimDetail claimDetail = entry.getValue();
                            return claimDetail == null ? new ClaimDetail() : claimDetail;
                        }
                )
        );
    }

    public String hashUserConsent(Claims claims,Map<String, Boolean> authorizeScopes) throws JsonProcessingException {

        Map<String,Object> claimsAndAuthorizeScopes = new LinkedHashMap<>();

        List<Map.Entry<String, ClaimDetail>> entryList;
        Map<String, ClaimDetail> sortedMap = new LinkedHashMap<>();
        if(claims.getUserinfo()!=null){
            entryList = new ArrayList<>(claims.getUserinfo().entrySet());
            sortClaims(entryList, sortedMap);

        }
        //Now for sorting  id_token
        if(claims.getId_token()!=null)
        {
            entryList = new ArrayList<>(claims.getId_token().entrySet());
            sortClaims(entryList, sortedMap);

        }
        //Now for authorizeScopes
        Map<String,Boolean> sortedAuthorzeScopeMap=new LinkedHashMap<>();

        List<Map.Entry<String,Boolean>>authorizeScopesList = new ArrayList<>(authorizeScopes.entrySet());
        authorizeScopesList.sort(new Comparator<Map.Entry<String, Boolean>>() {
            public int compare(Map.Entry<String, Boolean> o1, Map.Entry<String, Boolean> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        for (Map.Entry<String, Boolean> entry : authorizeScopesList) {
            sortedAuthorzeScopeMap.put(entry.getKey(), entry.getValue());
        }
        claimsAndAuthorizeScopes.put("claims",sortedMap);
        claimsAndAuthorizeScopes.put("authorizeScopes",sortedAuthorzeScopeMap);
        String s=claimsAndAuthorizeScopes.toString().trim().replace(" ","");
        return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, s);
    }

    private static void sortClaims(List<Map.Entry<String, ClaimDetail>> entryList, Map<String, ClaimDetail> sortedMap) {
        entryList.sort(new Comparator<>() {
            public int compare(Map.Entry<String, ClaimDetail> o1, Map.Entry<String, ClaimDetail> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        for (Map.Entry<String, ClaimDetail> entry : entryList) {
            sortedMap.put(entry.getKey(), sortClaimDetail(entry.getValue()));
        }
    }

    private static ClaimDetail sortClaimDetail(ClaimDetail claimDetail){
        if(claimDetail!= null && claimDetail.getValues() != null) {
            Arrays.sort(claimDetail.getValues());
        }
        return claimDetail;
    }

    private ConsentAction evaluateConsentAction(OIDCTransaction transaction, ConsentDetail consentDetail, boolean linked) {
        String hash;
        try {
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            if(linked ) {
                if (!verifyConsentSignature(consentDetail))
                    //throw new EsignetException(ErrorConstants.INVALID_TRANSACTION_ID);
                    return ConsentAction.CAPTURE;
            }
            Map<String, Boolean> authorizeScopes = permittedScopes != null ? permittedScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), authorizeScope::contains)) : Collections.emptyMap();
            Claims normalizedClaims = new Claims();
            normalizedClaims.setUserinfo(normalizeClaims(transaction.getRequestedClaims().getUserinfo()));
            normalizedClaims.setId_token(normalizeClaims(transaction.getRequestedClaims().getId_token()));
            hash = hashUserConsent(normalizedClaims, authorizeScopes);
        } catch (JsonProcessingException e) {
            throw new EsignetException(ErrorConstants.INVALID_CLAIM);
        }
        return consentDetail.getHash().equals(hash) ? ConsentAction.NOCAPTURE : ConsentAction.CAPTURE;
    }

    public boolean verifyConsentSignature(ConsentDetail consentDetail)  {
        try{
            String jwtToken = generateSignedObject(consentDetail);
            if(jwtToken==null || jwtToken.isEmpty())return false;
            SignedJWT signedJWT = SignedJWT.parse(jwtToken);
            JWSHeader header = signedJWT.getHeader();
            String thumbPrint=header.getX509CertSHA256Thumbprint().toString();
            Certificate jsonPulicKey=keyBindingHelperService.getCertificate(consentDetail.getPsuToken(),thumbPrint);

            PublicKey publicKey =jsonPulicKey.getPublicKey();
            JWSVerifier verifierr = new RSASSAVerifier((RSAPublicKey) publicKey);
            log.info("signed jwt {} ", signedJWT.toString());
            if (signedJWT.verify(verifierr)) {
                return true;
            } else {
                return false;
            }
        }catch (Exception e)
        {
            throw new EsignetException(e.getMessage());
        }
    }

    private String generateSignedObject(ConsentDetail consentDetail) throws ParseException {
        List<String> acceptedClaims = consentDetail.getAcceptedClaims();
        List<String> permittedScopes = consentDetail.getPermittedScopes();
        String jws = consentDetail.getSignature();
        if(!signatureFormatValidate(jws))return null;
        String[] parts = jws.split("\\.");

        String header = parts[0];
        String signature = parts[1];
        if(!CollectionUtils.isEmpty(acceptedClaims))
        Collections.sort(acceptedClaims);
        if(!CollectionUtils.isEmpty(permittedScopes))
        Collections.sort(permittedScopes);

        Map<String,Object> payLoadMap = new HashMap<>();
        payLoadMap.put("accepted_claims",acceptedClaims);
        payLoadMap.put("permitted_scopes",permittedScopes);

        Payload payload=new Payload(new JSONObject(payLoadMap).toJSONString());
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(".").append(payload.toBase64URL()).append(".").append(signature);
        return sb.toString();
    }

    private boolean signatureFormatValidate(String signature)
    {
        if(signature==null || signature.isEmpty())return false;
        String jws[]=signature.split("\\.");
        if(jws.length!=2)return false;
        return true;
    }
    private static final ObjectMapper mapper = new ObjectMapper();

}