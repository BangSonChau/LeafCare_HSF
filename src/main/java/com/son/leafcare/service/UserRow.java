package com.son.leafcare.service;

import com.son.leafcare.entity.AppUser;
import java.time.LocalDateTime;

/** One line of the ADMIN user list; premiumUntil is null for Free users. */
public record UserRow(AppUser user, LocalDateTime premiumUntil, long scans) {
}
