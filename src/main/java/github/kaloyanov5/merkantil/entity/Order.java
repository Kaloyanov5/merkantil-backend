package github.kaloyanov5.merkantil.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_user_timestamp", columnList = "user_id, timestamp"),
        @Index(name = "idx_order_symbol_timestamp", columnList = "symbol, timestamp"),
        @Index(name = "idx_order_user_symbol", columnList = "user_id, symbol")
})
@Getter @Setter
@EntityListeners(AuditingEntityListener.class)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    @NotBlank
    @Column(nullable = false)
    private String symbol;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Side type;

    @NotNull
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "at_price")
    private Double atPrice;

    @Column(name = "limit_price")
    private Double limitPrice;

    @NotNull
    @Column(nullable = false, name = "order_type")
    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.OPEN;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
