package com.tempest.anthem.redis.entity;

import java.security.Principal;
import java.util.Set;

public class OAuth2PasswordGrantAuthorization extends OAuth2AuthorizationGrantAuthorization {

    private final Principal principal;

    // @fold:on
    public OAuth2PasswordGrantAuthorization(String id, String registeredClientId, String principalName,
                                            Set<String> authorizedScopes, AccessToken accessToken,
                                            RefreshToken refreshToken, Principal principal) {
        super(id, registeredClientId, principalName, authorizedScopes, accessToken, refreshToken);
        this.principal = principal;
    }
    // @fold:off

    public Principal getPrincipal() {
        return this.principal;
    }

}
