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

        // Used when accessing via API Gateway (port 8080) — standard JWT flow
        SecurityScheme bearerScheme = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        SecurityScheme gatewaySecretScheme = new SecurityScheme()
                .name("X-Gateway-Secret")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER);

        // ── Direct service access (port 8082) ────────────────────────────────
        // The API Gateway normally injects these headers after validating the JWT.
        // When testing directly (Swagger UI at :8082 or Postman), you must provide
        // them manually so GatewayHeaderAuthFilter can authenticate the request.
        //
        //   X-User-Id   → any valid UUID, e.g. 00000000-0000-0000-0000-000000000001
        //   X-User-Role → ADMIN  (or CUSTOMER for non-admin endpoints)
        SecurityScheme userIdScheme = new SecurityScheme()
                .name("X-User-Id")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .description("Direct-access only. Any UUID, e.g. 00000000-0000-0000-0000-000000000001");

        SecurityScheme userRoleScheme = new SecurityScheme()
                .name("X-User-Role")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .description("Direct-access only. Use ADMIN or CUSTOMER");

        return new OpenAPI()
                .info(new Info()
                        .title("Gaming Castle Booking Service API")
                        .description("""
                                REST API for Gaming Castle Booking Service.

                                **Via API Gateway (port 8080):** Authenticate with your JWT token from /api/auth/login.

                                **Direct access (port 8082 / Swagger UI):** Click Authorize and fill in:
                                - `X-User-Id`: any UUID (e.g. `00000000-0000-0000-0000-000000000001`)
                                - `X-User-Role`: `ADMIN`
                                """)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme)
                        .addSecuritySchemes("gatewaySecretHeader", gatewaySecretScheme)
                        .addSecuritySchemes("X-User-Id", userIdScheme)
                        .addSecuritySchemes("X-User-Role", userRoleScheme))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("bearerAuth")
                                .addList("gatewaySecretHeader")
                                .addList("X-User-Id")
                                .addList("X-User-Role")
                );
    }
}