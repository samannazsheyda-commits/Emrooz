(function(){
 if(window.__JamesCatalog&&window.__JamesCatalog.version==="3.1")return;
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
      var st=(cur.getAttribute&&cur.getAttribute("style")||"")+";background-image:"+(getComputedStyle(cur).backgroundImage||"");
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
   const images=document.querySelectorAll("img");
   const entries=[],seen=new Set();
   const cards=Array.from(document.querySelectorAll("a[href],[role=link][href]")).slice(0,3500);
   const dimensions=function(anchor){
      let img=anchor.matches("img")?anchor:anchor.querySelector("img");
      let r=(img||anchor).getBoundingClientRect();
      let width=(img&&img.naturalWidth)||(img&&img.width)||r.width||0, height=(img&&img.naturalHeight)||(img&&img.height)||r.height||0;
      return {width:Math.round(width),height:Math.round(height),ratio:height?Math.round(width/height*1000)/1000:0};
   };
   for(let a of cards){
      if(entries.length>=300)break;
      if(a.closest("header,footer,nav,form"))continue;
      let href=absolute(a.getAttribute("href")), image=imageOf(a);
      if(!href||href===location.href||seen.has(href))continue;
      if(/\/(login|logout|register|signup|privacy|terms|contact|account|settings)(\/|[?#]|$)/i.test(href))continue;
      if(!image||/favicon|avatar|logo|icon|flag|placeholder|gravatar/i.test(image))continue;
      const dim=dimensions(a);
      if(dim.width>0&&dim.height>0&&dim.width<65&&dim.height<65)continue;
      let title=titleOf(a);
      if((!title||title.length<3)&&a.parentElement){
          const h=a.parentElement.querySelector("h2,h3,h4,[class*=title],[class*=name]");
          title=clean((h&&h.innerText)||(h&&h.textContent)||"");
      }
      if(!title||title.length<3||/^(home|login|menu|profile|ورود|خانه|خروج|جستجو|search)$/i.test(title))continue;
      const group=headingFor(a);
      const kind=/سریال|series|season|episode|tv.show|انیمه|anime/i.test(group+" "+href)?"series":"movie";
      const year=(title+" "+a.textContent).match(/(?:19|20)\d{2}/);
      seen.add(href);
      entries.push({title:title.slice(0,100),url:href,image,group,type:kind,
        year:year?year[0]:"",imageWidth:dim.width,imageHeight:dim.height,imageRatio:dim.ratio});
   }
   // Structured data is useful when React/Vue card text is hidden from the DOM.
   for(const node of Array.from(document.querySelectorAll('script[type="application/ld+json"]')).slice(0,15)){
      try {
         const root=JSON.parse(node.textContent);
         function walk(data,depth){
            if(!data||depth>5||entries.length>=300)return;
            if(Array.isArray(data)){data.slice(0,100).forEach(x=>walk(x,depth+1));return;}
            if(typeof data!=="object")return;
            const type=String(data["@type"]||"");
            if(/Movie|TVSeries|TVEpisode|VideoObject/i.test(type)){
              let pic=Array.isArray(data.image)?data.image[0]:data.image;
              if(pic&&typeof pic==="object")pic=pic.url||pic.contentUrl;
              const href=absolute(data.url||(data.mainEntityOfPage&&data.mainEntityOfPage["@id"])||data.mainEntityOfPage);
              const image=absolute(pic),title=clean(data.name||data.headline).slice(0,100);
              if(href&&image&&title.length>2&&!seen.has(href)){
                seen.add(href);entries.push({title,url:href,image,group:"عنوان‌های James",
                  type:/Series|Episode/.test(type)?"series":"movie",year:String(data.datePublished||"").slice(0,4),
                  overview:clean(data.description)});
              }
            }
            for(const key of ["@graph","mainEntity","itemListElement","item","hasPart"])
              if(data[key])walk(data[key],depth+1);
         }
         walk(root,0);
      }catch(e){}
   }
   return {items:entries,found:entries.length,imageCount:images.length,anchorCount:cards.length,
      pageType:Array.from(document.querySelectorAll("input[type=password]")).some(function(n){return n.offsetWidth>0&&n.offsetHeight>0})?"login":"content};
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
 window.__JamesCatalog={version:"3.1",collect:collect,details:details,play:play,player:player};
})();