/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mosip.esignet.api.validator.Purpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaimDetail extends VerifiedClaimDetail implements Serializable {

    private String value;
    private String[] values;
    private boolean essential;

    @Purpose
    private String purpose;

    public ClaimDetail(String value, String[] values, boolean essential) {
        this.value = value;
        this.values = values;
        this.essential = essential;
    }

}
