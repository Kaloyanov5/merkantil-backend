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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
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
    public ResponseEntity<?> placeOrder(@Valid @RequestBody OrderRequest request) {
        try {
            User user = authService.getCurrentUser();
            OrderResponse order = orderService.placeOrder(user.getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to place order: " + e.getMessage()));
        }
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
    public ResponseEntity<?> getUserOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User user = authService.getCurrentUser();
            Page<OrderResponse> orders = orderService.getUserOrders(user.getId(), page, size);
            return ResponseEntity.ok(orders);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
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
    public ResponseEntity<?> getUserOrdersBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User user = authService.getCurrentUser();
            Page<OrderResponse> orders = orderService.getUserOrdersBySymbol(
                    user.getId(), symbol.toUpperCase(), page, size);
            return ResponseEntity.ok(orders);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }
}
