package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.response.TransactionResponse;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * Get user's transaction history
     */
    public Page<TransactionResponse> getUserTransactions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Transaction> transactions = transactionRepository.findByUserId(userId, pageable);

        return transactions.map(this::mapToTransactionResponse);
    }

    /**
     * Get transactions by type (BUY/SELL)
     */
    public Page<TransactionResponse> getUserTransactionsByType(Long userId, String type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Side side = Side.valueOf(type.toUpperCase());
        Page<Transaction> transactions = transactionRepository.findByUserIdAndType(userId, side, pageable);

        return transactions.map(this::mapToTransactionResponse);
    }

    /**
     * Get transactions for specific stock
     */
    public List<TransactionResponse> getUserTransactionsBySymbol(Long userId, String symbol) {
        List<Transaction> transactions = transactionRepository.findByUserIdAndStockSymbol(
                userId, symbol.toUpperCase());

        return transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get transactions in date range
     */
    public List<TransactionResponse> getUserTransactionsByDateRange(
            Long userId, LocalDateTime start, LocalDateTime end) {
        List<Transaction> transactions = transactionRepository.findByUserIdAndTimestampBetween(
                userId, start, end);

        return transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get transaction statistics
     */
    public TransactionStats getTransactionStats(Long userId) {
        Long totalTransactions = transactionRepository.countByUserId(userId);

        Double totalBuyAmount = transactionRepository.sumTotalAmountByUserIdAndType(userId, Side.BUY);
        Double totalSellAmount = transactionRepository.sumTotalAmountByUserIdAndType(userId, Side.SELL);

        totalBuyAmount = totalBuyAmount != null ? totalBuyAmount : 0.0;
        totalSellAmount = totalSellAmount != null ? totalSellAmount : 0.0;

        return new TransactionStats(
                totalTransactions,
                totalBuyAmount,
                totalSellAmount,
                totalSellAmount - totalBuyAmount
        );
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getStockSymbol(),
                transaction.getType().name(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTotalAmount(),
                transaction.getTimestamp()
        );
    }

    // Inner record class for transaction stats
    public record TransactionStats(
            long totalCount,
            double totalBought,
            double totalSold,
            double netAmount
    ) {
    }
}
