package com.son.leafcare.controller;

import com.son.leafcare.service.RagIngestJobs;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Nạp tài liệu cho chatbot RAG. ADMIN-only: SecurityConfig chặn /admin/**. */
@Controller
public class AdminDocumentController {

    private static final Logger log = LoggerFactory.getLogger(AdminDocumentController.class);

    private final RagIngestJobs jobs;

    public AdminDocumentController(RagIngestJobs jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/admin/documents")
    public String form(Model model) {
        var recent = jobs.recent();
        model.addAttribute("jobs", recent);
        model.addAttribute("running", recent.stream().anyMatch(RagIngestJobs.Job::isRunning));
        return "admin/documents";
    }

    @PostMapping("/admin/documents")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        String name = file.getOriginalFilename() == null ? "tai-lieu" : file.getOriginalFilename();
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Bạn chưa chọn file, hoặc file rỗng.");
            return "redirect:/admin/documents";
        }
        try {
            // Phải chép vào bộ nhớ: file tạm của request bị xoá ngay khi request kết thúc, còn việc nạp chạy ở nền.
            jobs.start(new ByteArrayResource(file.getBytes()), name);
            ra.addFlashAttribute("success", "Đã nhận “" + name + "”. Đang đọc và nạp, theo dõi tiến độ bên dưới.");
        } catch (IOException e) {
            log.warn("Cannot read upload {}", name, e);
            ra.addFlashAttribute("error", "Không đọc được file vừa tải lên, bạn thử lại nhé.");
        }
        return "redirect:/admin/documents";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "File quá lớn, tối đa 20 MB.");
        return "redirect:/admin/documents";
    }
}
