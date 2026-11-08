package com.son.leafcare.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.son.leafcare.controller.HomeController;
import com.son.leafcare.controller.LoginController;
import com.son.leafcare.controller.PlantController;
import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Role;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.DashboardService;
import com.son.leafcare.service.DashboardStats;
import com.son.leafcare.service.PlantService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PlantController.class, LoginController.class, HomeController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    PlantService plants;

    @MockitoBean
    DashboardService dashboard;

    @BeforeEach
    void stubs() {
        when(plants.search(any(), anyInt())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(dashboard.stats(any())).thenReturn(new DashboardStats(0, 0, 0, List.of()));
    }

    @Test
    void anonymousIsSentToLoginPage() throws Exception {
        mvc.perform(get("/plants")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPageIsPublicAndLinksToGoogle() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/oauth2/authorization/google\"")))
                .andExpect(content().string(containsString("Đăng nhập bằng Google")));
    }

    @Test
    void loginErrorIsShown() throws Exception {
        mvc.perform(get("/login?error")).andExpect(content().string(containsString("Đăng nhập không thành công")));
    }

    @Test
    void userSeesListButNoAdminButtons() throws Exception {
        mvc.perform(get("/plants").with(user("u@x.com").roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/plants/new\""))))
                .andExpect(content().string(containsString("Đăng xuất")));
    }

    @Test
    void userCannotOpenOrPostAdminRoutes() throws Exception {
        mvc.perform(get("/plants/new").with(user("u@x.com").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/plants/1/edit").with(user("u@x.com").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(user("u@x.com").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").with(user("u@x.com").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/plants").with(csrf()).with(user("u@x.com").roles("USER")).param("name", "X"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/plants/1/delete").with(csrf()).with(user("u@x.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanOpenAdminRoutes() throws Exception {
        mvc.perform(get("/plants/new").with(user("a@x.com").roles("USER", "ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/plants").with(user("a@x.com").roles("USER", "ADMIN")))
                .andExpect(content().string(containsString("href=\"/plants/new\"")));
    }

    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/plants").with(user("a@x.com").roles("ADMIN")).param("name", "X"))
                .andExpect(status().isForbidden());
    }

    @Test
    void navbarShowsGoogleNameAndInitialWhenNoPhoto() throws Exception {
        mvc.perform(get("/").with(oidcLogin().idToken(t -> t.claim("name", "Shin Châu").claim("email", "s@x.com"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shin Châu")))
                .andExpect(content().string(containsString(">S<")));
    }

    @Test
    void googleUserGetsAppRolesAndEmailAsName() {
        AppUser admin = new AppUser();
        admin.setRole(Role.ADMIN);
        when(users.upsertFromGoogle("a@x.com", "A", null)).thenReturn(admin);
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "1", "email", "a@x.com", "name", "A"));
        OidcUser google = new DefaultOidcUser(List.of(), token);

        OidcUser result = SecurityConfig.withAppRole(google, users);

        assertThat(result.getName()).isEqualTo("a@x.com");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void plainGoogleUserIsOnlyUser() {
        AppUser u = new AppUser();
        when(users.upsertFromGoogle("u@x.com", null, null)).thenReturn(u);
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "2", "email", "u@x.com"));

        OidcUser result = SecurityConfig.withAppRole(new DefaultOidcUser(List.of(), token), users);

        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }
}
