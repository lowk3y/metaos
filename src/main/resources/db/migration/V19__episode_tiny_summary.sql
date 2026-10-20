-- A very short summary of an episode (Markdown), e.g. a sentence, beside its longer summary.
ALTER TABLE episodes
    ADD COLUMN tiny_summary TEXT;
