#!/usr/bin/env python3
import urllib.request,urllib.error,json,uuid,pathlib,subprocess,time,os,base64,concurrent.futures
root=pathlib.Path(__file__).resolve().parents[1]
url=os.environ.get('GRAPHQL_URL','http://127.0.0.1:8180/graphql')
results=[]
def gql(query,variables=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 req=urllib.request.Request(url,json.dumps({'query':query,'variables':variables or {}}).encode(),h)
 try:
  with urllib.request.urlopen(req,timeout=20) as response:return json.load(response)
 except urllib.error.HTTPError as e:return json.load(e)
def ok(name,value):
 assert not value.get('errors'),(name,value)
 results.append({'check':name,'ok':True,'response':value});print('PASS',name,flush=True);return value['data']
def error(name,value,code):
 assert any(e.get('extensions',{}).get('code')==code for e in value.get('errors',[])),(name,value)
 results.append({'check':name,'ok':True,'expectedCode':code});print('PASS',name,flush=True)
def cmd(*args,input=None):
 p=subprocess.run(['docker','compose',*args],cwd=root,input=input,text=True,capture_output=True,timeout=45)
 assert p.returncode==0,(args,p.stderr);return p.stdout.strip()
def sql(db,query):return cmd('exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',query)
def waitfor(f,timeout=45):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  try:
   value=f()
   if value:return value
  except (urllib.error.URLError,TimeoutError):pass
  time.sleep(1)
 raise AssertionError('Condition did not become true')
def login(n):
 a=gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken user{id}}}',{'i':{'email':'user'+str(n)+'@example.test','password':'Lab567-Travel!'}})
 assert not a.get('errors'),a
 return a['data']['authenticate']['accessToken']
def ready():
 for port in [8091,8092,8093,8094,8095,8096,8190,8180]:
  try:
   with urllib.request.urlopen('http://127.0.0.1:'+str(port)+'/actuator/health',timeout=2) as response:
    if json.load(response).get('status')!='UP':return False
  except Exception:return False
 return True
waitfor(ready,60)
c1,c2,staff,admin=[login(n) for n in [1,2,3,4]]
error('anonymous alias blocked',gql('{ alias:me{id} }'),'UNAUTHENTICATED')
error('anonymous fragment blocked',gql('query{...P} fragment P on Query{me{id}}'),'UNAUTHENTICATED')
did=ok('directions gRPC and Redis',gql('{directions{id}}',token=c1))['directions'][0]['id']
title='ЛР567 '+uuid.uuid4().hex[:8]
tour=ok('employee creates tour in PostgreSQL',gql('mutation($i:CreateTourInput!){createTour(input:$i){id version}}',{'i':{'directionId':did,'title':title,'description':'Интеграционная проверка','durationDays':3}},staff))['createTour'];tid=tour['id']
s=ok('employee creates schedule',gql('mutation($i:CreateScheduleInput!){createSchedule(input:$i){id priceVersion}}',{'i':{'tourId':tid,'startsAt':'2030-06-01T10:00:00Z','endsAt':'2030-06-04T10:00:00Z','capacity':100,'basePriceMinor':'120000'}},staff))['createSchedule'];sid=s['id']
v=ok('read updated tour version',gql('query($id:ID!){tour(id:$id){version}}',{'id':tid},staff))['tour']['version']
ok('publish tour',gql('mutation($i:UpdateTourInput!){updateTour(input:$i){id status}}',{'i':{'id':tid,'status':'PUBLISHED','expectedVersion':v}},staff))
error('client cannot create tour',gql('mutation($i:CreateTourInput!){createTour(input:$i){id}}',{'i':{'directionId':did,'title':'Forbidden','description':'','durationDays':1}},c1),'FORBIDDEN')
def book(key=None):
 return gql('mutation($i:CreateBookingInput!,$key:String!){createBooking(input:$i,idempotencyKey:$key){id status total{amountMinor} version}}',{'i':{'scheduleId':sid,'travelerCount':1,'expectedPriceVersion':'1'},'key':key or uuid.uuid4().hex},c1)
