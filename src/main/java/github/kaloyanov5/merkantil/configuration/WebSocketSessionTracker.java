package github.kaloyanov5.merkantil.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live WebSocket sessions keyed by their originating HTTP session id so
 * that when the HTTP session is destroyed (logout, expiry, admin revoke) the
 * STOMP connection can be force-closed instead of continuing to stream
 * broadcast frames to a now-unauthenticated client.
 *
 * Spring's WebSocket auth only runs at handshake; without this, deleting
 * MERKANTIL_SESSION server-side leaves the STOMP session alive for its full
 * lifetime — see issue #6 in the latest frontend report.
 */
@Component
@Slf4j
public class WebSocketSessionTracker {

    private final Map<String, Set<WebSocketSession>> httpToWs = new ConcurrentHashMap<>();

    public void register(String httpSessionId, WebSocketSession wsSession) {
        if (httpSessionId == null || wsSession == null) {
            return;
        }
        httpToWs.computeIfAbsent(httpSessionId, k -> ConcurrentHashMap.newKeySet()).add(wsSession);
    }

    public void unregister(WebSocketSession wsSession) {
        if (wsSession == null) {
            return;
        }
        httpToWs.values().forEach(set -> set.remove(wsSession));
    }

    public void closeAndUnregister(String httpSessionId, CloseStatus reason) {
        Set<WebSocketSession> sessions = httpToWs.remove(httpSessionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession ws : sessions) {
            try {
                if (ws.isOpen()) {
                    ws.close(reason);
                }
            } catch (IOException e) {
                log.warn("Failed to close WS session {} on HTTP session destroy: {}", ws.getId(), e.getMessage());
            }
        }
    }

    @EventListener
    public void onSessionDeleted(SessionDeletedEvent event) {
        closeAndUnregister(event.getSessionId(),
                CloseStatus.POLICY_VIOLATION.withReason("HTTP session ended"));
    }

    @EventListener
    public void onSessionExpired(SessionExpiredEvent event) {
        closeAndUnregister(event.getSessionId(),
                CloseStatus.POLICY_VIOLATION.withReason("HTTP session expired"));
    }
}
