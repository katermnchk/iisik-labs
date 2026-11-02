# Защита лабораторных 5–7

Вариант 20 — управление туристическими поездками. Отчёты: Ременчик Е. П., группа 377901, проверила Сильванович Ю. В. Проект продолжает ЛР4 в той же папке; инструкции запуска находятся в README.md.

## Подготовка

1. Выбрать JDK 17, выполнить `python3 scripts/prepare-env.py`, `./mvnw clean verify` и `docker compose --profile full up -d --force-recreate`.
2. Открыть http://localhost:8180/graphiql либо импортировать `examples/labs567.postman_collection.json`. Пароль переменной password должен совпадать с LAB_PASSWORD из `.env`.
3. Запустить коллекцию сверху вниз в Postman Runner. Она сохраняет токены CLIENT, EMPLOYEE и ADMIN, создаёт отдельного пользователя и новый тур. Каждый ответ проверяется; Kafka-проекции опрашиваются до 20 секунд. Результат Newman воспроизводится через `python3 scripts/verify-collection.py`.
4. Для ручного GraphiQL войти через authenticate и указать `{"Authorization":"Bearer <accessToken>"}` в Headers. Строки demo-client/demo-admin недействительны.

## Рассказ на три минуты

В ЛР5 я вынесла пароль и выдачу JWT в отдельный Auth Service. Пароль хранится как BCrypt-хеш, а активная сессия — в Redis с TTL. Перед выполнением защищённого поля Gateway проверяет токен через Auth Service. Роль и статус читаются заново из User Service, поэтому ранее выданный JWT не сохраняет отозванные права. Правила роли и принадлежности объекта также выполняются в бизнес-сервисах до записи.

В ЛР6 локальные mock-клиенты заменены сетевыми вызовами типизированного gRPC. Booking обращается к Tour для удержания мест; Payment получает контекст брони в Booking. После учебного подтверждения оплаты Payment публикует PaymentSucceeded в Kafka. Booking подтверждает бронь, Analytics формирует факт продажи, а Notification создаёт уведомления по событиям брони. Каждый сервис записывает только свою базу.

В ЛР7 я ограничила время ожидания и повторы, добавила outbox/inbox, восстановление промежуточных состояний и кеширование направлений. Между базами нет общей ACID-транзакции. После сбоя система продолжает сохранённую операцию, а не пытается считать сетевую ошибку доказательством отсутствия данных. Проверки останавливают отдельные зависимости и подтверждают восстановление.

## ЛР5 — что показать и объяснить

| Действие | Ожидаемое наблюдение | Почему |
|---|---|---|
| Запрос me без токена | UNAUTHENTICATED | Защита проверяется до бизнес-операции |
| register, затем me новым токеном | Один профиль, CLIENT | Роль нельзя повысить параметром регистрации |
| salesAnalytics клиентом | FORBIDDEN | Аутентификация не даёт административных прав |
| Чтение чужой брони | FORBIDDEN | Проверяется принадлежность данных |
| logout, затем me прежним JWT | UNAUTHENTICATED | Корректная подпись без Redis-сессии недостаточна |
| Блокировка или смена роли после входа | Старый JWT получает актуальные ограничения | Роль/статус перечитываются |

```sh
docker compose exec redis redis-cli --scan --pattern 'travel:session:*'
docker compose exec redis redis-cli TTL travel:session:<jti>
python3 scripts/verify-labs567.py
python3 scripts/verify-roles.py
```

Auth DB содержит credentials с BCrypt; User DB — профили, роли и статус. TokenService проверяет алгоритм HS256, подпись, issuer, audience, exp, iat и формат sub/jti. JWT_SECRET и SERVICE_KEY — разные ключи. SecurityConfig использует настоящее имя поля схемы: aliases и fragments не обходят проверку. Внутренние сервисы получают проверенный Principal и не перепроверяют JWT.

## ЛР6 — пройти один сценарий от запроса до проекции