def cancel(b,key=None):return gql('mutation($i:CancelBookingInput!,$key:String!){cancelBooking(input:$i,idempotencyKey:$key){id status}}',{'i':{'bookingId':b,'reason':'Проверка отмены'},'key':key or uuid.uuid4().hex},c1)
def pay(b,token=c1):return gql('mutation($i:CreatePaymentInput!,$key:String!){createPayment(input:$i,idempotencyKey:$key){id status}}',{'i':{'bookingId':b},'key':uuid.uuid4().hex},token)
key=uuid.uuid4().hex
b=ok('create booking through Booking to Tour gRPC',book(key))['createBooking'];bid=b['id']
again=ok('idempotent booking retry',book(key))['createBooking'];assert again['id']==bid
assert sql('tour',"select reserved_seats from tour_schedules where id='"+sid+"'")=='1'
error('foreign booking read forbidden',gql('query($id:ID!){booking(id:$id){id}}',{'id':bid},c2),'FORBIDDEN')
error('foreign payment forbidden',pay(bid,c2),'FORBIDDEN')
# Durable reservation survives a service process restart.
cmd('restart','tour-service')
waitfor(lambda:subprocess.run(['curl','-fsS','http://127.0.0.1:8092/actuator/health'],capture_output=True).returncode==0)
ok('cancel reservation after Tour restart',cancel(bid))
assert sql('tour',"select reserved_seats from tour_schedules where id='"+sid+"'")=='0'
b2=ok('create booking for payment',book())['createBooking']['id']
p=ok('payment retrieves actual booking via gRPC',pay(b2))['createPayment'];pid=p['id']
error('cancel with admitted payment forbidden',cancel(b2),'CONFLICT')
error('client cannot settle payment',gql('mutation($id:ID!){confirmPayment(paymentId:$id){id}}',{'id':pid},c1),'FORBIDDEN')
settled=ok('employee confirms training payment',gql('mutation($id:ID!){confirmPayment(paymentId:$id){id status}}',{'id':pid},staff))['confirmPayment'];assert settled['status']=='SUCCEEDED'
waitfor(lambda:gql('query($id:ID!){booking(id:$id){status}}',{'id':b2},c1).get('data',{}).get('booking',{}).get('status')=='CONFIRMED')
ok('Kafka confirms booking',gql('query($id:ID!){booking(id:$id){id status payments{id status}}}',{'id':b2},c1))
waitfor(lambda:int(sql('notification',"select count(*) from notifications where booking_id='"+b2+"' and title='Бронирование CONFIRMED'"))==1)
ok('Kafka notification projection',gql('{notifications(page:{page:0,size:100}){totalCount items{id title body}}}',token=c1))
waitfor(lambda:sql('analytics',"select count(*) from sales_facts where payment_id='"+pid+"'")=='1')
ok('Kafka sales analytics projection',gql('{salesAnalytics(filter:{from:"2026-01-01T00:00:00Z",to:"2031-01-01T00:00:00Z"}){grossSales{amountMinor} netSales{amountMinor} occupancy}}',token=admin))
# Kafka failure does not lose an accepted business operation.
cmd('stop','kafka')
try:
 b3=ok('booking succeeds while Kafka is unavailable',book())['createBooking']['id']
 assert int(sql('booking',"select count(*) from integration_outbox where aggregate_id='"+b3+"' and published_at is null"))>=1
finally:cmd('start','kafka')
waitfor(lambda:int(sql('notification',"select count(*) from notifications where booking_id='"+b3+"'"))>=1)
results.append({'check':'outbox delivered after Kafka recovery','ok':True});print('PASS outbox delivered after Kafka recovery',flush=True)
# Replay exactly the same event; the notification count must stay constant.
row=json.loads(sql('booking',"select json_build_object('eventId',id,'eventType',event_type,'schemaVersion',schema_version,'occurredAt',occurred_at,'aggregateId',aggregate_id,'aggregateVersion',aggregate_version,'correlationId',correlation_id,'payload',payload::jsonb)::text from integration_outbox where aggregate_id='"+b3+"' order by sequence limit 1"))
count=sql('notification',"select count(*) from notifications where booking_id='"+b3+"'")
cmd('exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9092','--topic','booking.events.v1','--property','parse.key=true','--property','key.separator=\t',input=row['aggregateId']+'\t'+json.dumps(row)+'\n')
time.sleep(2);assert sql('notification',"select count(*) from notifications where booking_id='"+b3+"'")==count
results.append({'check':'duplicate Kafka event has one notification','ok':True});print('PASS duplicate Kafka event has one notification',flush=True)
# Current user state overrides stale JWT role/status claims.
u2='00000000-0000-0000-0000-000000000002'
ok('admin blocks user',gql('mutation($i:UpdateUserInput!){updateUser(input:$i){id status}}',{'i':{'userId':u2,'status':'BLOCKED'}},admin))
error('blocked user existing JWT rejected',gql('{me{id}}',token=c2),'FORBIDDEN')
ok('admin restores user',gql('mutation($i:UpdateUserInput!){updateUser(input:$i){id status}}',{'i':{'userId':u2,'status':'ACTIVE'}},admin))
# A valid JWT is rejected after its Redis session expires.
c3=login(2);claims=json.loads(base64.urlsafe_b64decode(c3.split('.')[1]+'=='));cmd('exec','-T','redis','redis-cli','EXPIRE','travel:session:'+claims['jti'],'1');time.sleep(1.2)
error('expired Redis session rejects still-valid JWT',gql('{me{id}}',token=c3),'UNAUTHENTICATED')
# Frequently used directions are actually cached with a TTL.
ok('directions cache first read',gql('{directions{id}}',token=c1));ok('directions cache second read',gql('{directions{id}}',token=c1))
ttl=int(cmd('exec','-T','redis','redis-cli','TTL','travel:cache:directions:true'));assert 0<ttl<=30
results.append({'check':'Redis directions TTL','ok':True,'ttl':ttl})
# Failed dependency is bounded and leaves unrelated services available.
cmd('stop','tour-service');started=time.monotonic()
try:
 error('Tour unavailable produces bounded service error',gql('{tours(page:{page:0,size:5}){totalCount}}',token=c1),'SERVICE_UNAVAILABLE')
 assert time.monotonic()-started<10
 ok('unrelated me query remains available',gql('{me{id}}',token=c1))
