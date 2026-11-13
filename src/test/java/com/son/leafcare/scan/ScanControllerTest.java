package com.son.leafcare.scan;

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

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.disease.Disease;
import com.son.leafcare.plant.Plant;
import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
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
        when(service.create(any(), any(), any(), any())).thenThrow(new ScanLimitException());
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
        when(service.create(any(), eq("hi"), any(), any())).thenReturn(scan(7));
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "hi"))
                .andExpect(redirectedUrl("/scans/7")).andExpect(flash().attributeExists("success"));
    }

    @Test
    void invalidImageReShowsForm() throws Exception {
        when(service.create(any(), any(), any(), any())).thenThrow(new InvalidImageException("Ảnh không hợp lệ"));
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
        when(service.create(any(), any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(5));
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

    ScanHistory analysed(String label, double confidence, Disease d) {
        ScanHistory s = scan(1);
        s.setStatus(ScanStatus.DONE);
        s.setModelLabel(label);
        s.setConfidence(confidence);
        s.setDisease(d);
        s.setPlant(d == null ? null : d.getPlant());
        return s;
    }

    Disease septoria() {
        Plant p = new Plant();
        p.setName("Cà chua");
        Disease d = new Disease();
        d.setName("Bệnh đốm lá Septoria");
        d.setSymptoms("Đốm tròn nhỏ tâm xám");
        d.setTreatment("Ngắt bỏ lá bệnh");
        d.setPlant(p);
        return d;
    }

    @Test
    void pendingDetailShowsAnalyzeButtonAndNoDiagnosis() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(scan(1));
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("/scans/1/analyze")))
                .andExpect(content().string(containsString(">Phân tích</button>")))
                .andExpect(content().string(not(containsString("Độ tin cậy"))));
    }

    @Test
    void failedDetailOffersRetry() throws Exception {
        ScanHistory s = scan(1);
        s.setStatus(ScanStatus.FAILED);
        when(service.get(eq(1L), any())).thenReturn(s);
        mvc.perform(get("/scans/1")).andExpect(content().string(containsString(">Phân tích lại</button>")));
    }

    @Test
    void doneDetailShowsDiseaseConfidenceSymptomsAndTreatmentWithoutAnalyzeButton() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(analysed("Tomato___Septoria_leaf_spot", 0.97, septoria()));
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("Bệnh đốm lá Septoria")))
                .andExpect(content().string(containsString("Cà chua")))
                .andExpect(content().string(containsString("97%")))
                .andExpect(content().string(containsString("Đốm tròn nhỏ tâm xám")))
                .andExpect(content().string(containsString("Ngắt bỏ lá bệnh")))
                .andExpect(content().string(containsString("chỉ mang tính tham khảo")))
                .andExpect(content().string(not(containsString("chưa chắc chắn"))))
                .andExpect(content().string(not(containsString("/scans/1/analyze"))));
    }

    @Test
    void diseaseResultOffersToAskTheHandbook() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(analysed("Tomato___Septoria_leaf_spot", 0.97, septoria()));
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("href=\"/chat?q=C%C3%A1ch%20nh%E1%BA%ADn")))
                .andExpect(content().string(containsString("Hỏi cẩm nang về bệnh này")));
    }

    @Test
    void healthyOrPendingResultDoesNotOfferTheHandbook() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(analysed("Tomato___healthy", 1.0, null));
        mvc.perform(get("/scans/1")).andExpect(content().string(not(containsString("Hỏi cẩm nang"))));
        when(service.get(eq(1L), any())).thenReturn(scan(1));
        mvc.perform(get("/scans/1")).andExpect(content().string(not(containsString("Hỏi cẩm nang"))));
    }

    @Test
    void lowConfidenceShowsWarning() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(analysed("Tomato___Septoria_leaf_spot", 0.53, septoria()));
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("53%")))
                .andExpect(content().string(containsString("chưa chắc chắn")));
    }

    @Test
    void healthyNoLeafAndUnknownLabelsHaveTheirOwnMessage() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(analysed("Tomato___healthy", 1.0, null));
        mvc.perform(get("/scans/1")).andExpect(content().string(containsString("Lá khoẻ")));

        when(service.get(eq(1L), any())).thenReturn(analysed("Background_without_leaves", 1.0, null));
        mvc.perform(get("/scans/1")).andExpect(content().string(containsString("Không thấy lá trong ảnh")))
                .andExpect(content().string(not(containsString("chưa có trong danh mục"))));

        when(service.get(eq(1L), any())).thenReturn(analysed("Apple___Black_rot", 0.9, null));
        mvc.perform(get("/scans/1")).andExpect(content().string(containsString("chưa có trong danh mục")))
                .andExpect(content().string(containsString("Apple___Black_rot")));
    }

    @Test
    void analyzeDoneRedirectsWithSuccess() throws Exception {
        when(service.analyze(eq(1L), any())).thenReturn(analysed("Tomato___healthy", 1.0, null));
        mvc.perform(post("/scans/1/analyze").with(csrf()))
                .andExpect(redirectedUrl("/scans/1"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void analyzeFailedRedirectsWithError() throws Exception {
        ScanHistory s = scan(1);
        s.setStatus(ScanStatus.FAILED);
        when(service.analyze(eq(1L), any())).thenReturn(s);
        mvc.perform(post("/scans/1/analyze").with(csrf()))
                .andExpect(redirectedUrl("/scans/1"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void analyzeUnknownIs404() throws Exception {
        when(service.analyze(eq(9L), any())).thenThrow(new NotFoundException());
        mvc.perform(post("/scans/9/analyze").with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void analyzeWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/scans/1/analyze")).andExpect(status().isForbidden());
    }

    static Plant named(long id, String name) {
        Plant p = new Plant();
        p.setId(id);
        p.setName(name);
        return p;
    }

    @Test
    void newFormListsTheScannablePlantsAndLetsTheModelDecideByDefault() throws Exception {
        when(service.scannablePlants()).thenReturn(List.of(named(3, "Cà chua"), named(4, "Táo")));
        mvc.perform(get("/scans/new"))
                .andExpect(content().string(containsString("LeafCare nhận diện <span>2</span> loại cây")))
                .andExpect(content().string(containsString("Cà chua, Táo")))
                .andExpect(content().string(containsString("<option value=\"\">Để máy tự nhận</option>")))
                .andExpect(content().string(containsString("<option value=\"3\">Cà chua</option>")));
    }

    @Test
    void createPassesTheChosenPlantAndKeepsItWhenTheFormIsShownAgain() throws Exception {
        when(service.create(any(), eq("hi"), eq(3L), any())).thenReturn(scan(7));
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "hi").param("plantId", "3"))
                .andExpect(redirectedUrl("/scans/7"));
        verify(service).create(any(), eq("hi"), eq(3L), any());

        when(service.scannablePlants()).thenReturn(List.of(named(3, "Cà chua")));
        when(service.create(any(), eq("bad"), any(), any())).thenThrow(new InvalidImageException("Ảnh không hợp lệ"));
        mvc.perform(multipart("/scans").with(csrf()).file(file).param("note", "bad").param("plantId", "3"))
                .andExpect(content().string(containsString("<option value=\"3\" selected=\"selected\">Cà chua</option>")));
    }

    @Test
    void confidenceUnder80PercentShowsWarning() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(analysed("Tomato___Septoria_leaf_spot", 0.64, septoria()));
        mvc.perform(get("/scans/1")).andExpect(content().string(containsString("chưa chắc chắn")));
    }

    @Test
    void doneDetailShowsChosenPlantLooksLikeWarningAndTop3() throws Exception {
        ScanHistory s = analysed("Tomato___Septoria_leaf_spot", 0.9, septoria());
        s.setChosenPlant(named(3, "Cà chua"));
        s.setLooksLikePlant(named(5, "Nho"));
        when(service.get(eq(1L), any())).thenReturn(s);
        when(service.guesses(s)).thenReturn(List.of(
                new ScanHistoryService.Guess("Bệnh đốm lá Septoria", "Cà chua", 0.9),
                new ScanHistoryService.Guess("Lá khoẻ", "Cà chua", 0.07)));
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("Loại cây đã chọn")))
                .andExpect(content().string(containsString("trông giống lá <strong>Nho</strong> hơn")))
                .andExpect(content().string(containsString("Các khả năng máy đã xét")))
                .andExpect(content().string(containsString("2. Lá khoẻ")))
                .andExpect(content().string(containsString("7%")));
    }

    @Test
    void pendingDetailSaysTheModelDecidesWhenNoPlantWasChosen() throws Exception {
        when(service.get(eq(1L), any())).thenReturn(scan(1));
        mvc.perform(get("/scans/1"))
                .andExpect(content().string(containsString("Để máy tự nhận")))
                .andExpect(content().string(not(containsString("Các khả năng máy đã xét"))));
    }

    @Test
    void healthyLeafShowsItsPlant() throws Exception {
        ScanHistory s = analysed("Grape___healthy", 0.95, null);
        s.setPlant(named(5, "Nho"));
        when(service.get(eq(1L), any())).thenReturn(s);
        mvc.perform(get("/scans/1")).andExpect(content().string(containsString("Lá khoẻ")))
                .andExpect(content().string(containsString("Nho")));
    }
}
