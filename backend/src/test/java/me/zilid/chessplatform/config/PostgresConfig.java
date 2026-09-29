package me.zilid.chessplatform.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Configuration(proxyBeanMethods = false)
public class PostgresConfig {
    @Bean
    @ServiceConnection(name = "postgres")
    public PostgreSQLContainer postgreSQLContainer() {
        return new PostgreSQLContainer("postgres:17.5");
    }
}
