/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package au.org.ala.collectory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * JSON response returned by GET /session.
 * Matches the UserInfo type in @ala/common-ui.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Getter
@AllArgsConstructor
public class UserInfoDto {
    String userId;
    boolean authenticated;
    String error;
    String accessToken;
    Long expiresAt;
    String email;
    String firstName;
    String lastName;
    String[] roles;
}
