package me.zilid.chessplatform.controller;

import me.zilid.chessplatform.chess.Color;
import me.zilid.chessplatform.chess.PieceType;
import me.zilid.chessplatform.chess.game.Game;
import me.zilid.chessplatform.chess.game.Player;
import me.zilid.chessplatform.chess.game.TestGames;
import me.zilid.chessplatform.config.WebsocketConfig;
import me.zilid.chessplatform.model.dto.GameStateResponse;
import me.zilid.chessplatform.model.dto.MoveRequest;
import me.zilid.chessplatform.security.UserPrincipal;
import me.zilid.chessplatform.service.GameEventPublisher;
import me.zilid.chessplatform.service.MatchService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Starts two backend instances that share the RabbitMQ broker relay and checks that messages sent by one instance
 * reach clients connected to the other. Runs with the RabbitMQ provisioned by the backend CI job.
 */
@EnabledIfEnvironmentVariable(named = "APP_WEBSOCKET_RELAY_HOST", matches = ".+")
class WebSocketRelayIT {
    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UserPrincipal WHITE = principal("relay-white");
    private static final UserPrincipal BLACK = principal("relay-black");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ORIGIN = "https://chess.example";
    private static final Object PROBE = Map.of("probe", true);

    // Stands in for the Redis-backed game state both instances share
    private static final MatchService MATCH_SERVICE = mock(MatchService.class);

    // Instance A first, then B; a list so a failed start still closes whatever did start
    private static final List<ConfigurableApplicationContext> instances = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient client;
    private Game game;

    @BeforeAll
    static void startInstances() {
        instances.add(startInstance());
        instances.add(startInstance());
        instances.forEach(WebSocketRelayIT::awaitBrokerAvailable);
    }

    @AfterAll
    static void stopInstances() {
        instances.forEach(ConfigurableApplicationContext::close);
        instances.clear();
    }

    private static ConfigurableApplicationContext startInstance() {
        // Arguments, unlike default properties, take precedence over application.yaml
        return new SpringApplicationBuilder(TestApplication.class).run(
                "--server.port=0",
                "--management.server.port=-1",
                "--app.allowed-origins=" + ORIGIN,
                "--app.websocket.relay.enabled=true");
    }

    private static void awaitBrokerAvailable(ConfigurableApplicationContext instance) {
        StompBrokerRelayMessageHandler relay = instance.getBean(StompBrokerRelayMessageHandler.class);
        await("RabbitMQ relay connected").atMost(TIMEOUT).until(relay::isBrokerAvailable);
    }

    private static ConfigurableApplicationContext instanceA() {
        return instances.getFirst();
    }

    private static ConfigurableApplicationContext instanceB() {
        return instances.get(1);
    }

    private static SimpMessagingTemplate template(ConfigurableApplicationContext instance) {
        return instance.getBean(SimpMessagingTemplate.class);
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                messages.add((Map<String, Object>) Objects.requireNonNull(payload, "STOMP payload"));
            }
        });
        return messages;
    }

    /**
     * Subscriptions reach RabbitMQ asynchronously, so keep sending a probe until one arrives.
     */
    private static void awaitDelivery(BlockingQueue<Map<String, Object>> messages, Runnable sendProbe)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            sendProbe.run();
            Map<String, Object> received = messages.poll(100, TimeUnit.MILLISECONDS);
            if (received != null && received.containsKey("probe")) {
                return;
            }
        }
        throw new AssertionError("No message delivered across instances within " + TIMEOUT);
    }

    private static Map<String, Object> takeNonProbe(BlockingQueue<Map<String, Object>> messages)
            throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && !message.containsKey("probe")) {
                return message;
            }
        }
        throw new AssertionError("No game update within " + TIMEOUT);
    }

    private static GameStateResponse state(Game game) {
        Instant now = Instant.now();
        return new GameStateResponse(game.getStatus(), game.getFen(), game.getLastMoveFrom(),
                game.getLastMoveTo(), game.getTurnColor().name(),
                game.getRemaining(Color.WHITE, now).toMillis(), game.getRemaining(Color.BLACK, now).toMillis(),
                game.isClockRunning());
    }

    private static UserPrincipal principal(String username) {
        return principal(UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)), username);
    }

    private static UserPrincipal principal(UUID id, String username) {
        return new UserPrincipal(id, username, username + "@example.com", "{noop}password", true, List.of());
    }

    @BeforeEach
    void setUp() {
        game = TestGames.game(WHITE.toPlayer(), BLACK.toPlayer());
        reset(MATCH_SERVICE);
        when(MATCH_SERVICE.getGameOrThrow(GAME_ID)).thenReturn(game);
        when(MATCH_SERVICE.buildGameStateResponse(game)).thenAnswer(invocation -> state(game));
        doAnswer(invocation -> {
            String from = invocation.getArgument(2);
            String to = invocation.getArgument(3);
            PieceType promotion = invocation.getArgument(4);
            assertThat(game.makeMove(from, to, promotion)).isTrue();
            return state(game);
        }).when(MATCH_SERVICE).makeMove(any(Player.class), eq(GAME_ID), anyString(), anyString(),
                nullable(PieceType.class), any(Instant.class));

        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(StompSession::disconnect);
        client.stop();
    }

    @Test
    void moveHandledByOneInstanceReachesPlayerConnectedToAnother() throws Exception {
        String topic = "/topic/game." + GAME_ID;
        StompSession black = connect(instanceB(), BLACK);
        BlockingQueue<Map<String, Object>> updates = subscribe(black, topic);
        awaitDelivery(updates, () -> template(instanceA()).convertAndSend(topic, PROBE));

        StompSession white = connect(instanceA(), WHITE);
        white.send("/app/game/" + GAME_ID + "/move", new MoveRequest(GAME_ID.toString(), "e2", "e4", null));

        Map<String, Object> update = takeNonProbe(updates);
        assertThat(update.get("lastMoveFrom")).isEqualTo("e2");
        assertThat(update.get("lastMoveTo")).isEqualTo("e4");
        assertThat(update.get("turnColor")).isEqualTo("BLACK");
    }

    @Test
    void privateMessageSentByOneInstanceReachesUserConnectedToAnother() throws Exception {
        StompSession black = connect(instanceB(), BLACK);
        BlockingQueue<Map<String, Object>> errors = subscribe(black, "/user/topic/errors");

        // Instance A does not hold BLACK's session, so it must resolve the user through the broker
        awaitDelivery(errors, () -> template(instanceA()).convertAndSendToUser(
                BLACK.getUsername(), "/topic/errors", PROBE));
    }

    private StompSession connect(ConfigurableApplicationContext instance, UserPrincipal user) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(ORIGIN);
        String credentials = Base64.getEncoder().encodeToString(
                (user.getUsername() + ":password").getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + credentials);

        String port = instance.getEnvironment().getRequiredProperty("local.server.port");
        StompSession session = client.connectAsync(
                URI.create("ws://localhost:" + port + "/ws"), headers, new StompHeaders(),
                new StompSessionHandlerAdapter() {
                }).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        sessions.add(session);
        return session;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({WebsocketConfig.class, GameSocketController.class, GameEventPublisher.class})
    static class TestApplication {
        @Bean
        MatchService matchService() {
            return MATCH_SERVICE;
        }

        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        UserDetailsService testUsers() {
            return username -> Stream.of(WHITE, BLACK)
                    .filter(user -> user.getUsername().equals(username))
                    .map(user -> principal(user.getId(), username))
                    .findFirst()
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        }
    }
}
