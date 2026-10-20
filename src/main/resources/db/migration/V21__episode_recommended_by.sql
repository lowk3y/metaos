-- Who recommended an episode: by default the person of whoever added it (create_episode), or anyone else. Episodes
-- added before keep none, as whoever added them then (e.g. imported them) didn't necessarily recommend them.
ALTER TABLE episodes
    ADD COLUMN recommended_by UUID REFERENCES people (id) ON DELETE SET NULL;

CREATE INDEX episodes_recommended_by_idx ON episodes (recommended_by);
