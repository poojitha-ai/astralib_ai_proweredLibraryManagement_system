package com.astralib.backend;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reading_history")
public class ReadingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne
    @JoinColumn(name = "resource_id")
    private DigitalResource resource;

    // This now represents the most recent reading time.
    @Column(name = "access_time")
    private LocalDateTime accessTime;

    // 0 - 100
    @Column(name = "progress_percent")
    private Integer progressPercent;

    // Exact EPUB position used for Continue Reading.
    @Lob
    @Column(name = "last_location_cfi")
    private String lastLocationCfi;

    public ReadingHistory() {
    }

    public ReadingHistory(
            User user,
            Book book,
            DigitalResource resource) {

        this.user = user;
        this.book = book;
        this.resource = resource;
        this.accessTime = LocalDateTime.now();
        this.progressPercent = 0;
    }

    @PrePersist
    public void prePersist() {

        if (this.accessTime == null) {
            this.accessTime = LocalDateTime.now();
        }

        if (this.progressPercent == null) {
            this.progressPercent = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public DigitalResource getResource() {
        return resource;
    }

    public void setResource(DigitalResource resource) {
        this.resource = resource;
    }

    public LocalDateTime getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(LocalDateTime accessTime) {
        this.accessTime = accessTime;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getLastLocationCfi() {
        return lastLocationCfi;
    }

    public void setLastLocationCfi(String lastLocationCfi) {
        this.lastLocationCfi = lastLocationCfi;
    }
}