# События Kafka

В ЛР3 Kafka не запускается. Ниже закреплён полный реестр для следующих работ.

Конверт: eventId UUID, eventType string, schemaVersion int, occurredAt ISO 8601 UTC, aggregateId UUID, aggregateVersion long, correlationId UUID, payload object. Денежные значения JSON передаются строкой целого; Money = amountMinor + currency.

| Событие | Топик | Публикатор | Потребители |
| --- | --- | --- | --- |
| UserRegistered | user.events.v1 | User | Notification |
| UserUpdated | user.events.v1 | User | Notification |
| DirectionUpdated | tour.events.v1 | Tour | Analytics |
| ScheduleChanged | tour.events.v1 | Tour | Notification, Analytics |
| ScheduleCancelled | tour.events.v1 | Tour | Booking, Notification, Analytics |
| BookingStatusChanged | booking.events.v1 | Booking | Notification, Analytics |
| PaymentSucceeded | payment.events.v1 | Payment | Notification, Analytics |
| PaymentFailed | payment.events.v1 | Payment | Notification |
| PaymentRefunded | payment.events.v1 | Payment | Notification, Analytics |

## UserRegistered

```json
{
  "userId": "uuid",
  "email": "string",
  "firstName": "string",
  "lastName": "string",
  "role": "RoleCode"
}
```


## UserUpdated

```json
{
  "userId": "uuid",
  "email": "string",
  "role": "RoleCode",
  "status": "UserStatus"
}
```


## DirectionUpdated

```json
{
  "directionId": "uuid",
  "country": "string",
  "city": "string",
  "description": "string",
  "isActive": "boolean"
}
```


## ScheduleChanged

```json
{
  "scheduleId": "uuid",
  "tourId": "uuid",
  "directionId": "uuid",
  "startsAt": "DateTime",
  "endsAt": "DateTime",
  "capacity": "int",
  "basePriceMinor": "long as decimal string",
  "currency": "BYN",
  "status": "ScheduleStatus"
}
```


## ScheduleCancelled

```json
{
  "scheduleId": "uuid",
  "reason": "string",
  "startsAt": "DateTime"
}
```


## BookingStatusChanged

```json
{
  "bookingId": "uuid",
  "clientId": "uuid",
  "scheduleId": "uuid",
  "directionId": "uuid",
  "travelerCount": "int",
  "fromStatus": "BookingStatus nullable",
  "toStatus": "BookingStatus",
  "total": "Money",
  "startsAt": "DateTime"
}
```


## PaymentSucceeded

```json
{
  "paymentId": "uuid",
  "bookingId": "uuid",
  "scheduleId": "uuid",
  "directionId": "uuid",
  "amount": "Money",
  "paidAt": "DateTime"
}
```


## PaymentFailed

```json
{
  "paymentId": "uuid",
  "bookingId": "uuid",
  "clientId": "uuid",
  "reason": "string"
}
```


## PaymentRefunded

```json
{
  "refundId": "uuid",
  "paymentId": "uuid",
  "bookingId": "uuid",
  "scheduleId": "uuid",
  "directionId": "uuid",
  "amount": "Money",
  "refundedAt": "DateTime"
}
```

Направление и выезд платежа получаются через GetPaymentContext и сохраняются в Payment DB. UserRegistered содержит данные регистрации, без password_hash. Получатели изменения расписания определяются Booking.ListScheduleRecipients, контакты - User.GetUser/GetClientProfile.

Повтор eventId пропускается через processed_events. outbox записывается в той же транзакции, что изменение. Старые обзорные имена TourUpdated/BookingCreated/PaymentRegistered заменены подробным реестром. NotificationSent в текущую модель не входит.
