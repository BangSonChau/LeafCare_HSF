package com.son.leafcare.disease;

import com.son.leafcare.plant.PlantService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DiseaseController {

    private final DiseaseService service;
    private final PlantService plantService;

    public DiseaseController(DiseaseService service, PlantService plantService) {
        this.service = service;
        this.plantService = plantService;
    }

    @GetMapping("/diseases")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", service.search(q, page));
        model.addAttribute("q", q.trim());
        return "diseases/list";
    }

    @GetMapping("/diseases/new")
    public String newForm(Model model) {
        model.addAttribute("disease", new Disease());
        return form(model);
    }

    @PostMapping("/diseases")
    public String create(@Valid @ModelAttribute("disease") Disease disease, BindingResult result,
                         Model model, RedirectAttributes ra) {
        disease.setId(null);
        return persist(disease, result, model, ra);
    }

    @GetMapping("/diseases/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("disease", service.get(id));
        return form(model);
    }

    @PostMapping("/diseases/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("disease") Disease disease,
                         BindingResult result, Model model, RedirectAttributes ra) {
        service.get(id); // 404 for unknown id
        disease.setId(id);
        return persist(disease, result, model, ra);
    }

    @PostMapping("/diseases/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("success", "Đã xoá bệnh");
        return "redirect:/diseases";
    }

    private String persist(Disease disease, BindingResult result, Model model, RedirectAttributes ra) {
        if (!result.hasErrors()) {
            service.save(disease);
            ra.addFlashAttribute("success", "Đã lưu bệnh");
            return "redirect:/diseases";
        }
        return form(model);
    }

    private String form(Model model) {
        model.addAttribute("plants", plantService.findAll());
        return "diseases/form";
    }
}
