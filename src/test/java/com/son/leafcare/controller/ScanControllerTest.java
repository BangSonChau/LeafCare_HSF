package com.son.leafcare.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.exception.InvalidImageException;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.service.ScanHistoryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    @Autowired
    MockMvc mvc;

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
        when(service.list(0)).thenReturn(new PageImpl<>(List.of(scan(1)), PageRequest.of(0, 10), 1));
        mvc.perform(get("/scans")).andExpect(status().isOk()).andExpect(view().name("scans/list"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void listEmpty() throws Exception {
        when(service.list(0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/scans")).andExpect(status().isOk());
    }

    @Test
    void newForm() throws Exception {
        mvc.perform(get("/scans/new")).andExpect(status().isOk()).andExpect(view().name("scans/form"));
    }

    @Test
    void createRedirectsToDetail() throws Exception {
        when(service.create(any(), eq("hi"))).thenReturn(scan(7));
        mvc.perform(multipart("/scans").file(file).param("note", "hi"))
                .andExpect(redirectedUrl("/scans/7")).andExpect(flash().attributeExists("success"));
    }

    @Test
    void invalidImageReShowsForm() throws Exception {
        when(service.create(any(), any())).thenThrow(new InvalidImageException("Ảnh không hợp lệ"));
        mvc.perform(multipart("/scans").file(file).param("note", "keep me"))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attribute("error", "Ảnh không hợp lệ"))
                .andExpect(model().attribute("note", "keep me"));
    }

    @Test
    void tooLongNoteReShowsForm() throws Exception {
        mvc.perform(multipart("/scans").file(file).param("note", "x".repeat(1001)))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void maxUploadSizeShowsForm() throws Exception {
        when(service.create(any(), any())).thenThrow(new MaxUploadSizeExceededException(5));
        mvc.perform(multipart("/scans").file(file))
                .andExpect(status().isOk()).andExpect(view().name("scans/form"))
                .andExpect(model().attribute("error", "Ảnh tối đa 5 MB"));
    }

    @Test
    void detailUnknownIs404() throws Exception {
        when(service.get(99L)).thenThrow(new NotFoundException());
        mvc.perform(get("/scans/99")).andExpect(status().isNotFound());
    }

    @Test
    void detailShows() throws Exception {
        when(service.get(1L)).thenReturn(scan(1));
        mvc.perform(get("/scans/1")).andExpect(status().isOk()).andExpect(view().name("scans/detail"));
    }

    @Test
    void deleteRedirects() throws Exception {
        mvc.perform(post("/scans/1/delete")).andExpect(redirectedUrl("/scans"))
                .andExpect(flash().attributeExists("success"));
        verify(service).delete(1L);
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(new NotFoundException()).when(service).delete(5L);
        mvc.perform(post("/scans/5/delete")).andExpect(status().isNotFound());
    }
}
