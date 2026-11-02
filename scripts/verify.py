"""Verify running applications and preserve actual HTTP requests/responses."""
import argparse, datetime, json, urllib.request, urllib.error
from pathlib import Path

def uid(n):
    return f'00000000-0000-0000-0000-{n:012d}'

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--services', action='store_true')
    p.add_argument('--output', default='docs/evidence/current')
    args = p.parse_args()
    out = Path(args.output)
    (out / 'transactions').mkdir(parents=True, exist_ok=True)
    results = []

    def request(name, url, q=None, token='demo-client'):
        h = {}
        payload = None
        if q is not None:
            h = {'Content-Type': 'application/json'}
            payload = {'query': q}
            if token is not None:
                h['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request(url, data=None if payload is None else json.dumps(payload).encode(), headers=h)
        try:
            r = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as error:
            r = error
        with r:
            status = r.status
            body = json.load(r)
            record = {'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'request': {'method': req.get_method(), 'url': url, 'headers': h, 'body': payload}, 'response': {'status': status, 'headers': dict(r.headers), 'body': body}}
        (out / 'transactions' / (name + '.json')).write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
        return (status, body)

    def gql(name, q, token='demo-client'):
        return request(name, 'http://127.0.0.1:8080/graphql', q, token)

    def check(name, response, test, status=200):
        actual, b = response
        assert actual == status and test(b), (name, actual, b)
        (out / (name + '.json')).write_text(json.dumps(b, ensure_ascii=False, indent=2), encoding='utf-8')
        results.append({'name': name, 'result': 'PASS', 'httpStatus': actual})
        print('PASS', name, flush=True)
        return b

    def code(expected):
        return lambda b: b.get('errors', [{}])[0].get('extensions', {}).get('code') == expected
    check('me', gql('me', '{me{id role{code} firstName clientProfile{phone}}}'), lambda b: b['data']['me']['id'] == uid(1))
    check('tour_nested', gql('tour_nested', '{tour(id:"' + uid(201) + '"){id schedules{id accommodations{name} transports{kind}}}}'), lambda b: len(b['data']['tour']['schedules'][0]['accommodations']) == 2)
    check('same_schedule_filter', gql('same_schedule_filter', '{tours(page:{page:0,size:20},filter:{from:"2026-12-01",to:"2026-12-31",maxPrice:"60000"}){items{id} totalCount}}'), lambda b: b['data']['tours']['totalCount'] == 0)
    check('booking_payments', gql('booking_payments', '{booking(id:"' + uid(604) + '"){id payments{status amount{amountMinor}}}}'), lambda b: [p['status'] for p in b['data']['booking']['payments']] == ['FAILED', 'PENDING'])
    check('not_found', gql('not_found', '{tour(id:"' + uid(999) + '"){id}}'), lambda b: code('NOT_FOUND')(b) and b['data']['tour'] is None)
    check('partial_response', gql('partial_response', '{me{id} tour(id:"' + uid(999) + '"){id}}'), lambda b: code('NOT_FOUND')(b) and b['data']['me']['id'] == uid(1) and (b['data']['tour'] is None))
    check('foreign_booking', gql('foreign_booking', '{booking(id:"' + uid(602) + '"){id}}'), code('FORBIDDEN'))
    for token in ['demo-employee', 'demo-admin']:
        name = 'staff_booking_' + token
        check(name, gql(name, '{booking(id:"' + uid(602) + '"){id}}', token), lambda b: b['data']['booking']['id'] == uid(602))
    sales = '{salesAnalytics(filter:{from:"2026-10-01T00:00:00Z",to:"2026-10-05T00:00:00Z"}){grossSales{amountMinor} refunded{amountMinor} netSales{amountMinor}}}'
    check('sales', gql('sales', sales, 'demo-admin'), lambda b: [b['data']['salesAnalytics'][k]['amountMinor'] for k in ['grossSales', 'refunded', 'netSales']] == ['280000', '100000', '180000'])
    for token in ['demo-client', 'demo-employee']:
        name = 'forbidden_analytics_' + token
        check(name, gql(name, sales, token), code('FORBIDDEN'))
    for name, token, status, expected in [('missing_token', None, 401, 'UNAUTHENTICATED'), ('unknown_token', 'wrong', 401, 'UNAUTHENTICATED'), ('blocked_token', 'demo-blocked', 403, 'FORBIDDEN')]:
        check(name, gql(name, '{me{id}}', token), code(expected), status)
    for size in [0, 101]:
        name = 'invalid_size_' + str(size)
        check(name, gql(name, '{tours(page:{page:0,size:' + str(size) + '}){totalCount}}'), code('BAD_REQUEST'))
    check('invalid_uuid', gql('invalid_uuid', '{tour(id:"bad"){id}}'), code('BAD_REQUEST'))
    check('invalid_price_range', gql('invalid_price_range', '{tours(page:{page:0,size:20},filter:{minPrice:"100",maxPrice:"1"}){totalCount}}'), code('BAD_REQUEST'))
    check('huge_page', gql('huge_page', '{tours(page:{page:2147483647,size:100}){items{id} totalCount page{hasNext}}}'), lambda b: b['data']['tours']['items'] == [] and b['data']['tours']['totalCount'] == 2 and (not b['data']['tours']['page']['hasNext']))
    check('scalar_overflow', gql('scalar_overflow', '{tours(page:{page:0,size:20},filter:{maxPrice:"9223372036854775808"}){totalCount}}'), lambda b: b['errors'][0]['extensions']['classification'] == 'ValidationError')
    check('directions', gql('directions', '{directions{id}}'), lambda b: len(b['data']['directions']) == 2)
    check('notifications', gql('notifications', '{notifications(page:{page:0,size:20}){totalCount items{id}}}'), lambda b: b['data']['notifications']['totalCount'] == 1)
    batch = check('batch_payments', gql('batch_payments', '{myBookings(page:{page:0,size:20}){items{id payments{id bookingId}}}}'), lambda b: len(b['data']['myBookings']['items']) == 3)
    bookings = batch['data']['myBookings']['items']
    assert sum((len(b['payments']) for b in bookings)) == 4
    assert all((p['bookingId'] == b['id'] for b in bookings for p in b['payments']))
    if args.services:

        def normalize(value, key=None):
            if isinstance(value, dict):
                return {k: normalize(v, k) for k, v in value.items()}
            if isinstance(value, list):
                return [normalize(v) for v in value]
            if key in ('amountMinor', 'version', 'priceVersion'):
                return str(value)
            if key and (key.endswith('At') or key == 'asOf') and (value is not None):
                return datetime.datetime.fromisoformat(value.replace('Z', '+00:00')).astimezone(datetime.timezone.utc).isoformat()
            return value

        def project(rest, selected):
            if isinstance(selected, dict):
                return {k: project(rest[k], v) for k, v in selected.items()}
            if isinstance(selected, list):
                assert len(rest) == len(selected)
                return [project(r, s) for r, s in zip(rest, selected)]
            return rest
        cases = [('user', 8091, 'users/' + uid(3), '{me{id email firstName lastName status role{code name} employeeProfile{userId position}}}', 'me', 'demo-employee'), ('tour', 8092, 'tours/' + uid(201), '{tour(id:"' + uid(201) + '"){id title version direction{id country city isActive} schedules{id startsAt availableSeats price{amountMinor currency} accommodations{id name} transports{id kind}}}}', 'tour', 'demo-client'), ('booking', 8093, 'bookings?clientId=' + uid(1), '{myBookings(page:{page:0,size:20}){items{id total{amountMinor currency} status version} totalCount page{page size hasNext}}}', 'myBookings', 'demo-client'), ('payment', 8094, 'payments?bookingIds=' + uid(604), '{booking(id:"' + uid(604) + '"){payments{id bookingId status amount{amountMinor currency} createdAt}}}', 'booking', 'demo-client'), ('notification', 8095, 'notifications?userId=' + uid(1), '{notifications(page:{page:0,size:20}){items{id title body kind status createdAt} totalCount page{page size hasNext}}}', 'notifications', 'demo-client'), ('analytics', 8096, 'analytics?from=2026-10-01T00:00:00Z&to=2026-10-05T00:00:00Z', '{salesAnalytics(filter:{from:"2026-10-01T00:00:00Z",to:"2026-10-05T00:00:00Z"}){grossSales{amountMinor currency} refunded{amountMinor currency} netSales{amountMinor currency} occupancy directions{directionId directionName confirmedTourists rank} asOf}}', 'salesAnalytics', 'demo-admin')]
        for name, port, path, q, key, token in cases:
            status, rest = request('db_' + name, f'http://127.0.0.1:{port}/internal/v1/' + path)
            assert status == 200, (name, status, rest)
            status, graph = gql('parity_' + name, q, token)
            assert status == 200 and 'errors' not in graph, graph
            selected = graph['data'][key]
            if name == 'payment':
                selected = selected['payments']
            expected = normalize(project(rest, selected))
            check('parity_' + name, (status, selected), lambda b: normalize(b) == expected)
        status, dirs = request('db_directions', 'http://127.0.0.1:8092/internal/v1/directions?activeOnly=false')
        graph = gql('parity_directions', '{directions(activeOnly:false){id country city description isActive}}')[1]['data']['directions']
        check('parity_directions', (status, graph), lambda b: b == dirs)
        for name, port, path, status in [('db_not_found', 8092, 'tours/' + uid(999), 404), ('db_bad_uuid', 8093, 'bookings/bad', 400)]:
            check(name, request(name, f'http://127.0.0.1:{port}/internal/v1/' + path), lambda b: b['code'] == ('NOT_FOUND' if status == 404 else 'BAD_REQUEST'), status)
    summary = {'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'services': args.services, 'count': len(results), 'results': results}
    (out / 'verification.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'{len(results)} HTTP checks passed', flush=True)
if __name__ == '__main__':
    main()
