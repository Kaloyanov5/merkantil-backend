package github.kaloyanov5.merkantil.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "portfolios", uniqueConstraints = {
        @UniqueConstraint(name = "uk_portfolio_user_symbol", columnNames = {"user_id", "symbol"})
}, indexes = {
        @Index(name = "idx_portfolio_user", columnList = "user_id"),
        @Index(name = "idx_portfolio_user_symbol", columnList = "user_id, symbol")
})
@Getter @Setter
public class Portfolio {
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
    private Integer quantity;

    @NotNull
    @Column(nullable = false)
    private Double averageBuyPrice;

    // CURRENT VALUE
}
