#!/usr/bin/env python3
import argparse,concurrent.futures,json,time,urllib.request,urllib.error,platform,datetime
p=argparse.ArgumentParser();p.add_argument('--requests',type=int,default=300);p.add_argument('--concurrency',type=int,default=8);p.add_argument('--output',default='results/load.json');p.add_argument('--url',default='http://localhost:8080/api/check');p.add_argument('--recovery',action='store_true');p.add_argument('--fault',choices=['error','slow'],default='error');p.add_argument('--duration',type=float,default=0);a=p.parse_args()
if a.requests<1 or a.concurrency<1 or a.duration<0:p.error('positive requests/concurrency and nonnegative duration required')
def request(_=0):
 start=time.perf_counter()
 try:
  with urllib.request.urlopen(a.url,timeout=3) as r: code=r.status
 except urllib.error.HTTPError as e:code=e.code
 except Exception:code=0
 return code,(time.perf_counter()-start)*1000
def fault(mode):
 req=urllib.request.Request('http://localhost:8081/fault',json.dumps({'mode':mode,'ttl':30}).encode(),{'Content-Type':'application/json'})
 urllib.request.urlopen(req,timeout=3).close()
start=time.perf_counter()
def worker(_):
 rows=[]
 while time.perf_counter()-start<a.duration: rows.append(request())
 return rows
with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency) as pool:
 rows=[v for batch in pool.map(worker,range(a.concurrency)) for v in batch] if a.duration else list(pool.map(request,range(a.requests)))
elapsed=time.perf_counter()-start;lat=sorted(x[1] for x in rows)
result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'platform':platform.platform(),'requests':len(rows),'concurrency':a.concurrency,'elapsed_seconds':elapsed,'availability_percent':100*sum(c==200 for c,_ in rows)/len(rows),'throughput_rps':len(rows)/elapsed,'latency_ms':{str(q):lat[min(len(lat)-1,int(q*len(lat)))] for q in (.5,.95,.99)},'errors':sum(c!=200 for c,_ in rows)}
if a.recovery:
 try:
  fault(a.fault);failed=[request() for _ in range(6)];fault('healthy');t=time.perf_counter();success=0
  while time.perf_counter()-t<10 and success<3:
   code,_=request();success=success+1 if code==200 else 0;time.sleep(.1)
  result['recovery']={'mode':a.fault,'fault_responses':[c for c,_ in failed],'fault_latency_ms':[ms for _,ms in failed],'seconds_until_three_successes':time.perf_counter()-t,'recovered':success==3}
 finally:fault('healthy')
from pathlib import Path
Path(a.output).parent.mkdir(parents=True,exist_ok=True);Path(a.output).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
assert result['errors']==0,'baseline load failed'
if a.recovery:assert result['recovery']['recovered'],'recovery failed'
