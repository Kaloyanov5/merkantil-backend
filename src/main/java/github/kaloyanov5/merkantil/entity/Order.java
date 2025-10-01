package github.kaloyanov5.merkantil.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
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

    @NotNull
    @Column(nullable = false, name = "at_price")
    private BigDecimal atPrice;

    @NotNull
    @Column(nullable = false, name = "order_type")
    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
