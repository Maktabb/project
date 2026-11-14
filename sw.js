/* Service worker — works offline, but ALWAYS checks the network first for the app itself so updates arrive */
const CACHE='msalem-20261114164041';
const ASSETS=['./','index.html','manifest.webmanifest','icon-192.png','icon-512.png','icon-512-maskable.png','apple-touch-icon.png'];
self.addEventListener('install',e=>{
  e.waitUntil(caches.open(CACHE).then(c=>Promise.all(ASSETS.map(a=>fetch(new Request(a,{cache:'reload'})).then(r=>r.ok&&c.put(a,r)).catch(()=>0)))).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',e=>{
  e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));
});
self.addEventListener('message',e=>{ if(e.data==='SKIP_WAITING') self.skipWaiting(); });
self.addEventListener('fetch',e=>{
  const req=e.request; if(req.method!=='GET') return;
  const u=new URL(req.url); if(u.origin!==location.origin) return;
  if(u.pathname.endsWith('version.json')){ e.respondWith(fetch(req,{cache:'no-store'}).catch(()=>new Response('{}',{headers:{'Content-Type':'application/json'}}))); return; }
  const isPage=req.mode==='navigate'||u.pathname.endsWith('.html')||u.pathname.endsWith('/')||u.pathname.endsWith('.webmanifest');
  if(isPage){
    e.respondWith(fetch(req,{cache:'no-store'}).then(res=>{ const copy=res.clone(); caches.open(CACHE).then(c=>{ try{ c.put(req.mode==='navigate'?'index.html':req,copy); }catch(_){} }); return res; })
      .catch(()=>caches.match(req).then(h=>h||caches.match('index.html'))));
    return;
  }
  e.respondWith(caches.match(req).then(hit=>hit||fetch(req).then(res=>{ const copy=res.clone(); caches.open(CACHE).then(c=>{ try{ c.put(req,copy); }catch(_){} }); return res; })));
});
