# Historical Lab4 mock runtime; use verify-lab4-current.py for the current system.
"""Repeatable GraphQL checks. Run against an already started gateway; only stdlib required."""
import argparse,json,urllib.request,uuid
from pathlib import Path


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--url',default='http://127.0.0.1:8080/graphql')
    p.add_argument('--output',default='docs/evidence/lab4-repeat')
    a=p.parse_args();out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
    prefix=str(uuid.uuid4());results=[]
    def query(name,q,token='demo-client',expected_error=None):
        req=urllib.request.Request(a.url,data=json.dumps({'query':q}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
        with urllib.request.urlopen(req,timeout=20) as r:body=json.load(r)
        (out/(name+'.json')).write_text(json.dumps({'query':q,'response':body},ensure_ascii=False,indent=2),encoding='utf8')
        if expected_error:
            assert body['errors'][0]['extensions']['code']==expected_error,body
        else:assert 'errors' not in body,body
        results.append({'name':name,'result':'PASS'});print('PASS',name,flush=True)
        return body.get('data')
    d='00000000-0000-0000-0000-000000000101'
    t=query('01-create-draft','mutation{createTour(input:{directionId:"'+d+'",title:"Проверка ЛР4",description:"Проверка бизнес-операций",durationDays:2}){id version status}}','demo-employee')['createTour'];tid=t['id'];assert t['status']=='DRAFT'
    query('02-client-cannot-see-draft','{tour(id:"'+tid+'"){id}}',expected_error='NOT_FOUND')
    s=query('03-create-schedule','mutation{createSchedule(input:{tourId:"'+tid+'",startsAt:"2026-12-10T06:00:00Z",endsAt:"2026-12-12T06:00:00Z",capacity:2,basePriceMinor:"10000"}){id}}','demo-employee')['createSchedule'];sid=s['id']
    t=query('04-read-draft-version','{tour(id:"'+tid+'"){version status}}','demo-employee')['tour']
    query('05-publish','mutation{updateTour(input:{id:"'+tid+'",status:PUBLISHED,expectedVersion:"'+t['version']+'"}){status}}','demo-employee')
    def book(count,key):return 'mutation{createBooking(input:{scheduleId:"'+sid+'",travelerCount:'+str(count)+',expectedPriceVersion:"1"},idempotencyKey:"'+key+'" ){id status total{amountMinor}}}'
    b=query('06-create-booking',book(2,prefix))['createBooking'];bid=b['id'];assert b['total']['amountMinor']=='20000'
    same=query('07-idempotent-retry',book(2,prefix))['createBooking'];assert same['id']==bid
    query('08-key-payload-conflict',book(1,prefix),expected_error='CONFLICT')
    query('09-insufficient-seats',book(1,prefix+'-other'),expected_error='INSUFFICIENT_SEATS')
    query('10-read-filter-sort-summary','{myBookings(page:{page:0,size:20},filter:{status:PENDING_PAYMENT},sort:{field:TOTAL,direction:DESC}){totalCount items{id}} bookingSummary(filter:{status:PENDING_PAYMENT}){count total{amountMinor}}}')
    b=query('11-cancel','mutation{cancelBooking(input:{bookingId:"'+bid+'",reason:"Проверка отмены"},idempotencyKey:"'+prefix+'-cancel"){status}}')['cancelBooking'];assert b['status']=='CANCELLED'
    t=query('12-seats-restored','{tour(id:"'+tid+'"){schedules{availableSeats}}}')['tour'];assert t['schedules'][0]['availableSeats']==2
    (out/'summary.json').write_text(json.dumps({'passed':len(results),'total':len(results),'checks':results},ensure_ascii=False,indent=2),encoding='utf8')
    print(f'{len(results)}/{len(results)} PASS')


if __name__=='__main__':main()
