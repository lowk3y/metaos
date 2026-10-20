-- A link to each linked Dropbox folder that only those with access to it can open (DropboxFoldersMCP), as Dropbox's web
-- paths are in each viewer's own Dropbox; none while Dropbox won't make one.
ALTER TABLE episode_dropbox_folders ADD COLUMN url TEXT;
ALTER TABLE session_dropbox_folders ADD COLUMN url TEXT;
