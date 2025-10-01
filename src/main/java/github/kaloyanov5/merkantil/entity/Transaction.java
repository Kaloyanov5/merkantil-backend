package github.kaloyanov5.merkantil.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_user_timestamp", columnList = "user_id, timestamp"),
        @Index(name = "idx_tx_symbol_timestamp", columnList = "stock_symbol, timestamp"),
        @Index(name = "idx_tx_user_symbol", columnList = "user_id, stock_symbol")
})
@Getter @Setter
@EntityListeners(AuditingEntityListener.class)
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    // json back reference ?
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotBlank
    @Column(nullable = false, name = "stock_symbol")
    private String stockSymbol;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side type; // BUY or SELL

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Double price; // executed price per share

    @Column(nullable = false, name = "total_amount")
    private Double totalAmount;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime timestamp;

    @PrePersist
    private void computeTotalAmount() {
        if (price == null || quantity == null) {
            throw new IllegalStateException("Price and quantity must be set before persisting a Transaction");
        }
        this.totalAmount = price * quantity;
    }
}
