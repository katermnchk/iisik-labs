"""Malformed event retries/rollback, domain routing and subscriber-specific DLT proof."""
import subprocess,json,uuid,pathlib,datetime,time
root=pathlib.Path(__file__).resolve().parents[1]
def docker(*args,**kwargs):return subprocess.run(['docker','compose',*args],cwd=root,text=True,errors='replace',capture_output=True,**kwargs)
def send(topic,event):
 p=docker('exec','-T','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9092','--topic',topic,'--property','parse.key=true','--property','key.separator=\t',input=event['aggregateId']+'\t'+json.dumps(event)+'\n',timeout=30);assert p.returncode==0,p.stderr

def dead(topic,eid):
 p=docker('exec','-T','kafka','/opt/kafka/bin/kafka-console-consumer.sh','--bootstrap-server','localhost:9092','--topic',topic,'--from-beginning','--timeout-ms','7000','--property','print.headers=true',timeout=30)
 assert eid in p.stdout,(topic,eid,'Event never reached expected DLT')
 # Exception stack traces contain newlines, so the delivery header precedes the
 # line containing the JSON value. Inspect the entire original record prefix.
 end=p.stdout.index(eid);start=p.stdout.rfind('kafka_deliveryAttempt:',0,end)
 assert start>=0,'No observed delivery-attempt header'
 header=p.stdout[start:end]
 assert 'kafka_dlt-original-topic' in header and 'kafka_dlt-original-consumer-group' in header,repr(header[-500:])
 raw=p.stdout[start+len('kafka_deliveryAttempt:'):start+len('kafka_deliveryAttempt:')+4]
 attempts=int.from_bytes(bytes(ord(c) for c in raw),'big')
 assert attempts==3,(topic,attempts)
 return {'topic':topic,'eventId':eid,'deliveryAttempts':attempts,'originalTopicHeader':True,'originalConsumerGroupHeader':True}


def envelope(kind,payload):return {'eventId':str(uuid.uuid4()),'eventType':kind,'schemaVersion':1,'occurredAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'aggregateId':str(uuid.uuid4()),'aggregateVersion':1,'correlationId':str(uuid.uuid4()),'payload':payload}
# Only Notification subscribes to booking.events.v1.
e= envelope('BookingStatusChanged',{'malformed':True});send('booking.events.v1',e)
result=dead('booking.events.v1.notification.dlt',e['eventId'])
q=docker('exec','-T','postgres','psql','-U','notification_app','-d','notification_db','-Atc',"select count(*) from integration_inbox where event_id='"+e['eventId']+"'")
assert q.stdout.strip()=='0',q.stdout
print('PASS invalid booking payload -> Notification DLT; inbox transaction rolled back',flush=True)
# A known event in the WRONG domain topic is rejected independently by every subscriber.
wrong=envelope('UserRegistered',{'id':str(uuid.uuid4())});send('payment.events.v1',wrong)
routes=[]
for consumer in ['booking','analytics','notification']:
 routes.append(dead('payment.events.v1.'+consumer+'.dlt',wrong['eventId']))
 print('PASS wrong-domain event -> '+consumer+' own DLT',flush=True)
out=root/'docs/evidence/labs567';out.mkdir(parents=True,exist_ok=True)
(out/'kafka-errors.json').write_text(json.dumps({'malformedPayload':result,'inboxRowsAfterRollback':0,'isolatedSubscriberDeadLetters':routes,'sharedDltUsed':False},indent=2))
