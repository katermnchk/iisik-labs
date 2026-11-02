# Исполняемые контракты ЛР3

## Внешний API

POST /graphql. Content-Type: application/json. Authorization: Bearer <demo-token>. Тело: {"query":"...","variables":{...},"operationName":"..."}; последние два поля необязательны. Успех: HTTP 200, {"data":{...}}. Ошибка выполнения: HTTP 200, {"data":... или null,"errors":[{"message":"...","path":[...],"extensions":{"code":"NOT_FOUND|BAD_REQUEST|FORBIDDEN|INTERNAL_ERROR"}}]}. Для неизвестного объекта nullable field становится null. Не передан/неизвестен токен: HTTP 401, code UNAUTHENTICATED; заблокирован: HTTP 403, code FORBIDDEN.

Ошибки типов, синтаксиса и scalar-значений обрабатываются GraphQL как ValidationError и не обязаны иметь прикладной extensions.code. Ошибка non-null поля распространяет null до ближайшего nullable родителя; ошибка salesAnalytics может обнулить всё data.

Единственная точка входа /graphql. Названия me/tours/booking являются полями Query, а не URL вида /graphql/tours. GET /actuator/health возвращает {"status":"UP"}, HTTP 200. GET /graphiql открывает редактор запросов.

Полная исполняемая схема описывает восемь Query, типы, Input, 12 enum и scalars Date, DateTime, Long. Примеры всех Query лежат в examples. В Money.amountMinor используется строка целого числа копеек.

## Интерфейсы Java

| Клиент | Метод | Request | Response |
| --- | --- | --- | --- |
| UserClient | principal | accessToken:String | Principal(userId,role,status,email,clientId?,employeeId?) |
| UserClient | user | userId:String UUID | User с Role и профилями |
| TourClient | tours | filter:TourFilter?,sort:TourSort?,page:PageInput,popularity:List<DirectionPopularity>,popularityAsOf:OffsetDateTime? | Page<Tour> |
| TourClient | tour | tourId:String UUID,viewerRole:RoleCode | Tour с direction/schedules/accommodations/transports |
| TourClient | directions | activeOnly:boolean | List<Direction> |
| BookingClient | booking | bookingId:String UUID | Booking со снимком цены и выезда |
| BookingClient | bookings | clientId:String UUID,page:PageInput | Page<Booking> |
| PaymentClient | payments | bookingIds:List<String UUID> | List<Payment> |
| NotificationClient | notifications | userId:String UUID,page:PageInput | Page<Notification> |
| AnalyticsClient | analytics | filter:AnalyticsFilter | SalesAnalytics |
| AnalyticsClient | popularity | from:OffsetDateTime,to:OffsetDateTime | List<DirectionPopularity> |

Request и response - Java records из Model.java. Клиентские интерфейсы определены в Clients.java. DomainException содержит code и безопасное message. Эти вызовы в ЛР3 локальные: нет сетевого URL, HTTP-кода или gRPC-status; Gateway переводит исключение в GraphQL error. Полные поля и типы всех records находятся в contracts.

## Диагностические endpoints внутренних сервисов

Все методы GET, ответ Content-Type application/json. Успех HTTP 200. Для отсутствующего объекта HTTP 404 с code NOT_FOUND, для неверных параметров HTTP 400 с code BAD_REQUEST. Конкретные поля DTO совпадают с Java Model; GraphQL выбирает из них только поля своей схемы.

| Порт | Путь | Параметры | Ответ |
| --- | --- | --- | --- |
| 8091 | /internal/v1/users | нет | User[] |
| 8091 | /internal/v1/users/{id} | id UUID | User |
| 8092 | /internal/v1/directions | activeOnly:boolean=true | Direction[] |
| 8092 | /internal/v1/tours | page=0,size=20,directionId?,from?,to?,minPrice?,maxPrice?,onlyAvailable=true,sort=STARTS_AT,order=ASC | Page<Tour> |
| 8092 | /internal/v1/tours/{id} | id UUID | Tour |
| 8093 | /internal/v1/bookings | clientId UUID,page=0,size=20 | Page<Booking> |
| 8093 | /internal/v1/bookings/{id} | id UUID | Booking |
| 8094 | /internal/v1/payments | bookingIds: UUID через запятую | Payment[] |
| 8095 | /internal/v1/notifications | userId UUID,page=0,size=20 | Page<Notification> |
| 8096 | /internal/v1/analytics | from,to: ISO8601 timestamp, полные дни UTC | SalesAnalytics |

Tour REST не принимает POPULARITY: для неё нужен внешний снимок Analytics, который передаёт ListToursRequest в клиентском интерфейсе. Популярность демонстрируется через GraphQL. REST endpoints не выполняют межсервисных вызовов и не имеют пользовательской авторизации; их назначение - локальная проверка чтения собственных БД.

## Будущий gRPC

Пути вида /travel.v1.UserService/GetUser определены protobuf service/method. Полный набор 34 методов закреплён travel.proto; request/response не являются произвольным JSON. Схема скомпилирована protoc 3.25.5. Отсутствующие scalar-фильтры min_price/max_price/only_available имеют optional presence; отсутствие only_available трактуется как true при будущей реализации.

Сетевые gRPC серверы и клиенты сейчас не исполняются. Будущие статусы и источники полей описаны в grpc-methods.md.
