package com.example.nutmeg.api.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameSocketHandler handler;
    private final String[] allowedOrigins;

    /**
     * Origins default to "*". That's a deliberate call for THIS app: players
     * authenticate with a token sent in the first message (read from
     * localStorage, which other sites can't touch), not with cookies, so a
     * foreign page opening a socket gains nothing. Lock it down with
     * NUTMEG_ALLOWED_ORIGINS=https://your.domain if you ever add cookie auth.
     */
    public WebSocketConfig(GameSocketHandler handler,
                           @Value("${nutmeg.allowed-origins:*}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    /** Small box: cap buffers so one bad client can't eat the heap. */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(16 * 1024);
        container.setMaxBinaryMessageBufferSize(16 * 1024);
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }
}
