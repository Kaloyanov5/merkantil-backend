package github.kaloyanov5.merkantil.service;

import java.time.LocalDate;

/** A single day's return as a decimal fraction (0.01 = 1%). */
public record DailyReturn(LocalDate date, double value) {
}
