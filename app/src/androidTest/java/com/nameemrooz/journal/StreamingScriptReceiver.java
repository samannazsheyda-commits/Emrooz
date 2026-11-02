package com.nameemrooz.journal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
public final class StreamingScriptReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("configure".equals(intent.getStringExtra("action"))) {
            StreamingScript.mode=intent.getStringExtra("mode");
            StreamingScript.cycle=0;
            StreamingScript.format="";
        }
        Bundle result=new Bundle();result.putString("format",StreamingScript.format);result.putInt("cycles",StreamingScript.cycle);
        setResultExtras(result);
    }
}
