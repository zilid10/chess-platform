package me.zilid.chessplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import me.zilid.chessplatform.chess.game.TimeControl;
import me.zilid.chessplatform.exception.UserNotFoundException;
import me.zilid.chessplatform.model.dto.PlayerRatingResponse;
import me.zilid.chessplatform.model.dto.UserCreateRequest;
import me.zilid.chessplatform.model.dto.UserResponse;
import me.zilid.chessplatform.model.dto.UserUpdateRequest;
import me.zilid.chessplatform.security.SecurityConfig;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.RatingService;
import me.zilid.chessplatform.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@MockitoBean(types = SimpMessagingTemplate.class) // needed by the STOMP-only WebSocketExceptionHandler advice
class UserControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("724330e9-91ab-40b2-a1b8-2b822fd10bd7");
    private static final UserResponse USER = new UserResponse(
            USER_ID,
            "player",
            "player@example.com",
            "Chess fan",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(USER_ID, "player", "player@example.com", "password123", true, List.of());

    @Autowired
    private MockMvcTester mvcTester;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RatingService ratingService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

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
                        """, "rawPassword"));
    }

    @Test
    void ratingsAreListedPerTimeControl() {
        when(ratingService.getRatings(USER_ID))
                .thenReturn(List.of(
                        new PlayerRatingResponse(TimeControl.BLITZ, 1250, 12, 1290),
                        new PlayerRatingResponse(TimeControl.RAPID, 1200, 0, 1200)));

        MvcTestResult result = mvcTester
                .get()
                .uri("/api/users/{userId}/ratings", USER_ID)
                .with(user(PRINCIPAL))
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].timeControl").isEqualTo("BLITZ");
        assertThat(result).bodyJson().extractingPath("$[0].rating").isEqualTo(1250);
        assertThat(result).bodyJson().extractingPath("$[0].gamesPlayed").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$[0].peakRating").isEqualTo(1290);
        assertThat(result).bodyJson().extractingPath("$[1].timeControl").isEqualTo("RAPID");
    }

    @Test
    void ratingsForUnknownUserAreNotFound() {
        when(ratingService.getRatings(USER_ID)).thenThrow(new UserNotFoundException("User not found!"));

        assertThat(mvcTester.get().uri("/api/users/{userId}/ratings", USER_ID).with(user(PRINCIPAL)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void ratingsRequireAuthentication() {
        assertThat(mvcTester.get().uri("/api/users/{userId}/ratings", USER_ID)).hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(ratingService);
    }

    @Test
    void registrationIsPublicAndReturnsCreatedUser() {
        when(userService.createUser(any(UserCreateRequest.class))).thenReturn(USER);

        MvcTestResult result = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"player","email":"player@example.com",
                                 "rawPassword":"password123","about":"Chess fan"}
                                """)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(USER_ID.toString());
        assertThat(result).bodyJson().extractingPath("$.username").isEqualTo("player");
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("player@example.com");

        verify(userService)
                .createUser(new UserCreateRequest("player", "player@example.com", "password123", "Chess fan"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void invalidRegistrationIsRejectedBeforeCallingTheService(String scenario, String body, String field) {
        MvcTestResult result = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation failed");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/users");
        assertThat(result).bodyJson().extractingPath("$.errors." + field).asArray();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors." + field + "[0]")
                .isNotEmpty();

        verifyNoInteractions(userService);
    }

    @Test
    void databaseConflictDoesNotExposePersistenceDetails() {
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenThrow(new DataIntegrityViolationException("users_email_key violated: private database detail"));

        MvcTestResult result = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"player","email":"player@example.com",
                                 "rawPassword":"password123"}
                                """)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Request conflicts with existing data");
    }

    @Test
    void malformedRequestBodyUsesBadRequestResponse() {
        MvcTestResult result = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Invalid request body");

        verifyNoInteractions(userService);
    }

    @Test
    void currentUserRequiresAuthentication() {
        assertThat(mvcTester.get().uri("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(userService);
    }

    @Test
    void loginAuthenticatesAndReturnsCurrentUser() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null, List.of()));
        when(userService.getUserById(USER_ID)).thenReturn(USER);

        MvcTestResult login = mvcTester
                .post()
                .uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"player","password":"password123"}
                                """)
                .exchange();
        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.id").isEqualTo(USER_ID.toString());
        assertThat(login).bodyJson().extractingPath("$.username").isEqualTo("player");

        MvcTestResult me = mvcTester
                .get()
                .uri("/api/me")
                .session((MockHttpSession) login.getRequest().getSession())
                .exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.id").isEqualTo(USER_ID.toString());

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authentication.capture());
        assertThat(authentication.getValue().getPrincipal()).isEqualTo("player");
        assertThat(authentication.getValue().getCredentials()).isEqualTo("password123");
        verify(userService, times(2)).getUserById(USER_ID);
    }

    @Test
    void profileUpdateUsesTheAuthenticatedUserId() {
        when(userService.updateUser(USER_ID, new UserUpdateRequest(null, null, null, "New bio")))
                .thenReturn(USER);

        MvcTestResult result = mvcTester
                .put()
                .uri("/api/users")
                .with(user(PRINCIPAL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"about\":\"New bio\"}")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(USER_ID.toString());

        verify(userService).updateUser(USER_ID, new UserUpdateRequest(null, null, null, "New bio"));
    }

    @Test
    void invalidProfileUpdateIsRejectedBeforeCallingTheService() {
        MvcTestResult result = mvcTester
                .put()
                .uri("/api/users")
                .with(user(PRINCIPAL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.username").asArray();

        verifyNoInteractions(userService);
    }

    @Test
    void accountDeletionInvalidatesSessionAndClearsCookie() {
        MockHttpSession session = new MockHttpSession();

        MvcTestResult deletion = mvcTester
                .delete()
                .uri("/api/users")
                .with(user(PRINCIPAL))
                .session(session)
                .exchange();
        assertThat(deletion).hasStatus(HttpStatus.NO_CONTENT);

        verify(userService).deleteUser(USER_ID);
        assertThat(session.isInvalid()).isTrue();
        assertThat(deletion).cookies().hasMaxAge("JSESSIONID", Duration.ZERO);
    }

    @Test
    void badCredentialsUseThePublicAuthenticationError() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("secret detail"));

        MvcTestResult result = mvcTester
                .post()
                .uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"player","password":"wrong"}
                                """)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Invalid username or password");

        verifyNoInteractions(userService);
    }

    @Test
    void missingUserReturnsNotFound() {
        when(userService.getUserById(USER_ID)).thenThrow(new UserNotFoundException("User not found!"));
        MvcTestResult result =
                mvcTester.get().uri("/api/me").with(user(PRINCIPAL)).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("User not found!");
    }

    @Test
    void unsupportedContentTypeReturns415() {
        MvcTestResult result = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not JSON")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(415);
        verifyNoInteractions(userService);
    }

    @Test
    void validationReportsAllInvalidFieldsWithoutEchoingRejectedValues() {
        MvcTestResult result = mvcTester
                .post()
                .uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"username":"ab","email":"invalid","rawPassword":"pw"}
                                """)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.username[0]").isNotEmpty();
        assertThat(result).bodyJson().extractingPath("$.errors.email[0]").isNotEmpty();
        assertThat(result).bodyJson().extractingPath("$.errors.rawPassword[0]").isNotEmpty();
        assertThat(result).bodyText().doesNotContain("pw");
        verifyNoInteractions(userService);
    }
}
