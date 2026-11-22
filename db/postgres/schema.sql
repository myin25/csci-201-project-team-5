-- USC Ticket Auction: PostgreSQL schema (production, Supabase)
-- Paste into the Supabase SQL Editor and click Run.
-- Same tables and columns as db/mysql/schema.sql; keep the two in sync.
--
-- Differences from the MySQL version:
--   * ENUM columns are VARCHAR + CHECK (simpler than Postgres CREATE TYPE)
--   * AUTO_INCREMENT -> GENERATED ALWAYS AS IDENTITY, DATETIME -> TIMESTAMP, TINYINT -> SMALLINT
--   * unquoted names are lowercase in Postgres, so "Users" becomes users; SQL written
--     as SELECT * FROM Users still works because Postgres lowercases it too
--   * timestamps are UTC (Supabase runs in UTC; defaults use now() AT TIME ZONE 'utc')
--
-- Re-running drops every table first.

DROP TABLE IF EXISTS auctionparticipants, auctionqueue, tickets, notifications, transactions,
                     bids, listings, games, loginattempts, emailverifications, users CASCADE;

CREATE TABLE users (
  user_id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username        VARCHAR(30)  NOT NULL UNIQUE,
  email           VARCHAR(100) NOT NULL UNIQUE,
  password_hash   CHAR(60)     NOT NULL,
  first_name      VARCHAR(50)  NOT NULL,
  last_name       VARCHAR(50)  NOT NULL,
  profile_pic_url VARCHAR(255) NULL,
  is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
  role            VARCHAR(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','ADMIN')),
  balance         DECIMAL(10,2) NOT NULL DEFAULT 1000.00,
  last_login      TIMESTAMP    NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
  CONSTRAINT chk_users_usc_email CHECK (email LIKE '%@usc.edu'),
  CONSTRAINT chk_users_balance   CHECK (balance >= 0)
);

CREATE TABLE emailverifications (
  verification_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email           VARCHAR(100) NOT NULL,
  user_id         INT          NULL REFERENCES users(user_id),
  token_hash      CHAR(64)     NOT NULL,
  type            VARCHAR(20)  NOT NULL CHECK (type IN ('SIGNUP','PASSWORD_RESET')),
  attempts        SMALLINT     NOT NULL DEFAULT 0,
  expires_at      TIMESTAMP    NOT NULL,
  used_at         TIMESTAMP    NULL
);
CREATE INDEX idx_verif_email ON emailverifications (email);

CREATE TABLE loginattempts (
  attempt_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username     VARCHAR(30) NOT NULL,
  ip_address   VARCHAR(45) NOT NULL,
  success      BOOLEAN     NOT NULL,
  attempted_at TIMESTAMP   NOT NULL
);
CREATE INDEX idx_login_user_time ON loginattempts (username, attempted_at);

CREATE TABLE games (
  game_id     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  opponent    VARCHAR(100) NOT NULL,
  game_date   TIMESTAMP    NOT NULL,
  location    VARCHAR(100) NOT NULL,
  description TEXT         NULL
);
CREATE INDEX idx_games_date ON games (game_date);

CREATE TABLE listings (
  listing_id        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  seller_id         INT           NOT NULL REFERENCES users(user_id),
  game_id           INT           NOT NULL REFERENCES games(game_id),
  section           VARCHAR(10)   NOT NULL,
  seat_row          VARCHAR(10)   NOT NULL,
  quantity          SMALLINT      NOT NULL,
  starting_price    DECIMAL(10,2) NOT NULL,
  current_price     DECIMAL(10,2) NOT NULL,
  highest_bidder_id INT           NULL REFERENCES users(user_id),
  start_time        TIMESTAMP     NOT NULL DEFAULT (now() AT TIME ZONE 'utc'),
  end_time          TIMESTAMP     NOT NULL,
  status            VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','SOLD','EXPIRED','CANCELLED')),
  CONSTRAINT chk_listings_qty    CHECK (quantity BETWEEN 1 AND 10),
  CONSTRAINT chk_listings_price  CHECK (starting_price > 0),
  CONSTRAINT chk_listings_window CHECK (end_time > start_time)
);
CREATE INDEX idx_listings_status_end ON listings (status, end_time);

CREATE TABLE bids (
  bid_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  listing_id INT           NOT NULL REFERENCES listings(listing_id),
  bidder_id  INT           NOT NULL REFERENCES users(user_id),
  amount     DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP(3)  NOT NULL
);
CREATE INDEX idx_bids_listing_amount ON bids (listing_id, amount);

CREATE TABLE transactions (
  transaction_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  listing_id     INT           NOT NULL UNIQUE REFERENCES listings(listing_id),
  buyer_id       INT           NOT NULL REFERENCES users(user_id),
  seller_id      INT           NOT NULL REFERENCES users(user_id),
  amount         DECIMAL(10,2) NOT NULL,
  payment_status VARCHAR(10)   NOT NULL CHECK (payment_status IN ('PENDING','COMPLETED','FAILED')),
  created_at     TIMESTAMP     NOT NULL
);

CREATE TABLE notifications (
  notification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id         INT          NOT NULL REFERENCES users(user_id),
  message         VARCHAR(255) NOT NULL,
  type            VARCHAR(20)  NOT NULL
                  CHECK (type IN ('OUTBID','WON','SOLD','EXPIRED','QUEUE_ADMITTED')),
  is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP    NOT NULL
);
CREATE INDEX idx_notif_user_read ON notifications (user_id, is_read);

CREATE TABLE tickets (
  ticket_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  listing_id INT         NOT NULL REFERENCES listings(listing_id),
  owner_id   INT         NOT NULL REFERENCES users(user_id),
  status     VARCHAR(12) NOT NULL CHECK (status IN ('LISTED','TRANSFERRED','RETURNED'))
);

CREATE TABLE auctionqueue (
  queue_id   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  listing_id INT         NOT NULL REFERENCES listings(listing_id),
  user_id    INT         NOT NULL REFERENCES users(user_id),
  position   INT         NOT NULL,
  joined_at  TIMESTAMP   NOT NULL,
  status     VARCHAR(10) NOT NULL CHECK (status IN ('WAITING','ADMITTED','LEFT')),
  CONSTRAINT uq_queue_listing_user UNIQUE (listing_id, user_id)
);

CREATE TABLE auctionparticipants (
  listing_id     INT       NOT NULL REFERENCES listings(listing_id),
  user_id        INT       NOT NULL REFERENCES users(user_id),
  joined_at      TIMESTAMP NOT NULL,
  last_active_at TIMESTAMP NOT NULL,
  PRIMARY KEY (listing_id, user_id)
);

-- Supabase exposes the public schema through its auto-generated REST API. Our Java backend
-- connects over JDBC instead, so turn on Row Level Security with no policies: this blocks
-- the public API from reading or writing these tables, while JDBC (postgres role) is unaffected.
ALTER TABLE users               ENABLE ROW LEVEL SECURITY;
ALTER TABLE emailverifications  ENABLE ROW LEVEL SECURITY;
ALTER TABLE loginattempts       ENABLE ROW LEVEL SECURITY;
ALTER TABLE games               ENABLE ROW LEVEL SECURITY;
ALTER TABLE listings            ENABLE ROW LEVEL SECURITY;
ALTER TABLE bids                ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions        ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications       ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets             ENABLE ROW LEVEL SECURITY;
ALTER TABLE auctionqueue        ENABLE ROW LEVEL SECURITY;
ALTER TABLE auctionparticipants ENABLE ROW LEVEL SECURITY;
