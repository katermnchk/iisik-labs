# Исполняемые типизированные контракты ЛР5–7

Источник protobuf — `contracts/proto/travel/v1/travel.proto`, package `travel.v1`. Maven модуля transport генерирует Java messages и дескрипторы RPC. На сети передаются protobuf request/response соответствующих именованных методов: например, `/travel.v1.TourService/ReserveSeats`. Полей `arguments_json`, `result_json` и общего RPC `TravelRpc.Call` в текущем транспорте нет.

`RpcBindings` явно сопоставляет Java-порты домена с именованными RPC; `ProtoMapper` переводит поля DTO в protobuf и обратно. `RpcServer` регистрирует только методы, для которых сервис предоставил реализацию интерфейса. Наличие метода в проектной .proto само по себе не означает наличие его runtime-обработчика: оставшиеся проектные методы без Java-порта возвращают UNIMPLEMENTED. Полный сценарий возврата оплаты не реализован. Бизнес-правила выполняются в сервисном слое.

| Сервис и порт | Java-операции → типизированные RPC |
|---|---|
| User 9091 | user → GetUser; principal → GetPrincipal; registerProfile → RegisterProfile; updateUser → UpdateUser |
| Tour 9092 | tour → GetTour; tours → ListTours; directions → ListDirections; schedule → GetSchedule; createTour → CreateTour; updateTour → UpdateTour; createSchedule → CreateSchedule; updateSchedule → UpdateSchedule; reserve → ReserveSeats; release → ReleaseReservation |
| Booking 9093 | booking → GetBooking; bookings → ListClientBookings; filtered → FilterBookings; summary → SummarizeBookings; createBooking → CreateBooking; cancelBooking → CancelBooking; changeBookingStatus → ChangeBookingStatus; claimPayment → ClaimPayment; expireBooking → ExpireBooking; releaseOrphanReservation → ReleaseOrphanReservation |
| Payment 9094 | payments → ListBookingPayments; createPayment → CreatePayment; confirmPayment → ConfirmPayment; expirePayment → ExpirePayment |
| Notification 9095 | notifications → ListUserNotifications |
| Analytics 9096 | analytics → GetSalesAnalytics; popularity → GetPopularity |

Регистрация/вход/проверка JWT и logout Gateway → Auth выполняются через REST отдельного Auth Service. Auth обращается к User по типизированному RPC. Проектные Authenticate/CreateUser в старом UserService не заменяют отдельный Auth Service ЛР5.

## Пример удержания мест

В текущем runtime зарегистрирован 31 метод из 43 объявлений проектной схемы. Не зарегистрированы следующие 12 методов; они не используются текущим GraphQL и возвращают UNIMPLEMENTED:

- UserService: GetClientProfile, CreateUser, Authenticate.
- TourService: AddAccommodation, AddTransport, UpsertDirection, ConfirmReservation.
- BookingService: ListScheduleRecipients, GetPaymentContext, ApplyPaymentResult, ApplyRefundResult.
- PaymentService: RequestRefund.

Отдельный Auth Service выполняет регистрацию и вход. Подтверждение оплаты приходит в Booking через Kafka, а не через проектный ApplyPaymentResult. Неиспользуемые объявления не считаются работающими RPC.

`ReserveSeatsRequest` содержит booking_id, schedule_id, seats и expected_price_version. Ответ `ReserveSeatsResponse` содержит типизированный снимок удержания/цены/выезда. Booking сохраняет его в собственной БД. Повтор ReserveSeats с тем же booking_id использует постоянную запись `rpc_reservations`; ReleaseReservation возвращает места идемпотентно.

UUID передаются строками канонического формата. Время — google.protobuf.Timestamp; деньги — целочисленная сумма и код валюты. Optional/message поля сохраняют присутствие, repeated представляют пустую коллекцию при отсутствии элементов. Principal/actor передаётся типизированным полем команды внутри доверенного контура.

## Metadata и ошибки

`x-service-key` содержит SERVICE_KEY, `x-correlation-id` переносит correlationId. Это внутренний локальный контур; ключ не является пользовательским JWT и не заменяет роль/проверку владения объектом. Для публичного развёртывания требуется защищённый транспорт; текущий локальный стенд использует plaintext HTTP/2.

Каждая попытка RPC имеет deadline 2000 мс. Безопасные чтения и явно идемпотентные операции допускают до трёх попыток при UNAVAILABLE/DEADLINE_EXCEEDED с паузами 100/200 мс. Неидемпотентные изменения выполняются одной попыткой. Таблица retryable задаётся в RpcBindings.

Прикладной код ошибки переносится в trailer `domain-code`, а Gateway возвращает его в `errors[].extensions.code`. Транспортная недоступность преобразуется в SERVICE_UNAVAILABLE. Параметры, права, версия, вместимость и состояние проверяются до изменения данных; бизнес-ошибки не повторяются автоматически.

## История контракта

Типы и именованные операции продолжают проектную схему ЛР2. Дополнения для ЛР5–7 — actor/Principal, RegisterProfile, методы согласования оплаты и восстановления — добавлены в тот же versioned package с сохранением номеров существующих полей. Старые HTTP-контракты находятся в `http-contracts.md` как исторический материал и не описывают текущий runtime. Точная схема полей определяется .proto, а фактически зарегистрированные методы — RpcBindings и RpcConfiguration каждого сервиса.
