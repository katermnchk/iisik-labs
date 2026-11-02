"""Capture one actual payment trace and its committed data, without credentials."""
import pathlib,json,subprocess,time,xml.etree.ElementTree as ET,shutil
root=pathlib.Path(__file__).resolve().parents[1];out=root/'docs/evidence/labs567'
def cmd(*args):
 p=subprocess.run(['docker','compose',*args],cwd=root,text=True,capture_output=True,timeout=40);assert p.returncode==0,p.stderr;return p.stdout.strip()
def sql(db,q):return cmd('exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',q)
event=json.loads(sql('payment',"select json_build_object('eventId',id,'topic',topic,'eventType',event_type,'aggregateId',aggregate_id,'aggregateVersion',aggregate_version,'correlationId',correlation_id,'occurredAt',occurred_at,'publishedAt',published_at,'payload',payload::jsonb)::text from integration_outbox where event_type='PaymentSucceeded' and published_at is not null order by sequence desc limit 1"))
bid=event['payload']['booking']['id'];pid=event['aggregateId'];cid=event['correlationId']
logs=cmd('logs','--since','30m','--no-color','graphql-api','payment-service','booking-service','analytics-service','notification-service','tour-service')
trace=[line for line in logs.splitlines() if cid in line]
for required in ['graphql field=confirmPayment','grpc client','grpc server','kafka published topic=payment.events.v1','kafka consumed service=booking','kafka consumed service=analytics','kafka consumed service=notification']:
 assert any(required in line for line in trace),(required,cid)
(out/'runtime-excerpt.log').write_text('\n'.join(trace)+'\n')
state={
 'correlationId':cid,'paymentOutbox':event,
 'booking':json.loads(sql('booking',f"select row_to_json(b)::text from (select id,status,version,hold_expires_at from bookings where id='{bid}') b")),
 'history':json.loads(sql('booking',f"select coalesce(json_agg(h),'[]')::text from (select from_status,to_status,reason,changed_at from booking_status_history where booking_id='{bid}' order by changed_at)h")),
 'inbox':{db:int(sql(db,"select count(*) from integration_inbox where event_id='"+event['eventId']+"'")) for db in ['booking','analytics','notification']},
 'salesFact':json.loads(sql('analytics',f"select row_to_json(s)::text from (select payment_id,booking_id,source_event_id,amount_minor,occurred_at from sales_facts where payment_id='{pid}')s")),
 'notifications':json.loads(sql('notification',f"select coalesce(json_agg(n),'[]')::text from (select n.id,n.kind,n.title,n.status,d.channel from notifications n join notification_deliveries d on n.id=d.notification_id where n.booking_id='{bid}')n"))}
(out/'scenario-trace.json').write_text(json.dumps(state,ensure_ascii=False,indent=2))
suites=[ET.parse(p).getroot() for p in root.glob('*/target/surefire-reports/TEST-*.xml')]
collection=json.loads((out/'collection.json').read_text())
summary={'date':'2026-11-02','maven':{key:sum(int(s.get(key,'0')) for s in suites) for key in ['tests','failures','errors','skipped']},'integrationChecks':len(json.loads((out/'integration.json').read_text())),'securityChecks':len(json.loads((out/'security.json').read_text())),'postman':{'collectionSteps':len(json.loads((root/'examples/labs567.postman_collection.json').read_text())['item']),'runner':collection['runner'],'version':collection['version'],'httpRequests':collection['requests']['total'],'assertions':collection['assertions']['total'],'failures':len(collection['failures'])},'kafka':{'domainTopics':4,'partitionsPerTopic':3,'subscriberDlts':6},'additionalEvidence':['kafka-topology.json','kafka-errors.json','hold-expiry.json','paid-expiry-fence.json','analytics-cache.json','roles.json','recovery.json','scenario-trace.json']}
(out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
shutil.copy2('/Users/katar/outputs/iisik-review/verify.log',out/'maven-verify.log')
print('Captured real trace',cid,'lines',len(trace),'Maven tests',summary['maven']['tests'])
