package com.example.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "subgraph")
public class SubgraphProperties {

    private ServiceUrl shows = new ServiceUrl();
    private ServiceUrl reviews = new ServiceUrl();

    public ServiceUrl getShows() { return shows; }
    public void setShows(ServiceUrl shows) { this.shows = shows; }
    public ServiceUrl getReviews() { return reviews; }
    public void setReviews(ServiceUrl reviews) { this.reviews = reviews; }

    public static class ServiceUrl {
        private String url;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
