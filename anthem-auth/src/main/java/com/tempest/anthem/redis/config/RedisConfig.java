/*
 * Copyright 2020-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tempest.anthem.redis.config;

import java.util.Arrays;

import com.tempest.anthem.redis.convert.BytesToClaimsHolderConverter;
import com.tempest.anthem.redis.convert.BytesToOAuth2AuthorizationRequestConverter;
import com.tempest.anthem.redis.convert.BytesToUsernamePasswordAuthenticationTokenConverter;
import com.tempest.anthem.redis.convert.ClaimsHolderToBytesConverter;
import com.tempest.anthem.redis.convert.OAuth2AuthorizationRequestToBytesConverter;
import com.tempest.anthem.redis.convert.UsernamePasswordAuthenticationTokenToBytesConverter;
import com.tempest.anthem.redis.repository.OAuth2AuthorizationGrantAuthorizationRepository;
import com.tempest.anthem.redis.repository.OAuth2RegisteredClientRepository;
import com.tempest.anthem.redis.repository.OAuth2UserConsentRepository;
import com.tempest.anthem.redis.service.RedisOAuth2AuthorizationConsentService;
import com.tempest.anthem.redis.service.RedisOAuth2AuthorizationService;
import com.tempest.anthem.redis.service.RedisRegisteredClientRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.convert.RedisCustomConversions;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@EnableRedisRepositories("com.tempest.anthem.redis.repository")	// <1>
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		// 此为单体
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
		config.setHostName("192.168.100.12");
		config.setPort(16379);
		config.setPassword("Redis@0131");
		// 集群模式
		return new LettuceConnectionFactory(config);	// <2>
	}

	@Bean
	public RedisTemplate<?, ?> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<byte[], byte[]> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(redisConnectionFactory);
		return redisTemplate;
	}

	@Bean
	public RedisCustomConversions redisCustomConversions() {	// <3>
		return new RedisCustomConversions(Arrays.asList(new UsernamePasswordAuthenticationTokenToBytesConverter(),
				new BytesToUsernamePasswordAuthenticationTokenConverter(),
				new OAuth2AuthorizationRequestToBytesConverter(), new BytesToOAuth2AuthorizationRequestConverter(),
				new ClaimsHolderToBytesConverter(), new BytesToClaimsHolderConverter()));
	}

//	@Bean
//	public RedisRegisteredClientRepository registeredClientRepository(
//			OAuth2RegisteredClientRepository registeredClientRepository) {
//		return new RedisRegisteredClientRepository(registeredClientRepository);	// <4>
//	}

	@Bean
	public RedisOAuth2AuthorizationService authorizationService(RegisteredClientRepository registeredClientRepository,
			OAuth2AuthorizationGrantAuthorizationRepository authorizationGrantAuthorizationRepository) {
		return new RedisOAuth2AuthorizationService(registeredClientRepository,
				authorizationGrantAuthorizationRepository);	// <5>
	}

	@Bean
	public RedisOAuth2AuthorizationConsentService authorizationConsentService(
			OAuth2UserConsentRepository userConsentRepository) {
		return new RedisOAuth2AuthorizationConsentService(userConsentRepository);	// <6>
	}

}
