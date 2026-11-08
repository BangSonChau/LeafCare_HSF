package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.entity.AppUser;
import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.exception.InvalidImageException;
import com.son.leafcare.exception.ScanLimitException;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.service.ScanHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(ScanController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class ScanControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    ScanHistoryService service;

    MockMultipartFile file = new MockMultipartFile("file", "leaf.png", "image/png", new byte[] {1});

    ScanHistory scan(long id) {
        ScanHistory s = new ScanHistory();
        s.setId(id);
        s.setImageFileName("a.png");
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    @Test
    void listShowsPage() throws Exception {
        when(service.list(any(), eq(0))).thenReturn(new PageImpl<>(List.of(scan(1)), PageRequest.of(0, 10), 1));
        mvc.perform(get("/scans")).andExpect(status().isOk()).andExpect(view().name("scans/list"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void adminListShowsOwnerOrUnknown() throws Exception {
        AppUser u = new AppUser();
        u.setName("Shin");
        u.setEmail("shin@x.com");
        ScanHistory owned = scan(1);
        owned.setOwner(u);
        when(service.list(any(), eq(0))).thenReturn(new PageImpl<>(List.of(owned, scan(2)), PageRequest.of(0, 10), 2));
        mvc.perform(get("/scans"))
                .andExpect(content().string(containsString("Shin · shin@x.com")))
                .andExpect(content().string(containsString("Chưa rõ (quét trước khi có đăng nhập)")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userListHidesOwner() throws Exception {
        when(service.list(any(), eq(0))).thenReturn(new PageImpl<>(List.of(scan(1)), PageRequest.of(0, 10), 1));
        mvc.perform(get("/scans")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Người quét"))));
    }

    @Test
    void adminDetailShowsOwnerEmailWhenNoName() throws Exception {
        AppUser u = new AppUser();
        u.setEmail("noname@x.com");
        ScanHistory s = scan(1);
        s.setOwner(u);
        when(service.get(eq(1L), any())).thenReturn(s);
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("Người quét")))
                .andExpect(content().string(containsString("noname@x.com")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userDetailHidesOwner() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(scan(1));
        mvc.perform(get("/scans/1")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Người quét"))));
    }

    @Test
    void listEmpty() throws Exception {
        when(service.list(any(), eq(0))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/scans")).andExpect(status().isOk());
    }

    @Test
    void newForm() throws Exception {
        when(service.remainingToday(any())).thenReturn(null); // Premium / ADMIN: no counter
        mvc.perform(get("/scans/new")).andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(content().string(not(containsString("lượt hôm nay"))));
    }

    @Test
    void newFormShowsRemainingForFree() throws Exception {
        when(service.remainingToday(any())).thenReturn(3);
        mvc.perform(get("/scans/new"))
                .andExpect(content().string(containsString("Còn <strong class=\"tabular\">3/5</strong> lượt hôm nay")));
    }

    @Test
    void sixthScanShowsLimitMessageAndUpgradeLink() throws Exception {
        when(service.remainingToday(any())).thenReturn(0);
        when(service.create(any(), any(), any())).thenThrow(new ScanLimitException());
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "keep me"))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attribute("note", "keep me"))
                .andExpect(model().attributeDoesNotExist("error"))
                .andExpect(content().string(containsString("Tài khoản Free được quét 5 lượt/ngày")))
                .andExpect(content().string(containsString("href=\"/plans\"")))
                .andExpect(content().string(containsString("disabled")));
    }

    @Test
    void createRedirectsToDetail() throws Exception {
        when(service.create(any(), eq("hi"), any())).thenReturn(scan(7));
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "hi"))
                .andExpect(redirectedUrl("/scans/7")).andExpect(flash().attributeExists("success"));
    }

    @Test
    void invalidImageReShowsForm() throws Exception {
        when(service.create(any(), any(), any())).thenThrow(new InvalidImageException("Ảnh không hợp lệ"));
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "keep me"))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attribute("error", "Ảnh không hợp lệ"))
                .andExpect(model().attribute("note", "keep me"));
    }

    @Test
    void tooLongNoteReShowsForm() throws Exception {
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "x".repeat(1001)))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void maxUploadSizeShowsForm() throws Exception {
        when(service.create(any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(5));
        mvc.perform(multipart("/scans").with(csrf()).file(file))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attribute("error", "Ảnh tối đa 5 MB"));
    }

    @Test
    void detailUnknownIs404() throws Exception {
        when(service.get(eq(99L), any())).thenThrow(new NotFoundException());
        mvc.perform(get("/scans/99")).andExpect(status().isNotFound());
    }

    @Test
    void detailShows() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(scan(1));
        mvc.perform(get("/scans/1")).andExpect(status().isOk()).andExpect(view().name("scans/detail"));
    }

    @Test
    void deleteRedirects() throws Exception {
        mvc.perform(post("/scans/1/delete").with(csrf())).andExpect(redirectedUrl("/scans"))
                .andExpect(flash().attributeExists("success"));
        verify(service).delete(eq(1L), any());
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(new NotFoundException()).when(service).delete(eq(5L), any());
        mvc.perform(post("/scans/5/delete").with(csrf())).andExpect(status().isNotFound());
    }
}
