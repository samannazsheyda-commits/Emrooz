(function(){
  if(window.__jamesTvNav) return;
  var css=document.createElement("style");
  css.textContent=".james-tv-active {outline:4px solid #00E8D6 !important;outline-offset:3px !important;box-shadow:0 0 0 2px #132B40,0 0 14px rgba(0,232,214,.6) !important;}";
  (document.head||document.documentElement).appendChild(css);
  var current=null, cache=[], dirty=true;
  var selectors=[
    "a[href]","button","input:not([type=hidden])","select","textarea",
    "summary","[tabindex]:not([tabindex='-1'])","[role=button]","[role=link]",
    "[role=tab]","[role=option]","[role=menuitem]","[role=checkbox]",
    "[role=radio]","[role=switch]","[role=slider]","[onclick]",
    "video","iframe","[class*=movie-card]","[class*=poster]",
    "[class*=swiper-slide]","[class*=slick-slide]","[class*=film-card]",
    "article a","[data-testid*=poster]","[data-action]"
  ].join(",");
  function visible(e){
    if(!e||!e.isConnected) return false;
    var s=getComputedStyle(e), r=e.getBoundingClientRect();
    return s.visibility!=="hidden"&&s.display!=="none"&&parseFloat(s.opacity||"1")>0.05&&r.width>9&&r.height>9;
  }
  function gather(){
    if(!dirty&&cache.length) return cache;
    cache=Array.from(document.querySelectorAll(selectors)).filter(visible);
    cache=cache.filter(function(e){
      var ancestor=e.parentElement;
      while(ancestor&&ancestor!==document.body){
        if(cache.indexOf(ancestor)>=0 && (ancestor.tagName==="A"||ancestor.tagName==="BUTTON")) return false;
        ancestor=ancestor.parentElement;
      }
      return true;
    });
    dirty=false;return cache;
  }
  function mark(el){
    if(current&&current.classList) current.classList.remove("james-tv-active");
    current=el;
    if(current){
      current.classList.add("james-tv-active");
      current.scrollIntoView({block:"nearest",inline:"nearest",behavior:"auto"});
    }
  }
  function center(el){var r=el.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};}
  function first(list){
    var chosen=null,score=Infinity;
    list.forEach(function(e){var p=center(e);
      var off=Math.max(0,-p.y,p.y-innerHeight)*5;
      var s=off+Math.abs(p.x-innerWidth*0.5)*0.8+Math.abs(p.y-innerHeight*0.4);
      if(s<score){chosen=e;score=s;}
    });return chosen;
  }
  function move(dir){
    var list=gather();
    if(!list.length){scroll(dir);return false;}
    if(!current||!visible(current)){mark(first(list));return true;}
    var p=center(current), best=null, bestCost=Infinity;
    list.forEach(function(e){
      if(e===current)return;
      var q=center(e),dx=q.x-p.x,dy=q.y-p.y;
      var major=dir==="right"?dx:dir==="left"?-dx:dir==="down"?dy:-dy;
      var minor=(dir==="left"||dir==="right")?Math.abs(dy):Math.abs(dx);
      if(major<5)return;
      var cost=major+minor*2.5+Math.pow(minor/(major+15),2)*45;
      if(cost<bestCost){best=e;bestCost=cost;}
    });
    if(best){mark(best);return true;}
    scroll(dir);
    setTimeout(function(){
      dirty=true;
      var update=gather();
      var old=current;
      if(old&&update.indexOf(old)<0)mark(first(update));
      else if(update.length>0){
        var z=center(old||update[0]),b=null,d=Infinity;
        update.forEach(function(e){if(e===old)return;var r=center(e);
          var delta=dir==="down"?r.y-z.y:dir==="up"?z.y-r.y:dir==="right"?r.x-z.x:z.x-r.x;
          if(delta>4&&delta<d){d=delta;b=e;}
        });
        if(b)mark(b);
      }
    },110);
    return true;
  }
  function scroll(d){window.scrollBy(d==="up"?0:d==="down"?0:d==="left"?-innerWidth*0.55:innerWidth*0.55,
                              d==="up"?-innerHeight*0.6:d==="down"?innerHeight*0.6:0);}
  function activate(){
    if(!current||!visible(current)){var l=gather();mark(first(l));}
    if(!current)return {ok:false};
    if(current.matches("input,textarea,select,[contenteditable=true]")){
      current.focus();current.click();return {ok:false};
    }
    var r=current.getBoundingClientRect();
    if(r.width<1||r.height<1)return {ok:false};
    return {ok:true,x:Math.max(0,Math.min(.999,(r.left+r.width/2)/innerWidth)),
                    y:Math.max(0,Math.min(.999,(r.top+r.height/2)/innerHeight))};
  }
  new MutationObserver(function(){dirty=true;}).observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:["style","class","hidden"]});
  window.__jamesTvNav={move:move,activate:activate,version:"3.0",refresh:function(){dirty=true;}};
})();
