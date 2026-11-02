> Исторический документ этапа ЛР3. Для сдачи ЛР5–7 используйте [актуальную инструкцию](defense-labs567.md), [Kafka-контракты](events.md) и [gRPC-контракты](grpc-methods.md).

# Исправления строгого ревью

1. MockTourClient.directions читает независимый directions.json. Направление без туров не теряется. Две регрессии сначала падали, затем прошли.
2. ReadSupport.sameId сравнивает валидные UUID независимо от регистра. Исправлены mock lookups, owner/payment filters и directionId в обеих TourSearch. Шесть тестов с буквенными UUID сначала падали, затем прошли.
3. Long.parseLiteral выбрасывает CoercingParseLiteralException, parseValue — CoercingParseValueException. Пять scalar-тестов, включая дроби, overflow и границы long. На старом коде тест malformed literal не проходил. HTTP 500 не воспроизводился и не заявляется.
4. Добавлены проверки нескольких booking/payment списков, nullable ошибок с сохранением независимого поля, staff-доступа, огромной страницы и GraphQL scalar ValidationError.
5. В отчёте исправлены ключи users/roles, реальные Java-типы UUID, null propagation и различие ошибок выполнения/валидации GraphQL. Добавлены полная SDL, листинги и точная среда повторной проверки.

Граница работы сохраняется: запросы чтения, шесть самостоятельных БД, mock через интерфейсы; без реальных gRPC, Kafka и Mutation. Отчёт сопровождается исходниками и фактическими результатами, но оценку выставляет преподаватель.
