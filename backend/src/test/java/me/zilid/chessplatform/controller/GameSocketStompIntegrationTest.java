package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.GameStatus;
import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.chess.game.TestGames;
import me.zilid.chessplatform.config.WebsocketConfig;
import me.zilid.chessplatform.controller.advice.WebSocketExceptionHandler;
import me.zilid.chessplatform.model.dto.ChatMessage;
import me.zilid.chessplatform.model.dto.ErrorResponse;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
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
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SubscriptionRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the HTTP handshake, STOMP mappings, and broker destinations together.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = GameSocketStompIntegrationTest.TestApplication.class,
        properties = {"management.server.port=-1", "app.allowed-origins=https://chess.example"}
)
class GameSocketStompIntegrationTest {
    private static final UUID GAME_ID = UUID.fromString("8a169d0a-c121-4d83-a7b3-8ee30f87cfa9");
    private static final UserPrincipal WHITE = principal("02410898-174c-4cb5-b8c5-55fe3cc535b9", "white");
    private static final UserPrincipal BLACK = principal("5b8d4e32-bf67-474e-93ba-2f7914517a28", "black");
    private static final UserPrincipal SPECTATOR = principal("86a726da-2284-431a-97a0-9926e9c954ef", "spectator");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ErrorResponse SUBSCRIPTION_PROBE = new ErrorResponse("subscription-ready");
    private final List<StompSession> sessions = new ArrayList<>();
    @LocalServerPort
    private int port;

    // Declared as the @Bean method's return type; the name picks the simple broker over the (null) relay bean
    @Autowired
    private AbstractBrokerMessageHandler simpleBrokerMessageHandler;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private MatchService matchService;
    private WebSocketStompClient client;
    private Game game;

    private static void assertNoMessage(BlockingQueue<?> messages) throws InterruptedException {
        Object unexpected = messages.poll(200, TimeUnit.MILLISECONDS);
        assertThat(unexpected).isNull();
    }

    private static <T> T take(BlockingQueue<T> messages) throws InterruptedException {
        T message = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(message).isNotNull();
        return message;
    }

    private static UserPrincipal principal(String id, String username) {
        return new UserPrincipal(UUID.fromString(id), username, username + "@example.com",
                "{noop}password", true, List.of());
    }

    private static GameStateResponse state(Game game) {
        Instant now = Instant.now();
        return new GameStateResponse(game.getStatus(), game.getFen(), game.getLastMoveFrom(),
                game.getLastMoveTo(), game.getTurnColor().name(),
                game.getRemaining(Color.WHITE, now).toMillis(), game.getRemaining(Color.BLACK, now).toMillis(),
                game.isClockRunning(), null);
    }

    @BeforeEach
    void setUp() {
        game = TestGames.game(WHITE.toPlayer(), BLACK.toPlayer());
        when(matchService.getGameOrThrow(GAME_ID)).thenReturn(game);
        when(matchService.buildGameStateResponse(game)).thenAnswer(invocation -> state(game));
        doAnswer(invocation -> {
            Player user = invocation.getArgument(0);
            if (!game.isValidPlayer(user)) {
                throw new IllegalStateException("You are not a player in this game");
            }
            if (!game.isUserTurn(user)) {
                throw new IllegalStateException("It is not your turn");
            }
            String from = invocation.getArgument(2);
            String to = invocation.getArgument(3);
            PieceType promotion = invocation.getArgument(4);
            if (!game.makeMove(from, to, promotion)) {
                throw new IllegalArgumentException("Invalid move: " + from + " to " + to);
            }
            return state(game);
        }).when(matchService).makeMove(any(Player.class), eq(GAME_ID), anyString(), anyString(),
                nullable(PieceType.class));

        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(StompSession::disconnect);
        client.stop();
    }

    @Test
    void authenticatedJoinAndMoveReachTheGameTopic() throws Exception {
        StompSession session = connect("white");
        BlockingQueue<GameStateResponse> updates = subscribe(session, "/topic/game." + GAME_ID,
                GameStateResponse.class);

        session.send("/app/game/" + GAME_ID + "/join", new byte[0]);
        GameStateResponse joined = take(updates);
        assertThat(joined.fen()).isEqualTo(game.getFen());
        assertThat(joined.turnColor()).isEqualTo("WHITE");

        session.send("/app/game/" + GAME_ID + "/move",
                new MoveRequest(GAME_ID.toString(), "e2", "e4", null));

        GameStateResponse moved = take(updates);
        assertThat(moved.lastMoveFrom()).isEqualTo("e2");
        assertThat(moved.lastMoveTo()).isEqualTo("e4");
        assertThat(moved.turnColor()).isEqualTo("BLACK");
        assertThat(moved.fen()).isEqualTo(game.getFen());
        verify(matchService).makeMove(eq(WHITE.toPlayer()), eq(GAME_ID), eq("e2"), eq("e4"), isNull());
    }

