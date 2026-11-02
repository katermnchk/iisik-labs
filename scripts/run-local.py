#!/usr/bin/env python3
"""Run the same services from IntelliJ-built jars with Docker infrastructure."""
import pathlib, os, subprocess, signal, time
root=pathlib.Path(__file__).resolve().parents[1]
env=os.environ.copy()
for line in (root/'.env').read_text().splitlines():
 if line and not line.startswith('#'): k,v=line.split('=',1);env[k]=v
java=str(pathlib.Path(env.get('JAVA_HOME','/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home'))/'bin/java') if env.get('JAVA_HOME') or os.uname().sysname=='Darwin' else 'java'
logs=root/'docs/evidence/runtime';logs.mkdir(parents=True,exist_ok=True)
children=[]
try:
 for module in ['user-service','tour-service','booking-service','payment-service','notification-service','analytics-service','auth-service','graphql-api']:
  local=env.copy()
  if module!='auth-service':
   local.pop('JWT_SECRET',None);local.pop('LAB_PASSWORD',None)
  if module=='graphql-api':local['PORT']='8180'
  stream=(logs/(module+'.log')).open('w')
  child=subprocess.Popen([java,'-Xmx192m','-jar',str(root/module/'target'/(module+'-1.0.0.jar'))],env=local,stdout=stream,stderr=subprocess.STDOUT)
  children.append(child);print(module,'pid',child.pid,flush=True)
 while True:
  if any(p.poll() is not None for p in children):raise SystemExit('Service exited; inspect docs/evidence/runtime')
  time.sleep(1)
except KeyboardInterrupt:pass
finally:
 for p in children:p.terminate()
 for p in children:
  try:p.wait(10)
  except subprocess.TimeoutExpired:p.kill()
