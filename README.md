# Merkantil Backend (work in progress)

Merkantil is a Spring Boot 3 service that exposes a REST API for a simulated stock-trading platform. It combines real-time market data from the Massive API with a MySQL-backed domain model for users, portfolios, orders, and transactions while relying on Redis for HTTP sessions and response caching. Schedulers continuously refresh quotes and persist daily candles so that the front end always sees timely positions, movers, and historical prices. A WebSocket layer pushes live price updates to connected clients every 30 seconds.

## Key capabilities

* **Account & session management** – register, log in, and fetch the authenticated profile via `/api/auth` endpoints. Authentication uses `BCryptPasswordEncoder`, DAO auth providers, and Redis-backed HTTP sessions with custom cookies.

* **Email verification & password reset** – on registration a verification link is emailed; forgot-password sends a 6-digit OTP valid for 15 minutes. User enumeration is prevented — the forgot-password flow always returns 200 regardless of whether the email exists.

* **Two-factor authentication (2FA)** – users can enable email-based OTP 2FA. On login a temporary token is issued; the OTP must be verified within 5 minutes to complete the session.

* **Market data APIs** – list/search stocks, fetch quotes, movers, sectors, market status, OHLC history, and market news (with optional ticker filter and sentiment analysis), with Redis caches to stay within API rate limits.

* **Real-time price updates** – a STOMP WebSocket endpoint (`/ws`) broadcasts a `{ symbol → price }` map to all connected clients every 30 seconds after the scheduler refreshes prices. No polling required on the frontend.

* **Order & transaction lifecycle** – place market or limit buy/sell orders, cancel open limit orders, inspect paged history, and filter by symbol. Limit BUY orders reserve funds on placement; if executed below the limit price the difference is refunded. Limit SELL orders auto-cancel if shares are no longer available when the condition is met.

* **Portfolio insights** – view holdings, per-symbol positions, user-level summaries, and historical portfolio growth charts reconstructed from actual trading-day close prices.

* **User account management** – deposit/withdraw funds and query balances via `/api/users` endpoints with role-based access controls.

* **Admin tooling & imports** – seed/update the stock universe, backfill historical prices, and manage stock metadata via `/api/admin/stocks/**` endpoints with role checks.

* **Scheduled maintenance** – recurring jobs refresh intraday quotes every 30 seconds, capture end-of-day snapshots at 12:30 AM local time on trading days (Tuesday–Saturday, covering Mon–Fri US market closes), and fill historical gaps at 5 AM daily.

## Stack & architecture

| Layer | Details |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.6 with scheduling, caching, and JPA auditing enabled in `MerkantilApplication`. |
| Data | MySQL for persistence (Spring Data JPA), Redis for HTTP sessions, cache layers, and OTP/token storage with TTL. |
| API | Spring MVC controllers grouped by domain (`controller`, `service`, `repository`, `dto`, `entity`). Security uses role-based method annotations for admin-only routes. |
| WebSocket | STOMP over SockJS (`/ws`) with a simple in-memory broker. Broadcasts stock price updates to `/topic/prices` after each scheduler batch. |
| Integrations | Massive API consumed through a dedicated `WebClient` service (`MassiveApiService`) with caching and resilient fallbacks. |
| Email | Spring Mail (Gmail SMTP) for verification, password reset, and 2FA OTP emails. |

## Prerequisites

1. **Java 21** (matching the Maven compiler target) and **Maven 3.9+**.
2. **MySQL 8** (or compatible) instance and database for the app schema.
3. **Redis 6+** instance for Spring Session, cache storage, and OTP key storage.
4. **Massive API key** with market data entitlements.
5. **Gmail account** with an App Password for SMTP email sending.

## Configuration

Create `src/main/resources/application.yml` (or use environment variables) with the following sections:

```yaml
spring:
  application:
    name: merkantil

  datasource:
    url: jdbc:mysql://localhost:3306/merkantil_db?createDatabaseIfNotExist=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        format_sql: true

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
  port: 8080
  servlet:
    session:
      cookie:
        name: MERKANTIL_SESSION
        http-only: true
        secure: false # Set to true in production with HTTPS
        same-site: lax
        max-age: 86400 # 24 hours

stock:
  api:
    provider: massive

massive:
  api:
    key: ${MASSIVE_API_KEY}
    base-url: https://api.massive.com
    timeout: 10000  # 10 seconds

remember:
  me:
    key: ${REMEMBER_ME_KEY}

app:
  frontend:
    url: ${FRONTEND_URL:http://localhost:5173}
```