1. EMPLOYEE создаёт тур, выезд и публикует тур. Показать запись в tour_db и чтение через GraphQL.
2. CLIENT создаёт бронь. Показать Booking → Tour gRPC, удержание в `rpc_reservations`, увеличение `reserved_seats` и запись в booking_db. Повтор idempotencyKey возвращает тот же bookingId.
3. CLIENT создаёт платёж; EMPLOYEE вызывает confirmPayment. Это учебное подтверждение: банк не подключён.
4. Payment сохраняет SUCCEEDED и outbox одной транзакцией. Relay публикует PaymentSucceeded; Booking получает событие и сохраняет CONFIRMED, Analytics — факт продажи.
5. Booking публикует BookingStatusChanged, Notification формирует уведомление. Коллекция ожидает подтверждённую бронь, уведомление именно текущего тура и прирост продаж.

| Топик | События | Назначение |
|---|---|---|
| user.events.v1 | UserRegistered, UserUpdated | Сведения о пользователях |
| tour.events.v1 | TourChanged | Текущее направление и расписания тура |
| booking.events.v1 | BookingStatusChanged | Изменение состояния брони |
| payment.events.v1 | PaymentSucceeded | Подтверждение оплаты |

У каждого топика 3 партиции; ключ сообщения — aggregateId. Порядок гарантируется внутри одной партиции. Группы notification, booking и analytics независимы и подписаны на нужные им топики. Полный маршрут и payload описаны в `docs/events.md`. TourChanged включает выезды без продаж: это позволяет считать вместимость всех подходящих выездов. Поздний снимок сохраняет подтверждённые места, старая версия не откатывает проекцию. asOf читается из analytics_projection_state.snapshot_at. Конверт содержит eventId, schemaVersion, occurredAt, aggregateId, aggregateVersion, correlationId и объект payload (JsonNode). occurredAt записывается с бизнес-изменением, а не при поздней отправке Relay.

```sh
docker compose logs --since 10m booking-service tour-service payment-service notification-service analytics-service
```

Для связывания наблюдений используйте `docs/evidence/labs567/scenario-ids.json`, eventId и correlationId. В отчёте следует объяснить конкретную запись из протокола, а затем найти соответствующий вызов в коде. Карта типизированных RPC находится в `docs/grpc-methods.md`.

## ЛР7 — сбой и восстановление

| Сбой | Защитный механизм | Наблюдение |
|---|---|---|
| Tour недоступен | deadline и ограниченный retry безопасных методов | SERVICE_UNAVAILABLE за конечное время; me работает |
| Kafka недоступна | transactional outbox | Бизнес сохранён; неопубликованное событие доставляется позже |
| Повтор eventId | inbox и уникальность бизнес-фактов | Нет повторного эффекта |
| Некорректный payload | Два повтора по 500 мс и DLT | Inbox откатывается; неисправное событие изолировано |
| Redis-сессии недоступны | Запрет защищённого доступа | Запрос не выполняет бизнес-операцию |
| Прерванная отмена | Сохранённое CANCELLING и Recovery | release повторяется; состояние становится CANCELLED |
| Истёк срок неоплаченной брони | ExpirePayment → ExpireBooking и Recovery | Неоплаченные попытки FAILED, бронь CANCELLED, места возвращены |
| Удержание без брони | ReservationRecovery | После NOT_FOUND отсутствие перепроверяется под блокировкой Booking; старое удержание освобождается |

Всего шесть DLT: user.events.v1.notification.dlt, booking.events.v1.notification.dlt, payment.events.v1.notification.dlt, payment.events.v1.booking.dlt, tour.events.v1.analytics.dlt и payment.events.v1.analytics.dlt. DLT принадлежит паре исходного топика и consumer: `payment.events.v1.booking.dlt` и `payment.events.v1.analytics.dlt` различаются. Ошибка одной проекции не создаёт общий поток повторной обработки для всех подписчиков. Если публикация в DLT не подтверждена, offset не должен подтверждаться.

```sh
python3 scripts/verify-integration.py
python3 scripts/verify-kafka-errors.py
python3 scripts/verify-recovery.py
```

Скрипты меняют учебные данные и временно останавливают зависимости только этого compose-проекта; не запускайте их параллельно с ручной демонстрацией. Некоторые прерывания моделируются сохранением промежуточного состояния в БД: это проверка восстановителя, а не утверждение, что процесс действительно был аварийно завершён в нужную наносекунду.

