package com.tempest.anthem.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
//import com.tempest.anthem.granter.ExtensionAuthorizationGrantType;
//import com.tempest.anthem.granter.password.OAuth2PasswordAuthenticationConverter;
//import com.tempest.anthem.granter.password.OAuth2PasswordAuthenticationProvider;
import com.tempest.anthem.federation.FederatedIdentityIdTokenCustomizer;
import com.tempest.anthem.redis.repository.OAuth2AuthorizationGrantAuthorizationRepository;
import com.tempest.anthem.redis.repository.OAuth2RegisteredClientRepository;
import com.tempest.anthem.redis.repository.OAuth2UserConsentRepository;
import com.tempest.anthem.redis.service.RedisOAuth2AuthorizationConsentService;
import com.tempest.anthem.redis.service.RedisOAuth2AuthorizationService;
import com.tempest.anthem.redis.service.RedisRegisteredClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
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
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
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
import java.time.Duration;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    private static final String CUSTOM_CONSENT_PAGE_URI = "/oauth2/consent";

//    // 扩展Provider
//    private final OAuth2PasswordAuthenticationProvider oAuth2PasswordAuthenticationProvider;
//    // 扩展Converter
//    private final OAuth2PasswordAuthenticationConverter oAuth2PasswordAuthenticationConverter;

//    @Autowired
//    public AuthorizationServerConfig(@Lazy OAuth2PasswordAuthenticationProvider oAuth2PasswordAuthenticationProvider,
//                                     @Lazy OAuth2PasswordAuthenticationConverter oAuth2PasswordAuthenticationConverter) {
//        this.oAuth2PasswordAuthenticationProvider = oAuth2PasswordAuthenticationProvider;
//        this.oAuth2PasswordAuthenticationConverter = oAuth2PasswordAuthenticationConverter;
//    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, (authorizationServer) ->
                        authorizationServer
                                .oidc(Customizer.withDefaults())	// Enable OpenID Connect 1.0
                )
                .authorizeHttpRequests((authorize) ->
                        authorize
                                .anyRequest().authenticated()
                )
                // Redirect to the login page when not authenticated from the
                // authorization endpoint
                // 需要认证的请求，重定向到login进行登录验证
                .exceptionHandling((exceptions) -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                // 使用jwt处理接收到的access_token
                .oauth2ResourceServer((resourceServer) ->
                        resourceServer.jwt(Customizer.withDefaults()));
//                .with(authorizationServerConfigurer, (authorizationServer) -> authorizationServer
//                        .tokenEndpoint(tokenEndpoint -> tokenEndpoint
//                                .accessTokenRequestConverter(oAuth2PasswordAuthenticationConverter)
//                                .authenticationProvider(oAuth2PasswordAuthenticationProvider)
//                        ));

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
                // Form login handles the redirect to the login page from the
                // authorization server filter chain
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
     * http://localhost:8081/oauth2/authorize?response_type=code&client_id=oidc-client&scope=openid&state=some-state&redirect_uri=http://127.0.0.1:8081/login/oauth2/code/messaging-client-oidc
     * http://127.0.0.1:8081/oauth2/authorize?response_type=code&client_id=messaging-client&scope=openid&redirect_uri=http://127.0.0.1:8081/login/oauth2/code/messaging-client-oidc
     *
     *
     */
    @Bean
    public RedisRegisteredClientRepository registeredClientRepository(OAuth2RegisteredClientRepository registeredClientRepository) {

        TokenSettings tokenSettings = TokenSettings.builder()
                // 访问令牌有效时间
                .accessTokenTimeToLive(Duration.ofSeconds(30))
                // 刷新令牌有效期
                .refreshTokenTimeToLive(Duration.ofDays(1))
                // accessToken 形式
//                .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .build();
        RegisteredClient messagingClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("messaging-client")
                .clientSecret(passwordEncoder().encode("secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_JWT)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                // 新增密码模式
//                .authorizationGrantType(ExtensionAuthorizationGrantType.PASSWORD)
//                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/oidc-client")
//                .redirectUri("https://www.baidu.com")
                .redirectUri("http://127.0.0.1:8081/login/oauth2/code/messaging-client-oidc")
                .redirectUri("http://127.0.0.1:8081/authorized")
                .postLogoutRedirectUri("http://127.0.0.1:8081/logged-out")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("message.read")
                .scope("message.write")
                .scope("user.read")
                .tokenSettings(tokenSettings)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

//        RegisteredClient deviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("device-messaging-client")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
//                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
//                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
//                .scope("message.read")
//                .scope("message.write")
//                .build();



//        RegisteredClient tokenExchangeClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("token-client")
//                .clientSecret("{noop}token")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//                .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"))
//                .scope("message.read")
//                .scope("message.write")
//                .build();

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

        // Save registered client's in db as if in-memory
        RedisRegisteredClientRepository redisRegisteredClientRepository = new RedisRegisteredClientRepository(registeredClientRepository);
        redisRegisteredClientRepository.save(messagingClient);
//        redisRegisteredClientRepository.save(deviceClient);
//        redisRegisteredClientRepository.save(tokenExchangeClient);
//        redisRegisteredClientRepository.save(mtlsDemoClient);

        return redisRegisteredClientRepository;
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> idTokenCustomizer() {
        return new FederatedIdentityIdTokenCustomizer();
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}


