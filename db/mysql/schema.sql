-- USC Ticket Auction: MySQL 8 schema (local development)
-- Run in MySQL Workbench or: mysql -u root -p < db/mysql/schema.sql
-- WARNING: drops and recreates the ticket_auction database.
--
-- Conventions (from the Detailed Design):
--   * every money column is DECIMAL(10,2)
--   * every timestamp is stored in UTC (the JDBC URL sets the session time zone to UTC)
--   * the Postgres copy for Supabase lives in db/postgres/schema.sql; keep the two in sync

DROP DATABASE IF EXISTS ticket_auction;
CREATE DATABASE ticket_auction CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ticket_auction;

CREATE TABLE Users (
  user_id         INT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(30)  NOT NULL UNIQUE,
  email           VARCHAR(100) NOT NULL UNIQUE,
  password_hash   CHAR(60)     NOT NULL,
  first_name      VARCHAR(50)  NOT NULL,
  last_name       VARCHAR(50)  NOT NULL,
  profile_pic_url VARCHAR(255) NULL,
  is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
  role            ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  balance         DECIMAL(10,2) NOT NULL DEFAULT 1000.00,
  last_login      DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_users_usc_email CHECK (email LIKE '%@usc.edu'),
  CONSTRAINT chk_users_balance   CHECK (balance >= 0)
) ENGINE=InnoDB;

-- Written before a user row may exist, so keyed by email (user_id is NULL until the account exists)
CREATE TABLE EmailVerifications (
  verification_id INT AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(100) NOT NULL,
  user_id         INT          NULL,
  token_hash      CHAR(64)     NOT NULL,          -- SHA-256 of the 6-digit code
  type            ENUM('SIGNUP','PASSWORD_RESET') NOT NULL,
  attempts        TINYINT      NOT NULL DEFAULT 0,
  expires_at      DATETIME     NOT NULL,          -- created + 10 minutes
  used_at         DATETIME     NULL,
  INDEX idx_verif_email (email),
  CONSTRAINT fk_verif_user FOREIGN KEY (user_id) REFERENCES Users(user_id)
) ENGINE=InnoDB;

CREATE TABLE LoginAttempts (
  attempt_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  username     VARCHAR(30) NOT NULL,
  ip_address   VARCHAR(45) NOT NULL,
  success      BOOLEAN     NOT NULL,
  attempted_at DATETIME    NOT NULL,
  INDEX idx_login_user_time (username, attempted_at)
) ENGINE=InnoDB;

CREATE TABLE Games (
  game_id     INT AUTO_INCREMENT PRIMARY KEY,
  opponent    VARCHAR(100) NOT NULL,
  game_date   DATETIME     NOT NULL,
  location    VARCHAR(100) NOT NULL,
  description TEXT         NULL,
  INDEX idx_games_date (game_date)
) ENGINE=InnoDB;

