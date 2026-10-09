package me.zilid.chessplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.TestGames;
import me.zilid.chessplatform.config.WebsocketConfig;
import me.zilid.chessplatform.controller.advice.WebSocketExceptionHandler;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.rating.RatingChange;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.GameEventPublisher;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Exercises the HTTP handshake, STOMP mappings, and broker destinations together. {@link MatchService} is stubbed with
 * canned responses; its rules are covered by {@code MatchServiceTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = GameSocketStompTest.TestApplication.class,
        properties = {"management.server.port=-1", "app.allowed-origins=" + GameSocketStompTest.ORIGIN})
class GameSocketStompTest {
    static final String ORIGIN = "https://chess.example";
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");
    private static final String GAME_TOPIC = GameEventPublisher.gameTopic(GAME_ID);
    private static final UserPrincipal WHITE = principal("white");
    private static final UserPrincipal BLACK = principal("black");
    private static final UserPrincipal SPECTATOR = principal("spectator");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // Probes carry this STOMP header so subscribers can drop them instead of mistaking them for real messages
    private static final String PROBE_HEADER = "test-probe";
    private static final Map<String, Object> PROBE_HEADERS = Map.of(PROBE_HEADER, "true");
    private static final GameStateResponse INITIAL = new GameStateResponse(
            GameStatus.ONGOING,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            null,
            null,
            "WHITE",
            600_000,
            600_000,
            false,
            30_000L);

    private final List<StompSession> sessions = new ArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private MatchService matchService;

    private WebSocketStompClient client;

