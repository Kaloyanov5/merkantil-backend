# Merkantil Backend

Merkantil is a Spring Boot 3 service that exposes a REST API for a simulated stock-trading platform. It combines real-time market data from the Alpaca API with a MySQL-backed domain model for users, portfolios, orders, and transactions while relying on Redis for HTTP sessions and response caching. Schedulers continuously refresh quotes and persist daily candles so that the front end always sees timely positions, movers, and historical prices.【F:src/main/java/github/kaloyanov5/merkantil/controller/StockController.java†L19-L139】【F:src/main/java/github/kaloyanov5/merkantil/service/StockPriceScheduler.java†L1-L160】

## Key capabilities

* **Account & session management** – register, log in, and fetch the authenticated profile via `/api/auth` endpoints. Authentication uses `BCryptPasswordEncoder`, DAO auth providers, and Redis-backed HTTP sessions with custom cookies.【F:src/main/java/github/kaloyanov5/merkantil/controller/AuthController.java†L24-L74】【F:src/main/java/github/kaloyanov5/merkantil/configuration/SecurityConfig.java†L1-L64】【F:src/main/java/github/kaloyanov5/merkantil/configuration/SessionConfig.java†L1-L21】
* **Market data APIs** – list/search stocks, fetch quotes, movers, sectors, and OHLC history, with Redis caches to stay within Alpaca rate limits.【F:src/main/java/github/kaloyanov5/merkantil/controller/StockController.java†L19-L139】【F:src/main/java/github/kaloyanov5/merkantil/configuration/CacheConfig.java†L1-L28】
* **Order & transaction lifecycle** – place buy/sell orders, inspect paged history, filter by symbol/type, and see aggregate statistics to power portfolio dashboards.【F:src/main/java/github/kaloyanov5/merkantil/controller/OrderController.java†L19-L67】【F:src/main/java/github/kaloyanov5/merkantil/controller/TransactionController.java†L19-L91】
* **Portfolio insights** – view holdings, per-symbol positions, and user-level summaries derived from holdings/transactions logic.【F:src/main/java/github/kaloyanov5/merkantil/controller/PortfolioController.java†L19-L54】
* **Admin tooling & imports** – seed/update the stock universe, backfill historical prices, and manage stock metadata via `/api/admin/stocks/**` endpoints with role checks.【F:src/main/java/github/kaloyanov5/merkantil/controller/AdminStockController.java†L1-L70】【F:src/main/java/github/kaloyanov5/merkantil/controller/StockImportController.java†L1-L153】
* **Scheduled maintenance** – recurring jobs refresh intraday quotes, capture end-of-day snapshots, and fill historical gaps before markets open.【F:src/main/java/github/kaloyanov5/merkantil/service/StockPriceScheduler.java†L21-L160】

## Stack & architecture

| Layer | Details |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.6 with scheduling, caching, and JPA auditing enabled in `MerkantilApplication`.【F:pom.xml†L5-L57】【F:src/main/java/github/kaloyanov5/merkantil/MerkantilApplication.java†L1-L12】 |
| Data | MySQL for persistence (Spring Data JPA), Redis for HTTP sessions and cache layers.【F:pom.xml†L32-L67】【F:src/main/java/github/kaloyanov5/merkantil/configuration/RedisConfig.java†L1-L22】 |
| API | Spring MVC controllers grouped by domain (`controller`, `service`, `repository`, `dto`, `entity`). Security uses role-based method annotations for admin-only routes.【F:src/main/java/github/kaloyanov5/merkantil/controller/UserController.java†L1-L98】【F:src/main/java/github/kaloyanov5/merkantil/configuration/SecurityConfig.java†L32-L53】 |
| Integrations | Alpaca Market Data/Trading API consumed through a dedicated `WebClient` service with caching and resilient fallbacks.【F:src/main/java/github/kaloyanov5/merkantil/service/AlpacaApiService.java†L1-L129】 |

## Prerequisites

1. **Java 21** (matching the Maven compiler target) and **Maven 3.9+**.【F:pom.xml†L5-L68】
2. **MySQL 8** (or compatible) instance and database for the app schema.
3. **Redis 6+** instance for Spring Session and cache storage.
4. **Alpaca API credentials** with market data entitlements.

## Configuration

