package com.son.leafcare.home;

import com.son.leafcare.scan.ScanHistory;
import java.util.List;

public record DashboardStats(long plants, long diseases, long scans, List<ScanHistory> recent) {}
