package io.mosip.esignet.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.entity.Consent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Map;

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
            throw new RuntimeException("Failed to serialize Claims object to JSON", e);
        }
    }

    default String serializeMap(Map<String, Boolean> authorizationScopes) {
        try {
            return authorizationScopes!=null?objectMapper().writeValueAsString(authorizationScopes):null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Claims object to JSON", e);
        }
    }

    default Claims deSerializeClaims(String claims) {
        try {
            return claims != null ? objectMapper().readValue(claims, Claims.class) : null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Claims object to JSON", e);
        }
    }

    default Map<String, Boolean> deSerializeMap(String authorizationScopes) {
        try {
            return authorizationScopes!=null?objectMapper().readValue(authorizationScopes,Map.class):null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Claims object to JSON", e);
        }
    }

    @Mapping(target="claims",expression = "java(serializeClaims(consentDTo.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(consentDTo.getAuthorizationScopes()))")
    Consent toEntity(io.mosip.esignet.core.dto.Consent consentDTo);


    @Mapping(target="claims",expression = "java(serializeClaims(consentRequest.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(serializeMap(consentRequest.getAuthorizeScopes()))")
    Consent toEntity(ConsentRequest consentRequest);



    @Mapping(target="claims",expression = "java(deSerializeClaims(consent.getClaims()))")
    @Mapping(target="authorizationScopes",expression = "java(deSerializeMap(consent.getAuthorizationScopes()))")
//    @Mapping(target="claims",ignore = true)
//    @Mapping(target="authorizationScopes",ignore = true)
    io.mosip.esignet.core.dto.Consent toDto(Consent consent);

}

