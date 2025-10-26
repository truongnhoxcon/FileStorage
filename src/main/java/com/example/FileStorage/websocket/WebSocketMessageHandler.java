package com.example.FileStorage.websocket;

import com.example.FileStorage.entity.User;
import com.example.FileStorage.repository.UserRepository;
import com.example.FileStorage.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketMessageHandler implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    public WebSocketMessageHandler(JwtService jwtService, 
                                 UserDetailsService userDetailsService,
                                 UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract JWT token from headers
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    try {
                        // Validate JWT token
                        String username = jwtService.extractUsername(token);
                        if (username != null && jwtService.isTokenValid(token, username)) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            User user = userRepository.findByUsername(username)
                                    .orElse(null);
                            if (user == null) {
                                return null;
                            }
                            
                            // Set authentication
                            Authentication auth = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            accessor.setUser(auth);
                            
                            // Store user info in session attributes
                            if (accessor.getSessionAttributes() != null) {
                                accessor.getSessionAttributes().put("userId", user.getId());
                                accessor.getSessionAttributes().put("username", username);
                            }
                        }
                    } catch (Exception e) {
                        // Token validation failed
                        return null;
                    }
                }
            }
        }
        
        return message;
    }
}
