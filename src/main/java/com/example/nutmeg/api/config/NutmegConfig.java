package com.example.nutmeg.api.config;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.example.nutmeg.api.content.Content;
import com.example.nutmeg.api.content.ContentLoader;
import com.example.nutmeg.api.game.GameMode;
import com.example.nutmeg.api.game.GameRunner;
import com.example.nutmeg.api.game.headsup.HeadsUpGame;
import com.example.nutmeg.api.game.hol.HigherLowerGame;
import com.example.nutmeg.api.game.imposter.ImposterGame;
import com.example.nutmeg.api.game.ten.TenGame;
import com.example.nutmeg.api.room.RoomRegistry;
import com.example.nutmeg.api.ws.SessionRegistry;

/**
 * Wires the plain-Java core into Spring. Everything game-related is constructed
 * here explicitly; the core packages have no Spring annotations at all, which is
 * why a whole match can be simulated in a unit test without a Spring context.
 */
@Configuration
public class NutmegConfig {

    @Bean
    public Content content() {
        return ContentLoader.fromClasspath().load();   // fails startup loudly on bad XML
    }

    @Bean
    public Random gameRandom() {
        return new SecureRandom();
    }

    @Bean
    public RoomRegistry roomRegistry(@Value("${nutmeg.max-rooms:300}") int maxRooms) {
        return new RoomRegistry(System::currentTimeMillis, maxRooms);
    }

    /**
     * Named "taskScheduler" on purpose: @Scheduled picks the bean with this name,
     * and @EnableWebSocket can register a second TaskScheduler of its own. Naming
     * it (and qualifying the injection below) removes any ambiguity.
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("nutmeg-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean public ImposterGame imposterGame(Content c, Random r)       { return new ImposterGame(c, r); }
    @Bean public TenGame tenGame(Content c, Random r)                 { return new TenGame(c, r); }
    @Bean public HigherLowerGame higherLowerGame(Content c, Random r) { return new HigherLowerGame(c, r); }
    @Bean public HeadsUpGame headsUpGame(Content c, Random r)         { return new HeadsUpGame(c, r); }

    @Bean
    public GameRunner gameRunner(RoomRegistry rooms,
                                 @Qualifier("taskScheduler") ThreadPoolTaskScheduler scheduler,
                                 SessionRegistry sessions,
                                 List<GameMode> games) {
        return new GameRunner(
                rooms,
                (task, atMillis) -> scheduler.schedule(task, Instant.ofEpochMilli(atMillis)),
                sessions,
                games);
    }
}
