package github.kaloyanov5.merkantil.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_price_history", indexes = {
        @Index(name = "idx_stock_history_symbol", columnList = "symbol"),
        @Index(name = "idx_stock_history_date", columnList = "date"),
        @Index(name = "idx_stock_history_symbol_date", columnList = "symbol, date", unique = true)
})
@Getter @Setter
public class StockPriceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String symbol;

    @NotNull
    @Column(nullable = false)
    private LocalDate date;

    @NotNull
    @Column(nullable = false)
    private Double open;

    @NotNull
    @Column(nullable = false)
    private Double high;

    @NotNull
    @Column(nullable = false)
    private Double low;

    @NotNull
    @Column(nullable = false)
    private Double close;

    @Column
    private Long volume;

    @Column
    private LocalDateTime createdAt;
}