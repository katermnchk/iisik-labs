#!/usr/bin/env python3
"""Black-box checks against the real stack; evidence contains no JWT or passwords."""
import os, json, urllib.request, urllib.error, uuid, pathlib
URL=os.environ.get('GRAPHQL_URL','http://127.0.0.1:8180/graphql')
out=pathlib.Path(__file__).resolve().parents[1]/'docs/evidence/labs567'
out.mkdir(parents=True,exist_ok=True)
checks=[]
def gql(query, variables=None, token=None):
 headers={'Content-Type':'application/json'}
 if token: headers['Authorization']='Bearer '+token
 req=urllib.request.Request(URL,json.dumps({'query':query,'variables':variables or {}}).encode(),headers)
 try:
  with urllib.request.urlopen(req,timeout=20) as r: return json.load(r)
 except urllib.error.HTTPError as e: return json.load(e)
def check(name,result,code=None):
 if code: assert any(x.get('extensions',{}).get('code')==code for x in result.get('errors',[])),(name,result)
 else: assert not result.get('errors'),(name,result)
 checks.append({'check':name,'ok':True,'response':result})
 print('PASS',name)
check('anonymous query rejected',gql('{ me { id } }'),'UNAUTHENTICATED')
check('demo token rejected',gql('{ me { id } }',token='demo-admin'),'UNAUTHENTICATED')
email='lab-'+uuid.uuid4().hex+'@example.test'
a=gql('mutation($i:RegisterInput!){register(input:$i){accessToken user{id}}}',{'i':{'email':email,'password':'Lab567-password!','firstName':'Елена','lastName':'Ременчик','phone':'+375291234567'}})
assert not a.get('errors'),a
token=a['data']['register']['accessToken']; uid=a['data']['register']['user']['id']
assert len(token.split('.'))==3
check('registered principal persisted',gql('{ me { id email role { code } } }',token=token))
check('wrong password rejected',gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',{'i':{'email':email,'password':'wrong-password'}}),'UNAUTHENTICATED')
check('client analytics forbidden',gql('{ salesAnalytics(filter:{from:"2026-01-01T00:00:00Z",to:"2027-01-01T00:00:00Z"}){occupancy} }',token=token),'FORBIDDEN')
check('tampered signature rejected',gql('{me{id}}',token=token[:-8]+'AAAAAAAA'),'UNAUTHENTICATED')
check('catalog via grpc',gql('{tours(page:{page:0,size:5}){totalCount items{id}} directions{ id country }}',token=token))
check('logout removes session',gql('mutation{logout}',token=token))
check('revoked token rejected',gql('{me{id}}',token=token),'UNAUTHENTICATED')
(out/'security.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
print('Verified',len(checks),'checks')
