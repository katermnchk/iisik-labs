# Лабораторная работа 3

Система управления туристическими поездками. Вариант 20. Дисциплина: Интегрированные системы и компоненты.

Реализованы восемь GraphQL Query, шесть самостоятельных сервисов чтения с PostgreSQL, начальные данные и mock-клиенты. Gateway зависит только от `contracts`, получает данные через интерфейсы и не вызывает внутренние приложения по сети.

## Что нужно установить

- JDK 17 (именно JDK, с `javac`) и настроить JAVA_HOME.
- Docker с Compose, если нужно показать базы и внутренние сервисы.
- Python 3, если нужен скрипт проверки HTTP.

Maven устанавливать отдельно не нужно: есть Maven Wrapper 3.9.11. При первой сборке нужен интернет для зависимостей. Использованы Spring Boot 3.5.7, Spring GraphQL, JPA, Flyway, PostgreSQL 16.15. Это зафиксированные версии проекта, не утверждение о последних версиях.

## Быстро показать GraphQL без Docker

Из корня проекта Linux/macOS:

```sh
./mvnw clean verify
java -jar graphql-api/target/graphql-api-1.0.0.jar
```

Windows PowerShell:

```powershell
.\mvnw.cmd clean verify
java -jar graphql-api/target/graphql-api-1.0.0.jar
```

Открыть http://localhost:8080/graphiql. В нижней панели Headers указать:

```json
{"Authorization":"Bearer demo-client"}
```

Пример:

```graphql
query {
  tours(page: {page: 0, size: 20}, sort: {field: PRICE, direction: ASC}) {
    totalCount
    items { id title schedules { id availableSeats price { amountMinor currency } } }
  }
}
```

Если редактор Headers не виден, выполнить запрос через Postman: POST http://localhost:8080/graphql, Body/raw/JSON, Content-Type application/json, Authorization Bearer Token `demo-client`. Готовая коллекция находится в examples/lab3.postman_collection.json.

HTTP-запрос:

```sh
curl http://localhost:8080/graphql -H 'Content-Type: application/json' -H 'Authorization: Bearer demo-client' --data '{"query":"{me{id firstName role{code}}}"}'
```

Демонстрационные токены не являются JWT и не реализуют настоящий вход. `demo-client` - клиент 1, `demo-client2` - клиент 2, `demo-employee` - сотрудник, `demo-admin` - администратор, `demo-blocked` - заблокированный пользователь. Clock зафиксирован на 2026-10-05T00:00:00Z, чтобы ответы не менялись со временем.

## Полный запуск с базами

```sh
docker compose --profile full up --build -d
```

При первом запуске сборка образов занимает время. В каждом образе собирается только нужный модуль. Проверка:

```sh
docker compose --profile full ps
curl http://localhost:8080/actuator/health
python3 scripts/verify.py --services
```

Windows: `python scripts/verify.py --services`.

Остановить приложения, сохранив данные:

```sh
docker compose --profile full down
```

Первоначальная настройка шести БД выполняется один раз для нового volume. `down -v` удаляет данные, для обычной остановки эту команду использовать не нужно. Если нужен другой порт PostgreSQL, задать POSTGRES_PORT перед запуском; при запуске Java вне Docker также изменить DB_URL.

Повторная проверка 05.10.2026: сборка всех модулей, 41 JUnit-тест, 33 HTTP-проверки всех приложений и 24 после остановки внутренних сервисов. В дополнительном прогоне JDBC/JPA использованы отдельные экземпляры PostgreSQL 18.3 / PGlite с SQL V1/V2, применённым до запуска; Flyway отключён только для этого прогона. Штатная конфигурация сохраняет PostgreSQL 16.15 и Flyway. Docker daemon здесь отсутствует; контейнерный запуск повторно не проверен. Подробности и границы проверки — docs/verification.md.

## Запуск из IntelliJ IDEA

