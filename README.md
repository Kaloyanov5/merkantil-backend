# Merkantil Backend

Merkantil is a Spring Boot 3 service that exposes a REST API for a simulated stock-trading platform. It combines real-time market data from the Massive API with a MySQL-backed domain model for users, portfolios, orders, wallets, and transactions while relying on Redis for HTTP sessions, response caching, and rate-limit counters. Schedulers continuously refresh quotes and persist daily candles so that the front end always sees timely positions, movers, and historical prices. A WebSocket layer pushes live price updates to connected clients every 30 seconds.

## Key capabilities

* **Account & session management** – register, log in, log out, change password, and fetch the authenticated profile via `/api/auth` and `/api/users/me/**`. Authentication uses `BCryptPasswordEncoder`, DAO auth providers, and Redis-backed HTTP sessions with custom cookies. Active sessions can be listed and individually revoked from `/api/users/me/sessions`.

* **Login rate limiting** – failed login attempts are tracked in Redis (5 attempts per 15 minutes per email). When the limit is hit the API responds with **HTTP 429** plus a `Retry-After` header (seconds) and a `retryAfterSeconds` field in the body so the frontend can show an accurate countdown.

* **Email verification & password reset** – on registration a verification link is emailed; forgot-password sends a 6-digit OTP valid for 15 minutes. User enumeration is prevented — the forgot-password flow always returns 200 regardless of whether the email exists.

* **Two-factor authentication (2FA)** – users can enable email-based OTP 2FA. On login a temporary token is issued; the OTP must be verified within 5 minutes to complete the session.

* **Google OAuth2 login** – users can sign in with their Google account; new users are auto-provisioned and existing accounts are linked by email.

* **Wallet operations** – deposit, withdraw, and transfer funds between users, with paginated history under `/api/users/me/wallet/history`. Per-transaction caps: **$25,000 max deposit**, **$10,000 max withdrawal**.

* **Payment methods** – save and remove cards used for deposits via `/api/users/me/payment-methods` (soft-deleted via `deleted_at`).

* **Watchlist** – add, remove, and list watched stocks (`/api/watchlist`). The GET endpoint returns each watched symbol enriched with a current quote, batched into a single market-data call.

* **Market data APIs** – list/search stocks, fetch quotes, movers, sectors, market status, OHLC history, and market news (with optional ticker filter and sentiment analysis), with Redis caches to stay within API rate limits.

* **Real-time price updates** – a STOMP WebSocket endpoint (`/ws`) broadcasts a `{ symbol → price }` map to all connected clients every 30 seconds after the scheduler refreshes prices. No polling required on the frontend.

* **Order & transaction lifecycle** – place market or limit buy/sell orders, cancel open limit orders, inspect paged history, and filter by symbol. Limit BUY orders reserve funds on placement; if executed below the limit price the difference is refunded. Limit SELL orders auto-cancel if shares are no longer available when the condition is met.

* **Portfolio insights** – view holdings, per-symbol positions, user-level summaries, and historical portfolio growth charts reconstructed from actual trading-day close prices.

* **Admin tooling** – stock universe management (`/api/admin/stocks/**`) and user administration (`/api/admin/users/**`) including per-user transactions, portfolio, orders, wallet, sessions, ban/unban, and platform-wide stats. All admin routes are guarded by `ROLE_ADMIN`.

* **Scheduled maintenance** – recurring jobs refresh intraday quotes every 30 seconds, capture end-of-day snapshots at 12:30 AM local time on trading days (Tuesday–Saturday, covering Mon–Fri US market closes), and fill historical gaps at 5 AM daily.

## Stack & architecture

