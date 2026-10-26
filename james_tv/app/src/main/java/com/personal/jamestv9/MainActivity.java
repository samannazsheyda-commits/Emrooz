package com.personal.jamestv9;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import org.json.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import android.util.LruCache;
import java.security.MessageDigest;

/**
 * James Cinema: user-local TV catalog over the site's logged-in WebView session.
 * No third-party catalog, no credentials are exported, and no invented film entries.
 */
public final class MainActivity extends Activity {
    private static final String SITE = "https://james.david-lean.net/";
    private static final int BG=Color.rgb(9,13,24), PANEL=Color.rgb(20,28,43);
    private static final int TEAL=Color.rgb(0,227,202), WHITE=Color.WHITE;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService photos=Executors.newFixedThreadPool(4);
    private final LruCache<String,Bitmap> bitmaps=new LruCache<String,Bitmap>(18*1024*1024){
        @Override protected int sizeOf(String k,Bitmap v){return v.getByteCount();}
    };
    private FrameLayout root, tvScreen;
    private WebView browser;
    private VideoView video;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private LinearLayout playerButtons;
    private SeekBar playerSeek;
    private TextView playerTime;
    private Movie selected;
    private final List<Movie> catalog=new ArrayList<>();
    private final LinkedHashSet<String> favorites=new LinkedHashSet<>();
    private String scanner,navScript;
    private String mode="HOME", filter="all", search="";
    private boolean webFocusReady=false, loading=false, scannedHome=false;
    private long lastNavKey=0;
    private final SharedPreferences.OnSharedPreferenceChangeListener ignored=(p,k)->{};
    private SharedPreferences prefs;
    private final Runnable updatePlayback=new Runnable(){
        @Override public void run(){
            if(video!=null&&"PLAYER_NATIVE".equals(mode)){
                try{
                    int len=video.getDuration(),pos=video.getCurrentPosition();
                    if(playerSeek!=null&&len>0){playerSeek.setMax(len);playerSeek.setProgress(pos);}
                    if(playerTime!=null)playerTime.setText(clock(pos)+" / "+clock(len));
                }catch(Exception ignored){}
                ui.postDelayed(this,850);
            }
        }
    };
    static class Movie {
        String name,url,image,group,kind,description="",year="",stream="",playUrl="";
        Movie(JSONObject j){
            name=j.optString("title","");
            url=j.optString("url","");
            image=j.optString("image","");
            group=j.optString("group","پیشنهادهای سایت");
            kind=j.optString("type","movie");
            description=j.optString("overview","");
            year=j.optString("year","");
        }
    }
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable shape(int color,int radius,int border){
        GradientDrawable b=new GradientDrawable();
        b.setColor(color);b.setCornerRadius(dp(radius));
        if(border>0)b.setStroke(dp(border),TEAL);
        return b;
    }
    private TextView label(String text,int sp,boolean strong){
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(WHITE);v.setTextSize(sp);
        if(strong)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMaxLines(2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;
    }
    private TextView action(String title,Runnable task,boolean primary){
        TextView b=label(title,17,true);
        b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);
        b.setPadding(dp(20),dp(12),dp(20),dp(12));
        b.setBackground(shape(primary?TEAL:PANEL,10,0));
        if(primary)b.setTextColor(BG);
        b.setOnClickListener(v->task.run());
        b.setOnFocusChangeListener((v,focus)->{
            v.setBackground(shape(focus?Color.rgb(30,75,82):(primary?TEAL:PANEL),10,focus?2:0));
            b.setTextColor(focus?WHITE:(primary?BG:WHITE));
            v.animate().scaleX(focus?1.045f:1f).scaleY(focus?1.045f:1f).setDuration(115).start();
        });
        return b;
    }
    private LinearLayout.LayoutParams pad(int w,int h,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));
        p.setMargins(0,0,dp(12),dp(bottom));return p;
    }
    private String clock(int millis){
        if(millis<0)return "00:00";
        int total=millis/1000;
        return String.format(java.util.Locale.US,"%02d:%02d",total/60,total%60);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);immersive();
        prefs=getSharedPreferences("tv_local",MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites",Collections.<String>emptySet()));
        root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
        try{
            scanner=asset("tv_catalog.js");
            navScript=asset("tv_navigation.js");
            browser=new WebView(this);
            browser.setBackgroundColor(BG);
            browser.setFocusable(true);browser.setFocusableInTouchMode(true);
            WebSettings s=browser.getSettings();
            s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);
            s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setSupportMultipleWindows(false);
            s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser,true);
            browser.setWebViewClient(new WebViewClient(){
                @Override public void onPageStarted(WebView view,String url,Bitmap favicon){webFocusReady=false;loading=true;}
                @Override public void onPageFinished(WebView view,String url){
                    loading=false;CookieManager.getInstance().flush();
                    browser.evaluateJavascript(navScript + "\n;Boolean(window.__jamesTvNav)",x->webFocusReady="true".equals(x));
                    browser.evaluateJavascript(scanner + "\n;Boolean(window.__JamesCatalog)",x->{});
                    ui.postDelayed(()->{
                        if(browser==null)return;
                        if("DETAIL".equals(mode)||"PLAYER_SITE".equals(mode))readDetails();
                        else scanCatalog();
                    },650);
                    if("HOME".equals(mode)||"SITE".equals(mode))ui.postDelayed(()->{
                        if(browser!=null&&!"DETAIL".equals(mode)&&!"PLAYER_SITE".equals(mode))scanCatalog();
                    },2600);
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                    Uri u=request.getUrl();String scheme=u.getScheme();
                    if(scheme==null||"https".equals(scheme)||"http".equals(scheme))return false;
                    if(request.isForMainFrame()){
                        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){toast("امکان بازکردن این لینک نیست");}
                    }
                    return true;
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError err){
                    if(req.isForMainFrame()&&catalog.isEmpty())toast("بارگذاری سایت ناموفق بود؛ اینترنت و WebView را بررسی کن");
                }
            });
            browser.setWebChromeClient(new WebChromeClient(){
                @Override public void onShowCustomView(View view,CustomViewCallback cb){
                    if(fullscreenView!=null){cb.onCustomViewHidden();return;}
                    fullscreenView=view;fullscreenCallback=cb;
                    root.addView(view,new FrameLayout.LayoutParams(-1,-1));view.bringToFront();
                    view.requestFocus();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                @Override public void onHideCustomView(){hideFullScreen();}
                @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage message){
                    return super.onConsoleMessage(message);
                }
            });
            root.addView(browser,new FrameLayout.LayoutParams(-1,-1));
            tvScreen=new FrameLayout(this);root.addView(tvScreen,new FrameLayout.LayoutParams(-1,-1));
            restoreCache();
            showHome();
            browser.loadUrl(SITE);
        }catch(Throwable t){
            TextView problem=label("James Cinema\nخطا در راه‌اندازی موتور مرورگر.\nAndroid System WebView را فعال یا به‌روزرسانی کن.",22,true);
            problem.setGravity(Gravity.CENTER);root.addView(problem,new FrameLayout.LayoutParams(-1,-1));
        }
    }
    private String asset(String p)throws IOException{
        try(InputStream in=getAssets().open(p);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }
    private void immersive(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}
    private void evaluate(String js,android.webkit.ValueCallback<String> cb){
        if(browser!=null)browser.evaluateJavascript(js,cb);
    }
    private JSONObject unpack(String response){
        try{if(response==null||"null".equals(response))return null;
            String val=new JSONArray("["+response+"]").getString(0);
            return new JSONObject(val);
        }catch(Exception e){return null;}
    }
    private void scanCatalog(){
        if(browser==null)return;
        evaluate("(function(){try{return JSON.stringify(window.__JamesCatalog.collect())}catch(e){return '{}'}})()",response->{
            JSONObject j=unpack(response);
            if(j==null)return;
            JSONArray a=j.optJSONArray("items");if(a==null)return;
            ArrayList<Movie> found=new ArrayList<>();HashSet<String> seen=new HashSet<>();
            for(int i=0;i<a.length();i++){
                JSONObject item=a.optJSONObject(i);if(item==null)continue;
                Movie m=new Movie(item);
                if(m.name.length()>1&&m.url.startsWith("http")&&seen.add(m.url))found.add(m);
            }
            if(found.size()>=2){
                // Do not replace the TV home with unrelated links on a playback/detail page.
                if(!"DETAIL".equals(mode)&&!"PLAYER_SITE".equals(mode)){
                    catalog.clear();catalog.addAll(found);
                    scannedHome=true;saveCache();
                    if("HOME".equals(mode))showHome();
                }
            }
        });
    }
    private void saveCache(){
        try{JSONArray a=new JSONArray();
            for(Movie m:catalog){JSONObject o=new JSONObject();
                o.put("title",m.name);o.put("url",m.url);o.put("image",m.image);
                o.put("group",m.group);o.put("type",m.kind);o.put("year",m.year);
                a.put(o);}
            prefs.edit().putString("catalog",a.toString()).apply();
        }catch(Exception ignored){}
    }
    private void restoreCache(){
        try{JSONArray a=new JSONArray(prefs.getString("catalog","[]"));
            for(int i=0;i<a.length()&&i<240;i++){
                Movie m=new Movie(a.getJSONObject(i));
                if(m.url.startsWith("http"))catalog.add(m);
            }
        }catch(Exception ignored){}
    }
    private void screenNative(boolean show){
        if(tvScreen!=null)tvScreen.setVisibility(show?View.VISIBLE:View.GONE);
        if(browser!=null){
            browser.setVisibility(View.VISIBLE);
            browser.setAlpha(show?0f:1f);
            browser.setFocusable(!show);
            if(!show)browser.bringToFront();
            else if(tvScreen!=null)tvScreen.bringToFront();
        }
    }
    private void drawBackdrop(FrameLayout parent,String poster){
        ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        parent.addView(art,new FrameLayout.LayoutParams(-1,-1));fetchImage(art,poster);
        View shade=new View(this);
        GradientDrawable fade=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff090d18,0xf2090d18,0xa0090d18});
        shade.setBackground(fade);
        parent.addView(shade,new FrameLayout.LayoutParams(-1,-1));
    }
    private void showHome(){
        mode="HOME";selected=null;video=null;ui.removeCallbacks(updatePlayback);
        tvScreen.removeAllViews();screenNative(true);
        ScrollView scroll=new ScrollView(this);
        scroll.setClipToPadding(false);scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);tvScreen.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout layout=column();scroll.addView(layout);
        LinearLayout top=row();top.setPadding(dp(42),dp(19),dp(42),dp(12));
        TextView logo=label("JAMES  CINEMA",22,true);logo.setTextColor(TEAL);
        top.addView(logo,pad(215,50,0));
        addNav(top,"خانه",()->{filter="all";search="";showHome();});
        addNav(top,"فیلم‌ها",()->{filter="movie";search="";showHome();});
        addNav(top,"سریال‌ها",()->{filter="series";search="";showHome();});
        addNav(top,"علاقه‌مندی‌ها",()->{filter="favorites";search="";showHome();});
        addNav(top,"جست‌وجو",this::showSearch);
        addNav(top,"بروزرسانی",this::refreshSite);
        addNav(top,"ورود / سایت",this::showSite);
        layout.addView(top);

        List<Movie> list=new ArrayList<>();
        for(Movie m:catalog){
            if("favorites".equals(filter)&&!favorites.contains(m.url))continue;
            if(("movie".equals(filter)||"series".equals(filter))&&!filter.equals(m.kind))continue;
            if(search.length()>0&&!m.name.toLowerCase().contains(search.toLowerCase()))continue;
            list.add(m);
        }
        if(list.size()==0){emptyHome(layout);return;}
        Movie hero=list.get(0);
        FrameLayout banner=new FrameLayout(this);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(248));
        bp.setMargins(dp(42),dp(4),dp(42),dp(24));layout.addView(banner,bp);
        banner.setBackground(shape(PANEL,15,0));banner.setClipToOutline(true);
        drawBackdrop(banner,hero.image);
        LinearLayout bannerContent=column();bannerContent.setPadding(dp(34),dp(26),dp(25),dp(15));
        FrameLayout.LayoutParams bcp=new FrameLayout.LayoutParams(dp(670),-1,Gravity.LEFT);
        banner.addView(bannerContent,bcp);
        TextView kicker=label("●  ویژه  |  محتوای حساب James",13,true);kicker.setTextColor(TEAL);
        bannerContent.addView(kicker,pad(-1,26,9));
        TextView name=label(hero.name,34,true);name.setMaxLines(2);bannerContent.addView(name,pad(-1,83,4));
        TextView meta=label((hero.year.length()>0?hero.year+"   •   ":"")+
              ("series".equals(hero.kind)?"سریال":"فیلم")+"   •   از سایت James",15,false);
        meta.setTextColor(0xffc4cfdb);bannerContent.addView(meta,pad(-1,30,14));
        LinearLayout actions=row();
        actions.addView(action("▶  تماشا",()->openMovie(hero),true),pad(-2,49,0));
        actions.addView(action("جزئیات",()->openMovie(hero),false),pad(-2,49,0));
        bannerContent.addView(actions);

        LinkedHashMap<String,ArrayList<Movie>> groups=new LinkedHashMap<>();
        if("all".equals(filter)){
            groups.put("تازه در James",new ArrayList<>(list.subList(0,Math.min(24,list.size()))));
            ArrayList<Movie> series=new ArrayList<>(),movies=new ArrayList<>();
            for(Movie m:list)if("series".equals(m.kind))series.add(m);else movies.add(m);
            if(!movies.isEmpty())groups.put("فیلم‌ها",movies);
            if(!series.isEmpty())groups.put("سریال‌ها",series);
            LinkedHashMap<String,ArrayList<Movie>> fromPage=new LinkedHashMap<>();
            for(Movie m:list){
                String group=(m.group==null||m.group.length()==0)?"پیشنهادهای سایت":m.group;
                if(!fromPage.containsKey(group))fromPage.put(group,new ArrayList<>());
                if(fromPage.get(group).size()<22)fromPage.get(group).add(m);
            }
            for(Map.Entry<String,ArrayList<Movie>> e:fromPage.entrySet()){
                if(groups.size()>=9)break;
                if(e.getValue().size()>=3&&!groups.containsKey(e.getKey()))groups.put(e.getKey(),e.getValue());
            }
        }else groups.put("favorites".equals(filter)?"فهرست من": "series".equals(filter)?"سریال‌ها":"فیلم‌ها",new ArrayList<>(list));
        for(Map.Entry<String,ArrayList<Movie>> e:groups.entrySet())addShelf(layout,e.getKey(),e.getValue());

        TextView footer=label("محتوا از حساب خودت در James خوانده می‌شود  •  آخرین تغییرات: بروزرسانی",12,false);
        footer.setTextColor(0xff718395);
        footer.setPadding(dp(42),dp(10),0,dp(30));layout.addView(footer);
    }
    private void addNav(LinearLayout parent,String title,Runnable task){
        TextView a=action(title,task,false);
        a.setTextSize(14);parent.addView(a,pad(-2,43,0));
    }
    private void emptyHome(LinearLayout layout){
        LinearLayout empty=column();empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(55),dp(42),dp(55),dp(40));
        TextView brand=label("JAMES CINEMA",40,true);brand.setTextColor(TEAL);
        brand.setGravity(Gravity.CENTER);empty.addView(brand,pad(-1,70,5));
        String message;
        if("favorites".equals(filter))message="هنوز فیلمی به فهرست من اضافه نکردی.";
        else if(catalog.isEmpty())message="برای نمایش فیلم‌های واقعی با حساب اشتراکی وارد سایت شو.\nاپ پوستر و فیلم ساختگی نمایش نمی‌دهد.";
        else message="موردی در این بخش پیدا نشد.";
        TextView state=label(message,20,false);state.setGravity(Gravity.CENTER);
        empty.addView(state,pad(-1,125,9));
        LinearLayout buttons=row();buttons.setGravity(Gravity.CENTER);
        buttons.addView(action("ورود و مشاهده سایت",this::showSite,true),pad(-2,52,0));
        buttons.addView(action("بروزرسانی فهرست",this::refreshSite,false),pad(-2,52,0));
        empty.addView(buttons);layout.addView(empty,new LinearLayout.LayoutParams(-1,dp(460)));
    }
    private void addShelf(LinearLayout parent,String group,List<Movie> movies){
        TextView title=label(group,22,true);title.setPadding(dp(42),0,0,0);
        parent.addView(title,pad(-1,38,10));
        HorizontalScrollView h=new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);h.setClipToPadding(false);
        LinearLayout items=row();items.setPadding(dp(42),dp(6),dp(42),dp(12));
        h.addView(items);
        for(int i=0;i<Math.min(movies.size(),60);i++){
            Movie m=movies.get(i);
            LinearLayout card=column();card.setFocusable(true);card.setClickable(true);
            card.setBackground(shape(PANEL,10,0));
            card.setPadding(dp(5),dp(5),dp(5),dp(8));
            ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(shape(0xff27364a,8,0));
            card.addView(poster,new LinearLayout.LayoutParams(dp(146),dp(192)));fetchImage(poster,m.image);
            TextView name=label(m.name,14,true);name.setMaxLines(2);
            LinearLayout.LayoutParams nt=new LinearLayout.LayoutParams(-1,dp(49));nt.topMargin=dp(6);
            card.addView(name,nt);
            TextView year=label(m.year.length()>0?m.year:("series".equals(m.kind)?"سریال":"فیلم"),12,false);
            year.setTextColor(0xff93a8b8);card.addView(year);
            card.setOnClickListener(v->openMovie(m));
            card.setOnFocusChangeListener((v,on)->{
                card.setBackground(shape(on?0xff164750:PANEL,11,on?2:0));
                v.animate().scaleX(on?1.06f:1f).scaleY(on?1.06f:1f).setDuration(120).start();
                if(on)h.smoothScrollTo((int)(v.getLeft()-dp(50)),0);
            });
            items.addView(card,pad(156,278,0));
        }
        parent.addView(h,pad(-1,294,14));
    }
    private void openMovie(Movie movie){
        selected=movie;mode="DETAIL";
        renderDetail();
        if(browser!=null&&movie.url.startsWith("https://"))browser.loadUrl(movie.url);
    }
    private void renderDetail(){
        if(selected==null)return;
        mode="DETAIL";
        tvScreen.removeAllViews();screenNative(true);
        FrameLayout backdrop=new FrameLayout(this);tvScreen.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        drawBackdrop(backdrop,selected.image);
        LinearLayout info=column();info.setPadding(dp(54),dp(38),dp(54),0);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(820),-1,Gravity.LEFT);backdrop.addView(info,fp);
        TextView home=action("‹  بازگشت به فیلم‌ها",this::showHome,false);
        info.addView(home,pad(-2,45,45));
        TextView tag=label("JAMES  /  "+("series".equals(selected.kind)?"سریال":"فیلم"),14,true);
        tag.setTextColor(TEAL);info.addView(tag,pad(-1,30,9));
        TextView title=label(selected.name,39,true);title.setMaxLines(3);info.addView(title,pad(-1,148,12));
        TextView meta=label(selected.year+"   •   "+selected.group,16,false);
        meta.setTextColor(0xffcad8e3);info.addView(meta,pad(-1,31,15));
        TextView text=label(selected.description.length()>0?selected.description:"جزئیات و پخش از حساب اشتراکی سایت James بارگذاری می‌شود.",18,false);
        text.setMaxLines(4);info.addView(text,pad(-1,140,20));
        LinearLayout actions=row();
        actions.addView(action("▶  پخش",this::startMovie,true),pad(-2,58,0));
        actions.addView(action(favorites.contains(selected.url)?"✓  در فهرست من":"＋  فهرست من",()->{
            if(favorites.contains(selected.url))favorites.remove(selected.url);else favorites.add(selected.url);
            prefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();renderDetail();
        },false),pad(-2,58,0));
        actions.addView(action("بازکردن در سایت",this::showSite,false),pad(-2,58,0));
        info.addView(actions);
    }
    private void readDetails(){
        evaluate("(function(){try{return JSON.stringify(window.__JamesCatalog.details())}catch(e){return '{}'}})()",response->{
            JSONObject j=unpack(response);if(j==null||selected==null)return;
            String text=j.optString("description","");
            if(text.length()>20)selected.description=text;
            String url=j.optString("stream","");if(url.startsWith("https://"))selected.stream=url;
            String play=j.optString("playUrl","");if(play.startsWith("https://"))selected.playUrl=play;
            String picture=j.optString("image","");if(selected.image.length()==0&&picture.startsWith("https://"))selected.image=picture;
            if("DETAIL".equals(mode))renderDetail();
        });
    }
    private void startMovie(){
        if(selected==null)return;
        if(selected.stream.startsWith("https://")||selected.stream.startsWith("http://")){
            startNative(selected.stream);
        }else{
            showSitePlayer();
        }
    }
    private void startNative(String url){
        mode="PLAYER_NATIVE";tvScreen.removeAllViews();screenNative(true);
        tvScreen.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);
        tvScreen.addView(video,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout chrome=column();
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        chrome.setPadding(dp(40),dp(14),dp(40),dp(28));
        chrome.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000,0xf8000000}));
        tvScreen.addView(chrome,cp);
        TextView title=label(selected.name,19,true);chrome.addView(title,pad(-1,42,4));
        playerSeek=new SeekBar(this);playerSeek.setMax(100);
        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){if(video!=null)video.seekTo(s.getProgress());}
            public void onProgressChanged(SeekBar s,int progress,boolean user){if(user&&video!=null&&video.getDuration()>0)video.seekTo(progress);}
        });
        chrome.addView(playerSeek,pad(-1,35,4));
        playerTime=label("00:00 / 00:00",14,false);chrome.addView(playerTime,pad(-1,26,6));
        LinearLayout controls=row();playerButtons=controls;
        controls.addView(action("‹  برگشت",this::backToDetail,false),pad(-2,49,0));
        controls.addView(action("« ۱۰ ثانیه",()->{if(video!=null)video.seekTo(Math.max(0,video.getCurrentPosition()-10000));},false),pad(-2,49,0));
        controls.addView(action("⏯  پخش/توقف",()->{
            if(video!=null){if(video.isPlaying())video.pause();else video.start();}
        },true),pad(-2,49,0));
        controls.addView(action("۱۰ ثانیه »",()->{if(video!=null)video.seekTo(video.getCurrentPosition()+10000);},false),pad(-2,49,0));
        controls.addView(action("پخش سایت",this::showSitePlayer,false),pad(-2,49,0));
        chrome.addView(controls);
        try{
            HashMap<String,String> headers=new HashMap<>();
            String cookie=CookieManager.getInstance().getCookie(url);
            if(cookie!=null)headers.put("Cookie",cookie);
            headers.put("Referer",selected.url);
            video.setVideoURI(Uri.parse(url),headers);
            video.setOnPreparedListener(mp->{video.start();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);ui.post(updatePlayback);});
            video.setOnErrorListener((mp,what,extra)->{toast("پلیر داخلی نتوانست ویدئو را باز کند؛ پخش سایت");showSitePlayer();return true;});
        }catch(Exception e){showSitePlayer();}
    }
    private void showSitePlayer(){
        if(selected==null)return;
        mode="PLAYER_SITE";ui.removeCallbacks(updatePlayback);
        if(video!=null){video.stopPlayback();video=null;}
        tvScreen.removeAllViews();screenNative(false);
        // The original authenticated player is kept as the compatibility path
        // for protected streams, ads, subtitle engines, and cross-origin iframes.
        if(browser!=null&&selected.playUrl.startsWith("https://")&&!selected.playUrl.equals(browser.getUrl())){
            browser.loadUrl(selected.playUrl);
        }else{
            evaluate("window.__JamesCatalog&&window.__JamesCatalog.play()",null);
        }
        LinearLayout toolbar=row();toolbar.setPadding(dp(18),dp(7),dp(18),dp(7));
        toolbar.setBackground(shape(0xe9121a29,12,0));
        FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        bar.setMargins(dp(12),dp(10),0,0);
        root.addView(toolbar,bar);toolbar.setTag("player_toolbar");
        toolbar.addView(action("‹  جزئیات",this::backToDetail,false),pad(-2,41,0));
        toolbar.addView(action("⏯",()->evaluate("window.__JamesCatalog&&window.__JamesCatalog.player('toggle')",null),false),pad(-2,41,0));
        toolbar.addView(action("−10",()->evaluate("window.__JamesCatalog&&window.__JamesCatalog.player('rewind')",null),false),pad(-2,41,0));
        toolbar.addView(action("+10",()->evaluate("window.__JamesCatalog&&window.__JamesCatalog.player('forward')",null),false),pad(-2,41,0));
        toolbar.bringToFront();
    }
    private void removePlayerToolbar(){
        for(int i=root.getChildCount()-1;i>=0;i--){
            if("player_toolbar".equals(root.getChildAt(i).getTag()))root.removeViewAt(i);
        }
    }
    private void backToDetail(){
        removePlayerToolbar();
        if(video!=null){video.stopPlayback();video=null;}
        ui.removeCallbacks(updatePlayback);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(fullscreenView!=null)hideFullScreen();
        if(selected!=null)renderDetail();else showHome();
    }
    private void showSearch(){
        mode="SEARCH";tvScreen.removeAllViews();screenNative(true);
        LinearLayout parent=column();parent.setPadding(dp(60),dp(45),dp(60),0);
        tvScreen.addView(parent,new FrameLayout.LayoutParams(-1,-1));
        parent.addView(label("جست‌وجو در فیلم‌ها و سریال‌های حساب شما",28,true),pad(-1,65,20));
        EditText input=new EditText(this);
        input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(0xffa5aab9);
        input.setHint("عنوان فیلم یا سریال را بنویس");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackground(shape(PANEL,12,2));
        input.setPadding(dp(18),0,dp(18),0);
        parent.addView(input,pad(-1,64,15));
        TextView msg=label("جست‌وجو میان عنوان‌های دریافت‌شده از سایت انجام می‌شود.",15,false);
        msg.setTextColor(0xffa5b1c0);parent.addView(msg,pad(-1,44,10));
        LinearLayout ops=row();
        ops.addView(action("نمایش نتیجه",()->{search=input.getText().toString().trim();filter="all";showHome();},true),pad(-2,52,0));
        ops.addView(action("بازگشت",this::showHome,false),pad(-2,52,0));
        ops.addView(action("جست‌وجوی خود سایت",this::showSite,false),pad(-2,52,0));
        parent.addView(ops);
        input.requestFocus();
    }
    private void showSite(){
        removePlayerToolbar();mode="SITE";screenNative(false);
        if(browser!=null){browser.requestFocus();browser.setAlpha(1f);}
        toast("برای بازگشت به رابط تلویزیون، BACK را بزن");
    }
    private void refreshSite(){
        search="";filter="all";mode="HOME";showHome();
        if(browser!=null)browser.loadUrl(SITE);
        toast("درحال دریافت عنوان‌ها از سایت ...");
    }
    private void hideFullScreen(){
        if(fullscreenView!=null){root.removeView(fullscreenView);fullscreenView=null;}
        if(fullscreenCallback!=null){fullscreenCallback.onCustomViewHidden();fullscreenCallback=null;}
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(fullscreenView!=null)return super.dispatchKeyEvent(event);
        if("PLAYER_NATIVE".equals(mode)){
            if(event.getAction()==KeyEvent.ACTION_UP && key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){
                if(video!=null){if(video.isPlaying())video.pause();else video.start();}return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if("PLAYER_SITE".equals(mode)){
            if(event.getAction()==KeyEvent.ACTION_UP && key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){
                evaluate("window.__JamesCatalog&&window.__JamesCatalog.player('toggle')",null);return true;
            }
        }
        if(!"SITE".equals(mode)&&!"PLAYER_SITE".equals(mode))return super.dispatchKeyEvent(event);
        if(browser==null||!webFocusReady)return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_MENU&&event.getAction()==KeyEvent.ACTION_UP){
            if("SITE".equals(mode))showHome();else backToDetail();return true;
        }
        int dir=key==KeyEvent.KEYCODE_DPAD_UP?0:key==KeyEvent.KEYCODE_DPAD_DOWN?1:
                key==KeyEvent.KEYCODE_DPAD_LEFT?2:key==KeyEvent.KEYCODE_DPAD_RIGHT?3:-1;
        if(dir>=0&&"SITE".equals(mode)){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                long now=SystemClock.uptimeMillis();
                if(event.getRepeatCount()<=1||now-lastNavKey>70){
                    lastNavKey=now;
                    String[] names={"up","down","left","right"};
                    evaluate("window.__jamesTvNav&&window.__jamesTvNav.move('"+names[dir]+"')",null);
                }
            }return true;
        }
        if("SITE".equals(mode)&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){
            if(event.getAction()==KeyEvent.ACTION_UP){
                evaluate("JSON.stringify(window.__jamesTvNav&&window.__jamesTvNav.activate())",r->{
                    JSONObject p=unpack(r);if(p==null||!p.optBoolean("ok"))return;
                    float x=(float)p.optDouble("x",0)*browser.getWidth();
                    float y=(float)p.optDouble("y",0)*browser.getHeight();
                    long at=SystemClock.uptimeMillis();
                    MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0);
                    MotionEvent up=MotionEvent.obtain(at,at+50,MotionEvent.ACTION_UP,x,y,0);
                    browser.dispatchTouchEvent(down);browser.dispatchTouchEvent(up);
                    down.recycle();up.recycle();
                });
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){
        if(fullscreenView!=null){hideFullScreen();return;}
        if("PLAYER_NATIVE".equals(mode)||"PLAYER_SITE".equals(mode)){backToDetail();return;}
        if("DETAIL".equals(mode)||"SEARCH".equals(mode)){showHome();return;}
        if("SITE".equals(mode)){
            if(browser!=null&&browser.canGoBack()){browser.goBack();return;}
            showHome();return;
        }
        super.onBackPressed();
    }
    @Override public void onResume(){super.onResume();immersive();if(browser!=null)browser.onResume();}
    @Override public void onPause(){
        if(browser!=null){browser.onPause();CookieManager.getInstance().flush();}
        if(video!=null&&video.isPlaying())video.pause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        ui.removeCallbacksAndMessages(null);photos.shutdownNow();
        if(video!=null){video.stopPlayback();video=null;}
        if(browser!=null){root.removeView(browser);browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
    private void fetchImage(ImageView view,String url){
        if(url==null||!url.startsWith("http"))return;
        view.setTag(url);
        Bitmap b=bitmaps.get(url);if(b!=null){view.setImageBitmap(b);return;}
        photos.execute(()->{
            Bitmap bitmap=null;
            try{
                File file=new File(getCacheDir(),"poster_"+hash(url));
                if(file.exists())bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
                if(bitmap==null){
                    HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
                    conn.setConnectTimeout(6500);conn.setReadTimeout(7000);
                    conn.setRequestProperty("User-Agent",browser!=null?browser.getSettings().getUserAgentString():"Mozilla/5.0");
                    String cookies=CookieManager.getInstance().getCookie(url);
                    if(cookies!=null)conn.setRequestProperty("Cookie",cookies);
                    conn.setRequestProperty("Referer",SITE);
                    try(InputStream input=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] chunk=new byte[4096];int n;int total=0;
                        while((n=input.read(chunk))>0 && total<4*1024*1024){
                            total+=n;out.write(chunk,0,n);
                        }
                        byte[] bytes=out.toByteArray();
                        BitmapFactory.Options opts=new BitmapFactory.Options();
                        opts.inJustDecodeBounds=true;
                        BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                        opts.inSampleSize=Math.max(1,Math.max(opts.outWidth/480,opts.outHeight/640));
                        opts.inJustDecodeBounds=false;
                        bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                        if(bitmap!=null && bytes.length<1024*1024)try(FileOutputStream f=new FileOutputStream(file)){f.write(bytes);}
                    }finally{conn.disconnect();}
                }
            }catch(Exception ignored){}
            if(bitmap!=null){
                bitmaps.put(url,bitmap);
                Bitmap finalImage=bitmap;
                ui.post(()->{if(url.equals(view.getTag()))view.setImageBitmap(finalImage);});
            }
        });
    }
    private String hash(String v){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(v.getBytes("UTF-8"));
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<12;i++)sb.append(String.format(java.util.Locale.US,"%02x",bytes[i]&0xff));
            return sb.toString();
        }catch(Exception e){return String.valueOf(v.hashCode());}
    }
}
