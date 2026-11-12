package com.son.leafcare.disease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.common.NotFoundException;
import com.son.leafcare.config.SecurityConfig;
import com.son.leafcare.plant.Plant;
import com.son.leafcare.plant.PlantService;
import com.son.leafcare.user.AppUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiseaseController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class DiseaseControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppUserService users;

    @MockitoBean
    DiseaseService service;

    @MockitoBean
    PlantService plantService;

    Plant plant(Long id, String name) {
        Plant p = new Plant();
        p.setId(id);
        p.setName(name);
        return p;
    }

    Disease disease(Long id, String name, Plant plant) {
        Disease d = new Disease();
        d.setId(id);
        d.setName(name);
        d.setPlant(plant);
        return d;
    }

    @Test
    void listShowsPlantNameOrDash() throws Exception {
        when(service.search("", 0)).thenReturn(new PageImpl<>(List.of(
                disease(1L, "Đốm lá", plant(1L, "Lan hồ điệp")), disease(2L, "Thối rễ", null)),
                PageRequest.of(0, 10), 2));
        mvc.perform(get("/diseases"))
                .andExpect(status().isOk())
                .andExpect(view().name("diseases/list"))
                .andExpect(content().string(containsString("Đốm lá")))
                .andExpect(content().string(containsString("Lan hồ điệp")))
                .andExpect(content().string(containsString("—")));
    }

    @Test
    void listEmptyShowsEmptyState() throws Exception {
        when(service.search("", 0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/diseases")).andExpect(content().string(containsString("Chưa có bệnh nào")));
    }

    @Test
    void listEmptySearchShowsNoResults() throws Exception {
        when(service.search("zzz", 0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/diseases").param("q", "zzz"))
                .andExpect(content().string(containsString("Không tìm thấy bệnh nào")));
    }

    @Test
    void newFormListsPlants() throws Exception {
        when(plantService.findAll()).thenReturn(List.of(plant(1L, "Lan hồ điệp")));
        mvc.perform(get("/diseases/new")).andExpect(status().isOk())
                .andExpect(view().name("diseases/form"))
                .andExpect(content().string(containsString("— Không chọn —")))
                .andExpect(content().string(containsString("Lan hồ điệp")));
    }

    @Test
    void editFormPreselectsPlant() throws Exception {
        when(plantService.findAll()).thenReturn(List.of(plant(1L, "Lan"), plant(2L, "Cúc")));
        when(service.get(1L)).thenReturn(disease(1L, "Đốm lá", plant(2L, "Cúc")));
        mvc.perform(get("/diseases/1/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2\" selected")));
    }

    @Test
    void editUnknownIs404() throws Exception {
        when(service.get(99L)).thenThrow(new NotFoundException());
        mvc.perform(get("/diseases/99/edit")).andExpect(status().isNotFound());
    }

    @Test
    void createBlankNameShowsFieldErrorAndStillListsPlants() throws Exception {
        when(plantService.findAll()).thenReturn(List.of(plant(1L, "Lan hồ điệp")));
        mvc.perform(post("/diseases").with(csrf()).param("name", "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("diseases/form"))
                .andExpect(model().attributeHasFieldErrors("disease", "name"))
                .andExpect(content().string(containsString("Lan hồ điệp")));
    }

    @Test
    void createWithEmptyPlantSavesNullPlant() throws Exception {
        mvc.perform(post("/diseases").with(csrf()).param("name", "Đốm lá").param("plant.id", ""))
                .andExpect(redirectedUrl("/diseases"))
                .andExpect(flash().attributeExists("success"));
        ArgumentCaptor<Disease> cap = ArgumentCaptor.forClass(Disease.class);
        verify(service).save(cap.capture());
        // Binding yields an empty Plant (null id) here; the service turns it into null (see DiseaseServiceDbTest).
        assertThat(cap.getValue().getPlant() == null || cap.getValue().getPlant().getId() == null).isTrue();
    }

    @Test
    void createWithPlantPassesItsId() throws Exception {
        mvc.perform(post("/diseases").with(csrf()).param("name", "Đốm lá").param("plant.id", "3"))
                .andExpect(redirectedUrl("/diseases"));
        ArgumentCaptor<Disease> cap = ArgumentCaptor.forClass(Disease.class);
        verify(service).save(cap.capture());
        assertThat(cap.getValue().getPlant().getId()).isEqualTo(3L);
    }

    @Test
    void updateValidRedirects() throws Exception {
        when(service.get(1L)).thenReturn(disease(1L, "Đốm lá", null));
        when(service.save(any())).thenReturn(disease(1L, "Đốm lá 2", null));
        mvc.perform(post("/diseases/1").with(csrf()).param("name", "Đốm lá 2"))
                .andExpect(redirectedUrl("/diseases"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void updateUnknownIs404() throws Exception {
        when(service.get(9L)).thenThrow(new NotFoundException());
        mvc.perform(post("/diseases/9").with(csrf()).param("name", "X")).andExpect(status().isNotFound());
    }

    @Test
    void deleteOkFlashesSuccess() throws Exception {
        mvc.perform(post("/diseases/1/delete").with(csrf()))
                .andExpect(redirectedUrl("/diseases"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(new NotFoundException()).when(service).delete(5L);
        mvc.perform(post("/diseases/5/delete").with(csrf())).andExpect(status().isNotFound());
    }
}
