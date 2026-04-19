package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.response.PortfolioGrowthResponse;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating historical portfolio growth with financial accuracy.
 * Core principle: Historical portfolio values must be reconstructed using historical prices
 * and positions as of that day. Never use current prices to value past dates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioGrowthService {


    private final TransactionRepository transactionRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;
    private final MarketCalendar marketCalendar;

    /**
     * Generate 30-day portfolio growth chart.
     * Returns one data point per trading day showing reconstructed portfolio value.
     *
     * @param userId The user whose portfolio to reconstruct
     * @return List of date-value pairs for the last 30 trading days
     */
    public List<PortfolioGrowthResponse> getPortfolioGrowth30Days(Long userId) {
        log.info("Generating 30-day portfolio growth for user {}", userId);

        // Step 1: Determine the last 30 trading days
        List<LocalDate> tradingDays = getLast30TradingDays();

        if (tradingDays.isEmpty()) {
            log.warn("No trading days calculated");
            return Collections.emptyList();
        }

        log.debug("Calculated {} trading days from {} to {}",
                tradingDays.size(), tradingDays.get(0), tradingDays.get(tradingDays.size() - 1));

        List<PortfolioGrowthResponse> growthData = new ArrayList<>();
        Double lastKnownValue = 0.0;

        // Step 2: For each trading day, reconstruct portfolio value
        for (LocalDate date : tradingDays) {
            try {
                Double portfolioValue = reconstructPortfolioValueForDate(userId, date);
                lastKnownValue = portfolioValue;
                growthData.add(new PortfolioGrowthResponse(date, portfolioValue));

                log.debug("Date: {} | Portfolio Value: ${}", date,
                        String.format("%.2f", portfolioValue));
            } catch (Exception e) {
                log.error("Error calculating portfolio value for date {}: {}", date, e.getMessage());
                // Carry forward last known value to avoid false $0 cliffs
                growthData.add(new PortfolioGrowthResponse(date, lastKnownValue));
            }
        }

        log.info("Portfolio growth calculation completed: {} data points", growthData.size());
        return growthData;
    }

    /**
     * Reconstruct portfolio value as of the end of a specific trading day.
     * This is the core financial reconstruction logic.
     *
     * @param userId The user ID
     * @param date The trading day to reconstruct for
     * @return The total portfolio value on that date
     */
    private Double reconstructPortfolioValueForDate(Long userId, LocalDate date) {
        // Step 2.1: Reconstruct positions as of end of day
        Map<String, Integer> positions = reconstructPositionsAsOfDate(userId, date);

        if (positions.isEmpty()) {
            log.debug("No positions for user {} on {}", userId, date);
            return 0.0;
        }

        log.debug("User {} positions on {}: {}", userId, date, positions);

        // Step 2.2 & 2.3: Resolve prices and calculate value
        double totalValue = 0.0;

        for (Map.Entry<String, Integer> position : positions.entrySet()) {
            String symbol = position.getKey();
            Integer quantity = position.getValue();

            if (quantity == 0) {
                continue; // Skip symbols with zero quantity
            }

            // Resolve historical close price for this date
            Double closePrice = resolveHistoricalClosePrice(symbol, date);

            if (closePrice == null) {
                log.warn("No historical price found for {} on or before {}. Excluding from portfolio value.",
                        symbol, date);
                continue; // Exclude symbol if no price data exists
            }

            double positionValue = quantity * closePrice;
            totalValue += positionValue;

            log.debug("  {} | Qty: {} | Price: ${} | Value: ${}",
                    symbol, quantity,
                    String.format("%.2f", closePrice),
                    String.format("%.2f", positionValue));
        }

        return totalValue;
    }

    /**
     * Reconstruct portfolio positions as of end of day for a specific date.
     * Calculates net quantity (buys - sells) for each symbol up to and including the date.
     *
     * @param userId The user ID
     * @param date The date to reconstruct positions for
     * @return Map of symbol -> net quantity
     */
    private Map<String, Integer> reconstructPositionsAsOfDate(Long userId, LocalDate date) {
        // Get all transactions up to and including this date
        List<Transaction> transactions = transactionRepository
                .findByUserIdAndDateBeforeOrEqual(userId, date);

        // Calculate net quantity per symbol
        Map<String, Integer> positions = new HashMap<>();

        for (Transaction tx : transactions) {
            String symbol = tx.getStockSymbol().toUpperCase();
            int quantity = tx.getQuantity();

            if (tx.getType() == Side.BUY) {
                positions.put(symbol, positions.getOrDefault(symbol, 0) + quantity);
            } else if (tx.getType() == Side.SELL) {
                positions.put(symbol, positions.getOrDefault(symbol, 0) - quantity);
            }
        }

        // Filter out symbols with zero quantity
        return positions.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Resolve the official close price for a symbol on a specific date.
     * If no data exists for that date, fallback to the most recent prior trading day.
     * Never use prices from future dates.
     *
     * @param symbol The stock symbol
     * @param date The date to get price for
     * @return The close price, or null if no historical data exists
     */
    private Double resolveHistoricalClosePrice(String symbol, LocalDate date) {
        Optional<StockPriceHistory> priceHistory = stockPriceHistoryRepository
                .findMostRecentPriceOnOrBefore(symbol.toUpperCase(), date);

        if (priceHistory.isPresent()) {
            Double closePrice = priceHistory.get().getClose();
            LocalDate priceDate = priceHistory.get().getDate();

            if (!priceDate.equals(date)) {
                log.debug("Using fallback price for {} on {}: price from {}",
                        symbol, date, priceDate);
            }

            return closePrice;
        }

        return null; // No historical price data available
    }

    /**
     * Calculate the last 30 trading days (excluding weekends and holidays).
     * Uses a simplified approach that excludes weekends.
     *
     * Note: For production, integrate with a proper market calendar API
     * to handle holidays accurately.
     *
     * @return List of trading days in chronological order (oldest to newest)
     */
    private List<LocalDate> getLast30TradingDays() {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate currentDate = LocalDate.now().minusDays(1); // Start from yesterday
        int daysAdded = 0;

        // Go back until we have 30 trading days
        while (daysAdded < 30 && currentDate.isAfter(LocalDate.now().minusYears(1))) {
            if (isTradingDay(currentDate)) {
                tradingDays.add(currentDate);
                daysAdded++;
            }
            currentDate = currentDate.minusDays(1);
        }

        // Reverse to get chronological order (oldest first)
        Collections.reverse(tradingDays);
        return tradingDays;
    }

    private boolean isTradingDay(LocalDate date) {
        return marketCalendar.isTradingDay(date);
    }

    /**
     * Get portfolio growth for a custom date range.
     *
     * @param userId The user ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of date-value pairs
     */
    public List<PortfolioGrowthResponse> getPortfolioGrowthCustomRange(
            Long userId, LocalDate startDate, LocalDate endDate) {

        log.info("Generating custom portfolio growth for user {} from {} to {}",
                userId, startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        List<LocalDate> tradingDays = getTradingDaysInRange(startDate, endDate);
        List<PortfolioGrowthResponse> growthData = new ArrayList<>();
        Double lastKnownValue = 0.0;

        for (LocalDate date : tradingDays) {
            try {
                Double portfolioValue = reconstructPortfolioValueForDate(userId, date);
                lastKnownValue = portfolioValue;
                growthData.add(new PortfolioGrowthResponse(date, portfolioValue));
            } catch (Exception e) {
                log.error("Error calculating portfolio value for date {}: {}", date, e.getMessage());
                growthData.add(new PortfolioGrowthResponse(date, lastKnownValue));
            }
        }

        return growthData;
    }

    /**
     * Get all trading days within a date range.
     *
     * @param startDate Start date
     * @param endDate End date
     * @return List of trading days
     */
    private List<LocalDate> getTradingDaysInRange(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            if (isTradingDay(current)) {
                tradingDays.add(current);
            }
            current = current.plusDays(1);
        }

        return tradingDays;
    }
}

