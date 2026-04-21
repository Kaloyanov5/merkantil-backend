package github.kaloyanov5.merkantil.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "watchlist_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "stock_symbol"})
})
@EntityListeners(AuditingEntityListener.class)
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stock_symbol", nullable = false, length = 20)
    private String stockSymbol;

    @CreatedDate
    @Column(nullable = false, updatable = false, name = "added_at")
    private LocalDateTime addedAt;
}
