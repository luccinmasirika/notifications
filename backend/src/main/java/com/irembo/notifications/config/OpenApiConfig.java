package com.irembo.notifications.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.openapi.info.title:Irembo Notifications API}")
    private String apiTitle;

    @Value("${app.openapi.info.version:1.0.0}")
    private String apiVersion;

    @Value("${app.openapi.info.description:Distributed notification API with sophisticated rate limiting}")
    private String apiDescription;

    @Value("${app.openapi.info.contact.name:Irembo Engineering}")
    private String contactName;

    @Value("${app.openapi.info.contact.email:engineering@irembo.com}")
    private String contactEmail;

    @Value("${app.openapi.info.license.name:MIT License}")
    private String licenseName;

    @Value("${app.openapi.info.license.url:https://opensource.org/licenses/MIT}")
    private String licenseUrl;

    @Value("${app.openapi.server.url:http://localhost:1310}")
    private String serverUrl;

    @Value("${app.openapi.server.description:Development server}")
    private String serverDescription;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(apiTitle)
                        .version(apiVersion)
                        .description(apiDescription)
                        .contact(new Contact()
                                .name(contactName)
                                .email(contactEmail))
                        .license(new License()
                                .name(licenseName)
                                .url(licenseUrl)))
                .servers(List.of(
                        new Server()
                                .url(serverUrl)
                                .description(serverDescription)))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-KEY")
                                .description("API Key for client authentication"))
                        .addSecuritySchemes("BasicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic Authentication for admin endpoints")));
    }
}