finally:cmd('start','tour-service')
waitfor(lambda:subprocess.run(['curl','-fsS','http://127.0.0.1:8092/actuator/health'],capture_output=True).returncode==0)
cmd('stop','redis')
try:error('Redis unavailable fails closed',gql('{me{id}}',token=c1),'SERVICE_UNAVAILABLE')
finally:cmd('start','redis')
waitfor(lambda:cmd('exec','-T','redis','redis-cli','ping')=='PONG')
ok('sessions survive Redis restart through AOF',waitfor(lambda:(result if not (result:=gql('{me{id}}',token=c1)).get('errors') else None)))
# Recover an interrupted cancellation from durable state.
sql('booking',"update bookings set status='CANCELLING',hold_expires_at=null where id='"+b3+"'")
waitfor(lambda:sql('booking',"select status from bookings where id='"+b3+"'")=='CANCELLED')
assert sql('tour',"select status from rpc_reservations where booking_id='"+b3+"'")=='RELEASED'
results.append({'check':'interrupted cancellation recovered','ok':True});print('PASS interrupted cancellation recovered',flush=True)
# Concurrent payment/cancellation must yield only one accepted transition.
for index in range(3):
 race=ok('race setup '+str(index),book())['createBooking']['id']
 with concurrent.futures.ThreadPoolExecutor(2) as pool:
  a=pool.submit(pay,race);b=pool.submit(cancel,race);responses=[a.result(),b.result()]
 assert sum(not response.get('errors') for response in responses)==1,responses
 status=sql('booking',"select status from bookings where id='"+race+"'")
 payment_count=sql('payment',"select count(*) from payments where booking_id='"+race+"'")
 assert (status=='CANCELLED' and payment_count=='0') or (status=='PENDING_PAYMENT' and payment_count=='1'),(status,payment_count)
results.append({'check':'payment/cancellation race serialized in Booking Service','ok':True});print('PASS payment/cancellation race serialized',flush=True)
# Resume a registration intent whose profile already committed before an Auth crash.
email='recover-'+uuid.uuid4().hex+'@example.test'
a=gql('mutation($i:RegisterInput!){register(input:$i){user{id}}}',{'i':{'email':email,'password':'Recovery567-password!','firstName':'Елена','lastName':'Ременчик','phone':'+375291234567'}});uid=a['data']['register']['user']['id']
sql('auth',"update credentials set profile_ready=false where user_id='"+uid+"'")
a=gql('mutation($i:AuthenticateInput!){authenticate(input:$i){user{id}}}',{'i':{'email':email,'password':'Recovery567-password!'}});assert a['data']['authenticate']['user']['id']==uid,a
results.append({'check':'interrupted registration resumes same profile identity','ok':True});print('PASS interrupted registration resumes',flush=True)
out=root/'docs/evidence/labs567';out.mkdir(parents=True,exist_ok=True);(out/'integration.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));(out/'scenario-ids.json').write_text(json.dumps({'tourId':tid,'scheduleId':sid,'paidBookingId':b2,'paymentId':pid},indent=2))
print('Verified',len(results),'integration checks',flush=True)
