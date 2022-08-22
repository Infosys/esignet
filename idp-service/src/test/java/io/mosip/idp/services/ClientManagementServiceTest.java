/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.ClientDetailCreateRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.dto.ClientDetailUpdateRequest;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class ClientManagementServiceTest {

    @Autowired
    ClientManagementService clientDetailService;

    @Autowired
    ClientDetailRepository clientDetailRepository;

    //region Variables

    String commaSeparatedURIs = "https://clientapp.com/home,https://clientapp.com/home2";
    String commaSeparatedAMRs = "https://clientapp.com/home,https://clientapp.com/home2";
    String commaSeparatedClaims = "https://clientapp.com/home,https://clientapp.com/home2";

    String CLIENT_ID_1 = "C01";
    String CLIENT_ID_2 = "C02";
    String CLIENT_NAME_1 = "Client-01";
    String CLIENT_NAME_2 = "Client-02";
    String LOGO_URI = "https://clienapp.com/logo.png";
    String PUBLIC_KEY;
    List<String> LIST_OF_URIS = Arrays.asList(commaSeparatedURIs.split(","));
    List<String> CLAIMS = Arrays.asList(commaSeparatedClaims.split(","));
    List<String> AMRS = Arrays.asList(commaSeparatedAMRs.split(","));
    List<String> GRAND_TYPES = List.of("authorization_code");
    String STATUS_ACTIVE = "active";
    String STATUS_INACTIVE = "inactive";
    String RELAYING_PARTY_ID = "RP01";

    //endregion

    @Before
    public void Before() throws NoSuchAlgorithmException {
        clientDetailRepository.deleteAll();
        PUBLIC_KEY = generateBase64PublicKeyRSAString();
    }

    private String generateBase64PublicKeyRSAString() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        byte[] publicKey = keyGen.genKeyPair().getPublic().getEncoded();
        return Base64.getEncoder().encodeToString(publicKey);
    }

    //region Create Client Test

    @Test
    public void createClient_withValidDetail_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ClientDetailResponse respDto = clientDetailService.createOIDCClient(clientCreateReqDto);

        Assert.assertNotNull(respDto);

        Optional<ClientDetail> result = clientDetailRepository.findById(CLIENT_ID_1);
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus(CLIENT_ID_1, STATUS_ACTIVE);
        Assert.assertTrue(result.isPresent());
    }

    @Test
    public void createClientDetail_withInactiveStatus_thenPass() throws Exception {

        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_INACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ClientDetailResponse respDto = clientDetailService.createOIDCClient(clientCreateReqDto);

        Assert.assertNotNull(respDto);

        Optional<ClientDetail> result = clientDetailRepository.findById(CLIENT_ID_1);
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus(CLIENT_ID_1, STATUS_INACTIVE);
        Assert.assertTrue(result.isPresent());
    }

    @Test
    public void createClientDetail_withDuplicateClientId_thenFail() {

        ClientDetailCreateRequest clientCreateReqDto1 = new ClientDetailCreateRequest();
        clientCreateReqDto1.setClientId(CLIENT_ID_1);
        clientCreateReqDto1.setClientName(CLIENT_NAME_1);
        clientCreateReqDto1.setLogoUri(LOGO_URI);
        clientCreateReqDto1.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto1.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto1.setUserClaims(CLAIMS);
        clientCreateReqDto1.setAuthContextRefs(AMRS);
        clientCreateReqDto1.setStatus(STATUS_INACTIVE);
        clientCreateReqDto1.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto1.setGrantTypes(GRAND_TYPES);

        ClientDetailResponse respDto1 = null;
        try {
            respDto1 = clientDetailService.createOIDCClient(clientCreateReqDto1);
        } catch (Exception e) {
            Assert.fail();
        }

        Assert.assertNotNull(respDto1);

        ClientDetailCreateRequest clientCreateReqDto2 = new ClientDetailCreateRequest();
        clientCreateReqDto2.setClientId(CLIENT_ID_1);
        clientCreateReqDto2.setClientName(CLIENT_NAME_1);
        clientCreateReqDto2.setLogoUri(LOGO_URI);
        clientCreateReqDto2.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto2.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto2.setUserClaims(CLAIMS);
        clientCreateReqDto2.setAuthContextRefs(AMRS);
        clientCreateReqDto2.setStatus(STATUS_INACTIVE);
        clientCreateReqDto2.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto2.setGrantTypes(GRAND_TYPES);

        ClientDetailResponse respDto2 = null;
        try {
            respDto2 = clientDetailService.createOIDCClient(clientCreateReqDto2);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        Assert.assertNull(respDto2);
    }

    //endregion

    //region Update Client Test

    @Test
    public void updateClient_withValidDetail_thenPass() throws Exception {

        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        clientDetailService.createOIDCClient(clientCreateReqDto);

        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);

        ClientDetailResponse respDto = clientDetailService.updateOIDCClient(CLIENT_ID_1, clientUpdateReqDto);

        Assert.assertNotNull(respDto);

        Optional<ClientDetail> result = clientDetailRepository.findById(CLIENT_ID_1);
        Assert.assertTrue(result.isPresent());

        Assert.assertEquals(result.get().getName(), CLIENT_NAME_2);
    }

    @Test
    public void updateClient_withInvalidClientId_thenPass() {

        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        try {
            clientDetailService.createOIDCClient(clientCreateReqDto);
        } catch (Exception e) {
            Assert.fail();
        }

        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);

        ClientDetailResponse respDto = null;
        try {
            respDto = clientDetailService.updateOIDCClient(CLIENT_ID_2, clientUpdateReqDto);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("does not exist"));
        }

        Assert.assertNull(respDto);
    }

    //endregion

}
