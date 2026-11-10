(function () {
    "use strict";

    const root = document.getElementById("chat-root");
    if (!root) return;

    let convId = root.getAttribute("data-conversation-id");
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
    const newChatBtn = document.getElementById("btn-new-chat");
    const deleteConvBtn = document.getElementById("btn-delete-conv");
    const errorBanner = document.getElementById("chat-error-banner");
    const errorText = document.getElementById("chat-error-text");
    const retryBtn = document.getElementById("btn-retry-last");
    const charCounter = document.getElementById("char-counter");
    const activeConvTitle = document.getElementById("active-conv-title");

    // Right-hand Clinical Inspector elements
    const inspectorCard = document.getElementById("inspector-citations-card");
    const inspectorCount = document.getElementById("inspector-citation-count");
    const inspectorEmpty = document.getElementById("inspector-citations-empty");
    const inspectorList = document.getElementById("inspector-citations-list");
    const inspectorImageBox = document.getElementById("inspector-latest-image-box");
    const inspectorImage = document.getElementById("inspector-latest-image");

    let selectedFile = null;
    let lastFailedPayload = null;
    let isSending = false;

    function scrollToBottom() {
        if (messagesBox) {
            messagesBox.scrollTop = messagesBox.scrollHeight;
        }
    }

    function updateCharCounter() {
        if (!input || !charCounter) return;
        const len = input.value ? input.value.length : 0;
        charCounter.textContent = len + " / 2000";
    }

    function showError(msg) {
        if (!errorBanner || !errorText) return;
        errorText.textContent = msg || "Không thể kết nối tới máy chủ. Vui lòng thử lại.";
        errorBanner.classList.remove("hidden");
        errorBanner.classList.add("flex");
    }

    function hideError() {
        if (!errorBanner) return;
        errorBanner.classList.add("hidden");
        errorBanner.classList.remove("flex");
    }

    function clearSelectedImage() {
        selectedFile = null;
        if (imageInput) imageInput.value = "";
        if (previewThumb) previewThumb.src = "";
        if (previewBox) {
            previewBox.classList.add("hidden");
            previewBox.classList.remove("flex");
        }
    }

    function escapeHtml(str) {
        return String(str || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function stripFollowUpBlock(raw) {
        if (!raw) return "";
        const idx = raw.indexOf("[GỢI Ý HỎI TIẾP]");
        return idx >= 0 ? raw.substring(0, idx).trim() : raw;
    }

    function formatInlineMarkdown(escapedLine) {
        return escapedLine
            .replace(/\*\*(.+?)\*\*/g, '<strong class="font-bold">$1</strong>')
            .replace(/\*(?!\s)(.+?)(?<!\s)\*/g, '<em class="italic">$1</em>')
            .replace(/\[([A-Z0-9_-]{4,})\]/g, '<span class="inline-flex items-center rounded bg-primary/15 px-1.5 py-0.5 text-xs font-bold text-primary">[$1]</span>');
    }

    function renderMarkdownSafe(rawText) {
        const cleanRaw = stripFollowUpBlock(rawText);
        if (!cleanRaw) return "";

        const lines = cleanRaw.split(/\r?\n/);
        const htmlParts = [];

        for (let i = 0; i < lines.length; i++) {
            const rawLine = lines[i];
            const trimmed = rawLine.trim();

            if (!trimmed) {
                htmlParts.push('<div class="h-2"></div>');
                continue;
            }

            if (/^---+$/.test(trimmed)) {
                htmlParts.push('<hr class="my-2.5 border-base-300">');
                continue;
            }

            const h3Match = trimmed.match(/^###\s+(.+)$/);
            if (h3Match) {
                htmlParts.push('<h4 class="mt-3 mb-1 text-sm font-bold text-primary">' + formatInlineMarkdown(escapeHtml(h3Match[1])) + "</h4>");
                continue;
            }

            const h2Match = trimmed.match(/^##\s+(.+)$/);
            if (h2Match) {
                htmlParts.push('<h3 class="mt-3 mb-1 text-base font-bold text-primary">' + formatInlineMarkdown(escapeHtml(h2Match[1])) + "</h3>");
                continue;
            }

            const bulletMatch = trimmed.match(/^[-*•+]\s+(.+)$/);
            if (bulletMatch) {
                htmlParts.push(
                    '<div class="my-1 flex items-start gap-2 pl-1">' +
                        '<span class="mt-0.5 font-bold text-primary">•</span>' +
                        '<span class="flex-1">' + formatInlineMarkdown(escapeHtml(bulletMatch[1])) + "</span>" +
                    "</div>"
                );
                continue;
            }

            const numMatch = trimmed.match(/^(\d+)\.\s+(.+)$/);
            if (numMatch) {
                htmlParts.push(
                    '<div class="my-1 flex items-start gap-2 pl-1">' +
                        '<span class="font-bold text-primary">' + escapeHtml(numMatch[1]) + ".</span>" +
                        '<span class="flex-1">' + formatInlineMarkdown(escapeHtml(numMatch[2])) + "</span>" +
                    "</div>"
                );
                continue;
            }

            htmlParts.push('<p class="my-0.5">' + formatInlineMarkdown(escapeHtml(rawLine)) + "</p>");
        }

        return htmlParts.join("");
    }

    function updateInspectorImage(imageUrl) {
        if (!inspectorImageBox || !inspectorImage || !imageUrl) return;
        inspectorImage.src = imageUrl;
        inspectorImageBox.classList.remove("hidden");
    }

    function updateInspectorCitations(citations, highlightCard) {
        if (!inspectorList || !inspectorEmpty || !inspectorCount) return;

        inspectorList.innerHTML = "";
        const validList = Array.isArray(citations) ? citations : [];
        inspectorCount.textContent = validList.length + " nguồn";

        if (validList.length === 0) {
            inspectorList.classList.add("hidden");
            inspectorEmpty.classList.remove("hidden");
            return;
        }

        inspectorEmpty.classList.add("hidden");
        inspectorList.classList.remove("hidden");

        validList.forEach(function (c) {
            const li = document.createElement("li");
            li.className = "rounded-xl border border-base-300 bg-base-200/50 p-3 transition hover:border-primary/50 hover:bg-base-100";

            const topRow = document.createElement("div");
            topRow.className = "flex flex-wrap items-start justify-between gap-1.5";

            const codeBadge = document.createElement("span");
            codeBadge.className = "inline-flex items-center rounded-md bg-primary/15 px-2 py-0.5 text-[11px] font-bold text-primary";
            codeBadge.textContent = "[" + c.chunkCode + "]";
            topRow.appendChild(codeBadge);

            if (c.sourceUrl) {
                const link = document.createElement("a");
                link.href = c.sourceUrl;
                link.target = "_blank";
                link.rel = "noopener noreferrer";
                link.className = "link text-primary text-[11px] font-semibold";
                link.textContent = "Mở nguồn gốc ↗";
                topRow.appendChild(link);
            }
            li.appendChild(topRow);

            const secTitle = document.createElement("p");
            secTitle.className = "mt-1.5 font-bold text-base-content leading-snug";
            secTitle.textContent = c.sectionTitle || "";
            li.appendChild(secTitle);

            const meta = document.createElement("p");
            meta.className = "mt-1 text-[11px] text-base-content/70";
            meta.textContent = (c.sourceTitle || "") + " — " + (c.publisher || "") + (c.publishedDate ? " (" + c.publishedDate + ")" : "");
            li.appendChild(meta);

            if (c.snippet) {
                const snip = document.createElement("p");
                snip.className = "mt-1.5 rounded-lg bg-base-100 p-2 line-clamp-3 italic text-[11px] text-base-content/80 border border-base-200";
                snip.textContent = c.snippet;
                li.appendChild(snip);
            }

            inspectorList.appendChild(li);
        });

        if (highlightCard && inspectorCard) {
            inspectorCard.classList.add("ring-2", "ring-primary");
            inspectorCard.scrollIntoView({ behavior: "smooth", block: "nearest" });
            setTimeout(function () {
                inspectorCard.classList.remove("ring-2", "ring-primary");
            }, 1400);
        }
    }

    function formatNow() {
        const d = new Date();
        const pad = (n) => String(n).padStart(2, "0");
        return pad(d.getHours()) + ":" + pad(d.getMinutes()) + " " + pad(d.getDate()) + "/" + pad(d.getMonth() + 1);
    }

    function createMessageElement(msg) {
        const isUser = msg.role === "USER";
        const wrapper = document.createElement("div");
        wrapper.setAttribute("data-role", msg.role);
        wrapper.className = "chat-msg-item " + (isUser
            ? "ml-auto max-w-[85%] rounded-2xl rounded-tr-xs bg-primary px-4 py-3 text-primary-content shadow-xs"
            : "mr-auto w-full rounded-2xl rounded-tl-xs border border-base-300 bg-base-100 p-4 shadow-xs");

        const header = document.createElement("div");
        header.className = "flex items-center justify-between gap-2 text-xs " + (isUser
            ? "text-primary-content/80"
            : "text-base-content/70 border-b border-base-200 pb-2 mb-2.5");

        const leftHead = document.createElement("div");
        leftHead.className = "flex items-center gap-2";

        if (!isUser) {
            const avatar = document.createElement("span");
            avatar.className = "grid size-6 shrink-0 place-items-center rounded-full bg-primary text-primary-content text-[11px] font-bold";
            avatar.textContent = "LC";
            leftHead.appendChild(avatar);
        }

        const author = document.createElement("span");
        author.className = "font-bold" + (!isUser ? " text-base-content" : "");
        author.textContent = isUser ? "Bạn" : "Trợ lý LeafCare";
        leftHead.appendChild(author);

        const metaRight = document.createElement("div");
        metaRight.className = "flex items-center gap-2";

        if (!isUser && msg.statusLabel) {
            const badge = document.createElement("span");
            badge.className = "chat-msg-status badge badge-xs badge-outline border-primary/40 text-primary";
            badge.textContent = msg.statusLabel;
            metaRight.appendChild(badge);
        }

        const timeSpan = document.createElement("span");
        timeSpan.className = "tabular";
        timeSpan.textContent = formatNow();
        metaRight.appendChild(timeSpan);

        header.appendChild(leftHead);
        header.appendChild(metaRight);
        wrapper.appendChild(header);

        if (msg.imageUrl) {
            const imgWrap = document.createElement("div");
            imgWrap.className = "mt-2.5 mb-2";
            const img = document.createElement("img");
            img.src = msg.imageUrl;
            img.alt = "Ảnh lá đính kèm";
            img.className = "chat-attached-img max-h-52 rounded-xl border border-base-300 object-contain";
            imgWrap.appendChild(img);
            wrapper.appendChild(imgWrap);
        }

        const body = document.createElement("div");
        body.className = "chat-msg-body break-words text-sm leading-relaxed";
        if (isUser) {
            body.textContent = msg.content || "";
        } else {
            body.innerHTML = renderMarkdownSafe(msg.content || "");
        }
        wrapper.appendChild(body);

        // Compact citation trigger badge (keeps chat stream uncluttered while syncing with Right Inspector)
        if (!isUser && Array.isArray(msg.citations) && msg.citations.length > 0) {
            const citeBar = document.createElement("div");
            citeBar.className = "mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-primary/25 bg-primary/5 px-3 py-2 text-xs";

            const leftCite = document.createElement("div");
            leftCite.className = "flex flex-wrap items-center gap-1.5";

            const label = document.createElement("span");
            label.className = "font-bold text-primary";
            label.textContent = "📚 Tài liệu đối chiếu:";
            leftCite.appendChild(label);

            msg.citations.forEach(function (c) {
                const chip = document.createElement("span");
                chip.className = "inline-flex items-center rounded-md bg-primary/15 px-2 py-0.5 font-bold text-primary";
                chip.textContent = c.chunkCode;
                leftCite.appendChild(chip);
            });

            const inspectBtn = document.createElement("button");
            inspectBtn.type = "button";
            inspectBtn.className = "btn-focus-inspector link text-primary font-semibold";
            inspectBtn.textContent = "Xem nguồn tham khảo →";
            inspectBtn.addEventListener("click", function () {
                updateInspectorCitations(msg.citations, true);
            });

            citeBar.appendChild(leftCite);
            citeBar.appendChild(inspectBtn);
            wrapper.appendChild(citeBar);
        }

        // Follow-up Question Chips
        if (!isUser && Array.isArray(msg.followUpQuestions) && msg.followUpQuestions.length > 0) {
            const fqBox = document.createElement("div");
            fqBox.className = "mt-3 border-t border-base-200 pt-2.5";

            const fqTitle = document.createElement("p");
            fqTitle.className = "text-xs font-semibold text-base-content/60";
            fqTitle.textContent = "Gợi ý hỏi tiếp:";
            fqBox.appendChild(fqTitle);

            const chipWrap = document.createElement("div");
            chipWrap.className = "mt-1.5 flex flex-wrap gap-1.5";

            msg.followUpQuestions.forEach(function (fq) {
                const btn = document.createElement("button");
                btn.type = "button";
                btn.className = "followup-chip rounded-full border border-base-300 bg-base-200/70 px-3 py-1 text-left text-xs font-semibold transition hover:border-primary hover:bg-base-100";
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
        if (sendBtnLabel) sendBtnLabel.textContent = "Đang chẩn đoán...";

        let assistantEl = null;

        try {
            if (emptyState) emptyState.remove();

            let userImageUrl = null;
            if (payload.imageFile && previewThumb && previewThumb.src) {
                userImageUrl = previewThumb.src;
                updateInspectorImage(userImageUrl);
            }
            const userMsgEl = createMessageElement({
                role: "USER",
                content: payload.text || "Nhờ trợ lý phân tích ảnh lá đính kèm và gợi ý cách chăm sóc.",
                imageUrl: userImageUrl
            });
            messagesBox.appendChild(userMsgEl);

            assistantEl = createMessageElement({
                role: "ASSISTANT",
                content: "",
                statusLabel: "Gemini 3.5 Flash Lite đang phân tích...",
                citations: [],
                followUpQuestions: []
            });
            const bodyEl = assistantEl.querySelector(".chat-msg-body");
            if (bodyEl) {
                bodyEl.innerHTML = '<span class="inline-flex items-center gap-2 text-base-content/60"><span class="loading loading-dots loading-xs"></span> Đang đối chiếu kho tri thức và phân tích...</span>';
            }
            messagesBox.appendChild(assistantEl);
            scrollToBottom();

            if (input) input.value = "";
            clearSelectedImage();
            updateCharCounter();

            const streamUrl = "/api/chat/conversations/" + encodeURIComponent(convId) + "/messages/stream";
            let response;
            if (payload.imageFile) {
                const fd = new FormData();
                fd.append("text", payload.text);
                if (payload.plantId) fd.append("plantId", payload.plantId);
                fd.append("clientRequestId", payload.clientRequestId);
                fd.append("image", payload.imageFile);
                response = await fetch(streamUrl, {
                    method: "POST",
                    body: fd
                });
            } else {
                response = await fetch(streamUrl, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        text: payload.text,
                        plantId: payload.plantId ? Number(payload.plantId) : null,
                        clientRequestId: payload.clientRequestId
                    })
                });
            }

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                lastFailedPayload = payload;
                showError(errData.error || "Không thể gửi tin nhắn. Vui lòng thử lại.");
                if (assistantEl) assistantEl.remove();
                return;
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";
            let currentEvent = null;
            let currentDataLines = [];
            let streamedRaw = "";

            const handleEvent = (eventName, dataString) => {
                if (eventName === "start") {
                    try {
                        const startObj = JSON.parse(dataString);
                        if (startObj.conversationTitle && activeConvTitle) {
                            activeConvTitle.textContent = startObj.conversationTitle;
                            const activeNavSpan = document.querySelector("#conversation-list a.bg-primary span");
                            if (activeNavSpan) activeNavSpan.textContent = startObj.conversationTitle;
                        }
                        if (startObj.userMessage && startObj.userMessage.imageUrl) {
                            updateInspectorImage(startObj.userMessage.imageUrl);
                        }
                    } catch (e) {}
                } else if (eventName === "token") {
                    streamedRaw += dataString;
                    if (bodyEl) {
                        bodyEl.innerHTML = renderMarkdownSafe(streamedRaw);
                    }
                    scrollToBottom();
                } else if (eventName === "done") {
                    try {
                        const data = JSON.parse(dataString);
                        if (data.conversationTitle && activeConvTitle) {
                            activeConvTitle.textContent = data.conversationTitle;
                        }
                        if (data.assistantMessage) {
                            const finalEl = createMessageElement(data.assistantMessage);
                            if (assistantEl && assistantEl.parentNode) {
                                assistantEl.parentNode.replaceChild(finalEl, assistantEl);
                                assistantEl = finalEl;
                            }
                            updateInspectorCitations(data.assistantMessage.citations || [], false);
                        }
                        lastFailedPayload = null;
                        scrollToBottom();
                    } catch (e) {
                        console.error("Failed to parse done payload:", e);
                    }
                } else if (eventName === "error") {
                    try {
                        const errObj = JSON.parse(dataString);
                        showError(errObj.error || "Có lỗi xảy ra trong quá trình nhận phản hồi.");
                    } catch (e) {
                        showError(dataString || "Có lỗi xảy ra.");
                    }
                }
            };

            while (true) {
                const { value, done } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                let newlineIdx;
                while ((newlineIdx = buffer.indexOf("\n")) >= 0) {
                    let line = buffer.slice(0, newlineIdx);
                    buffer = buffer.slice(newlineIdx + 1);
                    if (line.endsWith("\r")) {
                        line = line.slice(0, -1);
                    }
                    if (line === "") {
                        if (currentEvent && currentDataLines.length > 0) {
                            handleEvent(currentEvent, currentDataLines.join("\n"));
                        }
                        currentEvent = null;
                        currentDataLines = [];
                    } else if (line.startsWith("event:")) {
                        currentEvent = line.slice(6).trim();
                    } else if (line.startsWith("data:")) {
                        currentDataLines.push(line.slice(5));
                    }
                }
            }
        } catch (err) {
            lastFailedPayload = payload;
            showError("Mất kết nối mạng hoặc phiên phản hồi quá hạn. Vui lòng thử lại.");
            if (assistantEl && !assistantEl.querySelector(".chat-msg-body")?.textContent) {
                assistantEl.remove();
            }
        } finally {
            isSending = false;
            if (sendBtn) sendBtn.disabled = false;
            if (sendBtnLabel) sendBtnLabel.textContent = "Gửi câu hỏi";
        }
    }

    function fillAndFocusPrompt(promptText) {
        if (!input) return;
        input.value = promptText;
        updateCharCounter();
        input.focus();
    }

    // Hydrate existing server-rendered messages: format Markdown and populate Right Inspector Panel
    function hydrateExistingConversation() {
        document.querySelectorAll(".chat-msg-item[data-role='ASSISTANT'] .chat-msg-body").forEach(function (el) {
            const raw = el.getAttribute("data-raw-markdown") || el.textContent || "";
            el.innerHTML = renderMarkdownSafe(raw);
        });

        // Find latest attached image if any
        const attachedImgs = document.querySelectorAll(".chat-attached-img");
        if (attachedImgs.length > 0) {
            const lastImg = attachedImgs[attachedImgs.length - 1];
            if (lastImg.src) {
                updateInspectorImage(lastImg.src);
            }
        }

        // Bind citation inspector focus buttons and populate Inspector with the latest assistant message's citations
        const assistantItems = document.querySelectorAll(".chat-msg-item[data-role='ASSISTANT']");
        let latestCitations = [];

        assistantItems.forEach(function (item) {
            const badgeEls = item.querySelectorAll("[data-chunk-code]");
            const msgCitations = [];
            badgeEls.forEach(function (b) {
                msgCitations.push({
                    chunkCode: b.getAttribute("data-chunk-code"),
                    sectionTitle: b.getAttribute("data-section-title"),
                    sourceTitle: b.getAttribute("data-source-title"),
                    publisher: b.getAttribute("data-publisher"),
                    publishedDate: b.getAttribute("data-published-date"),
                    sourceUrl: b.getAttribute("data-source-url"),
                    snippet: b.getAttribute("data-snippet")
                });
            });
            latestCitations = msgCitations;

            const focusBtn = item.querySelector(".btn-focus-inspector");
            if (focusBtn) {
                focusBtn.addEventListener("click", function () {
                    updateInspectorCitations(msgCitations, true);
                });
            }
        });

        updateInspectorCitations(latestCitations, false);
    }

    if (input) {
        input.addEventListener("input", updateCharCounter);
        input.addEventListener("keydown", function (e) {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                if (form) form.requestSubmit();
            }
        });
        updateCharCounter();
    }

    if (imageInput) {
        imageInput.addEventListener("change", function () {
            const file = imageInput.files && imageInput.files[0];
            if (!file) {
                clearSelectedImage();
                return;
            }
            if (file.size > 8 * 1024 * 1024) {
                showError("Ảnh lá vượt quá dung lượng tối đa 8MB.");
                clearSelectedImage();
                return;
            }
            selectedFile = file;
            const reader = new FileReader();
            reader.onload = function (ev) {
                if (previewThumb) previewThumb.src = ev.target.result;
                if (previewName) previewName.textContent = file.name;
                if (previewBox) {
                    previewBox.classList.remove("hidden");
                    previewBox.classList.add("flex");
                }
            };
            reader.readAsDataURL(file);
        });
    }

    if (clearImageBtn) {
        clearImageBtn.addEventListener("click", clearSelectedImage);
    }

    if (form) {
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            const text = input ? input.value.trim() : "";
            if (!text && !selectedFile) {
                showError("Vui lòng nhập mô tả triệu chứng hoặc chọn ảnh lá đính kèm.");
                return;
            }
            const payload = {
                text: text,
                plantId: plantSelect ? plantSelect.value : "",
                imageFile: selectedFile,
                clientRequestId: generateRequestId()
            };
            submitTurn(payload);
        });
    }

    if (retryBtn) {
        retryBtn.addEventListener("click", function () {
            if (lastFailedPayload) {
                submitTurn(lastFailedPayload);
            }
        });
    }

    if (newChatBtn) {
        newChatBtn.addEventListener("click", async function () {
            try {
                const plantId = plantSelect && plantSelect.value ? Number(plantSelect.value) : null;
                const res = await fetch("/api/chat/conversations", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ plantId: plantId })
                });
                if (res.ok) {
                    const created = await res.json();
                    window.location.href = "/chat?conversationId=" + encodeURIComponent(created.id);
                }
            } catch (e) {
                showError("Không thể tạo cuộc hội thoại mới.");
            }
        });
    }

    if (deleteConvBtn) {
        deleteConvBtn.addEventListener("click", async function () {
            if (!convId) return;
            try {
                const res = await fetch("/api/chat/conversations/" + encodeURIComponent(convId), {
                    method: "DELETE"
                });
                if (res.ok) {
                    window.location.href = "/chat";
                }
            } catch (e) {
                showError("Không thể xoá hội thoại.");
            }
        });
    }

    function bindQuickPromptButtons(container) {
        (container || document).querySelectorAll(".quick-prompt-btn, .followup-chip").forEach(function (btn) {
            btn.addEventListener("click", function () {
                const p = btn.getAttribute("data-prompt");
                const pId = btn.getAttribute("data-plant-id");
                if (pId && plantSelect) {
                    plantSelect.value = pId;
                }
                if (p) fillAndFocusPrompt(p);
            });
        });
    }

    if (plantSelect) {
        plantSelect.addEventListener("change", async function () {
            const grid = document.getElementById("starter-prompts-grid");
            if (!grid) return;
            try {
                const q = plantSelect.value ? "?plantId=" + encodeURIComponent(plantSelect.value) : "";
                const res = await fetch("/api/chat/starter-prompts" + q);
                if (!res.ok) return;
                const list = await res.json();
                grid.innerHTML = "";
                list.forEach(function (sp) {
                    const btn = document.createElement("button");
                    btn.type = "button";
                    btn.className = "quick-prompt-btn flex flex-col gap-1 rounded-xl border border-base-300 bg-base-200/60 p-3 text-left transition hover:border-primary hover:bg-base-100";
                    btn.setAttribute("data-prompt", sp.promptText || "");
                    if (sp.plantId) {
                        btn.setAttribute("data-plant-id", sp.plantId);
                    }
                    const titleSpan = document.createElement("span");
                    titleSpan.className = "text-xs font-bold text-primary";
                    titleSpan.textContent = "🌿 " + (sp.title || "");
                    const sumSpan = document.createElement("span");
                    sumSpan.className = "line-clamp-2 text-xs text-base-content/80";
                    sumSpan.textContent = sp.symptomSummary || "";
                    btn.appendChild(titleSpan);
                    btn.appendChild(sumSpan);
                    grid.appendChild(btn);
                });
                bindQuickPromptButtons(grid);
            } catch (e) {}
        });
    }

    bindQuickPromptButtons(document);
    hydrateExistingConversation();
    scrollToBottom();
})();
