package com.astralib.backend;

import org.springframework.stereotype.Service;

@Service
public class TextPreprocessingService {

    public String generateBookTextContent(Book book) {
        if (book == null) return "";
        
        StringBuilder sb = new StringBuilder();
        if (book.getTitle() != null && !book.getTitle().isBlank()) {
            sb.append("Title: ").append(book.getTitle().trim()).append("\n");
        }
        if (book.getAuthor() != null && book.getAuthor().getName() != null) {
            sb.append("Author: ").append(book.getAuthor().getName().trim()).append("\n");
        }
        if (book.getCategory() != null && book.getCategory().getName() != null) {
            sb.append("Category: ").append(book.getCategory().getName().trim()).append("\n");
        }
        if (book.getSubjects() != null && !book.getSubjects().isEmpty()) {
            sb.append("Subjects: ").append(String.join(", ", book.getSubjects())).append("\n");
        }
        if (book.getPublicationYear() != null) {
            sb.append("Publication Year: ").append(book.getPublicationYear()).append("\n");
        }
        if (book.getPublisher() != null && !book.getPublisher().isBlank()) {
            sb.append("Publisher: ").append(book.getPublisher().trim()).append("\n");
        }
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            sb.append("Description: ").append(book.getDescription().trim()).append("\n");
        }

        return sb.toString().trim();
    }

    public String generatePaperTextContent(ScholarlyPaper paper) {
        if (paper == null) return "";
        
        StringBuilder sb = new StringBuilder();
        if (paper.getTitle() != null && !paper.getTitle().isBlank()) {
            sb.append("Title: ").append(paper.getTitle().trim()).append("\n");
        }
        if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) {
            sb.append("Authors: ").append(String.join(", ", paper.getAuthors())).append("\n");
        }
        if (paper.getVenue() != null && !paper.getVenue().isBlank()) {
            sb.append("Venue: ").append(paper.getVenue().trim()).append("\n");
        }
        if (paper.getPublicationYear() != null) {
            sb.append("Publication Year: ").append(paper.getPublicationYear()).append("\n");
        }
        if (paper.getConcepts() != null && !paper.getConcepts().isEmpty()) {
            sb.append("Concepts: ").append(String.join(", ", paper.getConcepts())).append("\n");
        }
        if (paper.getAbstractText() != null && !paper.getAbstractText().isBlank()) {
            sb.append("Abstract: ").append(paper.getAbstractText().trim()).append("\n");
        }

        return sb.toString().trim();
    }
}
