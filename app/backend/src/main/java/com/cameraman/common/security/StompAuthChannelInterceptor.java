package com.cameraman.common.security;

import com.cameraman.common.exception.TokenException;
import com.cameraman.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates the STOMP CONNECT frame using the same JWT the client already
 * holds for REST calls. The WebSocket handshake HTTP request is left public
 * (a bearer header can't be attached to it the normal way from a browser), so
 * this interceptor is the only thing standing between an anonymous socket and
 * a live feed of business data — a missing or invalid token here means the
 * connection is refused, not silently left unauthenticated.
 *
 * On success, the STOMP session's principal name is set to the user's id
 * (not their email/mobile), so downstream code can target a specific user
 * with {@code SimpMessagingTemplate.convertAndSendToUser(userId, ...)} using
 * the same id already used everywhere else in the app (bookings, notifications).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String ALT_AUTH_HEADER = "X-Auth-Token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null || token.isBlank()) {
                throw new TokenException("Missing authentication token for WebSocket connection");
            }

            String username = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(token, userDetails.getUsername())) {
                throw new TokenException("Invalid or expired WebSocket authentication token");
            }

            String principalName = (userDetails instanceof User u) ? u.getId() : username;
            accessor.setUser((Principal) () -> principalName);
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return accessor.getFirstNativeHeader(ALT_AUTH_HEADER);
    }
}
