-- Other titles an episode's title is A/B tested against, e.g. on YouTube.
ALTER TABLE episodes
    ADD COLUMN title_b TEXT,
    ADD COLUMN title_c TEXT;
