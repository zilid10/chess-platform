package me.zilid.chessplatform;

import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs with the PostgreSQL, Redis, and migrations provisioned by the backend CI job. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class UserSessionIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepo users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void registrationLoginAndLogoutUseTheRealSecurityAndPersistenceLayers() throws Exception {
        String username = "test" + UUID.randomUUID().toString().substring(0, 8);
        String password = "securePassword123";

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com", "rawPassword":"%s"}
                                """.formatted(username, username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));

        User saved = users.findByUsername(username).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, saved.getPasswordHash())).isTrue();

        MvcResult login = mvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andReturn();

        // The session lives in Redis, so the cookie is all a client (or another backend instance) needs.
        // Without an embedded server the cookie keeps Spring Session's default name, so don't look it up by name.
        Cookie[] cookies = login.getResponse().getCookies();
        assertThat(cookies).hasSize(1);
        Cookie session = cookies[0];

        mvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mvc.perform(post("/api/logout").cookie(session))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }
}
