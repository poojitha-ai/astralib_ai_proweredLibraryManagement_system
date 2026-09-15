package com.astralib.backend;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scholarly_papers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"resource_id"}),
    @UniqueConstraint(columnNames = {"openalex_id"})
})
public class ScholarlyPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", unique = true)
    private String resourceId;

    @Column(name = "openalex_id", unique = true)
    private String openalexId;

    @Column
    private String doi;

    @Column(nullable = false)
    private String title;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(columnDefinition = "TEXT")
    private String abstractText;

    @Column
    private String venue;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "is_open_access")
    private Boolean isOpenAccess;

    @Column(name = "cited_by_count")
    private Integer citedByCount;

    @Column(name = "resource_type")
    private String resourceType;

    @ElementCollection
    @CollectionTable(name = "paper_concepts", joinColumns = @JoinColumn(name = "paper_id"))
    @Column(name = "concept")
    private List<String> concepts = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "paper_authors", joinColumns = @JoinColumn(name = "paper_id"))
    @Column(name = "author_name")
    private List<String> authors = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "paper_references", joinColumns = @JoinColumn(name = "paper_id"))
    @Column(name = "reference_id")
    private List<String> referencedWorks = new ArrayList<>();

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ScholarlyPaper() {}

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getOpenalexId() { return openalexId; }
    public void setOpenalexId(String openalexId) { this.openalexId = openalexId; }
    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getPublicationYear() { return publicationYear; }
    public void setPublicationYear(Integer publicationYear) { this.publicationYear = publicationYear; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public Boolean getIsOpenAccess() { return isOpenAccess; }
    public void setIsOpenAccess(Boolean isOpenAccess) { this.isOpenAccess = isOpenAccess; }
    public Integer getCitedByCount() { return citedByCount; }
    public void setCitedByCount(Integer citedByCount) { this.citedByCount = citedByCount; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public List<String> getConcepts() { return concepts; }
    public void setConcepts(List<String> concepts) { this.concepts = concepts == null ? new ArrayList<>() : concepts; }
    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors == null ? new ArrayList<>() : authors; }
    public List<String> getReferencedWorks() { return referencedWorks; }
    public void setReferencedWorks(List<String> referencedWorks) { this.referencedWorks = referencedWorks == null ? new ArrayList<>() : referencedWorks; }
    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
