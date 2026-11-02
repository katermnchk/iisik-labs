# Реальные Kafka-контракты ЛР6–7

Этот реестр описывает выполняемый код. Старый общий `travel.events.v1` не используется новыми producer/consumer. Ранее оставленные брокером записи являются историей предыдущего стенда; активных подписок на общий топик нет.

| Топик | Владелец | События | Подписчики и группы |
|---|---|---|---|
| user.events.v1 | User Service | UserRegistered, UserUpdated | Notification / travel-notification-v1 (UserUpdated фильтруется) |
| tour.events.v1 | Tour Service | TourChanged | Analytics / travel-analytics-v1 |
| booking.events.v1 | Booking Service | BookingStatusChanged | Notification / travel-notification-v1 |
| payment.events.v1 | Payment Service | PaymentSucceeded | Booking / travel-booking-v1, Analytics / travel-analytics-v1, Notification / travel-notification-v1 |

В каждом топике 3 партиции. Ключ — UUID aggregateId: userId, tourId, bookingId или paymentId соответственно. Порядок гарантируется внутри партиции для одного агрегата. Порядка между разными топиками нет: аналитика допускает PaymentSucceeded до TourChanged. У consumer группы 3 потока; одна партиция обрабатывается одним участником группы. Разные группы получают независимую копию одного события.

## Версионированный конверт

```json
{
  "eventId": "UUID",
  "eventType": "PaymentSucceeded",
  "schemaVersion": 1,
  "occurredAt": "2026-11-02T10:00:00Z",
  "aggregateId": "payment UUID",
  "aggregateVersion": 2,
  "correlationId": "UUID",
  "payload": { "payment": {}, "booking": {} }
}
```

payload — объект, а не строка с вложенным JSON. Номер версии агрегата выделяется в той же транзакции, что бизнес-запись и outbox. occurredAt — время сохранения бизнес-факта, а не время consumer. Неизвестная schemaVersion, неверный ключ и несовместимый с топиком eventType не применяются к проекции. Пользовательские события содержат публичный профиль без password_hash, пароля и JWT.

UserRegistered/UserUpdated содержат публичный User; TourChanged — текущий Tour с direction и schedules; BookingStatusChanged — снимок Booking; PaymentSucceeded — Payment и снимок Booking. Это реализованное подмножество сценариев ЛР2, а не обещание реализованных возвратов/банковских callback. TourChanged обновляет вместимость и неоплаченные выезды в аналитике, а PaymentSucceeded добавляет факт продажи на дату occurredAt в UTC.

## Outbox и порядок

EventJournal запрещает запись вне локальной транзакции. integration_outbox хранит topic, sequence, aggregateVersion, schemaVersion, occurredAt и payload. Relay проверяет владельца топика и последовательно публикует записи по sequence. Транзакционная advisory-блокировка допускает только один Relay одного сервиса в момент времени, в том числе при нескольких экземплярах. Kafka producer: acks=all, enable.idempotence=true. published_at обновляется только после подтверждения брокера. Повторная доставка возможна — после публикации процесс может упасть до commit published_at.

Inbox и изменение проекции — одна PostgreSQL-транзакция с rollbackFor=Exception.class. Повторы eventId пропускаются. Для платежей есть дополнительная уникальность бизнес-факта; для снимков TourChanged — проверка aggregateVersion, исключающая откат к старому состоянию. Offset подтверждается после успешного возврата из transactional consumer.

## Независимые DLT

| Источник | Обработчик | DLT |
|---|---|---|
| user.events.v1 | Notification | user.events.v1.notification.dlt |
| booking.events.v1 | Notification | booking.events.v1.notification.dlt |
| payment.events.v1 | Notification | payment.events.v1.notification.dlt |
| payment.events.v1 | Booking | payment.events.v1.booking.dlt |
| payment.events.v1 | Analytics | payment.events.v1.analytics.dlt |
| tour.events.v1 | Analytics | tour.events.v1.analytics.dlt |

Каждый DLT также имеет 3 партиции; сообщение сохраняет исходную партицию и ключ. Два повтора с интервалом 500 мс, затем DLT. Заголовки содержат исходный топик/партицию/offset, consumer group, исключение и delivery attempt. Ошибка отправки в DLT не считается успешной обработкой. DLT по подписке позволяет отдельно расследовать сбой Analytics, не смешивая его со сбоем Booking при одном исходном событии. Retention: 7 суток основной поток, 14 суток DLT. Репликация 1 — осознанное ограничение одноброкерного учебного стенда.

Исправление/повтор DLT выполняется осознанно после устранения причины с сохранением eventId; повтор на исходный топик безопасен для уже обработавших групп благодаря inbox. Нельзя менять валидный payload с прежним eventId после успешной обработки; новую бизнес-операцию оформляют новым событием.

Проверки: scripts/verify-kafka-topology.py и verify-kafka-errors.py; доставка и дубль — verify-integration.py. Broker auto-create отключён, чтобы опечатка в имени не создала незапланированный поток.
