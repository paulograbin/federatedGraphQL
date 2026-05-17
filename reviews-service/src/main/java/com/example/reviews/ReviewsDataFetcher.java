package com.example.reviews;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@DgsComponent
public class ReviewsDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(ReviewsDataFetcher.class);


    private static final Map<String, List<Review>> REVIEWS_BY_SHOW = Map.of(
            "1", List.of(
                    new Review("r1", 5, "Absolutely loved it!"),
                    new Review("r2", 4, "Great show, binge-worthy")
            ),
            "2", List.of(
                    new Review("r3", 5, "Brilliant performances"),
                    new Review("r4", 4, "Well written and acted")
            ),
            "3", List.of(
                    new Review("r5", 4, "Dark and intense")
            ),
            "4", List.of(
                    new Review("r6", 3, "Good but could be better"),
                    new Review("r7", 4, "Loved the world-building")
            ),
            "5", List.of(
                    new Review("r8", 5, "Tim Burton at his best"),
                    new Review("r9", 4, "Fun and quirky")
            )
    );

    @DgsEntityFetcher(name = "Show")
    public Show showEntity(Map<String, Object> values) throws InterruptedException {
        log.info("Fetching show entity");

        return new Show((String) values.get("id"));
    }

    @DgsData(parentType = "Show", field = "reviews")
    public List<Review> reviews(DgsDataFetchingEnvironment dfe) throws InterruptedException {
        long delay = ThreadLocalRandom.current().nextLong(100, 1001);
        log.info("Fetching reviews (simulated delay: {}ms)", delay);
        Thread.sleep(delay);

        Show show = dfe.getSource();
        return REVIEWS_BY_SHOW.getOrDefault(show.getId(), List.of());
    }
}
