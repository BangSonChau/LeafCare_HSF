package com.son.leafcare.admin;

import com.son.leafcare.user.AppUser;
import java.time.LocalDateTime;

/** One line of the ADMIN user list; premiumUntil is null for Free users. */
public record UserRow(AppUser user, LocalDateTime premiumUntil, long scans) {
}
