-- V2__create_game_tables.sql: Games Catalog and Station-Game Configuration (FR-30, BE-15)

CREATE TABLE games (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(150) NOT NULL,
    genre         VARCHAR(50)  NOT NULL,
    platforms     VARCHAR(255) NOT NULL,
    release_year  INT          NOT NULL,
    age_rating    VARCHAR(20)  NOT NULL,
    description   TEXT,
    cover_url     VARCHAR(500),
    max_players   INT          NOT NULL DEFAULT 1,
    featured      BOOLEAN      NOT NULL DEFAULT FALSE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE station_games (
    station_id   UUID NOT NULL REFERENCES game_stations(id) ON DELETE CASCADE,
    game_id      UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (station_id, game_id)
);

CREATE INDEX idx_games_title ON games(title);
CREATE INDEX idx_games_genre ON games(genre);
CREATE INDEX idx_games_active ON games(active);
CREATE INDEX idx_station_games_station_id ON station_games(station_id);
CREATE INDEX idx_station_games_game_id ON station_games(game_id);

-- Seed games matching frontend catalog
INSERT INTO games (id, title, genre, platforms, release_year, age_rating, description, cover_url, max_players, featured, active) VALUES
    ('00000000-0000-0000-0000-000000000101', 'Tekken 8', 'FIGHTING', 'PS5,PC,XBOX_SERIES_X', 2024, 'PEGI_16', 'The next chapter in the legendary fighting franchise with unmatched visuals and Heat system.', 'https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80', 2, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000102', 'EA Sports FC 25', 'SPORTS', 'PS5,XBOX_SERIES_X,PC,NINTENDO_SWITCH', 2024, 'PEGI_3', '5v5 Rush mode with friends, revamped tactics with FC IQ, and hyper-realistic stadium atmospheres.', 'https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=400&q=80', 4, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000103', 'Cyberpunk 2077: Phantom Liberty', 'RPG', 'PC,PS5,XBOX_SERIES_X', 2023, 'PEGI_18', 'Spy-thriller adventure in Night City featuring ray tracing and high-octane cybernetic combat.', 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=400&q=80', 1, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000000104', 'Forza Horizon 5', 'RACING', 'XBOX_SERIES_X,PC', 2021, 'PEGI_3', 'Explore the vibrant and ever-evolving open-world landscapes of Mexico with hundreds of world-class cars.', 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=400&q=80', 2, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000000105', 'Valorant', 'FPS', 'PC', 2020, 'PEGI_16', 'Character-based 5v5 tactical shooter where precise gunplay meets unique agent abilities.', 'https://images.unsplash.com/photo-1538481199705-c710c4e965fc?auto=format&fit=crop&w=400&q=80', 10, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000106', 'Super Smash Bros. Ultimate', 'FIGHTING', 'NINTENDO_SWITCH', 2018, 'PEGI_12', 'Gaming icons clash in the ultimate showdown. Over 80 playable fighters and 100+ stages.', 'https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=400&q=80', 8, FALSE, TRUE),
    ('00000000-0000-0000-0000-000000000107', 'Gran Turismo 7', 'RACING', 'PS5,VR', 2022, 'PEGI_3', 'The real driving simulator. Complete PS VR2 compatibility with realistic force feedback wheel support.', 'https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=400&q=80', 2, TRUE, TRUE),
    ('00000000-0000-0000-0000-000000000108', 'Street Fighter 6', 'FIGHTING', 'PS5,PC,XBOX_SERIES_X', 2023, 'PEGI_12', 'Powered by Capcom proprietary RE ENGINE, featuring Fighting Ground, Battle Hub, and World Tour.', 'https://images.unsplash.com/photo-1560253023-3ec5d502959f?auto=format&fit=crop&w=400&q=80', 2, FALSE, FALSE);

-- Seed station_games associations
INSERT INTO station_games (station_id, game_id)
SELECT s.id, g.id
FROM game_stations s, games g
WHERE (s.type = 'PC' AND g.platforms LIKE '%PC%')
   OR (s.type = 'CONSOLE' AND (g.platforms LIKE '%PS5%' OR g.platforms LIKE '%XBOX%'))
   OR (s.type = 'VR' AND g.platforms LIKE '%VR%');
