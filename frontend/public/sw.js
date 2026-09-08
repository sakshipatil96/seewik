const CACHE='seewik-app-v3';
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(['/','/manifest.webmanifest'])).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
  const url=new URL(event.request.url);
  if(event.request.method==='GET'&&url.origin===self.location.origin){
    const responsePromise=fetch(event.request).then(async response=>{
      if(response.ok){
        const cacheResponse=response.clone();
        try{
          const cache=await caches.open(CACHE);
          await cache.put(event.request,cacheResponse);
        }catch{
          // A cache failure must not replace a successful network response.
        }
      }
      return response;
    });
    event.waitUntil(responsePromise.then(()=>undefined,()=>undefined));
    event.respondWith(responsePromise.catch(async()=>await caches.match(event.request)||(event.request.mode==='navigate'?caches.match('/'):undefined)||new Response('Offline',{status:503,statusText:'Offline'})));
  }
});
