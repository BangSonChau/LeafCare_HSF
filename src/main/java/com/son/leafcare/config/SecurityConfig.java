package com.son.leafcare.config;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.Role;
import com.son.leafcare.service.AppUserService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AppUserService users) throws Exception {
        OidcUserService google = new OidcUserService();
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/css/**", "/error", "/error/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/sepay/webhook").permitAll()
                        .requestMatchers("/plants/new", "/plants/*/edit", "/diseases/new", "/diseases/*/edit")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/plants/**", "/diseases/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // SePay calls the webhook server-to-server, without our session or CSRF token; the API key protects it.
                .csrf(c -> c.ignoringRequestMatchers("/payments/sepay/webhook"))
                .oauth2Login(o -> o
                        .loginPage("/login")
                        .userInfoEndpoint(u -> u.oidcUserService(req -> withAppRole(google.loadUser(req), users))))
                .logout(l -> l.logoutSuccessUrl("/"));
        return http.build();
    }

    /** Saves the Google user and swaps Google's authorities for ours; the principal name becomes the email. */
    static OidcUser withAppRole(OidcUser google, AppUserService users) {
        AppUser u = users.upsertFromGoogle(google.getEmail(), google.getFullName(), google.getPicture());
        List<GrantedAuthority> roles = u.getRole() == Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new DefaultOidcUser(roles, google.getIdToken(), google.getUserInfo(), "email");
    }
}
