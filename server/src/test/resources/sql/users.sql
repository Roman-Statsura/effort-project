CREATE TABLE users(
  email text NOT NULL,
  hashedPassword text NOT NULL,
  firstName text,
  lastName text,
  company text,
  role text NOT NULL
);

ALTER TABLE
  users
ADD
  CONSTRAINT pk_users PRIMARY KEY (email);

INSERT INTO
  users (
    email,
    hashedPassword,
    firstName,
    lastName,
    company,
    role
  ) VALUES (
    'srs@yandex.ru',
    '$2a$10$OP8t8F306pjaLbrwXQEBHujRQ01vgBHQBdQ3Z.4aNl9QEYRLZGkdi',
    'Roman',
    'Statsura',
    'Unknown',
    'ADMIN'
  );

  INSERT INTO
  users (
    email,
    hashedPassword,
    firstName,
    lastName,
    company,
    role
  ) VALUES (
    'kalyam@yandex.ru',
    '$2a$10$4c3Sak/49QKXcdCrRYOMhOdWVgdpj9Tr5iy2xiymPS8dHfU/kJGc6',
    'Kalyam',
    'Jopin',
    'Yandex',
    'RECRUITER'
  );