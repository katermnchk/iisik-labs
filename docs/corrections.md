# Исправления по замечаниям преподавателя

| Замечание | Принятое исправление | Где закреплено |
| --- | --- | --- |
| Нет создания Employee profile | ADMIN.updateUser принимает employeeProfile.position; профиль и роль изменяются одной транзакцией. employeeId = employees.user_id = users.id. Профиль сохраняется для истории авторства при смене роли | graphql-future.graphqls; UpdateUserRequest; users/employees; начальные данные |
| Неполные gRPC request/response | Полный компилируемый travel.proto описывает все Query и будущие команды | contracts/proto/travel/v1/travel.proto |
| Не все outbox/processed таблицы указаны | outbox: User, Tour, Booking, Payment; processed: Booking, Notification, Analytics | V1__schema.sql каждой БД |
| sales_facts.payment_id PK препятствует возврату | fact_id PK; source_event_id UNIQUE; payment_id повторяется; UNIQUE(fact_type,source_operation_id) | Analytics migration, отдельные SALE/REFUND одного платежа |
| Неясен источник scheduleId/directionId платежа | ReserveSeats -> snapshot брони -> GetPaymentContext -> snapshot платежа -> event | DTO protobuf и fields в Booking/Payment DB |
| UserRegistered отсутствует в реестре | Событие добавлено с payload и Notification consumer | events.md |
| GraphQL не согласован с данными | Имена в users; списки accommodations/payments; payment_url, version, priceVersion, popularityAsOf; все enum | Активная schema.graphqls, Model.java, миграции |

Полный жизненный цикл создания и изменения записей закреплён в спецификации. В ЛР3 реализованы только операции чтения. updateUser и остальные Mutation ещё не исполняются, так как не входят в задание этой лабораторной.

Профили клиентов и сотрудников могут сохраняться после изменения роли, чтобы исторические ссылки не терялись. Текущую возможность выполнять действия определяет роль и ACTIVE/BLOCKED. При назначении CLIENT необходимо передать телефон либо использовать существующий профиль. При назначении EMPLOYEE необходимо заполнить должность. Для ADMIN профиль не обязателен.

В ЛР3 Gateway не имеет БД: его данные являются mock-снимками. Самостоятельные сервисы используют настоящие PostgreSQL-базы. Изменение таблицы через SQL не обновляет mock-снимок автоматически. Реальная межсервисная интеграция будет добавлена позже.