| Layer | Details |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.6 with scheduling, caching, and JPA auditing enabled in `MerkantilApplication`. |
| Data | MySQL 8 for persistence (Spring Data JPA), Redis for HTTP sessions, cache layers, OTP/token storage with TTL, and login-attempt rate-limit counters. |
| Migrations | **Flyway** manages schema. Existing environments are baselined at V1 (`baseline-on-migrate=true`, `baseline-version=1`); fresh databases run `V1__initial_schema.sql`. JPA runs in `validate` mode so the schema is owned by Flyway, not Hibernate. |
| API | Spring MVC controllers grouped by domain (`controller`, `service`, `repository`, `dto`, `entity`). Security uses role-based method annotations for admin-only routes. |
| Auth | Session-based auth + Spring Security, plus Google OAuth2 (`spring-boot-starter-oauth2-client`) and remember-me. |
| WebSocket | STOMP over SockJS (`/ws`) with a simple in-memory broker. Broadcasts stock price updates to `/topic/prices` after each scheduler batch. |
| Integrations | Massive API consumed through a dedicated `WebClient` service (`MassiveApiService`) with caching and resilient fallbacks. |
| Email | Spring Mail (Gmail SMTP) for verification, password reset, and 2FA OTP emails. |
| Docs | OpenAPI/Swagger UI via `springdoc-openapi-starter-webmvc-ui` at `/swagger-ui.html`. |

## Prerequisites

1. **Java 21** (matching the Maven compiler target) and **Maven 3.9+**.
2. **MySQL 8** (or compatible) instance and database for the app schema.
3. **Redis 6+** instance for Spring Session, cache storage, OTP key storage, and rate-limit counters.
4. **Massive API key** with market data entitlements.
5. **Gmail account** with an App Password for SMTP email sending.
6. **Google OAuth client** (Client ID + Secret) if you want Google sign-in enabled.

## Configuration

Configuration lives in `src/main/resources/application.yml` with profile overlays in `application-dev.yml` (default) and `application-prod.yml`. The profile is selected via `SPRING_PROFILES_ACTIVE` (defaults to `dev`).

The committed `application.yml` is reproduced below for reference — only the env vars need to be supplied.

```yaml
spring:
  application:
    name: merkantil
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  datasource:
    url: jdbc:mysql://localhost:3306/merkantil_db?createDatabaseIfNotExist=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true

  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    locations: classpath:db/migration

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      timeout: 3000ms

  session:
    store-type: redis
    timeout: 86400s # 24 hours
    redis:
      namespace: merkantil:session

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: email, profile

  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

server:
  port: ${SERVER_PORT:8080}
  servlet:
    session:
      cookie:
        name: MERKANTIL_SESSION
        http-only: true
        max-age: 86400
        # secure + same-site come from the active profile

stock:
  api:
    provider: massive

massive:
  api:
    key: ${MASSIVE_API_KEY}
    base-url: https://api.massive.com
    timeout: 10000

remember:
  me:
    key: ${REMEMBER_ME_KEY}

app:
  frontend:
    url: ${FRONTEND_URL:http://localhost:5173}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
```

### Profiles

* **`dev`** (default) — `Secure=false`, `SameSite=Lax` cookies; verbose app/security logging. Run plain over HTTP for local development.
* **`prod`** — `Secure=true`, `SameSite=None` cookies (required when frontend and API live on different domains, since browsers only accept `SameSite=None` together with `Secure`); INFO-level logging. **Requires HTTPS.**

Activate prod by exporting `SPRING_PROFILES_ACTIVE=prod` in the deploy environment.

### Required environment variables

| Variable | Notes |
| --- | --- |
| `DB_PASSWORD` | MySQL password for the configured user. |
| `DB_USERNAME` | Optional, defaults to `root`. |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection. Host/port have local defaults; password is required if Redis has auth enabled. |
| `MASSIVE_API_KEY` | Market data API key. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Gmail address + **App Password** (not the account password). Generate one under Google Account → Security → 2-Step Verification → App passwords. |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth credentials. |
| `REMEMBER_ME_KEY` | Server-side secret used to sign remember-me tokens. |
| `FRONTEND_URL` | Origin used for redirect/login flows; defaults to `http://localhost:5173`. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins permitted by CORS; defaults to common Vite/CRA dev ports. Add your production frontend origin here. |
| `SERVER_PORT` | Defaults to `8080`. |
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod`; defaults to `dev`. |

When running locally, a `.env` file or IDE run configuration can export these variables. Keep `.env` out of version control.

## Running the service

```bash
./mvnw clean spring-boot:run
```

The application listens on `http://localhost:8080` by default. Flyway will run on first boot — on a fresh database the V1 baseline creates every table; on an existing database the schema is baselined and only future migrations run.

