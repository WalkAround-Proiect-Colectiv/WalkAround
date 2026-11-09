CREATE TABLE preference_questions
(
    id            VARCHAR(50) PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    display_order INTEGER      NOT NULL
);

CREATE TABLE preference_options
(
    id            VARCHAR(80) PRIMARY KEY,
    question_id   VARCHAR(50)  NOT NULL REFERENCES preference_questions (id) ON DELETE CASCADE,
    label         VARCHAR(255) NOT NULL,
    display_order INTEGER      NOT NULL
);
CREATE INDEX preference_options_question_idx ON preference_options (question_id);

CREATE TABLE user_preference_choices
(
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    option_id VARCHAR(80) NOT NULL REFERENCES preference_options (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, option_id)
);
CREATE INDEX user_preference_choices_option_idx ON user_preference_choices (option_id);

INSERT INTO preference_questions (id, title, display_order)
VALUES ('hobbies', 'What do you enjoy doing?', 1),
       ('cultural_events', 'Which cultural events interest you?', 2);

INSERT INTO preference_options (id, question_id, label, display_order)
VALUES ('hobbies.walking', 'hobbies', 'Walking', 1),
       ('hobbies.hiking', 'hobbies', 'Hiking', 2),
       ('hobbies.cycling', 'hobbies', 'Cycling', 3),
       ('hobbies.photography', 'hobbies', 'Photography', 4),
       ('hobbies.reading', 'hobbies', 'Reading', 5),
       ('hobbies.cooking', 'hobbies', 'Cooking', 6),
       ('hobbies.sports', 'hobbies', 'Sports', 7),
       ('hobbies.arts_crafts', 'hobbies', 'Arts and crafts', 8),
       ('cultural_events.concerts', 'cultural_events', 'Concerts', 1),
       ('cultural_events.theatre', 'cultural_events', 'Theatre', 2),
       ('cultural_events.museums_exhibitions', 'cultural_events', 'Museums and exhibitions', 3),
       ('cultural_events.film', 'cultural_events', 'Film screenings', 4),
       ('cultural_events.festivals', 'cultural_events', 'Local festivals', 5),
       ('cultural_events.food', 'cultural_events', 'Food festivals', 6),
       ('cultural_events.dance', 'cultural_events', 'Dance performances', 7),
       ('cultural_events.literature', 'cultural_events', 'Literary events', 8);