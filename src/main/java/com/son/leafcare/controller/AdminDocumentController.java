package com.son.leafcare.controller;

import com.son.leafcare.service.RagDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Nạp tài liệu cho chatbot RAG. ADMIN-only: SecurityConfig chặn /admin/**. */
@Controller
public class AdminDocumentController {

    private static final Logger log = LoggerFactory.getLogger(AdminDocumentController.class);

    private final RagDocumentService documents;

    public AdminDocumentController(RagDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/admin/documents")
    public String form() {
        return "admin/documents";
    }

    @PostMapping("/admin/documents")
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        String name = file.getOriginalFilename();
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Bạn chưa chọn file, hoặc file rỗng.");
            return "redirect:/admin/documents";
        }
        try {
            int chunks = documents.ingest(file.getResource(), name);
            if (chunks == 0) {
                ra.addFlashAttribute("error", "Không đọc được chữ trong “" + name + "”. PDF scan hoặc ảnh thì chưa nạp được.");
            } else {
                ra.addFlashAttribute("success", "Đã nạp “" + name + "”: " + chunks + " đoạn.");
            }
        } catch (Exception e) { // Tika không đọc được định dạng, Gemini lỗi/hết quota, DB lỗi...
            log.warn("RAG ingest failed for {}", name, e);
            ra.addFlashAttribute("error", "Không nạp được “" + name + "”. Kiểm tra định dạng file hoặc thử lại sau.");
        }
        return "redirect:/admin/documents";
    }
}
