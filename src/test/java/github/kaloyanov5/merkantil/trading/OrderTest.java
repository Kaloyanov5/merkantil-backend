package github.kaloyanov5.merkantil.trading;

import github.kaloyanov5.merkantil.trading.model.Order;
import github.kaloyanov5.merkantil.trading.model.enums.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @ParameterizedTest(name = "{0} limit 140.00 at {1} -> fillable={2}")
    @CsvSource({
            "BUY,  139.99, true",
            "BUY,  140.00, true",
            "BUY,  140.01, false",
            "SELL, 140.01, true",
            "SELL, 140.00, true",
            "SELL, 139.99, false"
    })
    @DisplayName("isFillableAt: BUY fills at or below the limit, SELL at or above")
    void isFillableAt(Side side, String price, boolean expected) {
        Order order = limitOrder(side, "140.00");

        assertThat(order.isFillableAt(new BigDecimal(price))).isEqualTo(expected);
    }

    @Test
    @DisplayName("isFillableAt: compares by value, so a different scale is still the same price")
    void isFillableAt_ignoresScale() {
        Order order = limitOrder(Side.BUY, "140.0000");

        // BigDecimal.equals("140.0", "140.0000") is false; the comparison must not depend on it
        assertThat(order.isFillableAt(new BigDecimal("140.0"))).isTrue();
    }

    private static Order limitOrder(Side side, String limitPrice) {
        Order order = new Order();
        order.setType(side);
        order.setLimitPrice(new BigDecimal(limitPrice));
        return order;
    }
}
