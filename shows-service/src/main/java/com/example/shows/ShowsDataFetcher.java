package com.example.shows;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;

import java.util.List;
import java.util.Map;

@DgsComponent
public class ShowsDataFetcher {

    private static final List<Show> SHOWS = List.of(
            new Show("1", "Stranger Things", 2016),
            new Show("2", "The Crown", 2016),
            new Show("3", "Ozark", 2017),
            new Show("4", "The Witcher", 2019),
            new Show("5", "Wednesday", 2022)
    );

    @DgsQuery
    public List<Show> shows() {
        return SHOWS;
    }

    @DgsQuery
    public Show show(@InputArgument String id) {
        return SHOWS.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @DgsEntityFetcher(name = "Show")
    public Show showEntity(Map<String, Object> values) {
        String id = (String) values.get("id");
        return SHOWS.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
