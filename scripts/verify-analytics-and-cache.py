#!/usr/bin/env python3
"""Real event-time, empty-capacity, stale-snapshot and cache-RPC evidence."""
import urllib.request,json,uuid,pathlib,subprocess,time,datetime
root=pathlib.Path(__file__).resolve().parents[1]
results=[]
def gql(q,v=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 req=urllib.request.Request('http://127.0.0.1:8180/graphql',json.dumps({'query':q,'variables':v or {}}).encode(),h)
 with urllib.request.urlopen(req,timeout=20) as r:a=json.load(r)
 assert not a.get('errors'),a
 return a['data']
def cmd(*args,input=None):
 p=subprocess.run(['docker','compose',*args],cwd=root,input=input,text=True,capture_output=True,timeout=40);assert p.returncode==0,p.stderr;return p.stdout.strip()
def sql(db,q):return cmd('exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',q)
def waitfor(f):
 end=time.monotonic()+45
 while time.monotonic()<end:
  if f():return
  time.sleep(.5)
 raise AssertionError('Projection did not converge')
def login(n):return gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',{'i':{'email':'user'+str(n)+'@example.test','password':'Lab567-Travel!'}})['authenticate']['accessToken']
def send(topic,event):return cmd('exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9092','--topic',topic,'--property','parse.key=true','--property','key.separator=\t',input=event['aggregateId']+'\t'+json.dumps(event)+'\n')
client,staff,admin=login(1),login(3),login(4)
ids=json.loads((root/'docs/evidence/labs567/scenario-ids.json').read_text());tour=ids['tourId'];sid=ids['scheduleId']
second=gql('mutation($i:CreateScheduleInput!){createSchedule(input:$i){id version}}',{'i':{'tourId':tour,'startsAt':'2030-06-02T10:00:00Z','endsAt':'2030-06-05T10:00:00Z','capacity':10,'basePriceMinor':'10000'}},staff)['createSchedule']
sid2=second['id'];waitfor(lambda:sql('analytics',f"select capacity::text||':'||confirmed_seats::text from schedule_load_facts where schedule_id='{sid2}'")=='10:0')
results.append({'check':'new empty schedule is included in occupancy denominator','ok':True,'scheduleId':sid2,'capacity':10,'confirmedSeats':0});print('PASS empty schedule included',flush=True)
# Capture the old legitimate snapshot before changing capacity.
old=json.loads(sql('tour',"select json_build_object('eventId',id,'eventType',event_type,'schemaVersion',schema_version,'occurredAt',occurred_at,'aggregateId',aggregate_id,'aggregateVersion',aggregate_version,'correlationId',correlation_id,'payload',payload::jsonb)::text from integration_outbox where aggregate_id='"+tour+"' order by sequence desc limit 1"))
gql('mutation($i:UpdateScheduleInput!){updateSchedule(input:$i){id}}',{'i':{'id':sid2,'capacity':20,'expectedVersion':second['version']}},staff)
waitfor(lambda:sql('analytics',f"select capacity from schedule_load_facts where schedule_id='{sid2}'")=='20')
old['eventId']=str(uuid.uuid4());send('tour.events.v1',old)
waitfor(lambda:sql('analytics',"select count(*) from integration_inbox where event_id='"+old['eventId']+"'")=='1')
assert sql('analytics',f"select capacity from schedule_load_facts where schedule_id='{sid2}'")=='20'
results.append({'check':'capacity updates without a sale; late older snapshot cannot roll back projection','ok':True,'projectedCapacity':20,'staleAggregateVersion':old['aggregateVersion']});print('PASS updated capacity and stale snapshot guard',flush=True)
# A controlled backlog fixture sets business occurrence time to the previous UTC day.
# Only the event timestamp is changed, explicitly modelling delayed delivery; no clock wait.
b=gql('mutation($i:CreateBookingInput!,$k:String!){createBooking(input:$i,idempotencyKey:$k){id}}',{'i':{'scheduleId':sid,'travelerCount':1,'expectedPriceVersion':'1'},'k':uuid.uuid4().hex},client)['createBooking']['id']
p=gql('mutation($i:CreatePaymentInput!,$k:String!){createPayment(input:$i,idempotencyKey:$k){id}}',{'i':{'bookingId':b},'k':uuid.uuid4().hex},client)['createPayment']['id']
now=datetime.datetime.now(datetime.timezone.utc);occurred=(now.replace(hour=0,minute=0,second=0,microsecond=0)-datetime.timedelta(microseconds=123456)).isoformat()
cmd('stop','kafka')
try:
 gql('mutation($id:ID!){confirmPayment(paymentId:$id){id status}}',{'id':p},staff)
 sql('payment',f"update integration_outbox set occurred_at='{occurred}' where aggregate_id='{p}' and published_at is null")
finally:cmd('start','kafka')
waitfor(lambda:sql('analytics',f"select count(*) from sales_facts where payment_id='{p}'")=='1')
actual=sql('analytics',f"select to_char(occurred_at at time zone 'UTC','YYYY-MM-DD') from sales_facts where payment_id='{p}'")
assert actual==occurred[:10],(occurred,actual)
asof=gql('{salesAnalytics(filter:{from:"2026-01-01T00:00:00Z",to:"2031-01-01T00:00:00Z"}){asOf}}',t=admin)['salesAnalytics']['asOf']
assert asof[:10]==now.date().isoformat(),asof
results.append({'check':'delayed payment belongs to event UTC day, not consumer day','ok':True,'fixture':'controlled outbox occurred_at before UTC midnight','eventOccurredAt':occurred,'saleDate':actual,'projectionAsOf':asof,'paymentId':p});print('PASS event-time accounting',flush=True)
# Cache evidence observes the actual Tour RPC count, not only identical responses.
start=datetime.datetime.now(datetime.timezone.utc).isoformat()
cmd('exec','-T','redis','redis-cli','DEL','travel:cache:directions:true')
gql('{directions{id}}',t=client);gql('{directions{id}}',t=client)
cmd('exec','-T','redis','redis-cli','EXPIRE','travel:cache:directions:true','1');time.sleep(1.2);gql('{directions{id}}',t=client)
logs=cmd('logs','--since',start,'--no-color','graphql-api','tour-service')
server=[l for l in logs.splitlines() if 'grpc server' in l and '/ListDirections' in l and 'success' in l]
assert len(server)==2,(len(server),logs)
assert 'cache miss' in logs and 'cache hit' in logs,logs
results.append({'check':'cache miss-hit-expiry-refresh avoids redundant Tour RPC','ok':True,'externalQueries':3,'tourRpcCalls':len(server),'ttlSeconds':int(cmd('exec','-T','redis','redis-cli','TTL','travel:cache:directions:true'))})
(root/'docs/evidence/labs567/cache-trace.log').write_text('\n'.join(l for l in logs.splitlines() if 'cache ' in l or '/ListDirections' in l)+'\n')
(root/'docs/evidence/labs567/analytics-cache.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('PASS cache 3 queries -> 2 RPC calls with expiration',flush=True)
