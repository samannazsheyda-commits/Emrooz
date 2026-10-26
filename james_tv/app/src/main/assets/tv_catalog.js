(function(){
 if(window.__JamesCatalog)return;
 function clean(v){return String(v||"").replace(/\s+/g," ").trim().slice(0,240);}
 function absolute(v){if(!v)return "";try{var u=new URL(v,location.href);return /^https?:$/.test(u.protocol)?u.href:"";}catch(e){return "";}}
 function imageOf(node){
   if(!node)return "";
   var img=node.matches&&node.matches("img")?node:node.querySelector&&node.querySelector("img");
   if(img){
     var src=img.getAttribute("data-src")||img.getAttribute("data-original")||img.getAttribute("data-lazy-src")||img.currentSrc||img.getAttribute("src")||"";
     if(src.startsWith("data:"))src=img.getAttribute("data-src")||"";
     if(!src){var ss=img.getAttribute("srcset")||"";src=ss.split(",")[0].trim().split(/\s+/)[0]||"";}
     var a=absolute(src);if(a)return a;
   }
   for(var cur=node,depth=0;cur&&depth<3;cur=cur.firstElementChild,depth++){
      var st=cur.getAttribute&&cur.getAttribute("style")||"";
      var m=st.match(/background-image\s*:\s*url\(\s*['"]?([^'")]+)['"]?\s*\)/i);
      if(m)return absolute(m[1]);
   }
   return "";
 }
 function headingFor(n){
   var p=n;
   for(var i=0;i<5&&p;i++,p=p.parentElement){
      var c=String(p.className||"").toLowerCase();
      if(p.matches&&p.matches("section,[class*=shelf],[class*=carousel],[class*=slider],[class*=row],[class*=list]")){
         var h=p.querySelector("h2,h3,[class*=title],[class*=heading]");
         if(h){var t=clean(h.innerText||h.textContent);if(t&&t.length<55)return t;}
      }
   }
   var prev=n.closest&&n.closest("section");
   if(prev){var x=prev.querySelector("h2,h3");if(x)return clean(x.textContent);}
   return "پیشنهادهای سایت";
 }
 function titleOf(a){
   var image=a.querySelector&&a.querySelector("img");
   var headline=a.querySelector&&a.querySelector("h2,h3,h4,[class*=title]");
   var t=clean(a.getAttribute("aria-label")||a.getAttribute("title")||
     (headline&&headline.textContent)||(image&&(image.getAttribute("alt")||image.getAttribute("title")))||a.innerText||a.textContent);
   t=t.replace(/\b(watch now|play now|more info)\b/ig,"").trim();
   return t.slice(0,110);
 }
 function emit(items,seen,entry){
   if(!entry.url||!entry.title||seen.has(entry.url))return;
   if(/\/(login|logout|signup|register|account|privacy|terms|contact|settings|password)(\/|$|[?#])/i.test(entry.url))return;
   if(entry.title.length>110)return;
   seen.add(entry.url);items.push(entry);
 }
 function collect(){
   var seen=new Set(),items=[];
   var anchors=[].slice.call(document.querySelectorAll("a[href]")).slice(0,2500);
   anchors.forEach(function(a){
     if(items.length>=240)return;
     var href=absolute(a.getAttribute("href")),image=imageOf(a);
     if(!href||href===location.href||href.startsWith("javascript:")||href.endsWith("#"))return;
     var title=titleOf(a);
     if(!title)return;
     var likely=/film|movie|watch|series|tv-show|episode|video|media|title|play|detail|show|stream|item|content/i.test(href);
     if(!image&&!likely)return;
     if(!image && title.length<5)return;
     var grp=headingFor(a), combined=(grp+" "+href).toLowerCase();
     var type=/سریال|series|season|episode|tv.show/i.test(combined)?"series":"movie";
     var year=(title+" "+a.textContent).match(/(?:19|20)\d{2}/);
     emit(items,seen,{title:title,url:href,image:image||"",group:grp,type:type,
       year:year?year[0]:""});
   });
   Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0,15).forEach(function(s){
     try{
       var parsed=JSON.parse(s.textContent);
       function walk(obj,depth){
         if(!obj||depth>5||items.length>=240)return;
         if(Array.isArray(obj)){obj.slice(0,150).forEach(function(x){walk(x,depth+1);});return;}
         if(typeof obj!=="object")return;
         var type=String(obj["@type"]||"");
         if(/Movie|TVSeries|TVEpisode|VideoObject/i.test(type)){
            var pic=obj.image; if(Array.isArray(pic))pic=pic[0];if(pic&&typeof pic==="object")pic=pic.url||pic.contentUrl;
            emit(items,seen,{title:clean(obj.name||obj.headline),url:absolute(obj.url||obj.mainEntityOfPage),
              image:absolute(pic),group:"از اطلاعات سایت",type:/Series|Episode/.test(type)?"series":"movie",
              year:String(obj.datePublished||"").slice(0,4),overview:clean(obj.description)});
         }
         for(var key in obj)if(["itemListElement","mainEntity","@graph","item","hasPart"].indexOf(key)>=0)walk(obj[key],depth+1);
       }
       walk(parsed,0);
     }catch(e){}
   });
   return {url:location.href,pageTitle:document.title,items:items,found:items.length};
 }
 function details(){
   var desc=document.querySelector('meta[name="description"],meta[property="og:description"]');
   var title=document.querySelector('meta[property="og:title"]');
   var image=document.querySelector('meta[property="og:image"]');
   var source=document.querySelector("video source[src],video[src]");
   var buttons=Array.from(document.querySelectorAll("a,button")).filter(function(n){
       return /^(play|watch|تماشا|پخش|مشاهده|دیدن)(\s|$)/i.test(clean(n.innerText||n.textContent||n.title));
   });
   var playUrl=buttons.length ? absolute(buttons[0].getAttribute("href")) : "";
   var video= document.querySelector("video");
   return {title:clean((title&&title.content)||document.title),description:clean((desc&&desc.content)||""),
       image:absolute(image&&image.content||""),stream:absolute((source&&source.getAttribute("src"))||(video&&(video.currentSrc||video.src))||""),
       playUrl:playUrl};
 }
 function play(){
   var v=document.querySelector("video");
   if(v){try{v.play();return true;}catch(e){}}
   var els=Array.from(document.querySelectorAll("button,a,[role=button]"));
   var b=els.find(function(n){return /^(play|watch|تماشا|پخش|مشاهده|دیدن)(\s|$)/i.test(clean(n.textContent||n.getAttribute("aria-label")));});
   if(b){b.click();return true;}return false;
 }
 function player(action){
   var v=document.querySelector("video");if(!v)return false;
   if(action==="pause"){v.pause();return true;}
   if(action==="toggle"){if(v.paused)v.play();else v.pause();return true;}
   if(action==="forward"){v.currentTime=Math.min(v.duration||Infinity,v.currentTime+10);return true;}
   if(action==="rewind"){v.currentTime=Math.max(0,v.currentTime-10);return true;}
   if(action==="fullscreen"){try{v.requestFullscreen();return true;}catch(e){}}
   return false;
 }
 window.__JamesCatalog={collect:collect,details:details,play:play,player:player};
})();