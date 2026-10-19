-- The released episodes imported from the CMS (Hygraph) through the MCP tools came in as ideas, as every new episode does,
-- and lack what the lifecycle asks for on the way to PUBLISHED (recording dates, early ones also producers and links).
-- An episode with a code that has aired or has a public link has been released, so it is PUBLISHED, as in V10.
UPDATE episodes
SET status = 'PUBLISHED'
WHERE status = 'IDEA'
  AND code IS NOT NULL
  AND (aired_date IS NOT NULL OR youtube_url IS NOT NULL OR spotify_url IS NOT NULL OR apple_podcasts_url IS NOT NULL);
