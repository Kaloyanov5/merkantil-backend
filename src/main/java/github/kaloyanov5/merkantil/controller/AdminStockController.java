package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.request.StockAdminRequest;
import github.kaloyanov5.merkantil.dto.request.StockAdminUpdateRequest;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.service.MassiveWsService;
import org.springframework.beans.factory.ObjectProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Admin - Stocks", description = "Administrative endpoints for creating, updating and deactivating stocks. All endpoints require ADMIN role.")
public class AdminStockController {

    private final StockRepository stockRepository;
    private final ObjectProvider<MassiveWsService> massiveWsServiceProvider;

    /**
     * Add new stock (ADMIN only)
     * POST /api/admin/stocks
     */
    @PostMapping
    @Operation(summary = "Add a new stock", description = "Creates a new stock entry in the system. Requires ADMIN role. The symbol is automatically uppercased and the stock is set to active.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Stock created successfully"),
            @ApiResponse(responseCode = "400", description = "Stock with the given symbol already exists or request body is invalid"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
    public ResponseEntity<?> addStock(@Valid @RequestBody StockAdminRequest request) {
        String symbol = request.symbol().toUpperCase();
        if (stockRepository.findBySymbol(symbol).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Stock already exists: " + symbol));
        }

        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setName(request.name());
        stock.setExchange(request.exchange());
        stock.setCurrency(request.currency());
        stock.setSector(request.sector());
        stock.setIndustry(request.industry());
        stock.setIsActive(request.isActive() != null ? request.isActive() : true);
        stock.setLastUpdated(LocalDateTime.now());

        Stock saved = stockRepository.save(stock);
        refreshWsSubscriptions();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Update stock (ADMIN only)
     * PUT /api/admin/stocks/{symbol}
     */
    @PutMapping("/{symbol}")
    @Operation(summary = "Update a stock", description = "Updates the metadata of an existing stock identified by its symbol. Only provided fields are updated. Requires ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated successfully"),
            @ApiResponse(responseCode = "400", description = "Stock not found for the given symbol or request body is invalid"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
    public ResponseEntity<?> updateStock(
            @PathVariable String symbol,
            @Valid @RequestBody StockAdminUpdateRequest request
    ) {
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

        if (request.name() != null) stock.setName(request.name());
        if (request.exchange() != null) stock.setExchange(request.exchange());
        if (request.currency() != null) stock.setCurrency(request.currency());
        if (request.sector() != null) stock.setSector(request.sector());
        if (request.industry() != null) stock.setIndustry(request.industry());
        if (request.isActive() != null) stock.setIsActive(request.isActive());

        stock.setLastUpdated(LocalDateTime.now());

        Stock saved = stockRepository.save(stock);
        // Only resync subscriptions when activeness toggled — other field edits
        // (name, sector, etc.) don't change the WS subscription set.
        if (request.isActive() != null) {
            refreshWsSubscriptions();
        }
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete stock (ADMIN only)
     * DELETE /api/admin/stocks/{symbol}
     */
    @DeleteMapping("/{symbol}")
    @Operation(summary = "Deactivate a stock", description = "Soft-deletes a stock by marking it as inactive. The stock record is retained but hidden from normal listings. Requires ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock deactivated successfully"),
            @ApiResponse(responseCode = "400", description = "Stock not found for the given symbol"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - ADMIN role required")
    })
    public ResponseEntity<?> deleteStock(@PathVariable String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

        // Soft delete - just mark as inactive
        stock.setIsActive(false);
        stockRepository.save(stock);
        refreshWsSubscriptions();

        return ResponseEntity.ok(Map.of("message", "Stock deactivated: " + symbol));
    }

    private void refreshWsSubscriptions() {
        MassiveWsService ws = massiveWsServiceProvider.getIfAvailable();
        if (ws != null) {
            ws.refreshSubscriptions();
        }
    }
}