Create `src/main/resources/application.yml` (or use environment variables) with the following sections:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/merkantil
    username: merkantil
    password: change-me
  jpa:
    hibernate.ddl-auto: update
    properties:
      hibernate.format_sql: true
  session:
    store-type: redis
  data:
    redis:
      host: localhost
      port: 6379
  cache:
    type: redis

alpaca:
  api:
    base-url: https://paper-api.alpaca.markets
    data-url: https://data.alpaca.markets
    timeout: 5
    key-id: ${APCA_API_KEY_ID}
    secret-key: ${APCA_API_SECRET_KEY}
```

* Use a dedicated database user with appropriate privileges.
* Set `spring.jpa.hibernate.ddl-auto=validate` in production and manage schema via migrations.
* When running locally, a `.env` file or IDE run configuration can export `APCA_API_KEY_ID` and `APCA_API_SECRET_KEY` so `AlpacaApiService` picks them up via `@Value` injection.【F:src/main/java/github/kaloyanov5/merkantil/service/AlpacaApiService.java†L19-L74】

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
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` for current profile info.【F:src/main/java/github/kaloyanov5/merkantil/controller/AuthController.java†L24-L74】 |
| Stocks | `GET /api/stocks`, `GET /api/stocks/{symbol}/quote`, `POST /api/stocks/quotes` for batch quotes, `GET /api/stocks/movers/{type}` for gainers/losers/active lists.【F:src/main/java/github/kaloyanov5/merkantil/controller/StockController.java†L19-L139】 |
| Portfolio | `GET /api/portfolio`, `GET /api/portfolio/summary`, `GET /api/portfolio/{symbol}` for single positions.【F:src/main/java/github/kaloyanov5/merkantil/controller/PortfolioController.java†L19-L54】 |
| Orders & Transactions | `POST /api/orders`, `GET /api/orders`, `GET /api/transactions`, `GET /api/transactions/type/{type}`, `GET /api/transactions/stats`.【F:src/main/java/github/kaloyanov5/merkantil/controller/OrderController.java†L19-L67】【F:src/main/java/github/kaloyanov5/merkantil/controller/TransactionController.java†L19-L91】 |
| Admin | `POST /api/admin/stocks/import/all`, `POST /api/admin/stocks/import/backfill`, `POST /api/admin/stocks`, `PUT /api/admin/stocks/{symbol}`, all protected with `ROLE_ADMIN`.【F:src/main/java/github/kaloyanov5/merkantil/controller/AdminStockController.java†L1-L70】【F:src/main/java/github/kaloyanov5/merkantil/controller/StockImportController.java†L1-L153】 |

Use a REST client (Hoppscotch, Postman, Thunder Client) that can persist session cookies to simplify authenticated testing. For admin operations, manually update the `role` column of your user to `ADMIN` or seed via SQL.

## Background jobs & caching

* `StockPriceScheduler` runs multiple cron tasks: 30-second intraday refreshes, 4:05 PM EOD snapshots, and 5 AM historical gap backfills.【F:src/main/java/github/kaloyanov5/merkantil/service/StockPriceScheduler.java†L21-L160】
* Redis caches wrap `StockService` and `AlpacaApiService` responses for one minute by default to reduce Alpaca API usage, with explicit cache eviction during scheduled refreshes.【F:src/main/java/github/kaloyanov5/merkantil/service/StockService.java†L37-L92】【F:src/main/java/github/kaloyanov5/merkantil/configuration/CacheConfig.java†L1-L28】
* `@EnableScheduling`, `@EnableCaching`, and `@EnableJpaAuditing` are activated at the application entry point.【F:src/main/java/github/kaloyanov5/merkantil/MerkantilApplication.java†L1-L12】

## Tips for development

* Start MySQL and Redis before running the JVM; otherwise Spring Boot will fail at startup.
* Use the admin import endpoints to populate baseline stock data, then allow the scheduler to keep it fresh.
* Because HTTP sessions are server-side, run the backend and client on the same host (or configure CORS + cookie domain) to share the `MERKANTIL_SESSION` cookie.【F:src/main/java/github/kaloyanov5/merkantil/configuration/SessionConfig.java†L9-L21】
* When testing Alpaca integrations, consider paper trading credentials and limit `StockImportController` batch sizes to avoid quota exhaustion.

## Contributing

1. Fork the repository and create a feature branch.
2. Run `./mvnw test` to ensure the suite still passes.
3. Open a PR that describes your change set and any configuration impacts.

Bug reports and feature ideas are welcome—please include reproduction steps, logs, or stack traces when relevant.
