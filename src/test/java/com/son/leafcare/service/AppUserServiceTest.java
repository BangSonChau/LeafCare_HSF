package com.son.leafcare.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Role;
import com.son.leafcare.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(AppUserService.class)
class AppUserServiceTest {

    @Autowired
    AppUserService service;

    @Autowired
    AppUserRepository repo;

    @Test
    void firstLoginCreatesPlainUser() {
        AppUser u = service.upsertFromGoogle("Shin@Gmail.com", "Shin", "https://pic");
        assertThat(u.getId()).isNotNull();
        assertThat(u.getEmail()).isEqualTo("shin@gmail.com");
        assertThat(u.getRole()).isEqualTo(Role.USER);
        assertThat(u.getCreatedAt()).isNotNull();
    }

    @Test
    void adminEmailMatchesIgnoringCaseAndSpaces() {
        // test properties: leafcare.admin-emails=admin@test.com
        assertThat(service.upsertFromGoogle(" Admin@Test.com ", "A", null).getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void secondLoginUpdatesInsteadOfDuplicating() {
        service.upsertFromGoogle("a@b.com", "Old", "https://old");
        AppUser u = service.upsertFromGoogle("a@b.com", "New", "https://new");
        assertThat(repo.count()).isEqualTo(1);
        assertThat(u.getName()).isEqualTo("New");
        assertThat(u.getAvatarUrl()).isEqualTo("https://new");
    }

    @Test
    void removedFromAdminListLosesAdminAtNextLogin() {
        service.upsertFromGoogle("admin@test.com", "A", null);
        AppUser u = new AppUserService(repo, "").upsertFromGoogle("admin@test.com", "A", null);
        assertThat(u.getRole()).isEqualTo(Role.USER);
    }
}
