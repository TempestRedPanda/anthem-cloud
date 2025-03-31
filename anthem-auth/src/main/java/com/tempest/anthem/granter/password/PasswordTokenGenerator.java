//package com.tempest.anthem.granter.password;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.core.OAuth2AccessToken;
//import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
//import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
//
//import java.time.Instant;
//import java.util.Collections;
//
//@Configuration
//public class PasswordTokenGenerator implements OAuth2TokenGenerator<OAuth2AccessToken> {
//
//    @Override
//    public OAuth2AccessToken generate(OAuth2TokenContext context) {
//        // 生成访问令牌
//        Instant issuedAt = Instant.now();
//        Instant expiresAt = issuedAt.plusSeconds(3600); // 令牌有效期1小时
//
//        return new OAuth2AccessToken(
//                OAuth2AccessToken.TokenType.BEARER,
//                "generated-access-token", // 这里是生成的令牌字符串
//                issuedAt,
//                expiresAt,
//                Collections.singleton("scope") // 令牌的作用域
//        );
//    }
//}
