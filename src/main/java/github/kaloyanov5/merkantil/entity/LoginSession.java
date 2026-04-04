package github.kaloyanov5.merkantil.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_sessions", indexes = {
        @Index(name = "idx_login_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_login_sessions_session_id", columnList = "session_id", unique = true)
})
@Getter @Setter
@EntityListeners(AuditingEntityListener.class)
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "user_id")
    private Long userId;

    @Column(nullable = false, name = "session_id")
    private String sessionId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "device_info")
    private String deviceInfo;

    @CreatedDate
    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;
}