    private static UserPrincipal principal(String username) {
        return principal(UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)), username);
    }

    private static UserPrincipal principal(UUID id, String username) {
        return new UserPrincipal(id, username, username + "@example.com", "{noop}password", true, List.of());
    }

    private static <T> T take(BlockingQueue<T> messages) throws InterruptedException {
        T message = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(message).as("message within %s", TIMEOUT).isNotNull();
        return message;
    }

    private static void move(StompSession session, String from, String to) {
        session.send("/app/game/" + GAME_ID + "/move", new MoveRequest(GAME_ID.toString(), from, to, null));
    }

    @BeforeEach
    void setUp() {
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(TestGames.game(WHITE.toPlayer(), BLACK.toPlayer()));
        when(matchService.buildGameStateResponse(any())).thenReturn(INITIAL);

        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(StompSession::disconnect);
        client.stop();
    }

    @Test
    void joinAndMoveAreBroadcastToTheOpponent() throws Exception {
        GameStateResponse afterMove = new GameStateResponse(
                GameStatus.ONGOING,
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
                "e2",
                "e4",
                "BLACK",
                600_000,
                600_000,
                false,
                30_000L);
        when(matchService.makeMove(WHITE.toPlayer(), GAME_ID, "e2", "e4", null)).thenReturn(afterMove);
        StompSession white = connect(WHITE);
        StompSession black = connect(BLACK);
        BlockingQueue<GameStateResponse> updates = subscribe(black, GAME_TOPIC, GameStateResponse.class);

        white.send("/app/game/" + GAME_ID + "/join", new byte[0]);
        assertThat(take(updates)).isEqualTo(INITIAL);

        move(white, "e2", "e4");
        assertThat(take(updates)).isEqualTo(afterMove);
    }

    @Test
    void rejectedMovesReachOnlyTheMoverAsPrivateErrors() throws Exception {
        when(matchService.makeMove(SPECTATOR.toPlayer(), GAME_ID, "e2", "e4", null))
                .thenThrow(new IllegalStateException("You are not a player in this game"));
        when(matchService.makeMove(WHITE.toPlayer(), GAME_ID, "e2", "e5", null))
                .thenThrow(new IllegalArgumentException("Invalid move: e2 to e5"));
        StompSession white = connect(WHITE);
        StompSession spectator = connect(SPECTATOR);
        BlockingQueue<GameStateResponse> updates = subscribe(white, GAME_TOPIC, GameStateResponse.class);
        BlockingQueue<ErrorResponse> whiteErrors = subscribeErrors(white, WHITE);
        BlockingQueue<ErrorResponse> spectatorErrors = subscribeErrors(spectator, SPECTATOR);

        move(spectator, "e2", "e4");
        assertThat(take(spectatorErrors).error()).isEqualTo("Cannot perform action: You are not a player in this game");

        move(white, "e2", "e5");
        // White's first error is its own, so the spectator's error was not delivered to White
        assertThat(take(whiteErrors).error()).isEqualTo("Invalid input: Invalid move: e2 to e5");
        assertThat(spectatorErrors).isEmpty();
        assertThat(updates).isEmpty();
    }

    @Test
    void acceptedTimeoutClaimIsBroadcast() throws Exception {
        GameStateResponse flagged = new GameStateResponse(
                GameStatus.FLAGGED_BLACK_WINS, INITIAL.fen(), null, null, "WHITE", 0, 600_000, false, null);
        when(matchService.checkTimeout(GAME_ID)).thenReturn(Optional.of(flagged));
        when(matchService.archiveMatch(eq(GAME_ID), any()))
                .thenReturn(new RatingChange(WHITE.getId(), BLACK.getId(), 1192, 1208, -8, 8));
        StompSession black = connect(BLACK);
        BlockingQueue<GameStateResponse> updates = subscribe(black, GAME_TOPIC, GameStateResponse.class);

        black.send("/app/game/" + GAME_ID + "/flag", new byte[0]);

        assertThat(take(updates)).isEqualTo(flagged);
    }

    @Test
    void chatSenderComesFromTheAuthenticatedSession() throws Exception {
        StompSession white = connect(WHITE);
        BlockingQueue<ChatMessage> chat = subscribe(white, GameEventPublisher.chatTopic(GAME_ID), ChatMessage.class);

        white.send("/app/game/" + GAME_ID + "/chat", new ChatMessage("forged", "hello"));

        assertThat(take(chat))
                .usingRecursiveComparison()
                .ignoringFields("timestamp")
                .isEqualTo(new ChatMessage("white", "hello", ChatMessage.MessageType.CHAT));
    }

    @Test
    void handshakeFromAnotherOriginIsRejected() {
        assertThatThrownBy(() -> connect(WHITE, "https://evil.example")).isInstanceOf(ExecutionException.class);
    }

    private StompSession connect(UserPrincipal user) throws Exception {
        return connect(user, ORIGIN);
    }

    private StompSession connect(UserPrincipal user, String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        String credentials =
                Base64.getEncoder().encodeToString((user.getUsername() + ":password").getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + credentials);

        StompSession session = client.connectAsync(
                        URI.create("ws://localhost:" + port + "/ws"),
                        headers,
                        new StompHeaders(),
                        new StompSessionHandlerAdapter() {})
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        sessions.add(session);
        return session;
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, String topic, Class<T> type) {
        return subscribe(session, topic, type, () -> messagingTemplate.convertAndSend(topic, Map.of(), PROBE_HEADERS));
    }

    private BlockingQueue<ErrorResponse> subscribeErrors(StompSession session, UserPrincipal user) {
        return subscribe(
                session,
                "/user/topic/errors",
                ErrorResponse.class,
                () -> messagingTemplate.convertAndSendToUser(
                        user.getUsername(), "/topic/errors", Map.of(), PROBE_HEADERS));
    }

    /**
     * Subscribes, then sends probes until one arrives, so the subscription is active before the test acts. Probes are
     * not added to the returned queue.
     */
    private static <T> BlockingQueue<T> subscribe(
            StompSession session, String destination, Class<T> type, Runnable sendProbe) {
        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        AtomicBoolean ready = new AtomicBoolean();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return headers.containsKey(PROBE_HEADER) ? Map.class : type;
            }

            @Override
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                if (headers.containsKey(PROBE_HEADER)) {
                    ready.set(true);
                } else {
                    messages.add(type.cast(Objects.requireNonNull(payload, "STOMP payload")));
                }
            }
        });
        await("subscription to " + destination)
                .atMost(TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    sendProbe.run();
                    return ready.get();
                });
        return messages;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({
        WebsocketConfig.class,
        GameSocketController.class,
        GameEventPublisher.class,
        WebSocketExceptionHandler.class
    })
    static class TestApplication {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        UserDetailsService testUsers() {
            // A fresh copy per login: authentication erases the returned principal's password
            return username -> Stream.of(WHITE, BLACK, SPECTATOR)
                    .filter(user -> user.getUsername().equals(username))
                    .map(user -> principal(user.getId(), username))
                    .findFirst()
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        }
    }
}
