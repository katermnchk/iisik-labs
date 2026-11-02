> Исторический документ этапа ЛР3. Для сдачи ЛР5–7 используйте [актуальную инструкцию](defense-labs567.md), [Kafka-контракты](events.md) и [gRPC-контракты](grpc-methods.md).

# Повторная проверка 05.10.2026

Исходный проект пересобран на JDK 17.0.20. `./mvnw clean verify`: BUILD SUCCESS для восьми модулей; 41 тест, 0 failures/errors/skipped. В среде подготовки Maven использовал только внешнюю настройку сетевого proxy; она не входит в проект.

Состав JUnit: PageInput 2, Gateway 25, FutureSchema 1, LongScalar 5, DirectionCatalog 2, UuidIdentity 6. Регрессии для трёх исправленных дефектов сначала воспроизведены на прежнем коде; журналы before-fix сохранены. Long scalar и до исправления возвращал GraphQL ValidationError, а не HTTP 500; исправлен контракт типа исключения.

## Реальные HTTP-проверки

Все семь JAR запущены. Шесть внутренних сервисов читали отдельные экземпляры PostgreSQL 18.3 (PGlite 0.5.8; socket 0.2.11). SQL из V1__schema.sql и V2__demo.sql выполнен перед запуском; Hibernate `ddl-auto=validate` проверил соответствие сущностей. У каждого сервиса отдельный JDBC URL и экземпляр БД. В этом дополнительном прогоне Flyway отключён через аргумент запуска: PGlite имеет один backend и не заменяет обычный PostgreSQL для проверки конкурентных транзакций/ролей. Штатные application.yml и Compose сохраняют PostgreSQL 16.15 и включённый Flyway.

- 33 HTTP-проверки при работе всех приложений: PASS.
- Сравнение DB/mock для User, Tour, Booking, Payment, Notification, Analytics и независимого каталога направлений: PASS. Проекция учитывает только выбранные GraphQL-поля; целые Long и эквивалентные даты нормализуются.
- 24 HTTP-проверки после завершения процессов шести сервисов: PASS. Закрытие портов 8091–8096 проверено перед повторным запуском скрипта. Gateway продолжал работать.
- Проверены все восемь Query, права CLIENT/EMPLOYEE/ADMIN, HTTP 401/403, ошибочный UUID, NOT_FOUND, BAD_REQUEST, частичный ответ, большие страницы, переполнение scalar, связанность batch-платежей и базовая аналитика.

Источники: `docs/evidence/2026-10-05/`: maven-verify.log, JUnit XML, startup/*.log, health.json, http-checks.log, all-apps и gateway-only. В transactions сохранены фактические метод, URL, headers, query, статус, ответ и время получения. PNG в screenshots — визуализация сохранённых HTTP-записей и сводки, не имитация Postman или терминала.

## Повторение на штатной конфигурации

```sh
./mvnw clean verify
docker compose --profile full up --build -d
python3 scripts/verify.py --services --output docs/evidence/repeated-all
```

Для mock-режима отдельно запустить GraphQL JAR и `python3 scripts/verify.py --output docs/evidence/repeated-gateway`.

Docker daemon в текущей среде отсутствует: запуск образов и ограничения CONNECT ролей PostgreSQL повторно не проверены. Начальный комплект свидетельств от 04.10.2026 перенесён в `evidence/2026-10-04`; в нём указаны прежние 23 JUnit, 15+8 HTTP, PostgreSQL 16.15 и проверка изоляции. Эти исходные записи не являются результатом нового прогона. FutureSchemaTest проверен повторно; protoc повторно не запускался, исходный descriptor и сведения о 34 RPC сохранены.
