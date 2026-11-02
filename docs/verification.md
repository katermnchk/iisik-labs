# Проверка ЛР3 от 02.11.2026

JDK 17.0.17, Maven Wrapper 3.9.11, PostgreSQL 16.15 в Docker. Все семь приложений запущены из собранных JAR; шесть баз созданы init SQL, миграции Flyway включены, Hibernate ddl-auto=validate. Нет PGlite или отключения Flyway в текущем прогоне.

- Maven clean verify: 42 теста, 0 failures/errors/skipped, все восемь модулей собраны.
- Все приложения: 34 HTTP-проверки.
- После остановки шести внутренних сервисов: 25 HTTP-проверок Gateway. Он читает mock и не зависит от БД.
- Проверена изоляция: подключение user_app к tour_db отклонено из-за отсутствия CONNECT.
- Регрессия directions(activeOnly:null) сначала возвращала INTERNAL_ERROR, тест падал. После исправления применяется default true; тест и реальный HTTP проходят.

JUnit проверяет contracts и Gateway; отдельного набора JUnit для JPA-сервисов нет. Их реальные конфигурации/миграции/чтение проверены запуском на PostgreSQL и HTTP. Сравнение DB/mock касается выбранных GraphQL-полей, нормализует Long и даты и не является полным сравнением всех столбцов.

Протоколы: docs/evidence/2026-11-02/summary.json, maven-verify.log, all-apps, gateway-only, runtime.json. transactions содержит реальные запросы/ответы со временем. Старые проверки описаны отдельно в verification-2026-10-05.md.

Воспроизведение: README, раздел «Отдельный стенд рядом с ЛР5-7». compose.verify.yaml использует GraphQL 8280, сервисы 8291-8296 и PostgreSQL 6643. Базовый compose.yaml сохраняет исходные порты.
