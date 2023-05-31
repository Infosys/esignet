package io.mosip.esignet.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.KafkaHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ConsentHelperService {

    private final CacheUtilService cacheUtilService;

    private final ConsentService consentService;

    private final KafkaHelperService kafkaHelperService;

    @Value("${mosip.esignet.kafka.linked-auth-code.topic}")
    private String linkedAuthCodeTopicName;

    public ConsentHelperService(CacheUtilService cacheUtilService, ConsentService consentService, KafkaHelperService kafkaHelperService) {
        this.cacheUtilService = cacheUtilService;
        this.consentService = consentService;
        this.kafkaHelperService = kafkaHelperService;
    }

    public void processConsent(String transactionId) {
        OIDCTransaction transaction = cacheUtilService.getAuthenticatedTransaction(transactionId);
        if(transaction == null)
            throw new InvalidTransactionException();
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsu_token(transaction.getPartnerSpecificUserToken());
        Optional<Consent> consent = consentService.getUserConsent(userConsentRequest);

        ConsentAction consentAction = consent.isEmpty() ? ConsentAction.CAPTURE : validateConsentAction(transaction,consent.get());

        transaction.setConsentAction(consentAction);

        if(consentAction.equals(ConsentAction.NOCAPTURE)) {
            Consent userConsent = consent.get();
            log.info("consent {}", userConsent);
            List<String> acceptedClaims = userConsent.getClaims().getUserinfo().entrySet().stream().
                    filter(entry -> entry != null && entry.getValue() != null && entry.getValue().isAccepted())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            List<String> permittedScopes = userConsent.getAuthorizationScopes().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            transaction.setAcceptedClaims(acceptedClaims);
            transaction.setPermittedScopes(permittedScopes);
        }
        cacheUtilService.setAuthenticatedTransaction(transactionId,transaction);
    }

    public LinkedKycAuthResponseV2 processLinkedConsent(String linkedAuthTransactionId) {
        OIDCTransaction transaction = cacheUtilService.getLinkedAuthTransaction(linkedAuthTransactionId);
        if(transaction == null)
            throw new InvalidTransactionException();
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsu_token(transaction.getPartnerSpecificUserToken());
        Optional<Consent> consent = consentService.getUserConsent(userConsentRequest);

        ConsentAction consentAction =  ConsentAction.CAPTURE;
        if(consent.isPresent()) {
            consentAction = validateConsentAction(transaction,consent.get());
            if(consentAction.equals(ConsentAction.NOCAPTURE)){
                if(!validateSignature(transaction,consent.get())){
                    consentAction = ConsentAction.CAPTURE;
                }
            }
        }
        transaction.setConsentAction(consentAction);

        if(consentAction.equals(ConsentAction.NOCAPTURE)) {
            Consent userConsent = consent.get();
            log.info("consent {}", userConsent);
            List<String> acceptedClaims = userConsent.getClaims().getUserinfo().entrySet().stream().
                    filter(entry -> entry != null && entry.getValue() != null && entry.getValue().isAccepted())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            List<String> permittedScopes = userConsent.getAuthorizationScopes().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            transaction.setAcceptedClaims(acceptedClaims);
            transaction.setPermittedScopes(permittedScopes);
        }
        cacheUtilService.setLinkedAuthenticatedTransaction(linkedAuthTransactionId,transaction);
        if(consentAction.equals(ConsentAction.NOCAPTURE)){
            cacheUtilService.setLinkedConsentedTransaction(linkedAuthTransactionId, transaction);
            kafkaHelperService.publish(linkedAuthCodeTopicName, linkedAuthTransactionId);
        }
        LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = new LinkedKycAuthResponseV2();
        linkedKycAuthResponseV2.setLinkedTransactionId(linkedAuthTransactionId);
        linkedKycAuthResponseV2.setConsentAction(consentAction);
        return linkedKycAuthResponseV2;
    }

    public void addUserConsent(String transactionId, boolean linked) {
        OIDCTransaction transaction;
        if(!linked) {
            transaction = cacheUtilService.getWebConsentedTransaction(transactionId);
        } else {
            transaction = cacheUtilService.getLinkedConsentedTransaction(transactionId);
        }
        if(transaction == null)
            throw new InvalidTransactionException();
        if(transaction.getConsentAction().equals(ConsentAction.CAPTURE)){
            ConsentRequest consentRequest = new ConsentRequest();
            consentRequest.setClientId(transaction.getClientId());
            consentRequest.setPsuValue(transaction.getPartnerSpecificUserToken());
            Claims claims = transaction.getRequestedClaims();
            List<String> acceptedClaims = transaction.getAcceptedClaims();
            setIsAcceptedBasedOnClaims(acceptedClaims,claims);
            consentRequest.setClaims(claims);
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            Map<String, Boolean> authorizeScopes = permittedScopes != null ? permittedScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), authorizeScope::contains)) : Collections.emptyMap();
            consentRequest.setAuthorizationScopes(authorizeScopes);
            consentService.saveUserConsent(consentRequest);
        }
    }

    private void setIsAcceptedBasedOnClaims(List<String> acceptedClaims, Claims requestedClaims) {
        Map<String ,ClaimDetail> userInfo = requestedClaims.getUserinfo();
        for (String acceptedClaim : acceptedClaims) {
            if (userInfo.containsKey(acceptedClaim)) {
                ClaimDetail claimDetail = userInfo.get(acceptedClaim);
                if(claimDetail!= null){
                    claimDetail.setAccepted(true);
                } else {
                    claimDetail = new ClaimDetail();
                    claimDetail.setAccepted(true);
                }
                userInfo.put(acceptedClaim, claimDetail);
            }
        }
    }


    private static ConsentAction validateConsentAction(OIDCTransaction transaction, Consent consent) {
        if(consent == null) {
            return ConsentAction.CAPTURE;
        }
        //validate requested claims
        Claims requestedClaims = transaction.getRequestedClaims();
        List<String> requestedScopes = transaction.getRequestedAuthorizeScopes();

        if(((requestedClaims == null ||
                (requestedClaims.getId_token().isEmpty() && requestedClaims.getUserinfo().isEmpty()))
                && requestedScopes.isEmpty())
        ) {
            return ConsentAction.NOCAPTURE;
        }

        //validate consented claims
        if(requestedClaims!= null && consent.getClaims() != null &&!requestedClaims.isEqualToIgnoringAccepted(consent.getClaims())){
            return ConsentAction.CAPTURE;
        }

        //validate consented scopes
        Set<String> acceptedScopes = consent.getAuthorizationScopes()
                .entrySet()
                .stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if(!requestedScopes.isEmpty()
                && ( !new HashSet<>(requestedScopes).containsAll(consent.getAuthorizationScopes().keySet()) ||
                !new HashSet<>(requestedScopes).containsAll(acceptedScopes)
        )){
            return ConsentAction.CAPTURE;
        }
        return ConsentAction.NOCAPTURE;
    }

    private boolean validateSignature(OIDCTransaction transaction, Consent consent){
        return true;

    }
    private String generateSignedObject(OIDCTransaction transaction, Consent consent){
        List<String> acceptedClaims = transaction.getAcceptedClaims();
        List<String> permittedScopes = transaction.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(acceptedClaims);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim("acceptedClaims", acceptedClaims)
                .claim("authorizeScopes", permittedScopes)
                .build();
        String jws = consent.getSignature();
        String[] parts = jws.split("\\.");

        String header = parts[0];
        String signature = parts[1];
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.parse(header));
        JWSObject jwsObject = null;
        try {
            jwsObject = new JWSObject(jwsHeader.toBase64URL(), Base64URL.encode(claimsSet.toJSONObject().toJSONString())
                    ,Base64URL.encode(signature) );
        } catch (ParseException e) {

        }
        return jwsObject == null ? "": jwsObject.serialize();
    }

}