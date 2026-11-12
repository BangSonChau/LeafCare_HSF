package com.son.leafcare;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** In tests the real Gemini/pgvector beans are switched off (application.properties); full-context tests get these mocks. */
@Configuration
public class RagTestBeans {
    @Bean
    VectorStore vectorStore() {
        return mock(VectorStore.class);
    }

    @Bean
    ChatClient.Builder chatClientBuilder() {
        ChatClient.Builder b = mock(ChatClient.Builder.class, RETURNS_SELF);
        org.mockito.Mockito.when(b.build()).thenReturn(mock(ChatClient.class));
        return b;
    }
}
