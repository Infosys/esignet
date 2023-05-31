package io.mosip.esignet.services;

import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.KafkaHelperService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class ConsentHelperServiceTest {



    @Mock
    CacheUtilService cacheUtilService;

    @Mock
    ConsentService consentService;

    @Mock
    KafkaHelperService kafkaHelperService;

    @InjectMocks
    ConsentHelperService consentHelperService;


    @Test
    public void addUserConsent_withValidDetails_thenPass()
    {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        Mockito.when(cacheUtilService.getLinkedConsentedTransaction("123")).thenReturn(oidcTransaction);

        Mockito.when(consentService.saveUserConsent(Mockito.any())).thenReturn(new Consent());

        consentHelperService.addUserConsent("123", true);
    }

    @Test
    public void addUserConsent_withInValidDetails_thenFail()
    {
        Mockito.when(cacheUtilService.getLinkedConsentedTransaction("123")).thenReturn(null);
       try{
            consentHelperService.addUserConsent("123", true);
            Assert.fail();
        }catch (Exception e)
        {
            Assert.assertEquals(e.getMessage(),"invalid_transaction");
        }
    }

    @Test
    public void processLinkedConsent_withValidDetails_thenPass(){

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setClientId("123");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));

        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        oidcTransaction.setRequestedClaims(claims);

        Mockito.when(cacheUtilService.getLinkedAuthTransaction(Mockito.anyString())).thenReturn(oidcTransaction);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsu_token(oidcTransaction.getPartnerSpecificUserToken());

        Consent consent = new Consent();
        consent.setClientId("123");
        consent.setSignature("signature");
        consent.setAuthorizationScopes(Map.of("openid",true,"profile",true));
        consent.setClaims(claims);

        Mockito.when(consentService.getUserConsent(userConsentRequest)).thenReturn(Optional.of(consent));

        Mockito.when(cacheUtilService.setLinkedAuthenticatedTransaction(Mockito.anyString(),Mockito.any(OIDCTransaction.class))).
                thenReturn(oidcTransaction);

        LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = consentHelperService.processLinkedConsent("123");
        Assert.assertNotNull(linkedKycAuthResponseV2);

        Assert.assertEquals(linkedKycAuthResponseV2.getLinkedTransactionId(),oidcTransaction.getAuthTransactionId());
        Assert.assertEquals(linkedKycAuthResponseV2.getConsentAction(),oidcTransaction.getConsentAction());


    }

    @Test
    public void processLinkedConsent_withInValidDetails_thenFail(){

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setAuthTransactionId("123");
        oidcTransaction.setClientId("123");
        oidcTransaction.setPartnerSpecificUserToken("123");
        oidcTransaction.setAcceptedClaims(List.of("name","email"));
        oidcTransaction.setPermittedScopes(null);
        oidcTransaction.setConsentAction(ConsentAction.CAPTURE);
        oidcTransaction.setRequestedAuthorizeScopes(List.of("openid","profile"));

        Mockito.when(cacheUtilService.getLinkedAuthTransaction(Mockito.anyString())).thenReturn(null);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(oidcTransaction.getClientId());
        userConsentRequest.setPsu_token(oidcTransaction.getPartnerSpecificUserToken());

        Consent consent = new Consent();
        consent.setClientId("123");
        consent.setSignature("signature");
        consent.setAuthorizationScopes(Map.of("openid",true,"profile",true));

        try{
            LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = consentHelperService.processLinkedConsent("123");
            Assert.fail();
        }catch (Exception e)
        {
            Assert.assertEquals(e.getMessage(),"invalid_transaction");
        }
    }


}
