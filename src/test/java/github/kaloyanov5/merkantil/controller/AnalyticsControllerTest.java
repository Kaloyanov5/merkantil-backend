package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.configuration.AnalyticsProperties;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.service.AnalyticsWindow;
import github.kaloyanov5.merkantil.service.AuthService;
import github.kaloyanov5.merkantil.service.PortfolioAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsControllerTest {

    @Mock private PortfolioAnalyticsService analyticsService;
    @Mock private AuthService authService;

    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        AnalyticsProperties props = new AnalyticsProperties(0.04, "SPY", 252, "3M", 20);
        controller = new AnalyticsController(analyticsService, authService, props);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(authService.getCurrentUser()).thenReturn(user);
    }

    @Test @DisplayName("valid window returns 200 and delegates with the parsed window")
    void validWindow() {
        ResponseEntity<?> resp = controller.getAnalytics("6M");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(analyticsService).getAnalytics(eq(1L), eq(AnalyticsWindow.SIX_MONTHS));
    }

    @Test @DisplayName("null window falls back to the configured default (3M)")
    void defaultWindow() {
        controller.getAnalytics(null);
        verify(analyticsService).getAnalytics(eq(1L), eq(AnalyticsWindow.THREE_MONTHS));
    }

    @Test @DisplayName("invalid window returns 400")
    void invalidWindow() {
        ResponseEntity<?> resp = controller.getAnalytics("bogus");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
