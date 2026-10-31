package com.astralib.backend;

import jakarta.persistence.*;

/**
 * One book's place inside a pathway: its sequence position, the level
 * it represents, an optional prerequisite book and the reasoning for
 * that level (so the labels are documented rather than arbitrary).
 */
@Entity
@Table(
        name = "pathway_books",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pathway_book",
                columnNames = {"pathway_id", "book_id"}
        )
)
public class PathwayBook {

    /** Learning levels. */
    public static final String LEVEL_BEGINNER = "BEGINNER";
    public static final String LEVEL_INTERMEDIATE = "INTERMEDIATE";
    public static final String LEVEL_ADVANCED = "ADVANCED";

    /** Leisure reading-complexity labels (they describe the text). */
    public static final String LEVEL_ACCESSIBLE = "ACCESSIBLE";
    public static final String LEVEL_STANDARD = "STANDARD";
    public static final String LEVEL_COMPLEX = "COMPLEX";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pathway_id", nullable = false)
    private Pathway pathway;

    @ManyToOne(optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** 1-based reading order within the pathway. */
    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(nullable = false, length = 20)
    private String level;

    /** Optional earlier book that prepares the reader for this one. */
    @ManyToOne
    @JoinColumn(name = "prerequisite_book_id")
    private Book prerequisiteBook;

    /** Why this book is at this level in this pathway. */
    @Column(columnDefinition = "TEXT")
    private String rationale;

    /** Optional, verifiable outcome for learning pathways. */
    @Column(name = "learning_outcome", columnDefinition = "TEXT")
    private String learningOutcome;

    public PathwayBook() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Pathway getPathway() { return pathway; }
    public void setPathway(Pathway pathway) { this.pathway = pathway; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Book getPrerequisiteBook() { return prerequisiteBook; }
    public void setPrerequisiteBook(Book prerequisiteBook) { this.prerequisiteBook = prerequisiteBook; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getLearningOutcome() { return learningOutcome; }
    public void setLearningOutcome(String learningOutcome) { this.learningOutcome = learningOutcome; }
}