First register a user via `POST /api/auth/register`, verify the email via the link sent to your inbox, log in with `POST /api/auth/login`, then reuse the returned session cookie for authenticated routes.

### Running tests

```bash
./mvnw test
```

### API documentation

With the app running, visit:

* **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## API highlights

| Area | Example endpoints |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/verify-email?token=`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, `POST /api/auth/verify-2fa`, `POST /api/auth/2fa/enable`, `POST /api/auth/2fa/disable`. Login responds **HTTP 429** with a `Retry-After` header after 5 failed attempts in 15 minutes. |
| Stocks | `GET /api/stocks`, `GET /api/stocks/{symbol}/quote`, `POST /api/stocks/quotes` (batch, max 30), `GET /api/stocks/movers/{type}`, `GET /api/stocks/market-status`, `GET /api/stocks/news?ticker=&limit=`. |
| Stocks (browse) | `GET /api/stocks/search?q=`, `GET /api/stocks/sectors`, `GET /api/stocks/sector/{sector}`, `GET /api/stocks/{symbol}/history?startDate=&endDate=`. |
| Watchlist | `GET /api/watchlist` (returns enriched quotes), `POST /api/watchlist/{symbol}`, `DELETE /api/watchlist/{symbol}`. |
| Portfolio | `GET /api/portfolio`, `GET /api/portfolio/summary`, `GET /api/portfolio/{symbol}`, `GET /api/portfolio/growth`, `GET /api/portfolio/growth/range?startDate=&endDate=`. |
| Orders | `POST /api/orders`, `GET /api/orders`, `GET /api/orders/symbol/{symbol}`, `DELETE /api/orders/{id}` (cancel open limit order). |
| Transactions | `GET /api/transactions`, `GET /api/transactions/type/{type}`, `GET /api/transactions/stats`. |
| Wallet & profile | `GET /api/users/me/balance`, `POST /api/users/{id}/deposit` (≤ $25k), `POST /api/users/{id}/withdraw` (≤ $10k), `POST /api/users/me/transfer`, `GET /api/users/me/wallet/history`, `POST /api/users/me/change-password`. |
| Sessions | `GET /api/users/me/sessions`, `DELETE /api/users/me/sessions/{sessionId}`. |
| Payment methods | `GET /api/users/me/payment-methods`, `POST /api/users/me/payment-methods`, `DELETE /api/users/me/payment-methods/{id}`. |
| User lookup | `GET /api/users/lookup?email=` (used by transfer flow). |
| Admin – Users | `GET /api/users`, `GET /api/users/search`, `GET /api/admin/users/{id}/transactions`, `GET /api/admin/users/{id}/portfolio`, `GET /api/admin/users/{id}/orders`, `GET /api/admin/users/{id}/wallet`, `GET /api/admin/users/{id}/sessions`, `POST /api/admin/users/{id}/ban`, `POST /api/admin/users/{id}/unban`, `GET /api/admin/users/stats`. |
| Admin – Stocks | `POST /api/admin/stocks/import/all`, `POST /api/admin/stocks/import/top?limit=`, `POST /api/admin/stocks/import/single`, `POST /api/admin/stocks/import/multiple`, `POST /api/admin/stocks/import/update-prices`, `POST /api/admin/stocks/import/backfill`, `POST /api/admin/stocks/import/backfill-single`. All protected with `ROLE_ADMIN`. |

Use a REST client (Hoppscotch, Postman, Thunder Client) that can persist session cookies to simplify authenticated testing. For admin operations, manually update the `role` column of your user to `ADMIN` or seed via SQL.

## WebSocket

Connect to `ws://localhost:8080/ws` using STOMP over SockJS and subscribe to `/topic/prices` to receive live price updates:

