package com.son.leafcare.service;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Role;
import com.son.leafcare.repository.AppUserRepository;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    private final AppUserRepository repo;
    private final Set<String> adminEmails;

    public AppUserService(AppUserRepository repo, @Value("${leafcare.admin-emails:}") String adminEmails) {
        this.repo = repo;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Called at every Google login: creates the user once, then refreshes name, avatar and role. */
    @Transactional
    public AppUser upsertFromGoogle(String email, String name, String avatarUrl) {
        String key = email.trim().toLowerCase(Locale.ROOT);
        AppUser u = repo.findByEmail(key).orElseGet(AppUser::new);
        u.setEmail(key);
        u.setName(name);
        u.setAvatarUrl(avatarUrl);
        u.setRole(adminEmails.contains(key) ? Role.ADMIN : Role.USER);
        return repo.save(u);
    }
}
