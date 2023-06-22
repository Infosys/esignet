/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.entity.ConsentDetail;
import io.mosip.esignet.entity.ConsentHistory;
import io.mosip.esignet.mapper.ConsentMapper;
import io.mosip.esignet.mapper.ConsentMapperImpl;
import io.mosip.esignet.repository.ConsentHistoryRepository;
import io.mosip.esignet.repository.ConsentRepository;
import io.mosip.esignet.services.ConsentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConsentServiceImplTest {


    @Mock
    ConsentRepository consentRepository;

    @Mock
    ConsentHistoryRepository consentHistoryRepository;

    @Mock
    AuditPlugin auditWrapper;


    @Autowired
    ConsentMapper consentMapper=new ConsentMapperImpl();

    @InjectMocks
    ConsentServiceImpl consentService;



    @Test
    public void getUserConsent_withValidDetails_thenPass() throws Exception{
        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setId(UUID.randomUUID());
        consentDetail.setClientId("1234");
        consentDetail.setClaims("{\"userinfo\":{\"given_name\":{\"essential\":true},\"phone_number\":null,\"email\":{\"essential\":true},\"picture\":{\"essential\":false},\"gender\":{\"essential\":false}},\"id_token\":{}}");
        consentDetail.setAuthorizationScopes("{\"openid\":true,\"profile\":true,\"email\":true,\"phone\":true,\"offline_access\":true}");
        consentDetail.setPermittedScopes("openid,profile,email,phone,offline_access");
        consentDetail.setAcceptedClaims("name,gender,email,phone_number");
        consentDetail.setCreatedtimes(LocalDateTime.now());
        consentDetail.setPsuToken("psuValue");
        consentDetail.setExpiredtimes(LocalDateTime.now());

        Optional<ConsentDetail> consentOptional = Optional.of(consentDetail);
        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.anyString(),Mockito.anyString())).thenReturn(consentOptional);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsuToken("psuValue");

        Optional<io.mosip.esignet.core.dto.ConsentDetail> userConsentDto = consentService.getUserConsent(userConsentRequest);
        Assert.assertNotNull(userConsentDto);
        Assert.assertEquals("1234", userConsentDto.get().getClientId());
        Assert.assertEquals("psuValue", userConsentDto.get().getPsuToken());

        Claims claims =consentMapper.deSerializeClaims(consentDetail.getClaims());
        List<String> acceptedClaims = consentMapper.deSerializeList(consentDetail.getAcceptedClaims());
        List<String> permitedScopes = consentMapper.deSerializeList(consentDetail.getPermittedScopes());
        Map<String,Boolean> authorizationScopes = consentMapper.deSerializeMap(consentDetail.getAuthorizationScopes());

        Assert.assertEquals(claims, userConsentDto.get().getClaims());
        Assert.assertEquals(acceptedClaims, userConsentDto.get().getAcceptedClaims());
        Assert.assertEquals(permitedScopes, userConsentDto.get().getPermittedScopes());
        Assert.assertEquals(authorizationScopes, userConsentDto.get().getAuthorizationScopes());

    }

    @Test
    public void getUserConsent_withInValidClaimsDetails_thenFail() {
        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setId(UUID.randomUUID());
        consentDetail.setClientId("1234");
        consentDetail.setCreatedtimes(LocalDateTime.now());
        consentDetail.setClaims("claims");
        consentDetail.setPsuToken("psuValue");
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setHash("hash");
        consentDetail.setSignature("signature");

        Optional<ConsentDetail> consentOptional = Optional.of(consentDetail);
        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.anyString(),Mockito.anyString())).thenReturn(consentOptional);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsuToken("psuValue");
        try{
            Optional<io.mosip.esignet.core.dto.ConsentDetail> userConsentDto = consentService.getUserConsent(userConsentRequest);
            Assert.fail();
        }catch (Exception e){
            Assert.assertTrue(e.getMessage().equals("invalid_claim"));
        }
    }

    @Test
    public void saveUserConsent_withValidDetails_thenPass() throws Exception{
        Claims claims = new Claims();
        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("john", new String[]{"john", "parker"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("idToken", new String[]{"value1", "value2"}, true);
        userinfo.put("name", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);

        Map<String,Boolean> authorizeScopes = Map.of("given_name",true,"email",true);


        UserConsent userConsent = new UserConsent();
        userConsent.setClientId("1234");
        userConsent.setPsuToken("psuValue");
        userConsent.setClaims(claims);
        userConsent.setAuthorizationScopes(authorizeScopes);
        userConsent.setExpirydtimes(LocalDateTime.now());
        userConsent.setSignature("signature");

        ConsentDetail consentDetail =consentMapper.toEntity(userConsent);


        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.any(),Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(consentRepository.save(Mockito.any())).thenReturn(consentDetail);
        Mockito.when(consentHistoryRepository.save(Mockito.any())).thenReturn(new ConsentHistory());
        io.mosip.esignet.core.dto.ConsentDetail userConsentDtoDetail = consentService.saveUserConsent(userConsent);
        Assert.assertNotNull(userConsentDtoDetail);
        Assert.assertEquals("1234", userConsentDtoDetail.getClientId());

        Assert.assertEquals(claims, userConsentDtoDetail.getClaims());
        Assert.assertEquals(authorizeScopes, userConsentDtoDetail.getAuthorizationScopes());

    }
}
