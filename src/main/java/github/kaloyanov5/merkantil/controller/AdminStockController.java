package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stocks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockRepository stockRepository;

    /**
     * Add new stock (ADMIN only)
     * POST /api/admin/stocks
     */
    @PostMapping
    public ResponseEntity<?> addStock(@RequestBody Stock stock) {
        try {
            if (stockRepository.findBySymbol(stock.getSymbol().toUpperCase()).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Stock already exists: " + stock.getSymbol()));
            }

            stock.setSymbol(stock.getSymbol().toUpperCase());
            stock.setIsActive(true);
            stock.setLastUpdated(LocalDateTime.now());

            Stock saved = stockRepository.save(stock);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update stock (ADMIN only)
     * PUT /api/admin/stocks/{symbol}
     */
    @PutMapping("/{symbol}")
    public ResponseEntity<?> updateStock(
            @PathVariable String symbol,
            @RequestBody Stock updatedStock
    ) {
        try {
            Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

            if (updatedStock.getName() != null) stock.setName(updatedStock.getName());
            if (updatedStock.getExchange() != null) stock.setExchange(updatedStock.getExchange());
            if (updatedStock.getCurrency() != null) stock.setCurrency(updatedStock.getCurrency());
            if (updatedStock.getSector() != null) stock.setSector(updatedStock.getSector());
            if (updatedStock.getIndustry() != null) stock.setIndustry(updatedStock.getIndustry());
            if (updatedStock.getIsActive() != null) stock.setIsActive(updatedStock.getIsActive());

            stock.setLastUpdated(LocalDateTime.now());

            Stock saved = stockRepository.save(stock);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete stock (ADMIN only)
     * DELETE /api/admin/stocks/{symbol}
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<?> deleteStock(@PathVariable String symbol) {
        try {
            Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

            // Soft delete - just mark as inactive
            stock.setIsActive(false);
            stockRepository.save(stock);

            return ResponseEntity.ok(Map.of("message", "Stock deactivated: " + symbol));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}