package com.cameraman.common.config;

import com.cameraman.common.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time push channel for the admin/staff dashboard and the client portal:
 * new bookings, payments received, gallery-ready, low-stock alerts, and
 * pending-suggestion counts all arrive live over this instead of the client
 * having to poll REST endpoints on a timer.
 *
 * The handshake endpoint itself is intentionally public (see SecurityConfig)
 * because a plain HTTP GET can't carry a bearer token the way the rest of the
 * API expects; the actual authentication happens on the STOMP CONNECT frame
 * via {@link StompAuthChannelInterceptor}, which rejects the connection
 * outright if no valid token is presented. Nothing is broadcast to a
 * connection that hasn't authenticated.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback for browsers/proxies that can't do raw WebSocket.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // Plain STOMP-over-WebSocket for native/mobile clients that don't need SockJS.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic  -> broadcast channels (e.g. /topic/admin-feed for staff-wide events)
        // /queue  -> per-user channels, addressed via convertAndSendToUser(userId, ...)
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
