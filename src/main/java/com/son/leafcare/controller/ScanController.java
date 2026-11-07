package com.son.leafcare.controller;

import com.son.leafcare.entity.ScanHistory;
import com.son.leafcare.exception.InvalidImageException;
import com.son.leafcare.service.ScanHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ScanController {

    private static final int NOTE_MAX = 1000;

    private final ScanHistoryService service;

    public ScanController(ScanHistoryService service) {
        this.service = service;
    }

    @GetMapping("/scans")
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", service.list(page));
        return "scans/list";
    }

    @GetMapping("/scans/new")
    public String newForm() {
        return "scans/form";
    }

    @PostMapping("/scans")
    public String create(@RequestParam(required = false) MultipartFile file,
                         @RequestParam(defaultValue = "") String note,
                         Model model, RedirectAttributes ra) {
        if (note.length() > NOTE_MAX) {
            return formError("Ghi chú tối đa 1000 ký tự", note, model);
        }
        try {
            ScanHistory s = service.create(file, note);
            ra.addFlashAttribute("success", "Đã lưu ảnh lá");
            return "redirect:/scans/" + s.getId();
        } catch (InvalidImageException e) {
            return formError(e.getMessage(), note, model);
        }
    }

    @GetMapping("/scans/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("scan", service.get(id));
        return "scans/detail";
    }

    @PostMapping("/scans/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("success", "Đã xoá lượt quét");
        return "redirect:/scans";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(Model model) {
        // @ModelAttribute advice does not run for exception handlers; the layout needs currentPath.
        model.addAttribute("currentPath", "/scans/new");
        return formError("Ảnh tối đa 5 MB", "", model);
    }

    private String formError(String error, String note, Model model) {
        model.addAttribute("error", error);
        model.addAttribute("note", note);
        return "scans/form";
    }
}
