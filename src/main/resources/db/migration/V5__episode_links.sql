-- Where an episode can be watched or listened to.
ALTER TABLE episodes
    ADD COLUMN youtube_url        TEXT,
    ADD COLUMN spotify_url        TEXT,
    ADD COLUMN apple_podcasts_url TEXT;
