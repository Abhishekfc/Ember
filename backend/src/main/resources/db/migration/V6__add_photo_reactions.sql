-- One emoji reaction per (photo, reactor) — tapping a new emoji replaces the previous one,
-- tapping the same one again removes it (enforced in PhotoReactionService, not here). The unique
-- constraint is what makes that upsert-or-delete logic safe under concurrent requests from the
-- same person double-tapping.
CREATE TABLE photo_reactions (
    id UUID PRIMARY KEY,
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    reactor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_photo_reactions_photo_reactor UNIQUE (photo_id, reactor_id)
);

-- Backs ActivityService's "reactions on photos I sent" query (joins photos.sender_id) and
-- PhotoService's batched "my reaction on each of these photos" lookup (photo_id IN (...)).
CREATE INDEX idx_photo_reactions_photo_id ON photo_reactions(photo_id);
