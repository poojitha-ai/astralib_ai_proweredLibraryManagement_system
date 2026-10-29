package com.astralib_backend;

import com.astralib.backend.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.astralib.backend.AstralibBackendApplication.class)
class EmbeddingServiceIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void shouldGenerateLocalEmbedding() {
        float[] embedding = embeddingService.generateEmbedding(
                "AstraLib is an AI-powered smart library."
        );

        assertNotNull(embedding);
        assertEquals(384, embedding.length);

        for (float value : embedding) {
            assertTrue(Float.isFinite(value));
        }

        System.out.println(
                "SUCCESS: Java generated an embedding with "
                        + embedding.length + " dimensions."
        );
    }
}
