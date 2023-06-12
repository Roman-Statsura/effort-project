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
    'roman@yandex.ru',
    '$2a$10$yZor4QozB12x2BoofGmZy.6yOLKpVg4uw2WMpitNSHqCEzsKL/icW',
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
    'kalyam@yandex.com',
    '$2a$10$8a8R24PzhZOInGYR.35EM.j3b4wBi8/os.Si2SUd.CrREBF4wR7sG',
    'Kalyam',
    'Jopin',
    'Yandex',
    'RECRUITER'
  );