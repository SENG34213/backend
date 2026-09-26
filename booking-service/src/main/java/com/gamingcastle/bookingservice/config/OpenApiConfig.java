package com.gamingcastle.bookingservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingServiceOpenAPI() {

        SecurityScheme bearerScheme = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        SecurityScheme gatewaySecretScheme = new SecurityScheme()
                .name("X-Gateway-Secret")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER);

        return new OpenAPI()
                .info(new Info()
                        .title("Gaming Castle Booking Service API")
                        .description("REST API for Gaming Castle Booking Service")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme)
                        .addSecuritySchemes("gatewaySecretHeader", gatewaySecretScheme))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth")
                                .addList("gatewaySecretHeader")
                );
    }
}