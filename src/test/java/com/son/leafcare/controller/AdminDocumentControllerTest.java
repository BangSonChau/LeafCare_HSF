package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.service.AppUserService;
import com.son.leafcare.service.RagDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminDocumentController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "a@x.com", roles = {"USER", "ADMIN"})
class AdminDocumentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RagDocumentService documents;
    @MockitoBean AppUserService users;

    private static MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/plain", body.getBytes());
    }

    @Test
    void formShowsUploadFieldAndAdminSidebarWithActiveItem() throws Exception {
        mvc.perform(get("/admin/documents")).andExpect(status().isOk()).andExpect(view().name("admin/documents"))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"file\"")))
                // the only aria-current link is the documents item
                .andExpect(content().string(matchesPattern("(?s).*<a [^>]*href=\"/admin/documents\"[^>]*aria-current=\"page\".*")))
                .andExpect(content().string(not(matchesPattern("(?s).*aria-current=\"page\".*aria-current=\"page\".*"))));
    }

    @Test
    void successfulUploadFlashesChunkCount() throws Exception {
        when(documents.ingest(any(), eq("ca-chua.txt"))).thenReturn(3);

        mvc.perform(multipart("/admin/documents").file(file("ca-chua.txt", "abc")).with(csrf()))
                .andExpect(redirectedUrl("/admin/documents"))
                .andExpect(flash().attribute("success", "Đã nạp “ca-chua.txt”: 3 đoạn."));
    }

    @Test
    void fileWithoutTextShowsError() throws Exception {
        when(documents.ingest(any(), eq("scan.pdf"))).thenReturn(0);

        mvc.perform(multipart("/admin/documents").file(file("scan.pdf", "x")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void emptyFileIsRejectedWithoutIngest() throws Exception {
        mvc.perform(multipart("/admin/documents").file(file("rong.txt", "")).with(csrf()))
                .andExpect(redirectedUrl("/admin/documents"))
                .andExpect(flash().attributeExists("error"));
        verifyNoInteractions(documents);
    }

    @Test
    void ingestFailureShowsFriendlyError() throws Exception {
        when(documents.ingest(any(), any())).thenThrow(new RuntimeException("quota"));

        mvc.perform(multipart("/admin/documents").file(file("a.txt", "abc")).with(csrf()))
                .andExpect(redirectedUrl("/admin/documents"))
                .andExpect(flash().attribute("error", "Không nạp được “a.txt”. Kiểm tra định dạng file hoặc thử lại sau."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void plainUserIsForbidden() throws Exception {
        mvc.perform(get("/admin/documents")).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/documents").file(file("a.txt", "abc")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documents);
    }

    @Test
    void uploadWithoutCsrfIsRejected() throws Exception {
        mvc.perform(multipart("/admin/documents").file(file("a.txt", "abc"))).andExpect(status().isForbidden());
        verifyNoInteractions(documents);
    }
}
