package com.nameemrooz.journal;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Collections;

public final class StreamingRecognitionService extends RecognitionService {
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override protected void onStartListening(Intent intent, Callback callback) {
        StreamingScript.format=intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING);
        int cycle=++StreamingScript.cycle;
        if ("stall".equals(StreamingScript.mode) && cycle==1) return;
        String value;
        if ("interrupted".equals(StreamingScript.mode)) {
            String[] parts={"بخش نخست","بخش دوم","بخش سوم","نتیجه نهایی"};
            value=cycle<=parts.length?parts[cycle-1]:"ادامه نوشته";
        } else if ("long".equals(StreamingScript.mode)) {
            StringBuilder out=new StringBuilder();
            for(int i=0;i<3;i++)out.append("این بخش از یک یادداشت طولانی است و باید بدون توقف حفظ شود ");
            out.append("پایان بخش ").append(persianNumber(cycle));value=out.toString();
        } else value="امروز با میثم قدم زدم";
        final String text=value;
        try { callback.readyForSpeech(new Bundle());callback.beginningOfSpeech(); } catch(Exception ignored) {}
        if(!"network".equals(StreamingScript.mode)) main.postDelayed(()->{try {callback.partialResults(results(text));}catch(Exception ignored){}},25);
        main.postDelayed(()->{try {
            if("network".equals(StreamingScript.mode))callback.error(SpeechRecognizer.ERROR_NETWORK);
            else if("interrupted".equals(StreamingScript.mode)&&cycle<=3)callback.error(SpeechRecognizer.ERROR_NO_MATCH);
            else callback.results(results(text));
        }catch(Exception ignored){}},75);
    }
    @Override protected void onStopListening(Callback callback) {
        main.postDelayed(()->{try{callback.error(SpeechRecognizer.ERROR_CLIENT);}catch(Exception ignored){}},30);
    }
    @Override protected void onCancel(Callback callback) { StreamingScript.cancellations++; }
    private static Bundle results(String text){Bundle out=new Bundle();out.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Collections.singletonList(text)));return out;}
    private static String persianNumber(int value){StringBuilder out=new StringBuilder();for(char c:Integer.toString(value).toCharArray())out.append(c>='0'&&c<='9'?(char)('۰'+(c-'0')):c);return out.toString();}
}
