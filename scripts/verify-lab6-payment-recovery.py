#!/usr/bin/env python3
"""Payment projection must survive a temporary Tour RPC outage; local stack only."""
import datetime,json,os,pathlib,subprocess,sys,time,urllib.request,uuid
ROOT=pathlib.Path(__file__).resolve().parents[1]
BEFORE='--record-before' in sys.argv
results=[]
def cmd(*args,input=None):
 p=subprocess.run(['docker','compose',*args],cwd=ROOT,input=input,text=True,capture_output=True,timeout=45)
 assert p.returncode==0,p.stderr
 return p.stdout.strip()
def sql(db,q):return cmd('exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',q)
def gql(q,v=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 r=urllib.request.Request('http://127.0.0.1:8180/graphql',json.dumps({'query':q,'variables':v or {}}).encode(),h)
 with urllib.request.urlopen(r,timeout=20) as response:a=json.load(response)
 assert not a.get('errors'),a
 return a['data']
def check(n,condition,**data):
 assert condition,(n,data)
 results.append(dict(check=n,ok=True,**data));print('PASS',n,flush=True)
def wait(f,timeout=60):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  try:
   a=f()
   if a:return a
  except (OSError,ValueError,AssertionError):pass
  time.sleep(.5)
 raise AssertionError('Condition timeout')
def ready(port):
 with urllib.request.urlopen('http://127.0.0.1:'+str(port)+'/actuator/health',timeout=2) as r:return json.load(r)['status']=='UP'
wait(lambda:all(ready(p) for p in [8180,8190,8091,8092,8093,8094,8095,8096]))
def login(n):return gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',{'i':{'email':'user'+str(n)+'@example.test','password':os.environ.get('LAB_PASSWORD','Lab567-Travel!')}})['authenticate']['accessToken']
c,staff=login(1),login(3)
did=gql('{directions{id}}',t=c)['directions'][0]['id']
tour=gql('mutation($i:CreateTourInput!){createTour(input:$i){id version}}',{'i':{'directionId':did,'title':'ЛР6 восстановление '+uuid.uuid4().hex[:8],'description':'Временный отказ Tour при обработке PaymentSucceeded','durationDays':3}},staff)['createTour'];tid=tour['id']
s=gql('mutation($i:CreateScheduleInput!){createSchedule(input:$i){id}}',{'i':{'tourId':tid,'startsAt':'2030-06-01T10:00:00Z','endsAt':'2030-06-04T10:00:00Z','capacity':10,'basePriceMinor':'120000'}},staff)['createSchedule'];sid=s['id']
v=gql('query($id:ID!){tour(id:$id){version}}',{'id':tid},staff)['tour']['version']
gql('mutation($i:UpdateTourInput!){updateTour(input:$i){id}}',{'i':{'id':tid,'status':'PUBLISHED','expectedVersion':v}},staff)
wait(lambda:sql('analytics',"select count(*) from schedule_load_facts where schedule_id='"+sid+"' and included")=='1')
check('catalog already projected before outage',True)
b=gql('mutation($i:CreateBookingInput!,$k:String!){createBooking(input:$i,idempotencyKey:$k){id}}',{'i':{'scheduleId':sid,'travelerCount':2,'expectedPriceVersion':'1'},'k':uuid.uuid4().hex},c)['createBooking'];bid=b['id']
p=gql('mutation($i:CreatePaymentInput!,$k:String!){createPayment(input:$i,idempotencyKey:$k){id}}',{'i':{'bookingId':bid},'k':uuid.uuid4().hex},c)['createPayment'];pid=p['id']
cmd('stop','tour-service')
try:
 a=gql('mutation($id:ID!){confirmPayment(paymentId:$id){id status}}',{'id':pid},staff)['confirmPayment']
 check('payment commits while Tour unavailable',a['status']=='SUCCEEDED')
 wait(lambda:sql('booking',"select status from bookings where id='"+bid+"'")=='CONFIRMED')
 check('independent Booking consumer confirms',True)
 event=json.loads(wait(lambda:sql('payment',"select json_build_object('eventId',id,'eventType',event_type,'schemaVersion',schema_version,'occurredAt',occurred_at,'aggregateId',aggregate_id,'aggregateVersion',aggregate_version,'correlationId',correlation_id,'payload',payload::jsonb)::text from integration_outbox where aggregate_id='"+pid+"' and published_at is not null")))
 time.sleep(12)
 check('failed analytics application rolls back inbox and sale',sql('analytics',"select count(*) from sales_facts where payment_id='"+pid+"'")=='0' and sql('analytics',"select count(*) from integration_inbox where event_id='"+event['eventId']+"'")=='0')
finally:
 cmd('start','tour-service')
 wait(lambda:ready(8092))
if BEFORE:
 time.sleep(4)
 absent=sql('analytics',"select count(*) from sales_facts where payment_id='"+pid+"'")=='0'
 check('BEFORE valid event remains unprojected after Tour recovery',absent)
 dead=subprocess.run(['docker','compose','exec','-T','kafka','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:9092','--topic','payment.events.v1.analytics.dlt','--from-beginning','--timeout-ms','6000'],cwd=ROOT,text=True,capture_output=True,timeout=25)
 check('BEFORE valid event diverted into analytics DLT',event['eventId'] in dead.stdout)
else:
 wait(lambda:sql('analytics',"select count(*) from sales_facts where payment_id='"+pid+"'")=='1')
 check('analytics catches up automatically after Tour recovery',True)
 check('one event applied once by all subscribers',all(sql(db,"select count(*) from integration_inbox where event_id='"+event['eventId']+"'")=='1' for db in ['booking','analytics','notification']))
 fact=json.loads(sql('analytics',"select row_to_json(s)::text from (select payment_id,booking_id,source_event_id,amount_minor from sales_facts where payment_id='"+pid+"')s"))
 check('sale identity and integer money preserved',fact['source_event_id']==event['eventId'] and fact['amount_minor']==240000)
 check('occupancy seats counted once',sql('analytics',"select confirmed_seats from schedule_load_facts where schedule_id='"+sid+"'")=='2')
for t in [c,staff]:gql('mutation{logout}',t=t)
out=ROOT/'docs/evidence/lab6-current';out.mkdir(parents=True,exist_ok=True)
(out/('recovery-before.json' if BEFORE else 'payment-recovery.json')).write_text(json.dumps(dict(allPassed=True,checks=results,event=event,bookingId=bid,tourId=tid,scheduleId=sid,observedBeforeFix=BEFORE),ensure_ascii=False,indent=2))
print('Verified',len(results),'payment recovery checks',flush=True)
