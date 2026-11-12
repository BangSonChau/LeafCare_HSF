package com.son.leafcare.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

@SpringBootTest
class PagerFragmentTest {

    @Autowired
    SpringTemplateEngine engine;

    private String render(int pageIndex, long total, String q) {
        MockServletContext servletContext = new MockServletContext();
        IWebExchange exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext ctx = new WebContext(exchange, Locale.ROOT);
        ctx.setVariable("page", new PageImpl<>(List.of(), PageRequest.of(pageIndex, 10), total));
        ctx.setVariable("baseUrl", "/plants");
        ctx.setVariable("q", q);
        return engine.process(
                new TemplateSpec("fragments/pagination", Set.of("[th:fragment^='pager']"), TemplateMode.HTML, null),
                ctx);
    }

    @Test
    void middlePageMarksCurrentAndKeepsSearch() {
        String html = render(1, 25, "cây");
        assertThat(html).contains("aria-current=\"page\"")
                .contains("/plants?page=0&amp;q=c%C3%A2y")
                .contains("/plants?page=2&amp;q=c%C3%A2y");
    }

    @Test
    void singlePageRendersNothing() {
        assertThat(render(0, 5, "").trim()).isEmpty();
    }

    @Test
    void nullSearchDoesNotBreakLinks() {
        assertThat(render(0, 25, null)).contains("/plants?page=1");
    }
}
