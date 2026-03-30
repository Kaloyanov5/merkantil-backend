package github.kaloyanov5.merkantil.util;

import github.kaloyanov5.merkantil.service.MassiveApiService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketCalendar {

    private final MassiveApiService massiveApiService;

    private final Set<LocalDate> holidays = new HashSet<>();

    @PostConstruct
    void loadHolidays() {
        massiveApiService.getUpcomingHolidays().stream()
                .filter(h -> "NYSE".equals(h.getExchange()) && "closed".equals(h.getStatus()))
                .map(h -> LocalDate.parse(h.getDate()))
                .forEach(holidays::add);

        log.info("MarketCalendar: loaded {} holidays from Massive API", holidays.size());
    }

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }
}
