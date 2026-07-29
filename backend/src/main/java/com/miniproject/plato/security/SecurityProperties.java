package com.miniproject.plato.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.beans.ConstructorProperties;

@Component
@ConfigurationProperties(prefix = "plato.jwt")
@Setter
@Getter
@Validated
public class SecurityProperties {
    private String secret;
    private long expiration = 86400000L;
}