Кешируется только directions на 30 секунд; отдельные ключи для activeOnly. Для демонстрации очистить ключ, выполнить два чтения, показать `cache miss`, затем `cache hit` и TTL. После истечения TTL новое чтение снова обращается к Tour. Совпадение двух ответов само по себе не доказывает уменьшение числа RPC. Места, платежи и права доступа не кешируются.

```sh
docker compose exec redis redis-cli DEL travel:cache:directions:true
# Выполнить два запроса directions из Postman
docker compose logs --since 2m graphql-api
docker compose exec redis redis-cli TTL travel:cache:directions:true
```

## Ответы на ключевые вопросы

- Deadline ограничивает попытку RPC; retry ограничен тремя попытками только для безопасных операций и транспортных UNAVAILABLE/DEADLINE_EXCEEDED. Бизнес-ошибки повторять бессмысленно.
- Outbox устраняет потерю события между commit бизнеса и публикацией. Возможная повторная доставка устраняется на уровне эффекта inbox/идемпотентностью.
- Восстановление не является общей транзакцией между базами. Идемпотентные идентификаторы и промежуточные состояния позволяют завершить сценарий позднее.
- Redis для кеша можно обойти чтением источника; Redis для сессий нельзя обойти без нарушения контроля доступа.
- Один брокер Kafka и один PostgreSQL-контейнер с отдельными БД — учебный стенд. Внешние банк/email и публичное TLS-развёртывание не реализованы.
- Архивные инструкции ЛР3/ЛР4 и mock-тесты не описывают текущий runtime. Для демонстрации нужны текущие collection, README и evidence/labs567.

## Конкретный проверенный сценарий оплаты

Файлы `scenario-trace.json` и `runtime-excerpt.log` описывают один настоящий запуск Newman. correlationId: `bb1ed2b3-57bd-4224-91ca-edf37d4b9db9`; bookingId: `b2d434e9-ee56-3cb5-a334-8851dd11d712`; paymentId: `81fa1ae4-058a-39b5-a4a3-0f7b1005fd39`; eventId: `26b40252-4ee4-4bdc-b04a-2d513ccdecf1`.

1. Gateway исполнил confirmPayment. gRPC ConfirmPayment завершился за 8 мс; Payment перед этим получил настоящую бронь через GetBooking.
2. Payment outbox содержит PaymentSucceeded, aggregateVersion=1, occurredAt=09:12:14.660182Z и publishedAt=09:12:14.694984Z. В Kafka это payment.events.v1, partition=2, offset=1.
3. В inbox Booking, Analytics и Notification по одной записи с тем же eventId. Booking сохранён как CONFIRMED, version=2, hold_expires_at=NULL. История содержит переход PENDING_PAYMENT → CONFIRMED.
4. Analytics хранит SALE на 240000 копеек BYN с тем же source_event_id. Notification хранит SENT/IN_APP для оплаты и подтверждения брони. Это внутренние уведомления, а не email.
5. Booking опубликовал следующее BookingStatusChanged в booking.events.v1, partition=1, offset=9 с тем же correlationId.

Для защиты достаточно показать эти строки в двух протоколах, затем открыть соответствующие обработчики в PaymentSettlement, EventConfig.Relay и BusinessEvents. Полные JWT для объяснения сценария не нужны.

В финальном запуске: Maven 66 тестов без ошибок; integration.json — 41 проверка; security.json — 9. Newman 6.2.3 исполнил 24 шага, 25 HTTP-запросов с опросом и 64 assertions без ошибок. Количества зафиксированы в итоговом summary.json. В kafka-errors.json deliveryAttempts=3 подтверждает первую доставку и два повтора на всех четырёх проверенных DLT-маршрутах.

JWT_SECRET и LAB_PASSWORD передаются только Auth Service. Gateway проверяет JWT через Auth и не получает ключ подписи; прочим бизнес-сервисам он также не передаётся. SERVICE_KEY используется отдельно для внутреннего RPC.


Разбор типичных вопросов преподавателя: [defense-questions.md](defense-questions.md). Полное ревью с найденными дефектами и доказательствами исправления: [review-labs567.md](review-labs567.md).
