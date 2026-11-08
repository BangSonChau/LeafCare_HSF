package com.son.leafcare.service;

import com.son.leafcare.entity.Payment;
import java.util.List;

/** Numbers for the ADMIN dashboard. Money is VND; days are Vietnam calendar days. */
public record AdminStats(long users, long newUsers7d, long premiumActive,
                         long revenueTotal, long revenueThisMonth,
                         long scansToday, long scans7d, List<DayCount> scansByDay,
                         List<Payment> latestPayments, List<TopScanner> topScanners) {

    /** Tallest bar of the chart (at least 1, so empty days never divide by zero). */
    public long maxPerDay() {
        return Math.max(1, scansByDay.stream().mapToLong(DayCount::count).max().orElse(0));
    }
}
