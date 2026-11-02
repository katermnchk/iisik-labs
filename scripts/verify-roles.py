import pathlib,urllib.request,json
root=pathlib.Path(__file__).resolve().parents[1]
def gql(q,v=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 req=urllib.request.Request('http://127.0.0.1:8180/graphql',json.dumps({'query':q,'variables':v or {}}).encode(),h)
 with urllib.request.urlopen(req,timeout=15) as r:return json.load(r)
def login(n):
 a=gql('mutation($i:AuthenticateInput!){authenticate(input:$i){accessToken}}',{'i':{'email':'user'+str(n)+'@example.test','password':'Lab567-Travel!'}});assert not a.get('errors'),a;return a['data']['authenticate']['accessToken']
admin,oldtoken=login(4),login(2);u='00000000-0000-0000-0000-000000000002'
mutation='mutation($i:UpdateUserInput!){updateUser(input:$i){id role{code}}}'
try:
 a=gql(mutation,{'i':{'userId':u,'role':'EMPLOYEE','position':'Проверка смены роли'}},admin);assert not a.get('errors'),a
 a=gql('{me{role{code}}}',t=oldtoken);assert a['data']['me']['role']['code']=='EMPLOYEE',a
 a=gql('{myBookings(page:{page:0,size:5}){totalCount}}',t=oldtoken);assert a['errors'][0]['extensions']['code']=='FORBIDDEN',a
finally:
 a=gql(mutation,{'i':{'userId':u,'role':'CLIENT','phone':'+375290000002'}},admin);assert not a.get('errors'),a
(root/'docs/evidence/labs567/roles.json').write_text(json.dumps({'check':'current role overrides previous JWT role claim','ok':True,'oldJwtRole':'CLIENT','currentRole':'EMPLOYEE','clientOnlyOperation':'FORBIDDEN'},indent=2));print('PASS role changes affect previously issued JWT')
