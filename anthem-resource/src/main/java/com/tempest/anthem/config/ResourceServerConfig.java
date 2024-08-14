package com.tempest.anthem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true, securedEnabled = true)
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests((authorizeHttpRequests) -> authorizeHttpRequests
                        //所有的访问都需要通过身份认证
                        .anyRequest().authenticated()
                )
                // 使用jwt处理接收到的access token
                .oauth2ResourceServer((oauth2ResourceServer) -> oauth2ResourceServer
                        .jwt(Customizer.withDefaults())

                );

        return http.build();
    }

}
