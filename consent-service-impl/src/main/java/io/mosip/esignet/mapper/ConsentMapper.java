package io.mosip.esignet.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.entity.ConsentDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;

@Mapper(componentModel = "spring")
public interface ConsentMapper {

   // ConsentMapper INSTANCE = Mappers.getMapper(ConsentMapper.class);

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
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
        }
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
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
        }
    }

    @Mapping(target="claims",expression = "java(serializeClaims(consentDTo.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(consentDTo.getAuthorizationScopes()))")
    ConsentDetail toEntity(io.mosip.esignet.core.dto.ConsentDetail consentDTo);


    @Mapping(target="claims",expression = "java(serializeClaims(userConsent.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(userConsent.getAuthorizationScopes()))")
    ConsentDetail toEntity(UserConsent userConsent);



    @Mapping(target="claims",expression = "java(deSerializeClaims(consent.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(deSerializeMap(consent.getAuthorizationScopes()))")
//    @Mapping(target="claims",ignore = true)
//    @Mapping(target="authorizationScopes",ignore = true)
    io.mosip.esignet.core.dto.ConsentDetail toDto(ConsentDetail consent);
}
