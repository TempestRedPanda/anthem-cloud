package com.tempest.anthem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@MapperScan({"com.tempest.anthem.**.mapper"})
@EnableDiscoveryClient
@SpringBootApplication
public class AnthemAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnthemAuthApplication.class, args);
    }

}
