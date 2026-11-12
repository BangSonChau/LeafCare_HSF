package com.son.leafcare.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.son.leafcare.rag.RagIngestJobs;
import com.son.leafcare.user.AppUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@WebMvcTest(AdminDocumentController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "a@x.com", roles = {"USER", "ADMIN"})
class AdminDocumentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RagIngestJobs jobs;
    @MockitoBean AppUserService users;

    private static MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/plain", body.getBytes());
    }

    @Test
    void formShowsUploadFieldLimitAndAdminSidebarWithActiveItem() throws Exception {
        when(jobs.recent()).thenReturn(List.of());

        mvc.perform(get("/admin/documents")).andExpect(status().isOk()).andExpect(view().name("admin/documents"))
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("name=\"file\"")))
                .andExpect(content().string(containsString("tối đa 20 MB")))
                .andExpect(content().string(containsString("Chưa có tài liệu nào")))
                .andExpect(content().string(not(containsString("http-equiv=\"refresh\""))))
                // the only aria-current link is the documents item
                .andExpect(content().string(matchesPattern("(?s).*<a [^>]*href=\"/admin/documents\"[^>]*aria-current=\"page\".*")))
                .andExpect(content().string(not(matchesPattern("(?s).*aria-current=\"page\".*aria-current=\"page\".*"))));
    }

    @Test
    void runningJobShowsProgressAndAutoRefresh() throws Exception {
        var j = new RagIngestJobs.Job("cam-nang.pdf"); // READING = running
        when(jobs.recent()).thenReturn(List.of(j));

        mvc.perform(get("/admin/documents")).andExpect(status().isOk())
                .andExpect(content().string(containsString("http-equiv=\"refresh\"")))
                .andExpect(content().string(containsString("cam-nang.pdf")))
                .andExpect(content().string(containsString("Đang đọc file")))
                .andExpect(content().string(containsString("Đã chạy 0 phút")));
    }

    @Test
    void uploadStartsBackgroundJobWithFileNameAndBytes() throws Exception {
        mvc.perform(multipart("/admin/documents").file(file("ca-chua.txt", "abc")).with(csrf()))
                .andExpect(redirectedUrl("/admin/documents"))
                .andExpect(flash().attributeExists("success"));

        verify(jobs).start(any(), eq("ca-chua.txt"));
    }

    @Test
    void emptyFileIsRejectedWithoutStartingAJob() throws Exception {
        mvc.perform(multipart("/admin/documents").file(file("rong.txt", "")).with(csrf()))
                .andExpect(redirectedUrl("/admin/documents"))
                .andExpect(flash().attributeExists("error"));
        verifyNoInteractions(jobs);
    }

    @Test
    void tooLargeUploadRedirectsWithFriendlyError() {
        var ra = new RedirectAttributesModelMap();

        String view = new AdminDocumentController(mock(RagIngestJobs.class)).tooLarge(ra);

        assertThat(view).isEqualTo("redirect:/admin/documents");
        assertThat(ra.getFlashAttributes().get("error")).isEqualTo("File quá lớn, tối đa 20 MB.");
    }

    @Test
    @WithMockUser(roles = "USER")
    void plainUserIsForbidden() throws Exception {
        mvc.perform(get("/admin/documents")).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/documents").file(file("a.txt", "abc")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }

    @Test
    void uploadWithoutCsrfIsRejected() throws Exception {
        mvc.perform(multipart("/admin/documents").file(file("a.txt", "abc"))).andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }
}
