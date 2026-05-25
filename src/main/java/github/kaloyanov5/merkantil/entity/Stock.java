package github.kaloyanov5.merkantil.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks", indexes = {
        @Index(name = "idx_stock_symbol", columnList = "symbol", unique = true),
        @Index(name = "idx_stock_name", columnList = "name")
})
@Getter @Setter
@EntityListeners(AuditingEntityListener.class)
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 10)
    private String symbol;  // e.g., "AAPL", "GOOGL"

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;  // e.g., "Apple Inc."

    @Column(length = 100)
    private String exchange;  // e.g., "NASDAQ", "NYSE"

    @Column(length = 50)
    private String currency;  // e.g., "USD"

    @Column(length = 100)
    private String sector;  // e.g., "Technology"

    @Column(length = 100)
    private String industry;  // e.g., "Consumer Electronics"

    @Column(precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal previousClose;

    @Column(precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column
    private Long volume;

    // Display-only aggregate — never used in money arithmetic, kept as Double.
    @Column
    private Double marketCap;

    @Column(name = "extended_hours_price", precision = 19, scale = 4)
    private BigDecimal extendedHoursPrice;

    @Column
    private Boolean isActive = true;  // Whether stock is tradeable

    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}