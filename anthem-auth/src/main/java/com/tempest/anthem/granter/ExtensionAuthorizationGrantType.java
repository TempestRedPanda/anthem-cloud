package com.tempest.anthem.granter;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public record ExtensionAuthorizationGrantType(String value) {

    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");
}
