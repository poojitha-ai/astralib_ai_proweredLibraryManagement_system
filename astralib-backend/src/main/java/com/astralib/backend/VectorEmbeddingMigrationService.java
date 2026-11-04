
package com.astralib.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class VectorEmbeddingMigrationService {

    private final VectorRecordRepository repository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final String embeddingProvider;

    public VectorEmbeddingMigrationService(
            VectorRecordRepository repository,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper,
            @Value("${llm.embedding-provider:local}")
            String embeddingProvider) {

        this.repository = repository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.embeddingProvider = embeddingProvider;
    }

    public void migrateAllEmbeddings() throws Exception {

        if (!"local".equalsIgnoreCase(embeddingProvider)) {
            throw new IllegalStateException(
                    "Migration requires the local embedding provider."
            );
        }

        final int batchSize = 50;
        int pageNumber = 0;
        long migrated = 0;

        while (true) {

            Page<VectorRecord> page = repository.findAll(
                    PageRequest.of(
                            pageNumber,
                            batchSize,
                            Sort.by(Sort.Direction.ASC, "id")
                    )
            );

            if (page.isEmpty()) {
                break;
            }

            for (VectorRecord record : page.getContent()) {

                String content = record.getContent();

                if (content == null || content.isBlank()) {
                    throw new IllegalStateException(
                            "Empty content for record ID "
                                    + record.getId()
                    );
                }

                float[] embedding =
                        embeddingService.generateEmbedding(content);

                if (embedding.length != embeddingService.getDimension()) {
                    throw new IllegalStateException(
                            "Unexpected embedding dimension for record ID "
                                    + record.getId()
                    );
                }

                String embeddingJson =
                        objectMapper.writeValueAsString(embedding);

                // Change only the embedding.
                record.setEmbeddingJson(embeddingJson);
            }

            // Save this batch; other fields are left unchanged.
            repository.saveAllAndFlush(page.getContent());

            migrated += page.getNumberOfElements();

            System.out.println(
                    "Embedding migration progress: "
                            + migrated + " / " + page.getTotalElements()
            );

            pageNumber++;
        }

        System.out.println(
                "Embedding migration completed. Records processed: "
                        + migrated
        );
    }
}