```js
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
    webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
        client.subscribe('/topic/prices', (message) => {
            const prices = JSON.parse(message.body);
            // { "AAPL": 189.45, "TSLA": 245.10, ... }
        });
    }
});
client.activate();
```

Updates are broadcast every 30 seconds during market hours. The connection requires no authentication — price data is public.

## Orders

Both market and limit orders are supported.

**Market orders** execute immediately at the current live price.

**Limit orders** are placed as `OPEN` and monitored by the scheduler:
- **BUY**: funds are reserved on placement. Executes when `currentPrice ≤ limitPrice`. Any price difference below the limit is refunded.
- **SELL**: shares are validated on placement. Executes when `currentPrice ≥ limitPrice`. Auto-cancels if shares are gone by execution time.

Cancel an open limit order via `DELETE /api/orders/{id}` — reserved BUY funds are refunded.

## Background jobs & caching

* `StockPriceScheduler` runs multiple jobs: 30-second intraday price refreshes (batched in groups of 10) with WebSocket broadcast after each batch, EOD snapshots at 12:30 AM local (Tuesday–Saturday), and 5 AM historical gap backfills.
* Redis caches wrap `StockService` and `NewsService` responses (1-minute default TTL, 5 minutes for news) to reduce external API usage, with explicit cache eviction during scheduled refreshes.
* `@EnableScheduling`, `@EnableCaching`, and `@EnableJpaAuditing` are activated at the application entry point.

## Portfolio growth

The `/api/portfolio/growth` endpoints reconstruct historical portfolio value using actual OHLCV close prices from the database — never current prices for past dates. Values cover trading days only (weekends excluded), which means results are stable and repeatable. Missing price data falls back to the most recent available close.

## Database migrations

Schema is owned by **Flyway**. Migration files live in `src/main/resources/db/migration/` and follow the `V<n>__description.sql` naming convention. To add a new migration, create the next versioned file (e.g. `V2__add_some_column.sql`) and reboot the app — Flyway applies it automatically. Hibernate runs in `validate` mode so the running entities must match the migrated schema; mismatches fail fast at startup.

## Tips for development

* Start MySQL and Redis before running the JVM; otherwise Spring Boot will fail at startup.
* Use the admin import endpoints to populate baseline stock data, then allow the scheduler to keep it fresh.
* Because HTTP sessions are server-side, run the backend and client on the same host (or configure CORS + cookie domain) to share the `MERKANTIL_SESSION` cookie. Add the frontend origin to `CORS_ALLOWED_ORIGINS`.
* If you get locked out of login during testing, clear the rate-limit key from Redis: `redis-cli DEL auth:attempts:login:your@email.com`.
* When testing integrations, start with `import/top?limit=50` to seed a small but representative stock universe before running a full import.
* The portfolio growth chart requires data in the `stock_price_history` table. If values appear flat across days, trigger a manual backfill via the admin backfill endpoint.

## Contributing

1. Fork the repository and create a feature branch.
2. Run `./mvnw test` to ensure the suite still passes.
3. Open a PR that describes your change set and any configuration impacts (especially new env vars or migrations).

Bug reports and feature ideas are welcome — please include reproduction steps, logs, or stack traces when relevant.

## License

Copyright 2026 Bozhidar Kaloyanov

The source code in this repository is licensed under the **Apache License, Version 2.0** — see the [LICENSE](LICENSE) and [NOTICE](NOTICE) files. You can obtain a copy of the license at <http://www.apache.org/licenses/LICENSE-2.0>.