    @Test
    void acceptedTimeoutClaimReachesTheGameTopic() throws Exception {
        GameStateResponse flagged = new GameStateResponse(GameStatus.FLAGGED_BLACK_WINS, game.getFen(), null, null,
                "WHITE", 0, 600_000, false, null);
        when(matchService.checkTimeout(GAME_ID)).thenReturn(Optional.of(flagged));
        StompSession session = connect("black");
        BlockingQueue<GameStateResponse> updates = subscribe(session, "/topic/game." + GAME_ID,
                GameStateResponse.class);
        session.send("/app/game/" + GAME_ID + "/join", new byte[0]);
        take(updates);

        session.send("/app/game/" + GAME_ID + "/flag", new byte[0]);

        GameStateResponse update = take(updates);
        assertThat(update.gameStatus()).isEqualTo(GameStatus.FLAGGED_BLACK_WINS);
        assertThat(update.whiteRemainingMillis()).isZero();
        verify(matchService).checkTimeout(GAME_ID);
    }

    @Test
    void spectatorMoveProducesOnlyAPrivateError() throws Exception {
        StompSession session = connect("spectator");
        BlockingQueue<GameStateResponse> updates = subscribe(session, "/topic/game." + GAME_ID,
                GameStateResponse.class);
        BlockingQueue<ErrorResponse> errors = subscribeErrors(session, "spectator");

        session.send("/app/game/" + GAME_ID + "/join", new byte[0]);
        assertThat(take(updates).fen()).isEqualTo(game.getFen());

        session.send("/app/game/" + GAME_ID + "/move",
                new MoveRequest(GAME_ID.toString(), "e2", "e4", null));

        ErrorResponse error;
        do {
            error = take(errors);
        } while (SUBSCRIPTION_PROBE.equals(error));
        assertThat(error.error()).contains("not a player");
        assertNoMessage(updates);
        assertThat(game.getLastMoveFrom()).isNull();
        verify(matchService).makeMove(eq(SPECTATOR.toPlayer()), eq(GAME_ID), eq("e2"), eq("e4"), isNull());
    }

    @Test
    void illegalMoveProducesTheSpecificPrivateError() throws Exception {
        StompSession session = connect("white");
        BlockingQueue<GameStateResponse> updates = subscribe(session, "/topic/game." + GAME_ID,
                GameStateResponse.class);
        BlockingQueue<ErrorResponse> errors = subscribeErrors(session, "white");

        session.send("/app/game/" + GAME_ID + "/join", new byte[0]);
        assertThat(take(updates).fen()).isEqualTo(game.getFen());

        session.send("/app/game/" + GAME_ID + "/move",
                new MoveRequest(GAME_ID.toString(), "e2", "e5", null));

        ErrorResponse error;
        do {
            error = take(errors);
        } while (SUBSCRIPTION_PROBE.equals(error));
        assertThat(error.error()).isEqualTo("Invalid input: Invalid move: e2 to e5");
        assertNoMessage(updates);
    }

    @Test
    void chatSenderComesFromTheAuthenticatedSession() throws Exception {
        StompSession session = connect("white");
        BlockingQueue<ChatMessage> chat = subscribe(session, "/topic/game." + GAME_ID + ".chat",
                ChatMessage.class);

        session.send("/app/game/" + GAME_ID + "/chat", new ChatMessage("forged", "hello"));

        ChatMessage received = take(chat);
        assertThat(received.sender()).isEqualTo("white");
        assertThat(received.message()).isEqualTo("hello");
        assertThat(received.type()).isEqualTo(ChatMessage.MessageType.CHAT);
    }

    private StompSession connect(String username) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("https://chess.example");
        String credentials = Base64.getEncoder().encodeToString(
                (username + ":password").getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + credentials);

        StompSession session = client.connectAsync(
                URI.create("ws://localhost:" + port + "/ws"), headers, new StompHeaders(),
                new StompSessionHandlerAdapter() {
                }).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        sessions.add(session);
        return session;
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) {
        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                messages.add(type.cast(Objects.requireNonNull(payload, "STOMP payload")));
            }
        });
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        var probe = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        SubscriptionRegistry registry = ((SimpleBrokerMessageHandler) simpleBrokerMessageHandler)
                .getSubscriptionRegistry();
        await("STOMP subscription to " + destination).atMost(TIMEOUT)
                .until(() -> !registry.findSubscriptions(probe).isEmpty());
        return messages;
    }

    private BlockingQueue<ErrorResponse> subscribeErrors(StompSession session, String username)
            throws InterruptedException {
        BlockingQueue<ErrorResponse> errors = new LinkedBlockingQueue<>();
        session.subscribe("/user/topic/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ErrorResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                errors.add((ErrorResponse) Objects.requireNonNull(payload, "STOMP payload"));
            }
        });
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            messagingTemplate.convertAndSendToUser(username, "/topic/errors", SUBSCRIPTION_PROBE);
            ErrorResponse received = errors.poll(100, TimeUnit.MILLISECONDS);
            if (SUBSCRIPTION_PROBE.equals(received)) {
                return errors;
            }
        }
        assertThat(errors).as("private error subscription").contains(SUBSCRIPTION_PROBE);
        return errors;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({WebsocketConfig.class, GameSocketController.class, GameEventPublisher.class, WebSocketExceptionHandler.class})
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
            return username -> switch (username) {
                case "white" -> principal(WHITE.getId().toString(), username);
                case "black" -> principal(BLACK.getId().toString(), username);
                case "spectator" -> principal(SPECTATOR.getId().toString(), username);
                default -> throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username);
            };
        }
    }
}
