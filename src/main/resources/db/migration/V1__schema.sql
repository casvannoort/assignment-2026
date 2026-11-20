CREATE TABLE teacher (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE parent (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE child (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE parent_child (
    parent_id UUID NOT NULL REFERENCES parent (id),
    child_id  UUID NOT NULL REFERENCES child (id),
    PRIMARY KEY (parent_id, child_id)
);

CREATE TABLE teacher_child (
    teacher_id UUID NOT NULL REFERENCES teacher (id),
    child_id   UUID NOT NULL REFERENCES child (id),
    PRIMARY KEY (teacher_id, child_id)
);

CREATE TABLE evening (
    id                   UUID PRIMARY KEY,
    teacher_id           UUID    NOT NULL REFERENCES teacher (id),
    date                 DATE    NOT NULL,
    start_time           TIME    NOT NULL,
    end_time             TIME    NOT NULL,
    conversation_minutes INTEGER NOT NULL
);

CREATE TABLE slot (
    id         UUID PRIMARY KEY,
    evening_id UUID    NOT NULL REFERENCES evening (id),
    start_time TIME    NOT NULL,
    end_time   TIME    NOT NULL,
    capacity   INTEGER NOT NULL
);

CREATE TABLE booking (
    id         UUID PRIMARY KEY,
    evening_id UUID NOT NULL REFERENCES evening (id),
    slot_id    UUID NOT NULL REFERENCES slot (id),
    child_id   UUID NOT NULL REFERENCES child (id),
    booked_by  UUID NOT NULL REFERENCES parent (id),
    UNIQUE (evening_id, child_id)
);
