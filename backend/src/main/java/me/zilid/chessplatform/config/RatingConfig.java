package me.zilid.chessplatform.config;

import me.zilid.chessplatform.rating.RatingSystem;
import me.zilid.chessplatform.rating.elo.EloKFactorPolicy;
import me.zilid.chessplatform.rating.elo.EloRatingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RatingConfig {

    @Bean
    public RatingSystem ratingSystem() {
        return new EloRatingSystem(new EloKFactorPolicy());
    }
}
