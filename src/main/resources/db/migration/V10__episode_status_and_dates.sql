-- Where an episode is in its lifecycle (EpisodeStatus), and when it was added, recorded and aired.
-- The episodes there are by now have all been released, so they start out PUBLISHED; new ones start as an IDEA.
ALTER TABLE episodes
    ADD COLUMN status         TEXT NOT NULL DEFAULT 'PUBLISHED'
        CONSTRAINT episodes_status_check CHECK (status IN ('IDEA', 'PLANNED', 'SCHEDULED', 'RECORDED', 'IN_PRODUCTION',
                                                           'READY', 'EARLY_ACCESS', 'PUBLISHED', 'ON_HOLD', 'CANCELLED')),
    ADD COLUMN recording_date DATE,
    ADD COLUMN aired_date     DATE,
    ADD COLUMN added_date     DATE NOT NULL DEFAULT CURRENT_DATE;

ALTER TABLE episodes
    ALTER COLUMN status SET DEFAULT 'IDEA';

-- Only an idea (or one put on hold or cancelled) can do without a code
ALTER TABLE episodes
    ADD CONSTRAINT episodes_code_required CHECK (code IS NOT NULL OR status IN ('IDEA', 'ON_HOLD', 'CANCELLED'));
