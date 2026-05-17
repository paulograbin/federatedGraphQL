package com.example.gateway;

public class SubgraphConfig {

    private final String name;
    private final String url;

    public SubgraphConfig(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() { return name; }
    public String getUrl() { return url; }
}
