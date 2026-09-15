package com.astralib.backend;

import org.springframework.stereotype.Component;

@Component
public class InteractionScoreConfig {

    private int bookViewScore = 1;
    private int bookSearchScore = 2;
    private int favoriteScore = 3;
    private int readScore = 4;
    private int downloadScore = 5;
    private int citeScore = 3;

    public int getScoreForAction(String action) {
        if (action == null) return 0;
        
        return switch (action.toLowerCase()) {
            case "view", "book_view" -> bookViewScore;
            case "search", "book_search" -> bookSearchScore;
            case "favorite" -> favoriteScore;
            case "read" -> readScore;
            case "download" -> downloadScore;
            case "cite" -> citeScore;
            default -> 1;
        };
    }

    public int getBookViewScore() {
        return bookViewScore;
    }

    public void setBookViewScore(int bookViewScore) {
        this.bookViewScore = bookViewScore;
    }

    public int getBookSearchScore() {
        return bookSearchScore;
    }

    public void setBookSearchScore(int bookSearchScore) {
        this.bookSearchScore = bookSearchScore;
    }

    public int getFavoriteScore() {
        return favoriteScore;
    }

    public void setFavoriteScore(int favoriteScore) {
        this.favoriteScore = favoriteScore;
    }

    public int getReadScore() {
        return readScore;
    }

    public void setReadScore(int readScore) {
        this.readScore = readScore;
    }

    public int getDownloadScore() {
        return downloadScore;
    }

    public void setDownloadScore(int downloadScore) {
        this.downloadScore = downloadScore;
    }

    public int getCiteScore() {
        return citeScore;
    }

    public void setCiteScore(int citeScore) {
        this.citeScore = citeScore;
    }
}
