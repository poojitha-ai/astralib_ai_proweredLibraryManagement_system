package com.astralib.backend;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "books", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"isbn"}),
    @UniqueConstraint(columnNames = {"resource_id"}),
    @UniqueConstraint(columnNames = {"open_library_key"})
})
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true)
    private String isbn;

    @Column(name = "resource_id", unique = true)
    private String resourceId;

    @Column(name = "open_library_key", unique = true)
    private String openLibraryKey;

    private Integer publicationYear;

    private String publisher;

    private String language;

    private String coverImage;

    @Column(name = "resource_type")
    private String resourceType;

    @ElementCollection
    @CollectionTable(name = "book_subjects", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "subject")
    private List<String> subjects = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Author author;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DigitalResource> digitalResources = new ArrayList<>();

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    /* =========================================================
     * Curated-collection attribution
     *
     * Where the text came from, which edition it is and under what
     * terms it may be served. Kept on the book so every catalogue
     * entry can be traced back to its source.
     * ========================================================= */

    @Column(length = 255)
    private String edition;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "licence", length = 512)
    private String licence;

    /*
     * Genres are broader than the single catalogue category:
     * Frankenstein is both Gothic Fiction and Early Science Fiction.
     * A book keeps one Category (the existing catalogue grouping)
     * and any number of genre labels here.
     */
    @ElementCollection
    @CollectionTable(name = "book_genres", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "genre")
    private List<String> genres = new ArrayList<>();

    /*
     * A curated catalogue entry whose text we do not (yet) hold, for
     * example a commercial programming title we list and can describe
     * but cannot legally serve. It has no DigitalResource and no
     * vectors; the catalogue shows it as not available for reading.
     * Books with an actual file do not need this flag because the
     * stored resource already makes them visible.
     */
    @Column(name = "catalogue_entry")
    private Boolean catalogueEntry = Boolean.FALSE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Book() {}

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getOpenLibraryKey() { return openLibraryKey; }
    public void setOpenLibraryKey(String openLibraryKey) { this.openLibraryKey = openLibraryKey; }
    public Integer getPublicationYear() { return publicationYear; }
    public void setPublicationYear(Integer publicationYear) { this.publicationYear = publicationYear; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public List<String> getSubjects() { return subjects; }
    public void setSubjects(List<String> subjects) { this.subjects = subjects == null ? new ArrayList<>() : subjects; }
    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public List<DigitalResource> getDigitalResources() { return digitalResources; }
    public void setDigitalResources(List<DigitalResource> digitalResources) { this.digitalResources = digitalResources; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getLicence() { return licence; }
    public void setLicence(String licence) { this.licence = licence; }
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres == null ? new ArrayList<>() : genres; }
    public Boolean getCatalogueEntry() { return catalogueEntry; }
    public void setCatalogueEntry(Boolean catalogueEntry) { this.catalogueEntry = catalogueEntry; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
