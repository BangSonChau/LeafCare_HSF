package com.son.leafcare.service;

import java.util.List;

/** Câu trả lời + các nguồn đã dùng (để hiện trích dẫn). */
public record RagAnswer(String answer, List<Source> sources) {
    public record Source(int number, String file, String excerpt, Double score) {}
}
