package com.son.leafcare.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    /** Who is logged in, for the navbar. Null when anonymous. */
    public record CurrentUser(String name, String avatarUrl) {
    }

    /** Lets layout/base.html mark the active nav item (Thymeleaf 3.1 no longer exposes #request). */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("me")
    public CurrentUser me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (auth.getPrincipal() instanceof OidcUser g) {
            String name = g.getFullName() == null || g.getFullName().isBlank() ? g.getEmail() : g.getFullName();
            return new CurrentUser(name, g.getPicture());
        }
        return new CurrentUser(auth.getName(), null);
    }
}
