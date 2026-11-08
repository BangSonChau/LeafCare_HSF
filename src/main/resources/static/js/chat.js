(function () {
    "use strict";

    const root = document.getElementById("chat-root");
    if (!root) return;

    const convId = root.getAttribute("data-conversation-id");
    const messagesBox = document.getElementById("chat-messages");
    const emptyState = document.getElementById("chat-empty-state");
    const form = document.getElementById("chat-form");
    const input = document.getElementById("chat-input");
    const plantSelect = document.getElementById("chat-plant-select");
    const imageInput = document.getElementById("chat-image-input");
    const previewBox = document.getElementById("image-preview-box");
    const previewThumb = document.getElementById("image-preview-thumb");
    const previewName = document.getElementById("image-preview-name");
    const clearImageBtn = document.getElementById("btn-clear-image");
    const sendBtn = document.getElementById("btn-send-chat");
    const sendBtnLabel = document.getElementById("send-btn-label");
    const charCounter = document.getElementById("char-counter");
    const errorBanner = document.getElementById("chat-error-banner");
    const errorText = document.getElementById("chat-error-text");
    const retryBtn = document.getElementById("btn-retry-last");
    const newChatBtn = document.getElementById("btn-new-chat");
    const deleteConvBtn = document.getElementById("btn-delete-conv");
    const activeConvTitle = document.getElementById("active-conv-title");

    let isSending = false;
    let lastFailedPayload = null;

    function scrollToBottom() {
        if (messagesBox) {
            messagesBox.scrollTop = messagesBox.scrollHeight;
        }
    }

    function updateCharCounter() {
        if (charCounter && input) {
            charCounter.textContent = input.value.length + " / 2000";
        }
    }

    function showError(msg) {
        if (!errorBanner || !errorText) return;
        errorText.textContent = msg || "Không thể xử lý yêu cầu lúc này.";
        errorBanner.classList.remove("hidden");
        errorBanner.classList.add("flex");
    }

    function hideError() {
        if (!errorBanner) return;
        errorBanner.classList.add("hidden");
        errorBanner.classList.remove("flex");
    }

    function clearSelectedImage() {
        if (imageInput) imageInput.value = "";
        if (previewBox) {
            previewBox.classList.add("hidden");
            previewBox.classList.remove("flex");
        }
        if (previewThumb) previewThumb.src = "";
    }

    if (imageInput) {
        imageInput.addEventListener("change", function () {
            const file = imageInput.files && imageInput.files[0];
            if (!file) {
                clearSelectedImage();
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                showError("Ảnh tối đa 5 MB. Vui lòng chọn ảnh nhỏ hơn.");
                clearSelectedImage();
                return;
            }
            hideError();
            if (previewName) previewName.textContent = file.name;
            if (previewThumb) {
                const reader = new FileReader();
                reader.onload = function (e) {
                    previewThumb.src = e.target.result;
                };
                reader.readAsDataURL(file);
            }
            if (previewBox) {
                previewBox.classList.remove("hidden");
                previewBox.classList.add("flex");
            }
        });
    }

    if (clearImageBtn) {
        clearImageBtn.addEventListener("click", clearSelectedImage);
    }

    if (input) {
        input.addEventListener("input", updateCharCounter);
        input.addEventListener("keydown", function (e) {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                form.requestSubmit();
            }
        });
        updateCharCounter();
    }

    function createMessageElement(msg) {
        const isUser = msg.role === "USER";
        const wrapper = document.createElement("div");
        wrapper.className = "chat-msg-item rounded-xl border p-4 " +
            (isUser ? "ml-auto max-w-2xl border-primary/30 bg-base-200" : "mr-auto w-full border-base-300 bg-base-100");

        const header = document.createElement("div");
        header.className = "flex items-center justify-between gap-2 text-xs text-base-content/70";

        const author = document.createElement("span");
        author.className = "font-bold";
        author.textContent = isUser ? "Bạn" : "Trợ lý LeafCare";
        header.appendChild(author);

        const rightMeta = document.createElement("div");
        rightMeta.className = "flex items-center gap-2";
        if (!isUser && msg.statusLabel) {
            const badge = document.createElement("span");
            badge.className = "badge badge-sm badge-outline";
            badge.textContent = msg.statusLabel;
            rightMeta.appendChild(badge);
        }
        const timeSpan = document.createElement("span");
        timeSpan.className = "tabular";
        timeSpan.textContent = "Vừa xong";
        rightMeta.appendChild(timeSpan);
        header.appendChild(rightMeta);
        wrapper.appendChild(header);

        if (msg.imageUrl) {
            const imgWrap = document.createElement("div");
            imgWrap.className = "mt-3";
            const img = document.createElement("img");
            img.src = msg.imageUrl;
            img.alt = "Ảnh lá đính kèm";
            img.className = "max-h-56 rounded-xl border border-base-300 object-contain";
            imgWrap.appendChild(img);
            wrapper.appendChild(imgWrap);
        }

        const body = document.createElement("div");
        body.className = "mt-2 whitespace-pre-line break-words text-sm leading-relaxed";
        body.textContent = msg.content || "";
        wrapper.appendChild(body);

        if (Array.isArray(msg.citations) && msg.citations.length > 0) {
            const citeBox = document.createElement("div");
            citeBox.className = "mt-4 rounded-xl border border-base-300 bg-base-200 p-3";

            const citeHeading = document.createElement("p");
            citeHeading.className = "text-xs font-bold text-primary";
            citeHeading.textContent = "Tài liệu tham khảo đã kiểm duyệt:";
            citeBox.appendChild(citeHeading);

            const ul = document.createElement("ul");
            ul.className = "mt-2 space-y-2 text-xs";

            msg.citations.forEach(function (c) {
                const li = document.createElement("li");
                li.className = "rounded-lg border border-base-300 bg-base-100 p-2.5";

                const topRow = document.createElement("div");
                topRow.className = "flex flex-wrap items-center justify-between gap-1";

                const codeTitle = document.createElement("span");
                codeTitle.className = "font-bold text-base-content";
                codeTitle.textContent = "[" + (c.chunkCode || "") + "] " + (c.sectionTitle || "");
                topRow.appendChild(codeTitle);

                if (c.sourceUrl && (c.sourceUrl.startsWith("https://") || c.sourceUrl.startsWith("http://"))) {
                    const link = document.createElement("a");
                    link.href = c.sourceUrl;
                    link.target = "_blank";
                    link.rel = "noopener noreferrer";
                    link.className = "link text-primary font-semibold";
                    link.textContent = "Mở nguồn gốc ↗";
                    topRow.appendChild(link);
                }
                li.appendChild(topRow);

                const pub = document.createElement("p");
                pub.className = "mt-1 text-base-content/70";
                pub.textContent = (c.sourceTitle || "") + " — " + (c.publisher || "") +
                    (c.publishedDate ? " (" + c.publishedDate + ")" : "");
                li.appendChild(pub);

                if (c.snippet) {
                    const snip = document.createElement("p");
                    snip.className = "mt-1 line-clamp-2 italic text-base-content/80";
                    snip.textContent = c.snippet;
                    li.appendChild(snip);
                }
                ul.appendChild(li);
            });

            citeBox.appendChild(ul);
            wrapper.appendChild(citeBox);
        }

        if (Array.isArray(msg.followUpQuestions) && msg.followUpQuestions.length > 0) {
            const fqBox = document.createElement("div");
            fqBox.className = "mt-3";

            const fqTitle = document.createElement("p");
            fqTitle.className = "text-xs font-semibold text-base-content/70";
            fqTitle.textContent = "Gợi ý hỏi tiếp:";
            fqBox.appendChild(fqTitle);

            const chipWrap = document.createElement("div");
            chipWrap.className = "mt-1.5 flex flex-wrap gap-1.5";

            msg.followUpQuestions.forEach(function (fq) {
                const btn = document.createElement("button");
                btn.type = "button";
                btn.className = "followup-chip rounded-full border border-base-300 bg-base-200 px-3 py-1.5 text-left text-xs font-semibold hover:border-primary hover:bg-base-100";
                btn.setAttribute("data-prompt", fq);
                btn.textContent = fq;
                btn.addEventListener("click", function () {
                    fillAndFocusPrompt(fq);
                });
                chipWrap.appendChild(btn);
            });

            fqBox.appendChild(chipWrap);
            wrapper.appendChild(fqBox);
        }

        return wrapper;
    }

    function generateRequestId() {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID();
        }
        return "req-" + Date.now() + "-" + Math.random().toString(36).substring(2, 9);
    }

    async function submitTurn(payload) {
        if (isSending) return;
        isSending = true;
        hideError();

        if (sendBtn) sendBtn.disabled = true;
        if (sendBtnLabel) sendBtnLabel.textContent = "Đang đối chiếu tài liệu...";

        try {
            let response;
            if (payload.imageFile) {
                const fd = new FormData();
                fd.append("text", payload.text);
                if (payload.plantId) fd.append("plantId", payload.plantId);
                fd.append("clientRequestId", payload.clientRequestId);
                fd.append("image", payload.imageFile);
                response = await fetch("/api/chat/conversations/" + encodeURIComponent(convId) + "/messages", {
                    method: "POST",
                    body: fd
                });
            } else {
                response = await fetch("/api/chat/conversations/" + encodeURIComponent(convId) + "/messages", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        text: payload.text,
                        plantId: payload.plantId ? Number(payload.plantId) : null,
                        clientRequestId: payload.clientRequestId
                    })
                });
            }

            const data = await response.json();
            if (!response.ok) {
                lastFailedPayload = payload;
                showError(data.error || "Không thể gửi tin nhắn. Vui lòng thử lại.");
                return;
            }

            lastFailedPayload = null;
            if (emptyState) emptyState.remove();

            if (data.userMessage) {
                messagesBox.appendChild(createMessageElement(data.userMessage));
            }
            if (data.assistantMessage) {
                messagesBox.appendChild(createMessageElement(data.assistantMessage));
            }
            if (data.conversationTitle && activeConvTitle) {
                activeConvTitle.textContent = data.conversationTitle;
            }

            if (input) input.value = "";
            clearSelectedImage();
            updateCharCounter();
            scrollToBottom();
        } catch (err) {
            lastFailedPayload = payload;
            showError("Mất kết nối hoặc hết thời gian chờ. Bản nháp của bạn vẫn được giữ nguyên.");
        } finally {
            isSending = false;
            if (sendBtn) sendBtn.disabled = false;
            if (sendBtnLabel) sendBtnLabel.textContent = "Gửi câu hỏi";
        }
    }

    if (form) {
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            const text = input ? input.value.trim() : "";
            const file = imageInput && imageInput.files && imageInput.files[0] ? imageInput.files[0] : null;
            if (!text && !file) {
                showError("Vui lòng nhập mô tả triệu chứng hoặc đính kèm ảnh lá.");
                return;
            }
            const plantId = plantSelect && plantSelect.value ? plantSelect.value : null;
            submitTurn({
                text: text,
                plantId: plantId,
                imageFile: file,
                clientRequestId: generateRequestId()
            });
        });
    }

    if (retryBtn) {
        retryBtn.addEventListener("click", function () {
            if (lastFailedPayload) {
                submitTurn(lastFailedPayload);
            } else if (form) {
                form.requestSubmit();
            }
        });
    }

    function fillAndFocusPrompt(text) {
        if (!input) return;
        input.value = text;
        updateCharCounter();
        input.focus();
    }

    document.querySelectorAll(".quick-prompt-btn, .followup-chip").forEach(function (btn) {
        btn.addEventListener("click", function () {
            const p = btn.getAttribute("data-prompt");
            if (p) fillAndFocusPrompt(p);
        });
    });

    if (newChatBtn) {
        newChatBtn.addEventListener("click", async function () {
            const plantId = plantSelect && plantSelect.value ? Number(plantSelect.value) : null;
            try {
                const resp = await fetch("/api/chat/conversations", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ plantId: plantId })
                });
                if (resp.ok) {
                    const created = await resp.json();
                    window.location.href = "/chat?conversationId=" + encodeURIComponent(created.id);
                }
            } catch (e) {
                showError("Không thể tạo hội thoại mới.");
            }
        });
    }

    if (deleteConvBtn) {
        deleteConvBtn.addEventListener("click", async function () {
            if (!confirm("Xoá hội thoại này và toàn bộ lịch sử tin nhắn liên quan?")) {
                return;
            }
            try {
                const resp = await fetch("/api/chat/conversations/" + encodeURIComponent(convId), {
                    method: "DELETE"
                });
                if (resp.ok) {
                    window.location.href = "/chat";
                } else {
                    showError("Không thể xoá hội thoại.");
                }
            } catch (e) {
                showError("Lỗi kết nối khi xoá hội thoại.");
            }
        });
    }

    scrollToBottom();
})();

