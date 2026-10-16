#!/usr/bin/env python3
"""Local-only fault target; never expose its control port publicly."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import json,time,threading
state={"mode":"healthy","until":0};lock=threading.Lock()
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_POST(self):
  if self.path!='/fault': self.send_error(404);return
  try:
   data=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))))
   mode=data['mode'];ttl=float(data.get('ttl',10))
   if mode not in ('healthy','error','slow') or not 0<ttl<=60: raise ValueError()
  except Exception: self.send_error(400);return
  with lock: state.update(mode=mode,until=time.monotonic()+ttl)
  self.send_response(204);self.end_headers()
 def do_GET(self):
  if self.path!='/data': self.send_error(404);return
  with lock: mode=state['mode'] if time.monotonic()<state['until'] else 'healthy'
  if mode=='slow': time.sleep(1)
  self.send_response(503 if mode=='error' else 200);self.end_headers()
  try:self.wfile.write(b'dependency-ok')
  except (BrokenPipeError,ConnectionResetError):pass
if __name__=='__main__':
 import os
 class Server(ThreadingHTTPServer): request_queue_size=128
 Server((os.getenv('BIND','127.0.0.1'),int(os.getenv('PORT','8081'))),Handler).serve_forever()
