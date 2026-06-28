package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Reconstructs net share quantity per symbol as of the end of a given date from the
 * transaction ledger. Shared by {@code PortfolioGrowthService} and the analytics engine.
 */
@Component
@RequiredArgsConstructor
public class HoldingsReconstruction {

    private final TransactionRepository transactionRepository;

    /** Map of symbol → net quantity (only positions with quantity &gt; 0). */
    public Map<String, Integer> positionsAsOf(Long userId, LocalDate date) {
        Map<String, Integer> net = new HashMap<>();
        for (Transaction tx : transactionRepository.findByUserIdAndDateBeforeOrEqual(userId, date)) {
            String symbol = tx.getStockSymbol().toUpperCase();
            int qty = tx.getQuantity();
            if (tx.getType() == Side.BUY) {
                net.merge(symbol, qty, Integer::sum);
            } else if (tx.getType() == Side.SELL) {
                net.merge(symbol, -qty, Integer::sum);
            }
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> e : net.entrySet()) {
            if (e.getValue() > 0) result.put(e.getKey(), e.getValue());
        }
        return result;
    }
}
