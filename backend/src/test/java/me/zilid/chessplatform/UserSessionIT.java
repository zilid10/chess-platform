package me.zilid.chessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import me.zilid.chessplatform.model.entity.User;
import me.zilid.chessplatform.repository.UserRepo;
import me.zilid.chessplatform.util.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** Runs with the PostgreSQL, Redis, and migrations provisioned by the backend CI job. */
@IntegrationTest
class UserSessionIT {

    @Autowired
    private MockMvcTester mvcTester;

    @Autowired
    private UserRepo users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void registrationLoginAndLogoutUseTheRealSecurityAndPersistenceLayers() {
        String username = "test" + UUID.randomUUID().toString().substring(0, 8);
        String password = "securePassword123";

        MvcTestResult registration = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"%s","email":"%s@example.com", "rawPassword":"%s"}
                                """.formatted(username, username, password))
                .exchange();
        assertThat(registration).hasStatus(HttpStatus.CREATED);
        assertThat(registration).bodyJson().extractingPath("$.username").isEqualTo(username);

        User saved = users.findByUsername(username).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, saved.getPasswordHash())).isTrue();

        MvcTestResult login = mvcTester
                .post()
                .uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password))
                .exchange();
        assertThat(login).hasStatusOk();
        assertThat(login)
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(saved.getId().toString());

        // The session lives in Redis, so the cookie is all a client (or another backend instance) needs.
        // Without an embedded server the cookie keeps Spring Session's default name, so don't look it up by name.
        Cookie[] cookies = login.getResponse().getCookies();
        assertThat(cookies).hasSize(1);
        Cookie session = cookies[0];

        MvcTestResult me = mvcTester.get().uri("/api/me").cookie(session).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.username").isEqualTo(username);

        assertThat(mvcTester.post().uri("/api/logout").cookie(session)).hasStatusOk();

        MvcTestResult afterLogout =
                mvcTester.get().uri("/api/me").cookie(session).exchange();
        assertThat(afterLogout).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(afterLogout).bodyJson().extractingPath("$.status").isEqualTo(401);
        assertThat(afterLogout).bodyJson().extractingPath("$.detail").isEqualTo("Authentication required");
    }
}
