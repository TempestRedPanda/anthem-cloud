package com.tempest.anthem.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    private static final String CUSTOM_CONSENT_PAGE_URI = "/oauth2/consent";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http
                // Redirect to the login page when not authenticated from the
                // authorization endpoint
                .exceptionHandling((exceptions) -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                // Accept access tokens for User Info and/or Client Registration
                .oauth2ResourceServer((resourceServer) -> resourceServer
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
                .authorizeHttpRequests((authorize) -> authorize
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    /**
     * 注册客户端信息
     *
     * 查询认证服务器信息
     * http://localhost:8082/.well-known/openid-configuration
     *
     * 获取授权码
     * http://localhost:8082/oauth2/authorize?response_type=code&client_id=oidc-client&scope=profile openid&redirect_uri=http://www.google.com
     * http://127.0.0.1:8082/oauth2/authorize?client_id=oidc-client&response_type=code&scope=profile%20openid&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/oidc-client
     *
     * 正常的流程是存在一个web前端页面提供给客户端进行注册，然后后端接口会将客户端注册信息保存在DB中，然后去查询DB，最后封装为一个RegisteredClient
     * 我这里就直接写一个客户端，存放在内存中进行测试
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("oidc-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/oidc-client")
//                .redirectUri("http://www.google.com")
                .postLogoutRedirectUri("http://127.0.0.1:8080/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
//                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();

        return new InMemoryRegisteredClientRepository(oidcClient);
    }

//    @Bean
//    public JdbcRegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
//
//        RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("oidc-client")
//                .clientSecret("{noop}secret")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
//                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
//                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
//                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/oidc-client")
//                .redirectUri("http://www.google.com")
//                .postLogoutRedirectUri("http://127.0.0.1:8080/")
//                .scope(OidcScopes.OPENID)
//                .scope(OidcScopes.PROFILE)
//                .clientSettings(ClientSettings.builder().build())
//                .build();
//
//        RegisteredClient messagingClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("messaging-client")
//                .clientSecret("{noop}secret")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
//                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
//                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
//                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/messaging-client-oidc")
//                .redirectUri("http://127.0.0.1:8080/authorized")
//                .postLogoutRedirectUri("http://127.0.0.1:8080/logged-out")
//                .scope(OidcScopes.OPENID)
//                .scope(OidcScopes.PROFILE)
//                .scope("message.read")
//                .scope("message.write")
//                .scope("user.read")
//                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
//                .build();
//
//        RegisteredClient deviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("device-messaging-client")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
//                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
//                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
//                .scope("message.read")
//                .scope("message.write")
//                .build();
//
//        RegisteredClient tokenExchangeClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("token-client")
//                .clientSecret("{noop}token")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//                .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"))
//                .scope("message.read")
//                .scope("message.write")
//                .build();
//
//        RegisteredClient mtlsDemoClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("mtls-demo-client")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.TLS_CLIENT_AUTH)
//                .clientAuthenticationMethod(ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH)
//                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
//                .scope("message.read")
//                .scope("message.write")
//                .clientSettings(
//                        ClientSettings.builder()
//                                .x509CertificateSubjectDN("CN=demo-client-sample,OU=Spring Samples,O=Spring,C=US")
//                                .jwkSetUrl("http://127.0.0.1:8080/jwks")
//                                .build()
//                )
//                .tokenSettings(
//                        TokenSettings.builder()
//                                .x509CertificateBoundAccessTokens(true)
//                                .build()
//                )
//                .build();
//
//        JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);
//        registeredClientRepository.save(oidcClient);
//        registeredClientRepository.save(messagingClient);
//        registeredClientRepository.save(deviceClient);
//        registeredClientRepository.save(tokenExchangeClient);
//        registeredClientRepository.save(mtlsDemoClient);
//
//        return registeredClientRepository;
//    }


//    /**
//     * 授权信息
//     * 对应表：oauth2_authorization
//     */
//    @Bean
//    public JdbcOAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
//                                                               RegisteredClientRepository registeredClientRepository) {
//        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
//    }
//
//    /**
//     * 授权确认
//     * 对应表：oauth2_authorization_consent
//     */
//    @Bean
//    public JdbcOAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
//                                                                             RegisteredClientRepository registeredClientRepository) {
//        // Will be used by the ConsentController
//        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
//    }
//
//    @Bean
//    public OAuth2TokenCustomizer<JwtEncodingContext> idTokenCustomizer() {
//        return new FederatedIdentityIdTokenCustomizer();
//    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user")
                .password("{noop}secret")
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(userDetails);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
}


