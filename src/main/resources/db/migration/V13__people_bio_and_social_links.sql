-- A line about who a person is and where they can be found online, as in the CMS (Hygraph).
ALTER TABLE people
    ADD COLUMN bio           TEXT,
    ADD COLUMN homepage_url  TEXT,
    ADD COLUMN linkedin_url  TEXT,
    ADD COLUMN twitter_url   TEXT,
    ADD COLUMN instagram_url TEXT,
    ADD COLUMN facebook_url  TEXT;
