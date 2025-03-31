//package com.tempest.anthem.granter.password;
//
//import com.tempest.anthem.granter.ExtensionAuthorizationGrantType;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
//
//import java.util.Map;
//
//public class OAuth2PasswordAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {
//    private final String username;
//    private final String password;
//
//    public OAuth2PasswordAuthenticationToken(String username, String password, Authentication clientPrincipal, Map<String, Object> additionalParameters) {
//        super(ExtensionAuthorizationGrantType.PASSWORD, clientPrincipal, additionalParameters);
//        this.username = username;
//        this.password = password;
//    }
//
//    public String getUsername() {
//        return username;
//    }
//
//    public String getPassword() {
//        return password;
//    }
//}
