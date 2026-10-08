package me.zilid.chessplatform.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket. With the relay enabled, subscriptions live in an external broker (RabbitMQ), so a message sent
 * by any backend instance reaches clients connected to every instance. Without it, each instance uses Spring's
 * in-memory broker, which only works for a single instance.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String[] allowedOrigins;
    private final RelayProperties relay;

    public WebsocketConfig(
            @Value("${app.allowed-origins}") String allowedOrigins,
            @Value("${app.websocket.relay.enabled}") boolean relayEnabled,
            @Value("${app.websocket.relay.host}") String relayHost,
            @Value("${app.websocket.relay.port}") int relayPort,
            @Value("${app.websocket.relay.login}") String relayLogin,
            @Value("${app.websocket.relay.passcode}") String relayPasscode) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        this.relay = new RelayProperties(relayEnabled, relayHost, relayPort, relayLogin, relayPasscode);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // RabbitMQ only accepts dot-separated topic names, e.g. /topic/game.<id>.chat
        if (relay.enabled()) {
            registry.enableStompBrokerRelay("/topic")
                    .setRelayHost(relay.host())
                    .setRelayPort(relay.port())
                    .setSystemLogin(relay.login())
                    .setSystemPasscode(relay.passcode())
                    .setClientLogin(relay.login())
                    .setClientPasscode(relay.passcode())
                    // Let every instance deliver /user/... messages to users connected to another instance
                    .setUserDestinationBroadcast("/topic/unresolved-user-destination")
                    .setUserRegistryBroadcast("/topic/simp-user-registry");
        } else {
            registry.enableSimpleBroker("/topic");
        }
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    private record RelayProperties(boolean enabled, String host, int port, String login, String passcode) {}
}
