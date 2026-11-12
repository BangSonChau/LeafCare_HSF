package com.son.leafcare.scan;

import com.son.leafcare.user.AppUser;
import com.son.leafcare.user.AppUserService;
import org.springframework.security.core.Authentication;
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
    private final AppUserService users;

    public ScanController(ScanHistoryService service, AppUserService users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/scans")
    public String list(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("page", service.list(users.current(auth), page));
        return "scans/list";
    }

    @GetMapping("/scans/new")
    public String newForm(Authentication auth, Model model) {
        model.addAttribute("remaining", service.remainingToday(users.current(auth)));
        return "scans/form";
    }

    @PostMapping("/scans")
    public String create(@RequestParam(required = false) MultipartFile file,
                         @RequestParam(defaultValue = "") String note,
                         Authentication auth, Model model, RedirectAttributes ra) {
        AppUser me = users.current(auth);
        model.addAttribute("remaining", service.remainingToday(me));
        if (note.length() > NOTE_MAX) {
            return formError("Ghi chú tối đa 1000 ký tự", note, model);
        }
        try {
            ScanHistory s = service.create(file, note, me);
            ra.addFlashAttribute("success", "Đã lưu ảnh lá");
            return "redirect:/scans/" + s.getId();
        } catch (InvalidImageException e) {
            return formError(e.getMessage(), note, model);
        } catch (ScanLimitException e) {
            model.addAttribute("remaining", 0); // the form shows the limit box and upgrade button
            model.addAttribute("note", note);
            return "scans/form";
        }
    }

    @GetMapping("/scans/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        model.addAttribute("scan", service.get(id, users.current(auth)));
        return "scans/detail";
    }

    @PostMapping("/scans/{id}/analyze")
    public String analyze(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        ScanHistory s = service.analyze(id, users.current(auth));
        if (s.getStatus() == ScanStatus.DONE) {
            ra.addFlashAttribute("success", "Đã phân tích ảnh lá");
        } else {
            ra.addFlashAttribute("error", "Chưa phân tích được: máy phân tích đang bận hoặc đang khởi động. Thử lại sau ít phút.");
        }
        return "redirect:/scans/" + id;
    }

    @PostMapping("/scans/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        service.delete(id, users.current(auth));
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
