"""A settled payment must fence expiry even while its Kafka event is unavailable."""
import json,pathlib,urllib.request,subprocess,time,uuid,datetime
r=pathlib.Path(__file__).resolve().parents[1]
def cmd(*args):
 p=subprocess.run(['docker','compose',*args],cwd=r,text=True,capture_output=True,timeout=35);assert p.returncode==0,p.stderr;return p.stdout.strip()
def sql(db,q):return cmd('exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',q)
def gql(q,v=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 with urllib.request.urlopen(urllib.request.Request('http://localhost:8180/graphql',json.dumps({'query':q,'variables':v or {}}).encode(),h),timeout=20) as f:a=json.load(f)
 assert not a.get('errors'),a;return a['data']
def login(n):return gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',{'i':{'email':'user'+str(n)+'@example.test','password':'Lab567-Travel!'}})['authenticate']['accessToken']
c,s=login(1),login(3)
sid=json.loads((r/'docs/evidence/labs567/scenario-ids.json').read_text())['scheduleId']
b=gql('mutation($i:CreateBookingInput!,$k:String!){createBooking(input:$i,idempotencyKey:$k){id}}',{'i':{'scheduleId':sid,'travelerCount':1,'expectedPriceVersion':'1'},'k':uuid.uuid4().hex},c)['createBooking']['id']
p=gql('mutation($i:CreatePaymentInput!,$k:String!){createPayment(input:$i,idempotencyKey:$k){id}}',{'i':{'bookingId':b},'k':uuid.uuid4().hex},c)['createPayment']['id']
cmd('stop','kafka')
try:
 gql('mutation($id:ID!){confirmPayment(paymentId:$id){id status}}',{'id':p},s)
 sql('booking',f"update bookings set hold_expires_at=now()-interval '1 second' where id='{b}'")
 # Recovery runs every 10 seconds. This covers at least one full expiry sweep.
 time.sleep(13)
 assert sql('booking',f"select status from bookings where id='{b}'")=='PENDING_PAYMENT'
 assert sql('payment',f"select status from payments where id='{p}'")=='SUCCEEDED'
 assert sql('tour',f"select status from rpc_reservations where booking_id='{b}'")=='HELD'
finally:cmd('start','kafka')
end=time.monotonic()+45
while sql('booking',f"select status from bookings where id='{b}'")!='CONFIRMED':
 assert time.monotonic()<end,'Kafka did not confirm settled booking';time.sleep(.5)
assert sql('booking',f"select count(*) from booking_status_history where booking_id='{b}' and to_status in ('CANCELLING','CANCELLED')")=='0'
(r/'docs/evidence/labs567/paid-expiry-fence.json').write_text(json.dumps({'check':'settled payment fences hold expiry while Kafka is down','ok':True,'bookingId':b,'paymentId':p,'observedStateDuringOutage':'PENDING_PAYMENT + SUCCEEDED + HELD','finalBookingState':'CONFIRMED','cancellationHistoryRows':0,'expirySweepsCovered':1},indent=2))
print('PASS settled payment prevents expiry during Kafka outage; event later confirms booking')
