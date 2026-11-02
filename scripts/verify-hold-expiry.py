#!/usr/bin/env python3
"""Exercise durable hold expiry through the running Gateway and real service databases."""
import json
import os
import pathlib
import subprocess
import time
import urllib.request
import uuid

root = pathlib.Path(__file__).resolve().parents[1]
url = os.environ.get('GRAPHQL_URL', 'http://127.0.0.1:8180/graphql')


def gql(query, variables=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(url, json.dumps({'query': query, 'variables': variables or {}}).encode(), headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        result = json.load(response)
    assert not result.get('errors'), result
    return result['data']


def sql(service, query):
    result = subprocess.run(['docker', 'compose', 'exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1',
                             '-U', service + '_app', '-d', service + '_db', '-Atc', query],
                            cwd=root, text=True, capture_output=True, timeout=30)
    assert result.returncode == 0, result.stderr
    return result.stdout.strip()


def waitfor(predicate):
    deadline = time.monotonic() + 50
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.5)
    raise AssertionError('Recovery did not reach the expected durable state')


def login(number):
    return gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',
               {'i': {'email': f'user{number}@example.test', 'password': os.environ.get('LAB_PASSWORD', 'Lab567-Travel!')}})['authenticate']['accessToken']


client, staff = login(1), login(3)
direction = gql('{directions{id}}', token=client)['directions'][0]['id']
tour = gql('mutation($i:CreateTourInput!){createTour(input:$i){id}}',
           {'i': {'directionId': direction, 'title': 'Expiry regression ' + uuid.uuid4().hex[:8],
                  'description': 'Hold expiry regression', 'durationDays': 2}}, staff)['createTour']['id']
schedule = gql('mutation($i:CreateScheduleInput!){createSchedule(input:$i){id}}',
               {'i': {'tourId': tour, 'startsAt': '2030-07-01T10:00:00Z', 'endsAt': '2030-07-03T10:00:00Z',
                      'capacity': 10, 'basePriceMinor': '10000'}}, staff)['createSchedule']['id']
version = gql('query($id:ID!){tour(id:$id){version}}', {'id': tour}, staff)['tour']['version']
gql('mutation($i:UpdateTourInput!){updateTour(input:$i){id}}',
    {'i': {'id': tour, 'status': 'PUBLISHED', 'expectedVersion': version}}, staff)
gql('{tours(sort:{field:POPULARITY,direction:DESC},page:{page:0,size:10}){totalCount}}', token=client)
checks = [{'check': 'popularity sort uses a valid whole-day period', 'ok': True}]

for create_payment in (False, True):
    booking = gql('mutation($i:CreateBookingInput!,$key:String!){createBooking(input:$i,idempotencyKey:$key){id}}',
                  {'i': {'scheduleId': schedule, 'travelerCount': 1, 'expectedPriceVersion': '1'},
                   'key': uuid.uuid4().hex}, client)['createBooking']['id']
    payment = None
    if create_payment:
        payment = gql('mutation($i:CreatePaymentInput!,$key:String!){createPayment(input:$i,idempotencyKey:$key){id}}',
                      {'i': {'bookingId': booking}, 'key': uuid.uuid4().hex}, client)['createPayment']['id']
    assert sql('tour', f"select reserved_seats from tour_schedules where id='{schedule}'") == '1'
    # Time travel the persisted deadline instead of sleeping for the real 15-minute hold.
    sql('booking', f"update bookings set hold_expires_at=now()-interval '1 second' where id='{booking}'")
    waitfor(lambda: sql('booking', f"select status from bookings where id='{booking}'") == 'CANCELLED')
    waitfor(lambda: sql('tour', f"select reserved_seats from tour_schedules where id='{schedule}'") == '0')
    if payment:
        waitfor(lambda: sql('payment', f"select status from payments where id='{payment}'") == 'FAILED')
    assert sql('booking', f"select count(*) from booking_status_history where booking_id='{booking}' and to_status='CANCELLED'") == '1'
    assert sql('tour', f"select status from rpc_reservations where booking_id='{booking}'") == 'RELEASED'
    checks.append({'check': 'expired hold with pending payment' if payment else 'expired hold without payment',
                   'ok': True, 'bookingId': booking, 'paymentId': payment})

output = root / 'docs/evidence/labs567/hold-expiry.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(checks, indent=2))
print('PASS popularity and hold expiry with/without payment; seats released and history recorded once')
