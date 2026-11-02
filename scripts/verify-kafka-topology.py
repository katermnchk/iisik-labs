"""Real broker topology and consumer-subscription checks; no mocks."""
import subprocess,json,pathlib,re
root=pathlib.Path(__file__).resolve().parents[1]
def cli(script,*args):
 p=subprocess.run(['docker','compose','exec','-T','kafka','/opt/kafka/bin/'+script,'--bootstrap-server','localhost:9092',*args],cwd=root,text=True,capture_output=True,timeout=35)
 assert p.returncode==0,p.stderr;return p.stdout
names=cli('kafka-topics.sh','--list').splitlines()
expected={'user.events.v1','tour.events.v1','booking.events.v1','payment.events.v1'}
assert expected.issubset(names),'Missing domain topics: '+str(expected-set(names))
description=cli('kafka-topics.sh','--describe','--topic','(user|tour|booking|payment)\\.events\\.v1.*')
for line in description.splitlines():
 if 'PartitionCount:' in line:assert 'PartitionCount: 3' in line,line
for topic,consumer in [('user.events.v1','notification'),('booking.events.v1','notification'),('payment.events.v1','notification'),('payment.events.v1','booking'),('payment.events.v1','analytics'),('tour.events.v1','analytics')]:
 assert topic+'.'+consumer+'.dlt' in names,(topic,consumer)
print('PASS four domain topics, three partitions each, independent consumer DLTs')

subscriptions={'booking':{'payment.events.v1'},'analytics':{'payment.events.v1','tour.events.v1'},'notification':{'user.events.v1','booking.events.v1','payment.events.v1'}}
assignments={}
for consumer,topics in subscriptions.items():
 members=cli('kafka-consumer-groups.sh','--describe','--group','travel-'+consumer+'-v1','--members','--verbose')
 actual=set(re.findall(r'(?:user|tour|booking|payment)\.events\.v1',members))
 assert actual==topics,(consumer,actual,topics,members)
 assert 'travel.events.v1' not in members,members
 assignments[consumer]={'topics':sorted(actual),'members':members}
evidence=root/'docs/evidence/labs567';evidence.mkdir(parents=True,exist_ok=True)
(evidence/'kafka-topology.json').write_text(json.dumps({'domainTopics':sorted(expected),'partitionsPerTopic':3,'consumerAssignments':assignments,'topicDescriptions':description,'sharedTopicActivelyConsumed':False},ensure_ascii=False,indent=2))
print('PASS actual consumer assignments match domain subscriptions; no shared topic')
