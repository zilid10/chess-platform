package me.zilid.chessplatform;

import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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

/** Runs with the PostgreSQL and migrations provisioned by the backend CI job. */
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

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mvc.perform(post("/api/logout").session(session))
                .andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();

        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }
}
