# ИИСиК - система управления туристическими поездками

Вариант 20: система управления туристическими поездками. Продолжение проекта `iisik-lab4`, который открыт в IntelliJ. Название папки сохранено, чтобы не потерять конфигурацию IDE.

Добавлены отдельный Auth Service, JWT + Redis-сессии, реальные gRPC-клиенты и серверы, Kafka producer/consumers, outbox/inbox, retry/deadline, восстановление операций, Redis-кеш направлений и корреляция логов. Gateway больше не использует mock-клиенты и демонстрационные bearer-строки. Учебные платежи подтверждает сотрудник через `confirmPayment`; внешний банк и внешняя доставка email не подключены. Уведомления доступны в GraphQL.

## Запуск

Нужны JDK 17, Docker Desktop и Python 3. На этом Mac системная Java - 8, поэтому явно выберите 17:

```sh
cd iisik-lab4
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
python3 scripts/prepare-env.py
./mvnw clean verify
docker compose --profile full up -d
```

Готовность приложений занимает около 30 секунд. GraphiQL: <http://localhost:8180/graphiql>, GraphQL: <http://localhost:8180/graphql>. Порт 8180 выбран, потому что 8080 уже занят другим проектом. Данные сохраняются в отдельных Docker volumes `iisik-labs567_*`; существующие контейнеры других проектов не изменяются.

После изменения Java-кода пересоберите проект и используйте `docker compose --profile full up -d --force-recreate` для повторного монтирования новых jar. Если нужна сборка внутри Docker: `docker build --build-arg MODULE=graphql-api -t iisik-gateway .` (аналогично для остальных модулей).

Для IntelliJ выберите Project SDK 17 и Maven importer JDK 17. Альтернатива полному контейнерному запуску: `docker compose up -d postgres redis kafka`, затем `python3 scripts/run-local.py`. Перед этим остановите приложения профиля full, чтобы освободить порты; все приложения можно запускать и обычными Run Configuration с переменными из `.env`.

## Вход

Учебные аккаунты: `user1@example.test`, `user2@example.test` - CLIENT; `user3@example.test` - EMPLOYEE; `user4@example.test` - ADMIN; `user5@example.test` - BLOCKED. Локальный пароль из LAB_PASSWORD по умолчанию: `Lab567-Travel!`. BCrypt-хеши создаются в Auth DB; токены demo-* больше не принимаются.

```graphql
mutation {
  authenticate(input: {
    email: "user1@example.test"
    password: "Lab567-Travel!"
  }) { accessToken user { id role { code } } }
}
```

В Headers GraphiQL вставьте полученный токен:

```json
{"Authorization":"Bearer <accessToken>"}
```

При регистрации роли не передаются: новый пользователь всегда CLIENT. Вход и регистрация доступны без токена, остальные поля требуют активную Redis-сессию. TTL JWT/сессии - 1800 с; logout немедленно удаляет сессию. Роль и статус перечитываются из User Service для каждого запроса.

## Проверки и защита

```sh
python3 scripts/verify-labs567.py
python3 scripts/verify-integration.py
python3 scripts/verify-kafka-errors.py
python3 scripts/verify-recovery.py
python3 scripts/verify-roles.py
python3 scripts/verify-collection.py
```

`verify-integration.py` временно останавливает только Tour/Redis/Kafka этого compose-проекта и включает их обратно через finally. Создаёт новые учебные туры, брони и платежи; проверки повторяемые. `verify-recovery.py` использует идентификаторы последнего интеграционного сценария и моделирует удержание, оставшееся после сбоя Booking. SQL-команды проверки завершаются при ошибке. Во время этих проверок не выполняйте параллельно демонстрацию в GraphiQL.

Протоколы: `docs/evidence/labs567/*.json`. Подробная инструкция защиты: `docs/defense-labs567.md`. Старые тесты standalone/mock-режима ЛР4 сохранены в `docs/archive/lab4-tests`; они используют удалённые demo-токены и не описывают runtime ЛР5-7. Доменные тесты и тесты скалярных типов сохранены в активной сборке.

## ЛР4: бизнес-операции

Текущая версия содержит все девять операций ЛР4: create/updateTour, create/updateSchedule, create/cancelBooking, createPayment, changeBookingStatus и updateUser. Используется интегрированная версия с настоящими gRPC/Kafka. Фильтры/сортировка myBookings и bookingSummary работают над одним набором записей владельца. Актуальная проверка: `python3 scripts/verify-lab4-current.py`; протокол `docs/evidence/lab4-current/business.json` содержит 63 проверки, включая 46 основных GraphQL-запросов, проверки БД и явно отмеченную временную фикстуру завершения поездки. Скрипт кратковременно останавливает Tour и восстанавливает его через finally. Не запускать параллельно с ручной демонстрацией.

`lab3` в истории Git сохраняет автономную версию с mock-клиентами. В актуальной версии mock-режим отсутствует. Отчёты не входят в репозиторий.

## Проверка безопасности ЛР5

`python3 scripts/verify-lab5-security.py` выполняет 79 проверок JWT, Redis-сессий, ролей, владельца и закрытия внутренних HTTP-путей. Протокол: `docs/evidence/lab5-current/security.json`. Подробности исправленного обхода маршрутов и границы проверок: `docs/review-lab5-current.md`. Сценарий кратковременно останавливает Redis и восстанавливает его через finally; не запускайте его одновременно с демонстрацией.

## Проверка интеграции ЛР6

`python3 scripts/verify-lab6-payment-recovery.py` проверяет обработку действительного PaymentSucceeded при временном отказе Tour и автоматическое восстановление Analytics. Топология и ошибочные сообщения проверяются `verify-kafka-topology.py` и `verify-kafka-errors.py`; общий сценарий проверяется `verify-integration.py`. Итоговые протоколы: `docs/evidence/lab6-current`, детали: `docs/review-lab6-current.md`. Fault-проверки не следует запускать параллельно с ручной демонстрацией.

