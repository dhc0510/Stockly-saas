package com.stockly.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.Duration;

@Component
public class SessionRevoker {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final JdbcTemplate jdbc;
    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions, JdbcTemplate jdbc) {
        this.sessions=sessions; this.jdbc=jdbc;
    }
    public void revokeUser(String userId) {
        revokeSessions(userId);
        jdbc.update("UPDATE trusted_devices SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", Instant.now(), userId);
    }
    public void revokeSessions(String userId) {
        sessions.findByPrincipalName(userId).keySet().forEach(sessions::deleteById);
    }
    public void updateIdleTimeout(String userId,Long minutes) {
        Duration value=minutes==null || minutes>Integer.MAX_VALUE/60 ? Duration.ofSeconds(-1) : Duration.ofSeconds(minutes*60);
        updateTimeout(sessions,userId,value);
    }
    private static <S extends Session> void updateTimeout(FindByIndexNameSessionRepository<S> repository,String userId,Duration timeout) {
        repository.findByPrincipalName(userId).values().forEach(session->{session.setMaxInactiveInterval(timeout);repository.save(session);});
    }
}
