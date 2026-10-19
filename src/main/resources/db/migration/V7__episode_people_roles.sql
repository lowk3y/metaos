-- Producers, consultants and designers, as in the CMS (Hygraph); a person can now have several roles on one episode.
ALTER TABLE episode_people
    DROP CONSTRAINT episode_people_role_check,
    ADD CONSTRAINT episode_people_role_check
        CHECK (role IN ('HOST', 'CO_HOST', 'GUEST', 'PRODUCER', 'CONSULTANT', 'DESIGNER')),
    DROP CONSTRAINT episode_people_episode_person_key,
    ADD CONSTRAINT episode_people_episode_person_role_key UNIQUE (episode_id, person_id, role);
