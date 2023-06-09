package io.mosip.esignet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLIENT_ID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ConsentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = INVALID_CLIENT_ID)
    @Column(name = "client_id")
    private String clientId;

    @NotNull
    @Column(name = "psu_token")
    private String psuToken;

    @NotNull(message = INVALID_CLAIM)
    @Column(name = "claims")
    private String claims;

    @NotNull
    @Column(name = "authorization_scopes")
    private String authorizationScopes;

    @NotNull
    @Column(name = "cr_dtimes")
    private LocalDateTime createdtimes;

    @Column(name = "expire_dtimes")
    private LocalDateTime expiredtimes;

    @Column(name = "signature")
    private String signature;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ConsentDetail consentDetail = (ConsentDetail) o;
        return getId() != null && Objects.equals(getId(), consentDetail.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
