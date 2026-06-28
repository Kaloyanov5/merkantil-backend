package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingsReconstructionTest {

    @Mock private TransactionRepository transactionRepository;

    private Transaction tx(String symbol, Side side, int qty) {
        Transaction t = new Transaction();
        t.setStockSymbol(symbol);
        t.setType(side);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal("100.00"));
        t.setTotalAmount(BigDecimal.valueOf(100L * qty));
        t.setTimestamp(LocalDate.of(2026, 5, 4).atTime(LocalTime.NOON));
        return t;
    }

    @Test @DisplayName("net positions = buys - sells, zero/negative filtered out")
    void netPositions() {
        when(transactionRepository.findByUserIdAndDateBeforeOrEqual(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(
                        tx("AAPL", Side.BUY, 10),
                        tx("AAPL", Side.SELL, 4),
                        tx("TSLA", Side.BUY, 5),
                        tx("TSLA", Side.SELL, 5)
                ));

        HoldingsReconstruction r = new HoldingsReconstruction(transactionRepository);
        Map<String, Integer> pos = r.positionsAsOf(1L, LocalDate.of(2026, 5, 10));

        assertThat(pos).containsEntry("AAPL", 6);
        assertThat(pos).doesNotContainKey("TSLA");
    }

    @Test @DisplayName("symbols are upper-cased")
    void upperCases() {
        when(transactionRepository.findByUserIdAndDateBeforeOrEqual(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(tx("aapl", Side.BUY, 3)));

        HoldingsReconstruction r = new HoldingsReconstruction(transactionRepository);
        assertThat(r.positionsAsOf(1L, LocalDate.of(2026, 5, 10))).containsEntry("AAPL", 3);
    }
}
