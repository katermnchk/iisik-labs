import pathlib,json,subprocess,uuid,time,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
def sql(db,q):
 p=subprocess.run(['docker','compose','exec','-T','postgres','psql','-v','ON_ERROR_STOP=1','-U',db+'_app','-d',db+'_db','-Atc',q],cwd=root,text=True,capture_output=True);assert p.returncode==0,p.stderr;return p.stdout.strip()
ids=json.loads((root/'docs/evidence/labs567/scenario-ids.json').read_text());sid=ids['scheduleId'];bid=str(uuid.uuid4())
payload=json.loads(sql('tour',"select payload from rpc_reservations where status='HELD' and payload::jsonb->'schedule'->>'id'='"+sid+"' limit 1"));payload['bookingId']=bid
before=sql('tour',"select reserved_seats from tour_schedules where id='"+sid+"'")
sql('tour',"begin;update tour_schedules set reserved_seats=reserved_seats+1 where id='"+sid+"';insert into rpc_reservations(booking_id,payload,status,created_at) values ('"+bid+"','"+json.dumps(payload,ensure_ascii=False).replace("'","''")+"','HELD',now()-interval '3 minutes');commit;")
end=time.monotonic()+30
while time.monotonic()<end:
 if sql('tour',"select status from rpc_reservations where booking_id='"+bid+"'")=='RELEASED':break
 time.sleep(1)
else:raise AssertionError('Orphan reservation was not reconciled')
assert sql('tour',"select reserved_seats from tour_schedules where id='"+sid+"'")==before
(root/'docs/evidence/labs567/recovery.json').write_text(json.dumps({'check':'orphan reservation after Booking crash is compensated','ok':True,'reservationId':bid,'baselineSeats':int(before),'injectedSeats':int(before)+1,'recoveredSeats':int(before),'reservationStatus':'RELEASED'},indent=2));print('PASS orphan reservation reconciled without losing seats')
