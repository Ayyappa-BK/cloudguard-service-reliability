#!/usr/bin/env python3
"""Run after Compose startup and load to verify scrape, dashboard, and exported traces."""
import json,time,urllib.request

def get(url):
 with urllib.request.urlopen(url,timeout=5) as response:return json.load(response)
for _ in range(60):
 try:
  assert get('http://localhost:3000/api/health')['database']=='ok'
  result=get('http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22cloudguard%22%7D')['data']['result']
  assert result and result[0]['value'][1]=='1'
  # Tempo receives spans through Collector; search verifies the whole trace path.
  traces=get('http://localhost:3200/api/search?tags=service.name%3Dcloudguard')['traces']
  assert traces
  print('Grafana, Prometheus scrape and OTel → Tempo traces verified');break
 except Exception:time.sleep(1)
else:raise SystemExit('monitoring pipeline did not become healthy')
