#!/usr/bin/env python3
"""Focused Lab 5 checks on the local real stack. Never persists tokens/passwords."""
import base64
import json
import os
import pathlib
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
URL = os.environ.get('GRAPHQL_URL', 'http://127.0.0.1:8180/graphql')
RESULTS = []
PASSWORD = 'Review5-password!'

def record(name, condition, **details):
    assert condition, (name, details)
    RESULTS.append(dict(check=name, ok=True, **details))
    print('PASS', name, flush=True)

def request(url, body=None, authorization=None):
    headers = {'Content-Type': 'application/json'}
    if authorization is not None:
        headers['Authorization'] = authorization
    req = urllib.request.Request(url, None if body is None else json.dumps(body).encode(), headers)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        try:
            result = json.loads(raw)
        except ValueError:
            result = {'nonJson': True}
        return response.status, result

def gql(query, variables=None, token=None, authorization=None):
    return request(URL, {'query': query, 'variables': variables or {}},
                   authorization if authorization is not None else ('Bearer '+token if token else None))

def checked(name, query, variables=None, token=None, code=None, authorization=None):
    status, body = gql(query, variables, token, authorization)
    valid = (any(e.get('extensions', {}).get('code') == code for e in body.get('errors', []))
             if code else not body.get('errors') and body.get('data') is not None)
    record(name, valid and status == 200, httpStatus=status, expectedCode=code,
           response=redact(body))
    return body.get('data')

def redact(value):
    if isinstance(value, dict):
        return {k: ('<redacted>' if k in ('accessToken', 'password') else redact(v)) for k,v in value.items()}
    if isinstance(value, list):
        return [redact(v) for v in value]
    return value

def command(*args):
    p = subprocess.run(['docker', 'compose', *args], cwd=ROOT, capture_output=True, text=True, timeout=45)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()

def sql(db, query):
    return command('exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', db+'_app', '-d', db+'_db', '-Atc', query)

def wait_ready():
    deadline=time.monotonic()+45
    while time.monotonic()<deadline:
        try:
            if command('exec', '-T', 'redis', 'redis-cli', 'ping') == 'PONG':
                return
        except AssertionError:
            pass
        time.sleep(.5)
    raise AssertionError('Redis did not recover')

LOGIN='mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken user{id role{code}}}}'
REGISTER='mutation($i:RegisterInput!){register(input:$i){accessToken user{id email role{code}}}}'
UPDATE='mutation($i:UpdateUserInput!){updateUser(input:$i){id status role{code}}}'
ME='{me{id email role{code} status}}'
ANALYTICS='{salesAnalytics(filter:{from:"2026-01-01T00:00:00Z",to:"2031-01-01T00:00:00Z"}){occupancy}}'

def login(email, password, name):
    return checked(name, LOGIN, {'i':{'email':email,'password':password}})['authenticate']['accessToken']

def claims(token):
    part=token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(part+'='*((-len(part))%4)))

checked('anonymous Query rejected', ME, code='UNAUTHENTICATED')
checked('anonymous alias rejected', '{other:me{id}}', code='UNAUTHENTICATED')
checked('anonymous fragment rejected', 'query{...Private} fragment Private on Query{me{id}}', code='UNAUTHENTICATED')
checked('fake demo token rejected', ME, token='demo-admin', code='UNAUTHENTICATED')
checked('wrong authorization scheme rejected', ME, authorization='Basic example', code='UNAUTHENTICATED')
checked('anonymous Mutation rejected', 'mutation{logout}', code='UNAUTHENTICATED')
email='review5-'+uuid.uuid4().hex+'@example.test'
fields=dict(email=email.upper(),password=PASSWORD,firstName='Проверка',lastName='Безопасности',phone='+375290000099')
registered=checked('register creates CLIENT', REGISTER, {'i':fields})['register']
token=registered['accessToken']; uid=registered['user']['id']; c=claims(token)
record('normalized email and fixed registration role', registered['user']['email']==email and registered['user']['role']['code']=='CLIENT')
record('JWT expected claims and lifetime', c['iss']=='travel-auth' and c['aud']=='travel-api' and c['sub']==uid and c['role']=='CLIENT' and c['exp']-c['iat']==1800,
       issuer=c['iss'], audience=c['aud'], role=c['role'], lifetimeSeconds=c['exp']-c['iat'])
