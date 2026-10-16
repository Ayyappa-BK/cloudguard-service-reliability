#!/usr/bin/env python3
"""Optional single exporter queries aggregated Prometheus counters; no pod load balancing."""
import os,time,urllib.request,urllib.parse,json,math
import boto3
cw=boto3.client('cloudwatch')
def query(expression):
 url=os.getenv('PROMETHEUS_URL','http://prometheus:9090')+'/api/v1/query?'+urllib.parse.urlencode({'query':expression})
 with urllib.request.urlopen(url,timeout=5) as response: data=json.load(response)
 if data['status']!='success':raise ValueError('query failed')
 result=data['data']['result']
 if not result:return None
 value=float(result[0]['value'][1])
 return value if math.isfinite(value) else None
while True:
 try:
  total=query('sum(increase(cloudguard_requests_total[1m]))')
  errors=query('sum(increase(cloudguard_requests_total{outcome="error"}[1m]))') or 0
  if total and total>0:
   cw.put_metric_data(Namespace='CloudGuard',MetricData=[{'MetricName':'ErrorRate','Dimensions':[{'Name':'Service','Value':'cloudguard'}],'Value':100*errors/total,'Unit':'Percent'},{'MetricName':'Requests','Dimensions':[{'Name':'Service','Value':'cloudguard'}],'Value':total,'Unit':'Count'}])
 except Exception as e: print(json.dumps({'event':'export_failed','type':type(e).__name__}),flush=True)
 time.sleep(60)
