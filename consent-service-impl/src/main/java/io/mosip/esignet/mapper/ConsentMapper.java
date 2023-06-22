package io.mosip.esignet.mapper;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.entity.ConsentDetail;
import io.mosip.esignet.entity.ConsentHistory;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;


@Component
@Mapper(componentModel = "spring")
public interface ConsentMapper {

    ConsentMapper INSTANCE = Mappers.getMapper(ConsentMapper.class);

    default ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    default String serializeClaims(Claims claims) {
        try {
            return claims != null ? objectMapper().writeValueAsString(claims) : null;
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_CLAIM);
        }
    }

    default String serializeMap(Map<String, Boolean> authorizationScopes) {
        try {
            return authorizationScopes!=null?objectMapper().writeValueAsString(authorizationScopes):null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(INVALID_PERMITTED_SCOPE);
        }
    }

    default String serializeList(List<String> list) {
        return list==null?"":String.join(",",list);
    }

    default Claims deSerializeClaims(String claims) {
        try {
            return claims != null ? objectMapper().readValue(claims, Claims.class) : null;
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_CLAIM);
        }
    }

    default Map<String, Boolean> deSerializeMap(String authorizationScopes) {
        try {
            return authorizationScopes!=null?objectMapper().readValue(authorizationScopes,Map.class):null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(INVALID_PERMITTED_SCOPE);
        }
    }

    default List<String> deSerializeList(String stringList) {
       return StringUtils.isEmpty(stringList) ? List.of(): Arrays.asList(stringList.split(","));
    }

    @Mapping(target="claims",expression = "java(serializeClaims(consentDTo.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(consentDTo.getAuthorizationScopes()))")
    ConsentDetail toEntity(io.mosip.esignet.core.dto.ConsentDetail consentDTo);


    @Mapping(target="claims",expression = "java(serializeClaims(userConsent.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(userConsent.getAuthorizationScopes()))")
    @Mapping(target="acceptedClaims",expression = "java(serializeList(userConsent.getAcceptedClaims()))")
    @Mapping(target="permittedScopes",expression = "java(serializeList(userConsent.getPermittedScopes()))")
    ConsentDetail toEntity(UserConsent userConsent);



    @Mapping(target="claims",expression = "java(deSerializeClaims(consentDetail.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(deSerializeMap(consentDetail.getAuthorizationScopes()))")
    @Mapping(target="acceptedClaims",expression = "java(deSerializeList(consentDetail.getAcceptedClaims()))")
    @Mapping(target="permittedScopes",expression = "java(deSerializeList(consentDetail.getPermittedScopes()))")
    io.mosip.esignet.core.dto.ConsentDetail toDto(ConsentDetail consentDetail);


    @Mapping(target="claims",expression = "java(serializeClaims(userConsent.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(userConsent.getAuthorizationScopes()))")
    @Mapping(target="acceptedClaims",expression = "java(serializeList(userConsent.getAcceptedClaims()))")
    @Mapping(target="permittedScopes",expression = "java(serializeList(userConsent.getPermittedScopes()))")
    ConsentHistory toConsentHistoryEntity(UserConsent userConsent);

}
