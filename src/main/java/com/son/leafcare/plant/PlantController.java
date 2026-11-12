package com.son.leafcare.plant;

import com.son.leafcare.common.DuplicateNameException;
import com.son.leafcare.common.InUseException;
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
public class PlantController {

    private final PlantService service;

    public PlantController(PlantService service) {
        this.service = service;
    }

    @GetMapping("/plants")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", service.search(q, page));
        model.addAttribute("q", q.trim());
        return "plants/list";
    }

    @GetMapping("/plants/new")
    public String newForm(Model model) {
        model.addAttribute("plant", new Plant());
        return "plants/form";
    }

    @PostMapping("/plants")
    public String create(@Valid @ModelAttribute("plant") Plant plant, BindingResult result, RedirectAttributes ra) {
        plant.setId(null);
        return persist(plant, result, ra);
    }

    @GetMapping("/plants/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("plant", service.get(id));
        return "plants/form";
    }

    @PostMapping("/plants/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("plant") Plant plant,
                         BindingResult result, RedirectAttributes ra) {
        service.get(id); // 404 for unknown id
        plant.setId(id);
        return persist(plant, result, ra);
    }

    @PostMapping("/plants/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("success", "Đã xoá cây");
        } catch (InUseException e) {
            ra.addFlashAttribute("error", "Không thể xoá: cây này đang được dùng bởi bệnh hoặc lần quét");
        }
        return "redirect:/plants";
    }

    private String persist(Plant plant, BindingResult result, RedirectAttributes ra) {
        if (!result.hasErrors()) {
            try {
                service.save(plant);
                ra.addFlashAttribute("success", "Đã lưu cây");
                return "redirect:/plants";
            } catch (DuplicateNameException e) {
                result.rejectValue("name", "duplicate", "Tên đã tồn tại");
            }
        }
        return "plants/form";
    }
}
