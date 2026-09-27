package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.config.SecurityConfig;
import me.zilid.chessplatform.exception.GlobalExceptionHandler;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.dto.PlayerRatingResponse;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.model.entity.UserPrincipal;
import me.zilid.chessplatform.service.RatingService;
import me.zilid.chessplatform.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The production application enables JPA repositories, so keep this MVC slice isolated.
@WebMvcTest(UserController.class)
@ContextConfiguration(classes = UserControllerWebMvcTest.TestConfiguration.class)
class UserControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("724330e9-91ab-40b2-a1b8-2b822fd10bd7");
    private static final UserResponse USER = new UserResponse(
            USER_ID, "player", "player@example.com", "Chess fan",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(
            USER_ID, "player", "player@example.com", "password123", true, List.of());

    @SpringBootConfiguration
    @Import({UserController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestConfiguration {
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RatingService ratingService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @Test
    void ratingsAreListedPerTimeControl() throws Exception {
        when(ratingService.getRatings(USER_ID)).thenReturn(List.of(
                new PlayerRatingResponse(TimeControl.BLITZ, 1250, 12, 1290),
                new PlayerRatingResponse(TimeControl.RAPID, 1200, 0, 1200)));

        mvc.perform(get("/api/users/{userId}/ratings", USER_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timeControl").value("BLITZ"))
                .andExpect(jsonPath("$[0].rating").value(1250))
                .andExpect(jsonPath("$[0].gamesPlayed").value(12))
                .andExpect(jsonPath("$[0].peakRating").value(1290))
                .andExpect(jsonPath("$[1].timeControl").value("RAPID"));
    }

    @Test
    void ratingsForUnknownUserAreNotFound() throws Exception {
        when(ratingService.getRatings(USER_ID)).thenThrow(new UserNotFoundException("User not found!"));

        mvc.perform(get("/api/users/{userId}/ratings", USER_ID)
                        .with(SecurityMockMvcRequestPostProcessors.user(PRINCIPAL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ratingsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/users/{userId}/ratings", USER_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ratingService);
    }

    @Test
    void registrationIsPublicAndReturnsCreatedUser() throws Exception {
        when(userService.createUser(any(UserCreateRequest.class))).thenReturn(USER);

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"player","email":"player@example.com",
                                 "rawPassword":"password123","about":"Chess fan"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("player"))
                .andExpect(jsonPath("$.email").value("player@example.com"));

        verify(userService).createUser(new UserCreateRequest(
                "player", "player@example.com", "password123", "Chess fan"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void invalidRegistrationIsRejectedBeforeCallingTheService(String scenario, String body, String field) throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/users"))
                .andExpect(jsonPath("$.errors." + field).isArray())
                .andExpect(jsonPath("$.errors." + field + "[0]").isNotEmpty());

        verifyNoInteractions(userService);
    }

    private static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("short username", """
                        {"username":"ab","email":"player@example.com","rawPassword":"password123"}
                        """, "username"),
                Arguments.of("malformed email", """
                        {"username":"player","email":"invalid","rawPassword":"password123"}
                        """, "email"),
                Arguments.of("short password", """
                        {"username":"player","email":"player@example.com","rawPassword":"x"}
                        """, "rawPassword")
        );
    }

    @Test
    void databaseConflictDoesNotExposePersistenceDetails() throws Exception {
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenThrow(new DataIntegrityViolationException("users_email_key violated: private database detail"));

        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"player","email":"player@example.com",
                                 "rawPassword":"password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request conflicts with existing data"));
    }

    @Test
    void malformedRequestBodyUsesBadRequestResponse() throws Exception {
        mvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request body"));

        verifyNoInteractions(userService);
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void loginAuthenticatesAndReturnsCurrentUser() throws Exception {
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null, List.of()));
        when(userService.getUserById(USER_ID)).thenReturn(USER);

        MvcResult login = mvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"player","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("player"))
                .andReturn();

        mvc.perform(get("/api/me")
                        .session((MockHttpSession) login.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authentication.capture());
        assertThat(authentication.getValue().getPrincipal()).isEqualTo("player");
        assertThat(authentication.getValue().getCredentials()).isEqualTo("password123");
        verify(userService, times(2)).getUserById(USER_ID);
    }

    @Test
    void profileUpdateUsesTheAuthenticatedUserId() throws Exception {
        when(userService.updateUser(USER_ID, new UserUpdateRequest(null, null, null, "New bio")))
                .thenReturn(USER);

        mvc.perform(put("/api/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"about\":\"New bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));

        verify(userService).updateUser(USER_ID, new UserUpdateRequest(null, null, null, "New bio"));
    }

    @Test
    void invalidProfileUpdateIsRejectedBeforeCallingTheService() throws Exception {
        mvc.perform(put("/api/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").isArray());

        verifyNoInteractions(userService);
    }

    @Test
    void accountDeletionInvalidatesSessionAndClearsCookie() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult deletion = mvc.perform(delete("/api/users")
                        .with(SecurityMockMvcRequestPostProcessors.user(PRINCIPAL))
                        .session(session))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(userService).deleteUser(USER_ID);
        assertThat(session.isInvalid()).isTrue();
        assertThat(deletion.getResponse().getCookie("JSESSIONID")).isNotNull();
        assertThat(deletion.getResponse().getCookie("JSESSIONID").getMaxAge()).isZero();
    }

    @Test
    void badCredentialsUseThePublicAuthenticationError() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("secret detail"));

        mvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"player","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        verifyNoInteractions(userService);
    }

    @Test
    void missingUserReturnsNotFound() throws Exception {
        when(userService.getUserById(USER_ID)).thenThrow(new UserNotFoundException("User not found!"));
        mvc.perform(get("/api/me").with(SecurityMockMvcRequestPostProcessors.user(PRINCIPAL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found!"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.TEXT_PLAIN).content("not JSON"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        verifyNoInteractions(userService);
    }

    @Test
    void validationReportsAllInvalidFieldsWithoutEchoingRejectedValues() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","email":"invalid","rawPassword":"pw"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username[0]").isNotEmpty())
                .andExpect(jsonPath("$.errors.email[0]").isNotEmpty())
                .andExpect(jsonPath("$.errors.rawPassword[0]").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("pw"))));
        verifyNoInteractions(userService);
    }
}
