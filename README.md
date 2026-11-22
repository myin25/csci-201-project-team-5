# USC Ticket Auction (CSCI 201 Team 5)

A real-time marketplace where verified USC students auction football tickets. Java 21,
Jakarta Servlets and WebSockets on Tomcat 10.1, MySQL locally, PostgreSQL (Supabase) in
production, plain HTML/CSS/JS on the front end. The full design is in our Detailed Design doc.

## Project layout

```
pom.xml                         Maven build and every library we use
db/mysql/schema.sql             local database (MySQL 8)
db/postgres/schema.sql          production database (Supabase); keep in sync with the MySQL one
Dockerfile.vercel               production container for Vercel
src/main/java/edu/usc/team5/
  model/      plain objects for database rows (User, Listing, Bid, ...) and enums
  servlet/    HTTP endpoints under /api; every servlet extends BaseApiServlet
  service/    business rules; servlets call services, services call DAOs
  dao/        the only classes that run SQL; every DAO extends BaseDAO
  realtime/   WebSocket endpoint and auction rooms
  scheduler/  background jobs (closing auctions, sweeping idle bidders)
  util/       Db (connection pool), Json, Validator, Clock
src/main/resources/db.properties.example   template for your local DB settings
src/main/webapp/                pages (HTML, CSS, JS)
src/test/java/                  JUnit 5 tests
```

## First-time setup

You need JDK 21, MySQL 8, Eclipse IDE for Enterprise Java and Web Developers, and Tomcat 10.1
(or 11).

1. **Clone:** `git clone https://github.com/myin25/csci-201-project-team-5.git`
2. **Import into Eclipse:** File > Import > Maven > Existing Maven Projects, pick the cloned
   folder, Finish. Eclipse downloads every library from `pom.xml` automatically. If anything is
   red, right-click the project > Maven > Update Project.
3. **Create the database:** open `db/mysql/schema.sql` in MySQL Workbench and run it (lightning
   bolt). It creates the `ticket_auction` database with all 11 tables. Re-running it wipes and
   recreates everything.
4. **Point the app at your database:** copy `src/main/resources/db.properties.example` to
   `db.properties` in the same folder and put in your MySQL password. `db.properties` is
   gitignored, so it stays on your machine.
5. **Run it:** right-click the project > Run As > Run on Server > pick your Tomcat. Then open
   <http://localhost:8080/ticket-auction/api/health>. You should see
   `{"status":"ok","database":"MySQL"}`. If you see `"status":"down"`, re-check step 4.

## Running tests

- In Eclipse: right-click `src/test/java` > Run As > JUnit Test.
- Database tests are skipped unless you ask for them. They roll back everything they write, so
  they never leave rows behind: `RUN_DB_TESTS=true mvn test`

## How to add code (read before writing your first class)

**A DAO** extends `BaseDAO<YourModel>`, passes its table name and id column to `super(...)`, and
implements `mapRow`. Use the helpers (`queryList`, `queryOne`, `insert`, `update`, `queryLong`)
instead of writing JDBC boilerplate. There's a full example in the `BaseDAO` comment.

**A servlet** extends `BaseApiServlet` and is mapped with `@WebServlet("/api/...")`. Use
`readJson`, `writeJson`, `requireUserId`, and `pathId`. For an expected error, throw
`new ApiException(status, "message the user sees")`.

**Several writes that must all succeed or all fail** (placing a bid, closing an auction) go
inside `Db.inTransaction(conn -> { ... })`, passing `conn` to every DAO call.

**Rules that keep MySQL and Postgres both working:**

- Always use `?` placeholders. Never paste user input into SQL.
- Pass times as `Clock.now()` parameters. Never write `UTC_TIMESTAMP()` or `NOW()` in SQL.
- Write table names exactly as in the schema (`Users`, `Listings`).
- Stick to plain SQL both databases understand. `SELECT ... FOR UPDATE` works on both.
- Store money as `BigDecimal`, never `double`.
- Validate input with `Validator` on the server, even if the frontend already checked.

**Git:** work on your own branch (`git checkout -b yourname/feature`) and open a pull request
into `main`. Don't push straight to `main`.

## Production database (Supabase)

One person sets this up once, then shares the connection details privately (never in the repo).

1. Sign in at <https://supabase.com> and create a new project. Pick the US West region and save
   the database password it asks you to choose.
2. In the project, open **SQL Editor** > New query, paste all of `db/postgres/schema.sql`, and
   click **Run**. **Table Editor** should now list 11 tables. The script also turns on Row Level
   Security, which blocks Supabase's public REST API from touching our tables. Our Java backend
   connects with the database password, so it isn't affected.
3. Click **Connect** at the top of the project and copy the **Session pooler** connection details.
   Don't use the Direct connection, which Vercel may not be able to reach. Don't use the
   Transaction pooler either, because it breaks Java prepared statements. Turn them into these
   three values:

   ```
   DB_URL=jdbc:postgresql://<host from Supabase>:5432/postgres?sslmode=require
   DB_USERNAME=postgres.<your project ref>
   DB_PASSWORD=<the database password from step 1>
   ```

4. To test from your laptop, set those three as environment variables in your Tomcat run
   configuration (Run Configurations > Environment) and open `/api/health`. It should say
   `"database":"PostgreSQL"`.
5. In Vercel, add the same three variables plus `PORT=8080` under Project > Settings >
   Environment Variables.