-- end_time <= the game's kickoff is checked in Validator.isValidEndTime (CHECK can't read another table)
CREATE TABLE Listings (
  listing_id        INT AUTO_INCREMENT PRIMARY KEY,
  seller_id         INT          NOT NULL,
  game_id           INT          NOT NULL,
  section           VARCHAR(10)  NOT NULL,
  seat_row          VARCHAR(10)  NOT NULL,         -- ROW is reserved in MySQL 8
  quantity          TINYINT      NOT NULL,
  starting_price    DECIMAL(10,2) NOT NULL,
  current_price     DECIMAL(10,2) NOT NULL,        -- starts at starting_price
  highest_bidder_id INT          NULL,             -- NULL until the first bid
  start_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_time          DATETIME     NOT NULL,
  status            ENUM('ACTIVE','SOLD','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  INDEX idx_listings_status_end (status, end_time),
  CONSTRAINT fk_listings_seller  FOREIGN KEY (seller_id)         REFERENCES Users(user_id),
  CONSTRAINT fk_listings_game    FOREIGN KEY (game_id)           REFERENCES Games(game_id),
  CONSTRAINT fk_listings_bidder  FOREIGN KEY (highest_bidder_id) REFERENCES Users(user_id),
  CONSTRAINT chk_listings_qty    CHECK (quantity BETWEEN 1 AND 10),
  CONSTRAINT chk_listings_price  CHECK (starting_price > 0),
  CONSTRAINT chk_listings_window CHECK (end_time > start_time)
) ENGINE=InnoDB;

CREATE TABLE Bids (
  bid_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  listing_id INT           NOT NULL,
  bidder_id  INT           NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  created_at DATETIME(3)   NOT NULL,              -- millisecond precision breaks ties
  INDEX idx_bids_listing_amount (listing_id, amount),
  CONSTRAINT fk_bids_listing FOREIGN KEY (listing_id) REFERENCES Listings(listing_id),
  CONSTRAINT fk_bids_bidder  FOREIGN KEY (bidder_id)  REFERENCES Users(user_id)
) ENGINE=InnoDB;

CREATE TABLE Transactions (
  transaction_id INT AUTO_INCREMENT PRIMARY KEY,
  listing_id     INT           NOT NULL UNIQUE,   -- one sale per listing
  buyer_id       INT           NOT NULL,
  seller_id      INT           NOT NULL,
  amount         DECIMAL(10,2) NOT NULL,
  payment_status ENUM('PENDING','COMPLETED','FAILED') NOT NULL,
  created_at     DATETIME      NOT NULL,
  CONSTRAINT fk_tx_listing FOREIGN KEY (listing_id) REFERENCES Listings(listing_id),
  CONSTRAINT fk_tx_buyer   FOREIGN KEY (buyer_id)   REFERENCES Users(user_id),
  CONSTRAINT fk_tx_seller  FOREIGN KEY (seller_id)  REFERENCES Users(user_id)
) ENGINE=InnoDB;

CREATE TABLE Notifications (
  notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         INT          NOT NULL,
  message         VARCHAR(255) NOT NULL,
  type            ENUM('OUTBID','WON','SOLD','EXPIRED','QUEUE_ADMITTED') NOT NULL,
  is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at      DATETIME     NOT NULL,
  INDEX idx_notif_user_read (user_id, is_read),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES Users(user_id)
) ENGINE=InnoDB;

-- One row per seat: a listing with quantity 4 has four Tickets rows
CREATE TABLE Tickets (
  ticket_id  INT AUTO_INCREMENT PRIMARY KEY,
  listing_id INT NOT NULL,
  owner_id   INT NOT NULL,                        -- seller until sold
  status     ENUM('LISTED','TRANSFERRED','RETURNED') NOT NULL,
  CONSTRAINT fk_tickets_listing FOREIGN KEY (listing_id) REFERENCES Listings(listing_id),
  CONSTRAINT fk_tickets_owner   FOREIGN KEY (owner_id)   REFERENCES Users(user_id)
) ENGINE=InnoDB;

CREATE TABLE AuctionQueue (
  queue_id   INT AUTO_INCREMENT PRIMARY KEY,
  listing_id INT      NOT NULL,
  user_id    INT      NOT NULL,
  position   INT      NOT NULL,
  joined_at  DATETIME NOT NULL,
  status     ENUM('WAITING','ADMITTED','LEFT') NOT NULL,
  UNIQUE KEY uq_queue_listing_user (listing_id, user_id),
  CONSTRAINT fk_queue_listing FOREIGN KEY (listing_id) REFERENCES Listings(listing_id),
  CONSTRAINT fk_queue_user    FOREIGN KEY (user_id)    REFERENCES Users(user_id)
) ENGINE=InnoDB;

CREATE TABLE AuctionParticipants (
  listing_id     INT      NOT NULL,
  user_id        INT      NOT NULL,
  joined_at      DATETIME NOT NULL,
  last_active_at DATETIME NOT NULL,               -- refreshed by WebSocket heartbeat
  PRIMARY KEY (listing_id, user_id),
  CONSTRAINT fk_part_listing FOREIGN KEY (listing_id) REFERENCES Listings(listing_id),
  CONSTRAINT fk_part_user    FOREIGN KEY (user_id)    REFERENCES Users(user_id)
) ENGINE=InnoDB;
