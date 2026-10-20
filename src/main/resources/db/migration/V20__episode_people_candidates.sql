-- Candidates: people who could be an episode's guests, e.g. an idea's, until they say yes (confirm_guest).
ALTER TABLE episode_people
    DROP CONSTRAINT episode_people_role_check,
    ADD CONSTRAINT episode_people_role_check
        CHECK (role IN ('HOST', 'CO_HOST', 'GUEST', 'PRODUCER', 'CONSULTANT', 'DESIGNER', 'CANDIDATE'));
