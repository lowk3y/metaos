-- Episode descriptions (Markdown) and Patreon posts, as in the CMS (Hygraph).
ALTER TABLE episodes
    ADD COLUMN summary         TEXT,
    ADD COLUMN machine_summary TEXT,
    ADD COLUMN patreon_url     TEXT;
