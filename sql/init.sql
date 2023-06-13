CREATE DATABASE effort;

\c effort;

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

CREATE TABLE recoverytokens (
  email text NOT NULL,
  token text NOT NULL,
  expiration bigint NOT NULL
);

ALTER TABLE
  recoverytokens
ADD
  CONSTRAINT pk_recoverytokens PRIMARY KEY (email);