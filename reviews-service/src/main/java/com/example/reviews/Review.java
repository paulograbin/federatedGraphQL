package com.example.reviews;

public class Review {

    private String id;
    private Integer starRating;
    private String comment;

    public Review(String id, Integer starRating, String comment) {
        this.id = id;
        this.starRating = starRating;
        this.comment = comment;
    }

    public String getId() { return id; }
    public Integer getStarRating() { return starRating; }
    public String getComment() { return comment; }
}