1. Открыть корневой pom.xml как Maven project. Выбрать Project SDK 17.
2. Синхронизировать Maven и выполнить `verify` для корневого проекта.
3. Для GraphQL запустить `GatewayApplication`: Docker не нужен.
4. Для внутренних сервисов выполнить `docker compose up -d postgres`.
5. Запустить UserApplication, TourApplication, BookingApplication, PaymentApplication, NotificationApplication, AnalyticsApplication. Каждый использует собственный application.yml.
6. Проверить /actuator/health на портах 8091-8096.

Весь проект открывается одним Maven parent, но каждое приложение имеет свою точку входа, конфигурацию и отдельный запускаемый JAR.

## Модули и порты

| Модуль | Порт | База |
| --- | --- | --- |
| graphql-api | 8080 | нет, mock-снимки JSON |
| user-service | 8091 | user_db |
| tour-service | 8092 | tour_db |
| booking-service | 8093 | booking_db |
| payment-service | 8094 | payment_db |
| notification-service | 8095 | notification_db |
| analytics-service | 8096 | analytics_db |
| contracts | - | Java DTO, интерфейсы, будущий protobuf |

PostgreSQL на хосте: localhost:6543. Логины `<service>_app`, пароли `<service>_demo`; например user_app/user_demo. Общий пароль локального PostgreSQL: local_demo_admin. Каждая роль может подключиться только к своей БД. FK между БД нет; внешние id сохраняются UUID.

Диагностические REST endpoints находятся в docs/http-contracts.md. Они служат проверке собственных БД сервисов и не используются Gateway. При запуске JAR приложения слушают 127.0.0.1. При запуске Compose порты опубликованы на хост; это локальная учебная конфигурация без production-авторизации диагностических endpoints.

## Правила данных

- Деньги - целое число копеек BYN: `"90000"` означает 900.00 BYN. GraphQL Long передаёт десятичную строку, REST - JSON число; сопоставление в verify.py учитывает это.
- Date фильтрует дату начала выезда по UTC; границы включаются. Дата и цена должны подходить одному выезду.
- AnalyticsFilter использует полные дни UTC и полуоткрытый период [from,to). Можно передать другой offset, если момент соответствует 00:00 UTC. Частичный день отклоняется BAD_REQUEST: популярность хранится по дням.
- Цена тура для сортировки - минимальная цена среди подходящих выездов. Равные значения упорядочиваются по UUID.
- Свободные места = capacity - reserved_seats. reserved_seats включает подтверждённые места и временные удержания в фиксированном снимке. Автоматического освобождения удержаний по часам в чтении ЛР3 нет.
- grossSales - SALE; refunded - REFUND; netSales = grossSales - refunded. Загрузка = сумма подтверждённых мест / сумма вместимостей выездов, начинающихся в периоде.
- Популярность - число подтверждённых туристов по дню подтверждения. При сортировке берутся последние 30 полных дней; популярность направлений используется для ранжирования их туров.
- `me` показывает профиль текущего пользователя. `myBookings` доступен CLIENT, чужая бронь закрыта. Аналитика доступна ADMIN. Уведомления ограничены текущим пользователем.
- Неизвестный UUID даёт null объекта и NOT_FOUND. Пустой список даёт items:[] и totalCount:0.

## Состав сдачи

- docs/corrections.md - исправления замечаний ЛР1/ЛР2.
- docs/review-fixes.md - исправления строгого ревью и регрессионные тесты.
- docs/http-contracts.md - реальные HTTP-контракты и Java интерфейсы.
- docs/verification.md, docs/evidence - результаты проверок и ответы.
- docs/defense.md - порядок демонстрации и ответы на вопросы.
- graphql-api/src/main/resources/graphql/schema.graphqls - реально исполняемая схема.
- contracts/proto/travel/v1/travel.proto, docs/graphql-future.graphqls, docs/events.md - исправленная спецификация следующих работ.
- examples - восемь Query и Postman-коллекция.

Реальных gRPC-вызовов, Kafka, отправки уведомлений, регистрации, оплаты и Mutation в ЛР3 нет: задание посвящено структуре и чтению. Таблицы outbox_events/processed_events подготовлены, но обработчики событий ещё не запускаются. Изменение БД не меняет mock-снимки автоматически.
