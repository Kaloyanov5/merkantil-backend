package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.dto.request.OrderRequest;
import github.kaloyanov5.merkantil.dto.response.OrderResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Orders", description = "Endpoints for placing stock buy/sell orders and retrieving order history for the authenticated user")
public class OrderController {

    private final OrderService orderService;
    private final AuthService authService;

    /**
     * Place a new order
     * POST /api/orders
     */
    @PostMapping
    @Operation(summary = "Place an order", description = "Places a new BUY or SELL stock order for the authenticated user. Sufficient balance is required for BUY orders and sufficient shares for SELL orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order placed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid order details, insufficient funds or shares"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Failed to place order due to an internal error")
    })
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        User user = authService.getCurrentUser();
        OrderResponse order = orderService.placeOrder(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Get user's order history
     * GET /api/orders?page=0&size=20
     */
    @GetMapping
    @Operation(summary = "Get order history", description = "Returns a paginated list of all orders placed by the authenticated user, sorted by most recent first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order history returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Page<OrderResponse>> getUserOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        User user = authService.getCurrentUser();
        Page<OrderResponse> orders = orderService.getUserOrders(user.getId(), page, size);
        return ResponseEntity.ok(orders);
    }

    /**
     * Cancel an open limit order
     * DELETE /api/orders/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel limit order", description = "Cancels an open limit order. Reserved funds are refunded for BUY orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Order not found, not cancellable, or does not belong to user"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        User user = authService.getCurrentUser();
        OrderResponse order = orderService.cancelOrder(user.getId(), id);
        return ResponseEntity.ok(order);
    }

    /**
     * Get orders by symbol
     * GET /api/orders/symbol/AAPL?page=0&size=20
     */
    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get orders by symbol", description = "Returns a paginated list of all orders placed by the authenticated user for the specified stock symbol")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders for the symbol returned successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Page<OrderResponse>> getUserOrdersBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        User user = authService.getCurrentUser();
        Page<OrderResponse> orders = orderService.getUserOrdersBySymbol(
                user.getId(), symbol.toUpperCase(), page, size);
        return ResponseEntity.ok(orders);
    }
}