## Реализация по лабораторным

| Работа | Основные файлы |
|---|---|
| 5 | auth-service, SecurityConfig, AuthClient, AuthController, Rules |
| 6 | contracts/proto/travel/v1/travel.proto, RpcClient/RpcServer, RpcConfiguration сервисов, RemoteSeats, PersistentReservations, BusinessEvents |
| 7 | EventConfig.Relay, integration_outbox/inbox, Recovery, ReservationRecovery, CachedTourClient, логи correlationId |

gRPC использует типизированные protobuf request/response и сгенерированные дескрипторы RPC. Актуальная карта методов приведена в `docs/grpc-methods.md`; доменная логика остаётся в сервисах. Внутренний контур защищён SERVICE_KEY; ключ подписи JWT - другой JWT_SECRET. Значения генерирует prepare-env.py. `.env` не включается в архив исходников.

Синхронные операции: deadline 2 с на попытку; безопасные вызовы - до 3 попыток с паузами 100/200 мс. Неидемпотентные записи автоматически не повторяются. Kafka: delivery timeout 10 с; consumer - 2 повтора по 500 мс, затем DLT конкретного consumer и исходного топика (`<source-topic>.<consumer>.dlt`). События разделены на `user.events.v1`, `tour.events.v1`, `booking.events.v1`, `payment.events.v1`; у каждого 3 партиции, ключ - aggregateId. Группы notification, booking и analytics подписаны на необходимые им события. Конверт содержит schemaVersion, occurredAt, aggregateVersion и объект payload. После успешного publish Relay помечает outbox; после успешной локальной транзакции consumer подтверждает offset. Повторная доставка возможна и обрабатывается по eventId.

Отмена сначала сохраняет CANCELLING, затем возвращает места и фиксирует CANCELLED. Recovery раз в 10 с завершает прерванные отмены. ReservationRecovery освобождает удержания старше 2 минут только после определённого NOT_FOUND от Booking либо после CANCELLED. Недоступность Booking не считается отсутствием брони. После NOT_FOUND метод ReleaseOrphanReservation повторно проверяет отсутствие под блокировкой Booking и удерживает её до освобождения мест, исключая гонку с незавершённым созданием. Оплата согласуется через payment_intents в Booking DB. По истечении holdExpiresAt typed RPC ExpirePayment → ExpireBooking согласуют отказ неоплаченных попыток и перевод брони в CANCELLING; Recovery возвращает места и завершает CANCELLED. Успешный платёж исключает истечение. Старые учебные удержания seat_holds также освобождаются идемпотентно.

Справочник направлений кешируется 30 с; места, платежи и права доступа не кешируются. При недоступности кеша справочник читается из Tour; при недоступности Redis-сессий запросы запрещены. Кластер высокой доступности Kafka, внешний банк и публичное TLS-развёртывание не входят в этот локальный стенд.

Отчёты DOCX/PDF предоставляются отдельно от исходников.

## Postman и Newman

Импортируйте `examples/labs567.postman_collection.json`, задайте переменную `password` и выполняйте коллекцию сверху вниз. Коллекция сохраняет JWT и идентификаторы, создаёт нового CLIENT, проверяет запрет доступа и отзыв сессии, выполняет бронирование и оплату. Проверки асинхронных проекций опрашивают API до 20 секунд и завершаются ошибкой, если результата нет. Повтор полного запуска создаёт отдельный тур.

`verify-collection.py` запускает настоящую коллекцию через Newman, включая её JavaScript assertions. Newman установлен вне исходников: `/Users/katar/outputs/iisik-labs567/newman/node_modules/newman`. Для другого компьютера выполните `npm install --prefix <tools> newman` и задайте `NEWMAN_MODULE=<tools>/node_modules/newman`. `NODE_BINARY` позволяет указать Node.js; `LAB_PASSWORD` - пароль, если он изменён. Протокол `collection.json` содержит только статистику, имена проверок и ошибки, без тел ответов с JWT.

Два запроса направлений в коллекции проверяют одинаковый результат. Cache hit подтверждается отдельно: `docker compose logs --since 2m graphql-api` содержит `cache miss` и `cache hit`, а Redis показывает TTL ключа. Старые `docs/defense.md`, `README-Lab3.md` и `docs/http-contracts.md` относятся к прошлым этапам; для ЛР5-7 используйте `docs/defense-labs567.md`.

Событие хранит payload как JSON-объект (JsonNode), а не строку с повторно закодированным JSON. occurredAt сохраняется вместе с бизнес-изменением; задержанная отправка Relay не переносит дату продажи. TourChanged передаёт направление и все выезды, включая не имеющие продаж: Analytics учитывает их вместимость, не обнуляет уже подтверждённые места при позднем снимке и пропускает устаревшую версию. asOf аналитики берётся из постоянного analytics_projection_state.snapshot_at, а не из времени чтения API. Шесть DLT соответствуют шести подпискам: Notification - user/booking/payment, Booking - payment, Analytics - tour/payment.

JWT_SECRET и LAB_PASSWORD передаются только Auth Service. Gateway проверяет JWT через Auth и не получает ключ подписи; прочим бизнес-сервисам он также не передаётся. SERVICE_KEY используется отдельно для внутреннего RPC.

При локальном запуске Kafka использует heap 128-256 MiB. Одновременный запуск всех старых стендов может исчерпать память Docker; compose.verify.yaml ЛР3 следует остановить перед полным стендом. Объёмы данных сохраняются.
