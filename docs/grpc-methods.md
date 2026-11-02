# Внутренние контракты

Это спецификация следующих работ. В ЛР3 выполняются только mock-вызовы Java-интерфейсов. Полные модели находятся в contracts/proto/travel/v1/travel.proto.

UUID имеет канонический строковый формат; timestamps передаются как google.protobuf.Timestamp. Optional и поля-сообщения поддерживают присутствие; repeated отсутствующие коллекции являются пустыми.

Авторизация будущего gRPC выполняется через защищённые metadata и сервисные credentials; идентификатор пользователя не является доказательством полномочий.

| Сервис | Методы |
| --- | --- |
| User | GetPrincipal, GetUser, GetClientProfile, CreateUser, Authenticate, UpdateUser |
| Tour | ListTours, GetTour, ListDirections, CreateTour, UpdateTour, CreateSchedule, UpdateSchedule, AddAccommodation, AddTransport, UpsertDirection, ReserveSeats, ConfirmReservation, ReleaseReservation |
| Booking | GetBooking, ListClientBookings, ListScheduleRecipients, CreateBooking, CancelBooking, ChangeBookingStatus, GetPaymentContext, ApplyPaymentResult, ApplyRefundResult |
| Payment | ListBookingPayments, CreatePayment, RequestRefund |
| Notification | ListUserNotifications |
| Analytics | GetSalesAnalytics, GetPopularity |

Статусы: OK; INVALID_ARGUMENT для параметров; NOT_FOUND для отсутствующего объекта; UNAUTHENTICATED/PERMISSION_DENIED для доступа; FAILED_PRECONDITION для недопустимого состояния; ABORTED для версии; RESOURCE_EXHAUSTED при отсутствии мест; UNAVAILABLE/DEADLINE_EXCEEDED для временной недоступности.

GetPaymentContext передаёт scheduleId и directionId из снимка брони; ReserveSeats возвращает их из Tour DB. Payment сохраняет их в собственной записи платежа. При частичном возврате Payment остаётся SUCCEEDED; REFUNDED означает возврат всей суммы. ApplyRefundResult сообщает суммарную возвращённую сумму.