session='travel:session:'+c['jti']
record('Redis session bound to user', command('exec','-T','redis','redis-cli','GET',session)==uid)
ttl=int(command('exec','-T','redis','redis-cli','TTL',session))
record('Redis session has finite TTL', 0<ttl<=1800, ttlSeconds=ttl)
record('BCrypt hash and completed intent persisted', sql('auth',"select (password_hash like '$2%')::text || ':' || profile_ready::text from credentials where user_id='"+uid+"'")=='true:true')
record('role and client profile persisted in User DB', sql('user',"select r.code || ':' || (c.user_id is not null)::text from users u join roles r on r.id=u.role_id left join clients c on c.user_id=u.id where u.id='"+uid+"'")=='CLIENT:true')
checked('registered user me succeeds', ME, token=token)
checked('duplicate normalized email rejected', REGISTER, {'i':fields}, code='CONFLICT')
bad=dict(fields,email='short-'+uuid.uuid4().hex+'@example.test',password='short')
checked('short registration password rejected',REGISTER,{'i':bad},code='BAD_REQUEST')
bad['password']='я'*37
checked('BCrypt UTF8 byte limit enforced',REGISTER,{'i':bad},code='BAD_REQUEST')
# Unknown input fields are SDL validation errors, not application BAD_REQUEST.
status,body=gql(REGISTER,{'i':dict(fields,role='ADMIN')})
record('SDL rejects injected registration role', status==200 and bool(body.get('errors')) and not body.get('data'), httpStatus=status)
checked('wrong password rejected', LOGIN, {'i':{'email':email,'password':'wrong-password!'}},code='UNAUTHENTICATED')
checked('unknown account rejected', LOGIN, {'i':{'email':'absent-'+uuid.uuid4().hex+'@example.test','password':PASSWORD}},code='UNAUTHENTICATED')
second=login(email,PASSWORD,'valid login creates second session')
record('separate login has distinct session',claims(second)['jti']!=c['jti'])
parts=token.split('.'); parts[2]=('A' if parts[2][0]!='A' else 'B')+parts[2][1:]
checked('tampered signature rejected',ME,token='.'.join(parts),code='UNAUTHENTICATED')
checked('CLIENT analytics rejected',ANALYTICS,token=token,code='FORBIDDEN')
checked('CLIENT updateUser rejected',UPDATE,{'i':{'userId':uid,'firstName':'Forbidden'}},token,code='FORBIDDEN')
checked('CLIENT own bookings succeeds','{myBookings(page:{page:0,size:5}){totalCount}}',token=token)
checked('own notifications succeeds','{notifications(page:{page:0,size:5}){totalCount}}',token=token)
foreign=sql('booking',"select id from bookings where client_id <> '"+uid+"' order by created_at limit 1")
assert foreign, 'Seeded bookings required for ownership checks'
checked('foreign booking and nested payments rejected','query($id:ID!){booking(id:$id){id payments{id}}}',{'id':foreign},token,code='FORBIDDEN')
checked('foreign payment creation rejected','mutation($i:CreatePaymentInput!,$k:String!){createPayment(input:$i,idempotencyKey:$k){id}}',{'i':{'bookingId':foreign},'k':uuid.uuid4().hex},token,code='FORBIDDEN')
checked('foreign booking cancellation rejected','mutation($i:CancelBookingInput!,$k:String!){cancelBooking(input:$i,idempotencyKey:$k){id}}',{'i':{'bookingId':foreign,'reason':'Forbidden'},'k':uuid.uuid4().hex},token,code='FORBIDDEN')
admin=login('user4@example.test',os.environ.get('LAB_PASSWORD','Lab567-Travel!'),'ADMIN login')
checked('ADMIN analytics succeeds',ANALYTICS,token=admin)
checked('ADMIN changes role to EMPLOYEE',UPDATE,{'i':{'userId':uid,'role':'EMPLOYEE','position':'Проверка ЛР5'}},admin)
me=checked('existing JWT sees current EMPLOYEE role',ME,token=token)
record('current role overrides stale JWT claim',me['me']['role']['code']=='EMPLOYEE' and c['role']=='CLIENT')
checked('old CLIENT JWT loses client-only access','{myBookings(page:{page:0,size:5}){totalCount}}',token=token,code='FORBIDDEN')
checked('EMPLOYEE analytics rejected',ANALYTICS,token=token,code='FORBIDDEN')
checked('ADMIN blocks new test user',UPDATE,{'i':{'userId':uid,'status':'BLOCKED'}},admin)
checked('blocked existing JWT rejected',ME,token=token,code='FORBIDDEN')
checked('blocked valid login rejected',LOGIN,{'i':{'email':email,'password':PASSWORD}},code='FORBIDDEN')
checked('ADMIN restores CLIENT and ACTIVE',UPDATE,{'i':{'userId':uid,'role':'CLIENT','phone':fields['phone'],'status':'ACTIVE'}},admin)
checked('restored existing JWT usable',ME,token=token)
checked('logout revokes one session','mutation{logout}',token=token)
record('logout Redis key removed',command('exec','-T','redis','redis-cli','EXISTS',session)=='0')
checked('logged-out JWT rejected',ME,token=token,code='UNAUTHENTICATED')
checked('other active session remains usable',ME,token=second)
secondclaims=claims(second)
secondkey='travel:session:'+secondclaims['jti']
command('exec','-T','redis','redis-cli','SET',secondkey,'00000000-0000-0000-0000-000000000004','EX','1800')
checked('session subject mismatch rejected',ME,token=second,code='UNAUTHENTICATED')
command('exec','-T','redis','redis-cli','SET',secondkey,uid,'EX','1800')
command('exec','-T','redis','redis-cli','EXPIRE','travel:session:'+secondclaims['jti'],'1')
time.sleep(1.2)
record('JWT still valid during forced session expiry',secondclaims['exp']>time.time(), fixture='Only this new test session TTL shortened to one second')
checked('expired Redis session rejects valid JWT',ME,token=second,code='UNAUTHENTICATED')
third=login(email,PASSWORD,'re-authentication creates active session')
command('stop','redis')
try:
    checked('Redis outage denies protected Query',ME,token=third,code='SERVICE_UNAVAILABLE')
    checked('Redis outage denies successful credential login',LOGIN,{'i':{'email':email,'password':PASSWORD}},code='SERVICE_UNAVAILABLE')
