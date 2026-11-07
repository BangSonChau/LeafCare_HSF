package com.son.leafcare.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.son.leafcare.entity.Plant;
import com.son.leafcare.exception.DuplicateNameException;
import com.son.leafcare.exception.InUseException;
import com.son.leafcare.exception.NotFoundException;
import com.son.leafcare.service.PlantService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlantController.class)
class PlantControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlantService service;

    Plant plant(Long id, String name) {
        Plant p = new Plant();
        p.setId(id);
        p.setName(name);
        return p;
    }

    @Test
    void listRendersRows() throws Exception {
        when(service.search("lan", 0)).thenReturn(
                new PageImpl<>(List.of(plant(1L, "Lan hồ điệp")), PageRequest.of(0, 10), 1));
        mvc.perform(get("/plants").param("q", "lan"))
                .andExpect(status().isOk())
                .andExpect(view().name("plants/list"))
                .andExpect(content().string(containsString("Lan hồ điệp")));
    }

    @Test
    void listEmptyShowsEmptyState() throws Exception {
        when(service.search("", 0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/plants")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có cây nào")));
    }

    @Test
    void listEmptySearchShowsNoResults() throws Exception {
        when(service.search("zzz", 0)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        mvc.perform(get("/plants").param("q", "zzz")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy cây nào")));
    }

    @Test
    void newFormOk() throws Exception {
        mvc.perform(get("/plants/new")).andExpect(status().isOk()).andExpect(view().name("plants/form"));
    }

    @Test
    void editFormOk() throws Exception {
        when(service.get(1L)).thenReturn(plant(1L, "Lan"));
        mvc.perform(get("/plants/1/edit")).andExpect(status().isOk()).andExpect(view().name("plants/form"));
    }

    @Test
    void createValidRedirectsWithSuccess() throws Exception {
        when(service.save(any())).thenReturn(plant(1L, "Lan"));
        mvc.perform(post("/plants").param("name", "Lan"))
                .andExpect(redirectedUrl("/plants"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void createBlankNameShowsFieldError() throws Exception {
        mvc.perform(post("/plants").param("name", "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("plants/form"))
                .andExpect(model().attributeHasFieldErrors("plant", "name"));
    }

    @Test
    void createDuplicateShowsFieldError() throws Exception {
        when(service.save(any())).thenThrow(new DuplicateNameException());
        mvc.perform(post("/plants").param("name", "Lan"))
                .andExpect(status().isOk())
                .andExpect(view().name("plants/form"))
                .andExpect(model().attributeHasFieldErrorCode("plant", "name", "duplicate"))
                .andExpect(content().string(containsString("Tên đã tồn tại")));
    }

    @Test
    void editUnknownIs404() throws Exception {
        when(service.get(99L)).thenThrow(new NotFoundException());
        mvc.perform(get("/plants/99/edit")).andExpect(status().isNotFound());
    }

    @Test
    void updateValidRedirects() throws Exception {
        when(service.get(1L)).thenReturn(plant(1L, "Lan"));
        when(service.save(any())).thenReturn(plant(1L, "Lan 2"));
        mvc.perform(post("/plants/1").param("name", "Lan 2"))
                .andExpect(redirectedUrl("/plants"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void updateDuplicateShowsFieldError() throws Exception {
        when(service.get(1L)).thenReturn(plant(1L, "Lan"));
        when(service.save(any())).thenThrow(new DuplicateNameException());
        mvc.perform(post("/plants/1").param("name", "Cúc"))
                .andExpect(view().name("plants/form"))
                .andExpect(model().attributeHasFieldErrorCode("plant", "name", "duplicate"));
    }

    @Test
    void deleteOkFlashesSuccess() throws Exception {
        mvc.perform(post("/plants/1/delete"))
                .andExpect(redirectedUrl("/plants"))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void deleteInUseFlashesError() throws Exception {
        doThrow(new InUseException()).when(service).delete(1L);
        mvc.perform(post("/plants/1/delete"))
                .andExpect(redirectedUrl("/plants"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(new NotFoundException()).when(service).delete(5L);
        mvc.perform(post("/plants/5/delete")).andExpect(status().isNotFound());
    }
}
