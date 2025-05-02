package com.tempest.anthem.granter.password;

import com.tempest.anthem.entity.AnthemUser;
import com.tempest.anthem.redis.service.RedisOAuth2AuthorizationService;
import com.tempest.anthem.redis.service.RedisRegisteredClientRepository;
import com.tempest.anthem.service.IAnthemUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Optional;

@Service
public class OAuth2PasswordAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(OAuth2PasswordAuthenticationProvider.class);

    private static final OAuth2TokenType PASSWORD = new OAuth2TokenType(OAuth2ParameterNames.PASSWORD);

    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    private final RedisOAuth2AuthorizationService authorizationService;

    private final IAnthemUserService anthemUserService;

    private final PasswordEncoder passwordEncoder;

    private final RedisRegisteredClientRepository redisRegisteredClientRepository;

    @Autowired
    public OAuth2PasswordAuthenticationProvider(OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
                                                RedisOAuth2AuthorizationService authorizationService,
                                                IAnthemUserService anthemUserService,
                                                PasswordEncoder passwordEncoder,
                                                RedisRegisteredClientRepository redisRegisteredClientRepository) {
        this.tokenGenerator = tokenGenerator;
        this.authorizationService = authorizationService;
        this.anthemUserService = anthemUserService;
        this.passwordEncoder = passwordEncoder;
        this.redisRegisteredClientRepository = redisRegisteredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2PasswordAuthenticationToken passwordAuthentication = (OAuth2PasswordAuthenticationToken) authentication;

        // Ensure the client is authenticated
        OAuth2ClientAuthenticationToken clientPrincipal =
                getAuthenticatedClientElseThrowInvalidClient(passwordAuthentication);
//        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        RegisteredClient registeredClient = redisRegisteredClientRepository.findById("password-client");

        // Ensure the client is configured to use this authorization grant type
        assert registeredClient != null;
        if (!registeredClient.getAuthorizationGrantTypes().contains(passwordAuthentication.getGrantType())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }


        // 校验账户
        String username = passwordAuthentication.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new OAuth2AuthenticationException("账户不能为空");
        }
        // 校验密码
        String password = passwordAuthentication.getPassword();
        if (!StringUtils.hasText(password)) {
            throw new OAuth2AuthenticationException("密码不能为空");
        }

        // 查询账户信息 实际要根据自己的业务来写
        AnthemUser anthemUser = anthemUserService.lambdaQuery().eq(AnthemUser::getAccount, username).or().eq(AnthemUser::getNickName, username).one();
        Optional<AnthemUser> optionalAnthemUser = Optional.ofNullable(anthemUser);
        if (optionalAnthemUser.isEmpty()) {
            throw new OAuth2AuthenticationException("账户信息不存在，请联系管理员");
        } else if (!passwordEncoder.matches(password, anthemUser.getPassword())) {
            throw new OAuth2AuthenticationException("密码不正确");
        }

        UsernamePasswordAuthenticationToken userPrincipal = new UsernamePasswordAuthenticationToken(
                // 用户信息
                anthemUser,
                // 范围
                null,
                null
        );

        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userPrincipal)
                // 身份验证成功的认证信息(用户名、权限等信息)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizationGrantType(passwordAuthentication.getGrantType())
                // 授权方式
                .authorizationGrant(passwordAuthentication);

        // Generate the access token
        OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType((OAuth2TokenType.ACCESS_TOKEN)).build();
        OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);
        if (generatedAccessToken == null) {
            OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                    "The token generator failed to generate the access token.", null);
            throw new OAuth2AuthenticationException(error);
        }


        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
                generatedAccessToken.getExpiresAt(), null);

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(userPrincipal.getName())
                .authorizationGrantType(passwordAuthentication.getGrantType())
                .attribute(Principal.class.getName(), userPrincipal);
        // attribute 字段
        if (generatedAccessToken instanceof ClaimAccessor) {
            authorizationBuilder.token(accessToken, (metadata) -> {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, ((ClaimAccessor) generatedAccessToken).getClaims());
                metadata.put(OAuth2TokenFormat.class.getName(), OAuth2TokenFormat.SELF_CONTAINED.getValue());
            });
        } else {
            authorizationBuilder.accessToken(accessToken);
        }

        // Generate the refresh token
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);
            if (generatedRefreshToken != null) {
                if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                    OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                            "The token generator failed to generate a valid refresh token.", null);
                    throw new OAuth2AuthenticationException(error);
                }

                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("Generated refresh token");
                }

                refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
                authorizationBuilder.refreshToken(refreshToken);
            }
        }

        OAuth2Authorization authorization = authorizationBuilder.build();

        // Save the OAuth2Authorization
        this.authorizationService.save(authorization);

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken, refreshToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2PasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(Authentication authentication) {
        OAuth2ClientAuthenticationToken clientPrincipal = null;
        if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal().getClass())) {
            clientPrincipal = (OAuth2ClientAuthenticationToken) authentication.getPrincipal();
        }
        if (clientPrincipal != null && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    private static <T extends OAuth2Token> OAuth2AccessToken accessToken(OAuth2Authorization.Builder builder, T token,
                                                                         OAuth2TokenContext accessTokenContext) {

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token.getTokenValue(),
                token.getIssuedAt(), token.getExpiresAt(), accessTokenContext.getAuthorizedScopes());
        OAuth2TokenFormat accessTokenFormat = accessTokenContext.getRegisteredClient()
                .getTokenSettings()
                .getAccessTokenFormat();
        builder.token(accessToken, (metadata) -> {
            if (token instanceof ClaimAccessor claimAccessor) {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims());
            }
            metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, false);
            metadata.put(OAuth2TokenFormat.class.getName(), accessTokenFormat.getValue());
        });

        return accessToken;
    }
}
