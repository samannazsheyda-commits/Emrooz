package com.personal.jamestv9;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.content.Intent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.Map;
import java.util.HashMap;
import android.net.Uri;

@androidx.media3.common.util.UnstableApi
public final class CinemaPlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView status;
    private LinearLayout ribbon;
    private long lastBack;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private void fullScreen(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(12));return b;}
    private TextView action(String txt,Runnable click) {
        TextView t=new TextView(this);t.setText(txt);t.setTextSize(16);t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);t.setPadding(dp(17),dp(9),dp(17),dp(9));
        t.setFocusable(true);t.setClickable(true);t.setBackground(background(0xcc171a24));
        t.setOnClickListener(v->click.run());
        t.setOnFocusChangeListener((v,f)->{
            GradientDrawable b=background(f?0xffb81d32:0xcc171a24);
            if(f)b.setStroke(dp(2),Color.WHITE);
            v.setBackground(b);v.animate().scaleX(f?1.05f:1f).scaleY(f?1.05f:1f).setDuration(100).start();
        });
        return t;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);fullScreen();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        playerView=new PlayerView(this);playerView.setUseController(true);
        playerView.setControllerAutoShow(true);playerView.setControllerShowTimeoutMs(4500);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setPadding(dp(28),dp(10),dp(28),dp(10));
        overlay.setOrientation(LinearLayout.VERTICAL);overlay.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable grad=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
               new int[]{0xe9000000,0x42000000,0x00000000});
        overlay.setBackground(grad);
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,dp(130),Gravity.TOP);
        root.addView(overlay,top);
        String title=getIntent().getStringExtra("name");
        TextView caption=new TextView(this);caption.setText(title==null?"JAMES CINEMA":title);
        caption.setTextColor(Color.WHITE);caption.setTextSize(23);caption.setTypeface(null,1);
        overlay.addView(caption,new LinearLayout.LayoutParams(-1,dp(54)));
        ribbon=new LinearLayout(this);ribbon.setOrientation(LinearLayout.HORIZONTAL);
        overlay.addView(ribbon);
        ribbon.addView(action("↩ بازگشت",()->finish()));
        ribbon.addView(action("↶ ۱۰ ثانیه",()->seek(-10000)));
        ribbon.addView(action("⏯ پخش/توقف",()->toggle()));
        ribbon.addView(action("۱۰ ثانیه ↷",()->seek(10000)));
        ribbon.getChildAt(0).requestFocus();
        TextView exit=action("⏻  خروج از برنامه",this::confirmExit);
        exit.setTextSize(13);
        exit.setContentDescription("خروج از برنامه");
        FrameLayout.LayoutParams exitPos=new FrameLayout.LayoutParams(-2,dp(43),Gravity.TOP|Gravity.RIGHT);
        exitPos.setMargins(0,dp(11),dp(12),0);
        root.addView(exit,exitPos);exit.bringToFront();
        status=new TextView(this);status.setTextColor(0xfff1f1f1);status.setTextSize(17);
        status.setPadding(dp(30),dp(20),dp(30),dp(20));status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        root.addView(status,sp);status.setText("در حال آماده‌سازی ویدئو ...");
        String uri=getIntent().getStringExtra("stream");
        if(uri==null||(!uri.startsWith("https://")&&!uri.startsWith("http://"))){
            status.setText("لینک پخش معتبر پیدا نشد.");
            setResult(RESULT_CANCELED);return;
        }
        String cookie=getIntent().getStringExtra("cookie");
        String referer=getIntent().getStringExtra("referer");
        Map<String,String> headers=new HashMap<>();
        if(cookie!=null&&!cookie.isEmpty())headers.put("Cookie",cookie);
        if(referer!=null&&!referer.isEmpty())headers.put("Referer",referer);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
             .setAllowCrossProtocolRedirects(true)
             .setDefaultRequestProperties(headers)
             .setConnectTimeoutMs(13000).setReadTimeoutMs(13000);
        DefaultDataSource.Factory dataSource=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this)
          .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource)).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY){status.setVisibility(View.GONE);}
                if(state==Player.STATE_ENDED){status.setText("پایان پخش");status.setVisibility(View.VISIBLE);}
            }
            @Override public void onPlayerError(PlaybackException error){
                status.setText("این ویدئو با پلیر مستقل سازگار نیست. دکمه بازگشت را بزن و پخش سایت را انتخاب کن.");
                status.setVisibility(View.VISIBLE);
                setResult(RESULT_CANCELED);
            }
        });
        MediaItem.Builder item=new MediaItem.Builder().setUri(Uri.parse(uri));
        String l=uri.toLowerCase(java.util.Locale.US);
        if(l.contains(".m3u8"))item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if(l.contains(".mpd"))item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());
        player.prepare();player.setPlayWhenReady(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void confirmExit(){
        new android.app.AlertDialog.Builder(this)
            .setTitle("خروج از James Cinema")
            .setMessage("می‌خواهی پخش و برنامه را ببندی؟")
            .setPositiveButton("خروج", (dialog,which)->{
                if(player!=null){player.stop();player.release();player=null;}
                finishAffinity();
            })
            .setNegativeButton("انصراف", (dialog,which)->{}).show();
    }
    private void seek(long offset){
        if(player!=null)player.seekTo(Math.max(0,player.getCurrentPosition()+offset));
    }
    private void toggle(){
        if(player!=null)player.setPlayWhenReady(!player.getPlayWhenReady());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0){
            switch(e.getKeyCode()){
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_DPAD_CENTER:
                    if(e.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){toggle();return true;} break;
                case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-10000);return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(10000);return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onPause(){if(player!=null)player.pause();super.onPause();}
    @Override protected void onResume(){super.onResume();fullScreen();}
    @Override protected void onDestroy(){
        if(player!=null){player.release();player=null;}
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
