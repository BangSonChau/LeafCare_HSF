package com.son.leafcare.scan;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.user.AppUserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImageController.class)
@Import(SecurityConfig.class)
@WithMockUser
class ImageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    FileStorageService storage;

    @Test
    void servesImageWithTypeAndCache() throws Exception {
        when(storage.find("a.png")).thenReturn(Optional.of(new ImageFile("a.png", "image/png", new byte[] {7, 8})));
        mvc.perform(get("/uploads/a.png")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {7, 8}))
                .andExpect(header().string("Cache-Control", "max-age=31536000, private"));
    }

    @Test
    void missingIs404() throws Exception {
        when(storage.find("x.png")).thenReturn(Optional.empty());
        mvc.perform(get("/uploads/x.png")).andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void anonymousIsSentToLogin() throws Exception {
        mvc.perform(get("/uploads/a.png")).andExpect(status().is3xxRedirection());
    }
}
