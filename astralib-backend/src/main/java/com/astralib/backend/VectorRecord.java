package com.astralib.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vector_records", indexes = {
    @Index(name = "idx_chunk_id", columnList = "chunk_id", unique = true),
    @Index(name = "idx_document_id", columnList = "document_id"),
    @Index(name = "idx_document_type", columnList = "document_type")
})
public class VectorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chunk_id", nullable = false, unique = true)
    private String chunkId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String author;

    @Column
    private String category;

    @Column
    private String section;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "embedding_json", columnDefinition = "LONGTEXT", nullable = false)
    private String embeddingJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public VectorRecord() {}

    public VectorRecord(String chunkId, String documentId, String documentType, String title, String author, String category, String section, Integer chunkIndex, String content, String embeddingJson) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentType = documentType;
        this.title = title;
        this.author = author;
        this.category = category;
        this.section = section;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embeddingJson = embeddingJson;
        this.createdAt = LocalDateTime.now();
    }

    public VectorRecord(String chunkId, String documentId, String documentType, String title, String author, String category, String section, String content, String embeddingJson) {
        this(chunkId, documentId, documentType, title, author, category, section, 0, content, embeddingJson);
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