finally:
    command('start','redis'); wait_ready()
deadline=time.monotonic()+30
while True:
    status,body=gql(ME,token=third)
    if not body.get('errors'): break
    if time.monotonic()>deadline: raise AssertionError('Auth Redis connection did not recover')
    time.sleep(.5)
record('Redis AOF preserves active session across restart',status==200 and body['data']['me']['id']==uid)
for port,path in [(8091,'users'),(8092,'directions'),(8093,'bookings'),(8094,'payments'),(8095,'notifications'),(8096,'analytics')]:
    for segment in ['internal','internal;x','%69nternal','internal%3Bx']:
        status,_=request('http://127.0.0.1:'+str(port)+'/'+segment+'/v1/'+path)
        record('internal HTTP closed '+str(port)+' '+segment,status in (400,404),httpStatus=status)
checked('cleanup last test session','mutation{logout}',token=third)
checked('cleanup ADMIN test session','mutation{logout}',token=admin)
output=ROOT/'docs/evidence/lab5-current';output.mkdir(parents=True,exist_ok=True)
(output/'security.json').write_text(json.dumps(dict(allPassed=True,checks=RESULTS,userId=uid,
    scope='Real local services, PostgreSQL, Redis. One test-session TTL explicitly shortened; Redis briefly stopped and restored.'),ensure_ascii=False,indent=2))
print('Verified',len(RESULTS),'Lab5 security checks',flush=True)
