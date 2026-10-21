-- Pictures MetaOS serves to everyone from its public bucket (Images.kt), e.g. episodes' thumbnails and people's avatars.
-- Each is at images/<sha256>/ there: original.<png|jpg|webp|gif> as it came, and copies of it as wide as widths says,
-- <width>.<format>, smaller than the original. The same file is stored once, however often it's used. Where it came
-- from is kept with it: a place and a path in Dropbox or Koofr, or the URL (in source_path) it was fetched from.
CREATE TABLE images
(
    id           UUID PRIMARY KEY,
    sha256       TEXT        NOT NULL UNIQUE,
    name         TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    size         BIGINT      NOT NULL,
    width        INT         NOT NULL,
    height       INT         NOT NULL,
    widths       INT[]       NOT NULL,
    format       TEXT        NOT NULL CHECK (format IN ('jpg', 'png')),
    source       TEXT        NOT NULL CHECK (source IN ('DROPBOX', 'KOOFR', 'URL')),
    source_place TEXT,
    source_path  TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER images_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON images
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

-- An episode's thumbnail (16:9), with one for each of its alternative titles (B and C, for A/B tests), and its cover
-- (square, e.g. for podcast apps); a person's avatar. An image stays as long as anything uses it.
ALTER TABLE episodes
    ADD COLUMN thumbnail_image_id   UUID REFERENCES images (id),
    ADD COLUMN thumbnail_b_image_id UUID REFERENCES images (id),
    ADD COLUMN thumbnail_c_image_id UUID REFERENCES images (id),
    ADD COLUMN cover_image_id       UUID REFERENCES images (id);

ALTER TABLE people
    ADD COLUMN avatar_image_id UUID REFERENCES images (id);
