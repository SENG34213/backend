package com.gamingcastle.bookingservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record GameRequest(
    @NotBlank(message = "Game title is required")
    String title,

    @NotBlank(message = "Genre is required")
    String genre,

    @NotEmpty(message = "At least one supported platform is required")
    List<String> platforms,

    @Min(value = 1980, message = "Release year must be >= 1980")
    int releaseYear,

    @NotBlank(message = "Age rating is required")
    String ageRating,

    String description,
    String coverUrl,

    @Min(value = 1, message = "Max players must be at least 1")
    int maxPlayers,

    Boolean featured,
    Boolean active,
    List<java.util.UUID> stationIds
) {
    public GameRequest(
        String title,
        String genre,
        List<String> platforms,
        int releaseYear,
        String ageRating,
        String description,
        String coverUrl,
        int maxPlayers,
        Boolean featured,
        Boolean active
    ) {
        this(title, genre, platforms, releaseYear, ageRating, description, coverUrl, maxPlayers, featured, active, null);
    }
}
