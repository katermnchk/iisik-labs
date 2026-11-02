"""Verify all Lab4 business operations on the integrated, current runtime.
Creates isolated test objects. One completion test explicitly advances a booking's
start snapshot in SQL; this is a time fixture, not a claim that time elapsed.
"""
import json, os, pathlib, subprocess, time, urllib.request, urllib.error, uuid
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'docs/evidence/lab4-current';out.mkdir(parents=True,exist_ok=True)
url=os.getenv('GRAPHQL_URL','http://127.0.0.1:8180/graphql');results=[];tokens={}
def raw(q,v=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 req=urllib.request.Request(url,json.dumps({'query':q,'variables':v or {}}).encode(),h)
 with urllib.request.urlopen(req,timeout=25) as r:return r.status,json.load(r)
def check(name,q,v=None,role='client',code=None):
 status,b=raw(q,v,tokens.get(role));assert status==200,(name,status,b)
 if code:assert any(e.get('extensions',{}).get('code')==code for e in b.get('errors',[])),(name,b)
 else:assert not b.get('errors'),(name,b)
 record={'name':name,'ok':True,'request':{'query':q,'variables':v or {},'role':role},'response':b}
 results.append(record);print('PASS',name,flush=True);return b.get('data')
def assertcheck(name,value):
 assert value,name
 results.append({'name':name,'ok':True});print('PASS',name,flush=True)
def cmd(*a):
 p=subprocess.run(['docker','compose',*a],cwd=root,text=True,capture_output=True,timeout=60);assert p.returncode==0,(a,p.stderr);return p.stdout.strip()
def sql(db,q):return cmd('exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',q)
def wait(f,seconds=60):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  try:
   v=f()
   if v:return v
  except (OSError,KeyError,TypeError):pass
  time.sleep(.5)
 raise AssertionError('Readiness/projection timeout')
def ready():
 for port in [8180,8190,8091,8092,8093,8094,8095,8096]:
  try:
   if json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=2)).get('status')!='UP':return False
  except OSError:return False
 return True
wait(ready,90)
for role,n in [('client',1),('other',2),('staff',3),('admin',4)]:
 _,b=raw('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',{'i':{'email':f'user{n}@example.test','password':os.getenv('LAB_PASSWORD','Lab567-Travel!')}});assert not b.get('errors'),b;tokens[role]=b['data']['authenticate']['accessToken']
