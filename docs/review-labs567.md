# Итоговое ревью лабораторных работ 5–7

Проверены исходники, реальные межсервисные вызовы, Kafka, PostgreSQL, Redis, отчёты и демонстрационная коллекция. Результат — исправленная реализация в исходной папке проекта, а не только новые отчёты. Пункты ниже обнаружены при строгом повторном ревью и исправлены; это не список оставленных дефектов.

| Приоритет | Обнаружено | Исправление | Проверяемое доказательство |
|---|---|---|---|
| P1 | Один Kafka-топик смешивал все домены и ошибки подписчиков | 4 доменных топика, явные маршруты, 3 партиции, 6 DLT по подписке | kafka-topology.json: реальные assignments; kafka-errors.json: 4 маршрута ошибок |
| P1 | Использовался общий gRPC Call с JSON вместо типизированных моделей ЛР2 | Канонический travel.v1 proto, именованные методы и generated marshallers; сохранены номера полей | TypedRpcTest: все опубликованные Java-порты через реальные сокеты и native generated stub |
| P1 | POPULARITY передавала недопустимые несуточные границы | Полные дни UTC | PopularityWindowTest; verify-hold-expiry.py через GraphQL |
| P1 | Просроченная бронь с PENDING-платежом могла удерживать места навсегда | Согласованные expirePayment/expireBooking, FAILED для неоплаченной попытки, recoverable cancellation | hold-expiry.json; места RELEASED, терминальная история ровно одна |
| P1 | Оплаченная бронь рисковала истечь до доставки Kafka | SUCCEEDED проверяется под тем же Payment-lock, что подтверждение и expiry | paid-expiry-fence.json: Kafka остановлена, deadline просрочен, места HELD; затем CONFIRMED |
| P1 | Проверка orphan через простой NOT_FOUND могла не увидеть незакоммиченную бронь | Повторная проверка и release под Booking writer-lock | ExpiryAdmissionTest; verify-recovery.py |
| P2 | Checked-исключение consumer могло зафиксировать inbox без изменения проекции | rollbackFor=Exception.class, offset после успешного применения или подтверждённого DLT | malformedPayload inboxRowsAfterRollback=0, наблюдаемые 3 попытки |
| P2 | Доставка события на следующий день искажала дату продажи | Используется business occurredAt в UTC | analytics-cache.json: controlled backlog fixture до полуночи |
| P2 | Пустые выезды и изменение вместимости не попадали в загрузку | TourChanged проецирует каталог, включая нулевые продажи; проверка aggregateVersion | new empty schedule, capacity update и stale snapshot checks |
| P2 | asOf аналитики было константой из фикстуры | Сохранён snapshot_at последнего применённого изменения проекции | analytics_projection_state и фактический GraphQL asOf |
| P2 | Восстановление отмены пропускало историю | История и outbox в одной recovery-транзакции | hold-expiry.json и сценарий interrupted cancellation |
| P2 | Сбой регистрации после создания профиля мог оставить email без пароля | Сначала сохраняется registration intent с userId; повтор продолжает тот же профиль | integration.json: interrupted registration resumes same profile |
| P2 | Перезапуск Tour терял связь с удержаниями; старые seat_holds могли не освобождаться | Постоянные rpc_reservations и идемпотентный fallback для legacy seat_holds | restart/cancel + LegacyHoldReleaseTest |
| P2 | Переполнение полной стоимости могло произойти после удержания | multiplyExact до изменения мест | ReservationAmountTest |
| P2 | Частичные protobuf updates теряли null/empty/zero, деньги могли пострадать от JSON | Наличие scalar optional полей, int64 и Timestamp с nanos | TypedRpcTest: nullable/empty/zero, >2^53, nanoseconds |
| P2 | Статусы gRPC и BAD_REQUEST были сведены к одному FAILED_PRECONDITION | Нативные INVALID_ARGUMENT, NOT_FOUND, PERMISSION_DENIED, ABORTED и INTERNAL; domain-code trailers | Native stub auth/error tests |
| P2 | Коллекция не проверяла успешность большинства запросов и не ждала Kafka | Общие assertions, отрицательные проверки, bounded polling и фактический Newman | collection.json: runner/version/request/assertion/failure stats |
| P2 | Проверка кеша показывала хранение, но не экономию вызовов | Измерение серверных RPC при miss/hit/expiry | cache-trace.log: 3 Query, 2 ListDirections RPC |
| P2 | Исторические спецификации противоречили runtime | Актуальные events/grpc/defense и отчёты; исторический HTTP-контракт помечен | docs/events.md, grpc-methods.md, defense-labs567.md |

## Что реально проверено

Итоговые числа берутся из docs/evidence/labs567/summary.json. Общая чистая сборка: 66 JUnit-тестов, 0 failures/errors/skipped. Сквозной интеграционный сценарий: 41 именованная проверка; безопасность: 9 проверок; дополнительно отдельные протоколы ролей, orphan recovery, expiry, оплаченной брони при Kafka outage, аналитики, кеша, topology и DLT. Реальный Newman 6.2.3 выполняет 24 шага; число HTTP-запросов может быть больше из-за polling, поэтому оно записано отдельно от числа шагов.

Все виды повторов проверяются по смыслу: неидемпотентная запись не повторяется автоматически; reserve/release/claim/register/expiry имеют постоянную идентичность или безопасную проверку состояния. Kafka может доставлять событие повторно; inbox и уникальность бизнес-факта препятствуют повторному применению. Указание «exactly once доставка» не используется.

## Как объяснить архитектурные решения

- По одному исходному Kafka-топику на владельца домена; тип события версионирован внутри конверта. Порядок — по aggregateId внутри партиции, не между разными топиками.
- DLT принадлежит подписке, поскольку одно payment-событие независимо обрабатывают Booking, Analytics и Notification.
- Один Relay одного владельца публикует по sequence; версия агрегата выделяется в бизнес-транзакции. Даже при нескольких экземплярах advisory-lock не позволяет обогнать предыдущее событие того же агрегата.
- Промежуточные состояния и recovery нужны, потому что общей ACID-транзакции между сервисами нет. Транзакционные блокировки учебного стенда укрупнены; они обеспечивают корректность, но ограничивают throughput.
- asOf — timestamp применённого локального снимка аналитики, а не гарантия, что все сообщения Kafka уже догнаны. eventual consistency показана ожиданием проекции в Postman.
- JWT_SECRET нужен только Auth Service. Внутренние сервисы получают Principal по доверенному RPC-контуру и используют отдельный SERVICE_KEY.

## Явные границы результата

Это локальный учебный стенд с одним брокером Kafka (replication factor 1) и отдельными логическими PostgreSQL каждого сервиса. Внешний банк не подключён: сотрудник подтверждает учебный платёж через confirmPayment; уведомления реализованы внутри приложения (IN_APP), а не как обещание внешнего email/push. Исторически запланированные RPC для не входящих в текущую реализацию функций возвращают UNIMPLEMENTED и перечислены в grpc-methods.md. Полные банковские callback/refund и кластер высокой доступности не заявляются как готовые функции.

Технические проверки подтверждают перечисленные требования лабораторных. Оценка зависит от критериев преподавателя и объяснения решений на защите; документ не обещает балл и не называет систему production-ready.
