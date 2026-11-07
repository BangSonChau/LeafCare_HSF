package com.son.leafcare.service;

import com.son.leafcare.entity.ScanHistory;
import java.util.List;

public record DashboardStats(long plants, long diseases, long scans, List<ScanHistory> recent) {}
