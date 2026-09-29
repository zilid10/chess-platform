package me.zilid.chessplatform.util;

import me.zilid.chessplatform.config.ContainersConfig;
import me.zilid.chessplatform.config.PostgresConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ContainersConfig.class, PostgresConfig.class})
@ActiveProfiles("test")
public @interface IntegrationTest {
}