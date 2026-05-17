package com.example.shows;

public class Show {

    private String id;
    private String title;
    private Integer releaseYear;

    public Show(String id, String title, Integer releaseYear) {
        this.id = id;
        this.title = title;
        this.releaseYear = releaseYear;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Integer getReleaseYear() { return releaseYear; }
}