* Use a dedicated database user with appropriate privileges.
* Set `spring.jpa.hibernate.ddl-auto=validate` in production and manage schema via migrations.
* `MAIL_PASSWORD` must be a Gmail App Password (not your account password). Generate one under Google Account → Security → 2-Step Verification → App passwords.
* When running locally, a `.env` file or IDE run configuration can export the required variables.

## Running the service

```bash
./mvnw clean spring-boot:run
```

The application listens on `http://localhost:8080` by default. First register a user via `POST /api/auth/register`, verify the email via the link sent to your inbox, log in with `POST /api/auth/login`, then reuse the returned session cookie for authenticated routes.

### Running tests

```bash
./mvnw test
```

## API highlights

| Area | Example endpoints |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/auth/verify-email?token=`, `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`, `POST /api/auth/verify-2fa`, `POST /api/auth/2fa/enable`, `POST /api/auth/2fa/disable`. |
| Stocks | `GET /api/stocks`, `GET /api/stocks/{symbol}/quote`, `POST /api/stocks/quotes` (batch, max 30), `GET /api/stocks/movers/{type}`, `GET /api/stocks/market-status`, `GET /api/stocks/news?ticker=&limit=`. |
| Stocks (browse) | `GET /api/stocks/search?q=`, `GET /api/stocks/sectors`, `GET /api/stocks/sector/{sector}`, `GET /api/stocks/{symbol}/history?startDate=&endDate=`. |
| Portfolio | `GET /api/portfolio`, `GET /api/portfolio/summary`, `GET /api/portfolio/{symbol}`, `GET /api/portfolio/growth`, `GET /api/portfolio/growth/range?startDate=&endDate=`. |
| Orders | `POST /api/orders`, `GET /api/orders`, `GET /api/orders/symbol/{symbol}`, `DELETE /api/orders/{id}` (cancel open limit order). |
| Transactions | `GET /api/transactions`, `GET /api/transactions/type/{type}`, `GET /api/transactions/stats`. |
| Users & Balances | `GET /api/users/me/balance`, `POST /api/users/{id}/deposit`, `POST /api/users/{id}/withdraw`. Admin: `GET /api/users`, `GET /api/users/search`. |
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

> **Note:** If upgrading from a version where `at_price` was NOT NULL, run: `ALTER TABLE orders MODIFY COLUMN at_price DOUBLE NULL;`

## Background jobs & caching

* `StockPriceScheduler` runs multiple jobs: 30-second intraday price refreshes (batched in groups of 10) with WebSocket broadcast after each batch, EOD snapshots at 12:30 AM local (Tuesday–Saturday), and 5 AM historical gap backfills.
* Redis caches wrap `StockService` and `NewsService` responses (1-minute default TTL, 5 minutes for news) to reduce external API usage, with explicit cache eviction during scheduled refreshes.
* `@EnableScheduling`, `@EnableCaching`, and `@EnableJpaAuditing` are activated at the application entry point.

## Portfolio growth

The `/api/portfolio/growth` endpoints reconstruct historical portfolio value using actual OHLCV close prices from the database — never current prices for past dates. Values cover trading days only (weekends excluded), which means results are stable and repeatable. Missing price data falls back to the most recent available close.

## Tips for development

* Start MySQL and Redis before running the JVM; otherwise Spring Boot will fail at startup.
* Use the admin import endpoints to populate baseline stock data, then allow the scheduler to keep it fresh.
* Because HTTP sessions are server-side, run the backend and client on the same host (or configure CORS + cookie domain) to share the `MERKANTIL_SESSION` cookie.
* When testing integrations, start with `import/top?limit=50` to seed a small but representative stock universe before running a full import.
* The portfolio growth chart requires data in the `stock_price_history` table. If values appear flat across days, trigger a manual backfill via the admin backfill endpoint.

## Contributing

1. Fork the repository and create a feature branch.
2. Run `./mvnw test` to ensure the suite still passes.
3. Open a PR that describes your change set and any configuration impacts.

Bug reports and feature ideas are welcome — please include reproduction steps, logs, or stack traces when relevant.
