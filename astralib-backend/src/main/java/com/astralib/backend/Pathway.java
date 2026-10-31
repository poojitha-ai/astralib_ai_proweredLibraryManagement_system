package com.astralib.backend;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered collection of books that forms a reading or learning
 * progression, such as "Python Foundations" (LEARNING) or
 * "Detective Fiction" (LEISURE).
 *
 * Membership, level and sequence live in {@link PathwayBook}; a book
 * may belong to several pathways without being duplicated.
 */
@Entity
@Table(name = "pathways")
public class Pathway {

    public static final String TYPE_LEARNING = "LEARNING";
    public static final String TYPE_LEISURE = "LEISURE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    /** LEARNING or LEISURE. */
    @Column(nullable = false, length = 20)
    private String type;

    private String subject;

    /** Who the pathway is for, in plain language. */
    @Column(columnDefinition = "TEXT")
    private String audience;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "pathway", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PathwayBook> entries = new ArrayList<>();

    public Pathway() {}

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public boolean isLearning() {
        return TYPE_LEARNING.equalsIgnoreCase(type);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<PathwayBook> getEntries() { return entries; }
    public void setEntries(List<PathwayBook> entries) { this.entries = entries; }
}
