package github.kaloyanov5.merkantil.trading.error;

import github.kaloyanov5.merkantil.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum TradingError implements ErrorCode {
    ORDER_NOT_FOUND("TRAD-3001", "Order not found", HttpStatus.NOT_FOUND),
    STOCK_NOT_FOUND("TRAD-3002", "Stock not found", HttpStatus.NOT_FOUND),
    NO_POSITION("TRAD-3003", "You do not own any shares of this stock", HttpStatus.NOT_FOUND),
    STOCK_NOT_TRADEABLE("TRAD-3004", "Stock is not active for trading", HttpStatus.BAD_REQUEST),
    LIMIT_PRICE_REQUIRED("TRAD-3005", "Limit price is required for LIMIT orders", HttpStatus.BAD_REQUEST),
    LIMIT_PRICE_OUT_OF_RANGE("TRAD-3006", "Limit price is outside the allowed range", HttpStatus.BAD_REQUEST),
    MARKET_CLOSED("TRAD-3007", "Market orders can only be placed during regular trading hours", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_FUNDS("TRAD-3008", "Insufficient funds to complete this order", HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_SHARES("TRAD-3009", "Insufficient shares to complete this order", HttpStatus.UNPROCESSABLE_ENTITY),
    ORDER_NOT_CANCELLABLE("TRAD-3010", "Only open limit orders can be cancelled", HttpStatus.UNPROCESSABLE_ENTITY),
    PRICE_UNAVAILABLE("TRAD-3011", "Unable to determine the current market price", HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final String message;
    private final HttpStatus status;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