check('nullable directions default','{directions(activeOnly:null){id}}')
did=check('read directions','{directions{id}}')['directions'][0]['id']
create='mutation($i:CreateTourInput!){createTour(input:$i){id status version}}'
params={'directionId':did,'title':'ЛР4 '+uuid.uuid4().hex[:8],'description':'Проверка бизнес-операций','durationDays':3}
check('client cannot create tour',create,{'i':params},code='FORBIDDEN')
t=check('create DRAFT tour',create,{'i':params},'staff')['createTour'];tid=t['id'];assert t['status']=='DRAFT'
read='query($id:ID!){tour(id:$id){id status version title schedules{id version priceVersion availableSeats price{amountMinor}}}}'
check('client cannot see draft',read,{'id':tid},code='NOT_FOUND')
update='mutation($i:UpdateTourInput!){updateTour(input:$i){id status version}}'
check('cannot publish without future schedule',update,{'i':{'id':tid,'status':'PUBLISHED','expectedVersion':t['version']}},'staff','INVALID_STATE')
cs='mutation($i:CreateScheduleInput!){createSchedule(input:$i){id version priceVersion}}'
si={'tourId':tid,'startsAt':'2030-06-01T10:00:00Z','endsAt':'2030-06-04T10:00:00Z','capacity':6,'basePriceMinor':'10000'}
check('negative schedule price rejected',cs,{'i':dict(si,basePriceMinor='-1')},'staff','BAD_REQUEST')
s=check('create schedule',cs,{'i':si},'staff')['createSchedule'];sid=s['id']
us='mutation($i:UpdateScheduleInput!){updateSchedule(input:$i){id version priceVersion price{amountMinor}}}'
s=check('update schedule price and version',us,{'i':{'id':sid,'expectedVersion':s['version'],'basePriceMinor':'12000'}},'staff')['updateSchedule']
assertcheck('priceVersion incremented',s['priceVersion']=='2')
check('stale schedule version rejected',us,{'i':{'id':sid,'expectedVersion':'1','capacity':8}},'staff','CONFLICT')
t=check('read tour after schedule update',read,{'id':tid},'staff')['tour']
check('stale tour version rejected',update,{'i':{'id':tid,'expectedVersion':'1','title':'stale'}},'staff','CONFLICT')
check('publish tour',update,{'i':{'id':tid,'expectedVersion':t['version'],'status':'PUBLISHED'}},'staff')
cb='mutation($i:CreateBookingInput!,$key:String!){createBooking(input:$i,idempotencyKey:$key){id status version total{amountMinor} unitPrice{amountMinor}}}'
def booking(key,count=2,pv='2',name='create booking',code=None):return check(name,cb,{'i':{'scheduleId':sid,'travelerCount':count,'expectedPriceVersion':pv},'key':key},code=code)
key=str(uuid.uuid4());booking(str(uuid.uuid4()),pv='1',name='stale price rejected',code='PRICE_CHANGED')
a=booking(key)['createBooking'];aid=a['id'];assertcheck('money snapshot exact',a['total']['amountMinor']=='24000' and a['unitPrice']['amountMinor']=='12000')
assertcheck('booking persisted',sql('booking',f"select status from bookings where id='{aid}'")=='PENDING_PAYMENT')
assertcheck('tour seats reserved in own DB',sql('tour',f"select reserved_seats from tour_schedules where id='{sid}'")=='2')
assertcheck('same key returns same booking',booking(key,name='repeat same booking')['createBooking']['id']==aid)
booking(key,count=1,name='key different payload rejected',code='CONFLICT')
booking(str(uuid.uuid4()),count=5,name='insufficient seats rejected',code='INSUFFICIENT_SEATS')
check('capacity cannot drop below reserved',us,{'i':{'id':sid,'expectedVersion':check('read reservation version',read,{'id':tid},'staff')['tour']['schedules'][0]['version'],'capacity':1}},'staff','CONFLICT')
my='{myBookings(page:{page:0,size:100},filter:{status:PENDING_PAYMENT},sort:{field:TOTAL,direction:DESC}){totalCount items{id status total{amountMinor}}} bookingSummary(filter:{status:PENDING_PAYMENT}){count pending confirmed cancelled total{amountMinor}}}'
view=check('filter sort and aggregate',my);items=view['myBookings']['items'];summary=view['bookingSummary']
assertcheck('summary same filter before pagination',summary['count']==view['myBookings']['totalCount'] and int(summary['total']['amountMinor'])==sum(int(x['total']['amountMinor']) for x in items))
assertcheck('booking totals sorted DESC', [int(x['total']['amountMinor']) for x in items]==sorted([int(x['total']['amountMinor']) for x in items],reverse=True))
cancel='mutation($i:CancelBookingInput!,$key:String!){cancelBooking(input:$i,idempotencyKey:$key){id status version}}'
cp={'i':{'bookingId':aid,'reason':'Отмена проверки'},'key':str(uuid.uuid4())}
check('foreign cancellation forbidden',cancel,cp,'other','FORBIDDEN')
assertcheck('cancel booking',check('cancel pending booking',cancel,cp)['cancelBooking']['status']=='CANCELLED')
assertcheck('cancel replay stable',check('repeat cancellation',cancel,cp)['cancelBooking']['status']=='CANCELLED')
assertcheck('seats released exactly once',sql('tour',f"select reserved_seats from tour_schedules where id='{sid}'")=='0')
b=booking(str(uuid.uuid4()),name='booking for payment')['createBooking'];bid=b['id']
pay='mutation($i:CreatePaymentInput!,$key:String!){createPayment(input:$i,idempotencyKey:$key){id status amount{amountMinor} paymentUrl}}'
pp={'i':{'bookingId':bid},'key':str(uuid.uuid4())}
check('foreign payment forbidden',pay,pp,'other','FORBIDDEN')
p=check('create pending payment',pay,pp)['createPayment'];pid=p['id'];assertcheck('pending is not settlement',p['status']=='PENDING' and '.invalid/' in p['paymentUrl'])
assertcheck('payment retry same id',check('repeat payment',pay,pp)['createPayment']['id']==pid)
check('second active payment rejected',pay,dict(pp,key=str(uuid.uuid4())),code='CONFLICT')
check('cancel with active payment rejected',cancel,{'i':{'bookingId':bid,'reason':'Cannot cancel'},'key':str(uuid.uuid4())},code='CONFLICT')
check('training settlement', 'mutation($id:ID!){confirmPayment(paymentId:$id){id status}}',{'id':pid},'staff')
br='query($id:ID!){booking(id:$id){id status version}}'
wait(lambda:raw(br,{'id':bid},tokens['client'])[1].get('data',{}).get('booking',{}).get('status')=='CONFIRMED')
b=check('payment event confirms booking',br,{'id':bid})['booking']
change='mutation($i:ChangeBookingStatusInput!){changeBookingStatus(input:$i){id status version}}'
ci={'bookingId':bid,'targetStatus':'COMPLETED','reason':'Завершение поездки','expectedVersion':b['version']}
check('future trip cannot be completed',change,{'i':ci},'staff','INVALID_STATE')
# Explicit temporal fixture for a new, isolated paid booking.
sql('booking',f"update bookings set starts_at_snapshot=now()-interval '2 days' where id='{bid}'")
results.append({'name':'time fixture for started trip','ok':True,'bookingId':bid,'fixture':'starts_at_snapshot=now()-2 days; isolated booking only'})
assertcheck('complete started confirmed trip',check('changeBookingStatus COMPLETED',change,{'i':ci},'staff')['changeBookingStatus']['status']=='COMPLETED')
check('stale booking version rejected',change,{'i':ci},'staff','CONFLICT')
email='lab4-'+uuid.uuid4().hex+'@example.test'
_,r=raw('mutation($i:RegisterInput!){register(input:$i){user{id}}}',{'i':{'email':email,'password':'Lab4-Review-Password!','firstName':'Проверка','lastName':'ЛР4','phone':'+375290000009'}});assert not r.get('errors'),r;uid=r['data']['register']['user']['id']
uq='mutation($i:UpdateUserInput!){updateUser(input:$i){id role{code} employeeProfile{userId position}}}'
check('employee role needs position',uq,{'i':{'userId':uid,'role':'EMPLOYEE'}},'admin','BAD_REQUEST')
u=check('updateUser creates employee profile',uq,{'i':{'userId':uid,'role':'EMPLOYEE','position':'Менеджер проверки'}},'admin')['updateUser']
assertcheck('employee profile shares identity',u['employeeProfile']['userId']==uid and u['role']['code']=='EMPLOYEE')
check('client cannot update users',uq,{'i':{'userId':uid,'firstName':'Forbidden'}},code='FORBIDDEN')
check('last active admin protected',uq,{'i':{'userId':'00000000-0000-0000-0000-000000000004','status':'BLOCKED'}},'admin','CONFLICT')
# Interrupted cancellation on real RPC and persistent PostgreSQL receipts.
x=booking(str(uuid.uuid4()),count=1,name='cancellation recovery booking A')['createBooking']['id'];y=booking(str(uuid.uuid4()),count=1,name='cancellation recovery booking B')['createBooking']['id'];ck=str(uuid.uuid4())
try:
 cmd('stop','tour-service')
 check('cancel A while Tour offline',cancel,{'i':{'bookingId':x,'reason':'Original'},'key':ck},code='SERVICE_UNAVAILABLE')
 check('same key cannot cancel B after failure',cancel,{'i':{'bookingId':y,'reason':'Original'},'key':ck},code='CONFLICT')
 check('same key cannot change reason after failure',cancel,{'i':{'bookingId':x,'reason':'Changed'},'key':ck},code='CONFLICT')
finally:cmd('start','tour-service')
wait(lambda:json.load(urllib.request.urlopen('http://127.0.0.1:8092/actuator/health',timeout=2))['status']=='UP')
wait(lambda:not raw(cancel,{'i':{'bookingId':x,'reason':'Original'},'key':ck},tokens['client'])[1].get('errors'),30)
assertcheck('original cancellation resumes',check('retry original interrupted cancellation',cancel,{'i':{'bookingId':x,'reason':'Original'},'key':ck})['cancelBooking']['status']=='CANCELLED')
assertcheck('B untouched by reused key',sql('booking',f"select status from bookings where id='{y}'")=='PENDING_PAYMENT')
check('cancel recovery fixture B',cancel,{'i':{'bookingId':y,'reason':'Cleanup'},'key':str(uuid.uuid4())})
final={'date':time.strftime('%Y-%m-%d'),'count':len(results),'allPassed':True,'ids':{'tourId':tid,'scheduleId':sid,'cancelledBookingId':aid,'completedBookingId':bid,'paymentId':pid,'employeeId':uid},'results':results}
(out/'business.json').write_text(json.dumps(final,ensure_ascii=False,indent=2));print(f'{len(results)} current Lab4 checks passed')
