package me.zilid.chessplatform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!prod")
public class OpenAPIConfig {

    private static final String SCHEME_NAME = "cookieAuth";
    private static final String DEFAULT_APP_VERSION = "0.1.0-SNAPSHOT";
    private final String appVersion;

    public OpenAPIConfig(ObjectProvider<BuildProperties> buildProperties) {
        this.appVersion = buildProperties.stream()
                .findFirst()
                .map(BuildProperties::getVersion)
                .orElse(DEFAULT_APP_VERSION);
    }

    @Bean
    public OpenAPI chessPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChessPlatform API")
                        .description("This is the ChessPlatform API.")
                        .version(appVersion)
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
