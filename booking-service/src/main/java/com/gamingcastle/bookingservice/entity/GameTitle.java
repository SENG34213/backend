package com.gamingcastle.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameTitle {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(nullable = false, length = 255)
    private String platforms; // Comma-separated: "PS5,PC,XBOX_SERIES_X"

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    @Column(name = "age_rating", nullable = false, length = 20)
    private String ageRating;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "max_players", nullable = false)
    @Builder.Default
    private int maxPlayers = 1;

    @Builder.Default
    private boolean featured = false;

    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToMany
    @JoinTable(
        name = "station_games",
        joinColumns = @JoinColumn(name = "game_id"),
        inverseJoinColumns = @JoinColumn(name = "station_id")
    )
    @Builder.Default
    private Set<GameStation> stations = new HashSet<>();

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public List<String> getPlatformList() {
        if (this.platforms == null || this.platforms.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(this.platforms.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setPlatformList(List<String> list) {
        if (list == null || list.isEmpty()) {
            this.platforms = "";
        } else {
            this.platforms = String.join(",", list);
        }
    }
}
