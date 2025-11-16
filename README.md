# Merkantil Backend (work in progress)

Merkantil is a Spring Boot 3 service that exposes a REST API for a simulated stock-trading platform. It combines real-time market data from the Alpaca API with a MySQL-backed domain model for users, portfolios, orders, and transactions while relying on Redis for HTTP sessions and response caching. Schedulers continuously refresh quotes and persist daily candles so that the front end always sees timely positions, movers, and historical prices.

## Key capabilities

* **Account & session management** – register, log in, and fetch the authenticated profile via `/api/auth` endpoints. Authentication uses `BCryptPasswordEncoder`, DAO auth providers, and Redis-backed HTTP sessions with custom cookies.
  
* **Market data APIs** – list/search stocks, fetch quotes, movers, sectors, and OHLC history, with Redis caches to stay within Alpaca rate limits.
  
* **Order & transaction lifecycle** – place buy/sell orders, inspect paged history, filter by symbol/type, and see aggregate statistics to power portfolio dashboards.
  
* **Portfolio insights** – view holdings, per-symbol positions, and user-level summaries derived from holdings/transactions logic.
  
* **Admin tooling & imports** – seed/update the stock universe, backfill historical prices, and manage stock metadata via `/api/admin/stocks/**` endpoints with role checks.
  
* **Scheduled maintenance** – recurring jobs refresh intraday quotes, capture end-of-day snapshots, and fill historical gaps before markets open.

## Stack & architecture

| Layer | Details |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.6 with scheduling, caching, and JPA auditing enabled in `MerkantilApplication`. |
| Data | MySQL for persistence (Spring Data JPA), Redis for HTTP sessions and cache layers. |
| API | Spring MVC controllers grouped by domain (`controller`, `service`, `repository`, `dto`, `entity`). Security uses role-based method annotations for admin-only routes. |
| Integrations | Alpaca Market Data/Trading API consumed through a dedicated `WebClient` service with caching and resilient fallbacks. |

## Prerequisites

1. **Java 21** (matching the Maven compiler target) and **Maven 3.9+**.
2. **MySQL 8** (or compatible) instance and database for the app schema.
3. **Redis 6+** instance for Spring Session and cache storage.
4. **Alpaca API credentials** with market data entitlements.

## Configuration

Create `src/main/resources/application.yml` (or use environment variables) with the following sections:

```yaml
spring:
  application:
    name: merkantil

  datasource:
    url: jdbc:mysql://localhost:3306/${DB_NAME}?createDatabaseIfNotExist=true # Configure the url and port as well
    username: ${DB_USERNAME}
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

  # Redis Configuration
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}
      timeout: 3000ms

  # Session Configuration
  session:
    store-type: redis
    timeout: 86400s # 24 hours
    redis:
      namespace: merkantil:session

# Server Configuration
server:
  port: 8080
  servlet:
    session:
      cookie:
        name: MERKANTIL_SESSION
        http-only: true
        secure: false 
        same-site: lax
        max-age: 86400 # 24 hours

stock:
  api:
    provider: alpaca

alpaca:
  api:
    key-id: ${ALPACA_API_KEY}
    secret-key: ${ALPACA_SECRET_KEY}
    base-url: https://paper-api.alpaca.markets/v2
    data-url: https://data.alpaca.markets
    timeout: 10000  # 10 seconds
```

* Use a dedicated database user with appropriate privileges.
* Set `spring.jpa.hibernate.ddl-auto=validate` in production and manage schema via migrations.
* When running locally, a `.env` file or IDE run configuration can export `APCA_API_KEY_ID` and `APCA_API_SECRET_KEY` so `AlpacaApiService` picks them up via `@Value` injection.

## Running the service

```bash
./mvnw clean spring-boot:run
```

The application listens on `http://localhost:8080` by default. First register a user via `POST /api/auth/register`, log in with `POST /api/auth/login`, then reuse the returned session cookie for authenticated routes.

### Running tests

```bash
./mvnw test
```

## API highlights

| Area | Example endpoints |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` for current profile info. |
| Stocks | `GET /api/stocks`, `GET /api/stocks/{symbol}/quote`, `POST /api/stocks/quotes` for batch quotes, `GET /api/stocks/movers/{type}` for gainers/losers/active lists.|
| Portfolio | `GET /api/portfolio`, `GET /api/portfolio/summary`, `GET /api/portfolio/{symbol}` for single positions. |
| Orders & Transactions | `POST /api/orders`, `GET /api/orders`, `GET /api/transactions`, `GET /api/transactions/type/{type}`, `GET /api/transactions/stats`. |
| Admin | `POST /api/admin/stocks/import/all`, `POST /api/admin/stocks/import/backfill`, `POST /api/admin/stocks`, `PUT /api/admin/stocks/{symbol}`, all protected with `ROLE_ADMIN`. |

Use a REST client (Hoppscotch, Postman, Thunder Client) that can persist session cookies to simplify authenticated testing. For admin operations, manually update the `role` column of your user to `ADMIN` or seed via SQL.

## Background jobs & caching

* `StockPriceScheduler` runs multiple cron tasks: 30-second intraday refreshes, 4:05 PM EOD snapshots, and 5 AM historical gap backfills.
* Redis caches wrap `StockService` and `AlpacaApiService` responses for one minute by default to reduce Alpaca API usage, with explicit cache eviction during scheduled refreshes.
* `@EnableScheduling`, `@EnableCaching`, and `@EnableJpaAuditing` are activated at the application entry point.

## Tips for development

* Start MySQL and Redis before running the JVM; otherwise Spring Boot will fail at startup.
* Use the admin import endpoints to populate baseline stock data, then allow the scheduler to keep it fresh.
* Because HTTP sessions are server-side, run the backend and client on the same host (or configure CORS + cookie domain) to share the `MERKANTIL_SESSION` cookie.
* When testing Alpaca integrations, consider paper trading credentials and limit `StockImportController` batch sizes to avoid quota exhaustion.

## Contributing

1. Fork the repository and create a feature branch.
2. Run `./mvnw test` to ensure the suite still passes.
3. Open a PR that describes your change set and any configuration impacts.

Bug reports and feature ideas are welcome—please include reproduction steps, logs, or stack traces when relevant.
