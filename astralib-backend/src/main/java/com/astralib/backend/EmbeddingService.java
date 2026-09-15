package com.astralib.backend;

public interface EmbeddingService {
    float[] generateEmbedding(String text);
    int getDimension();
}
