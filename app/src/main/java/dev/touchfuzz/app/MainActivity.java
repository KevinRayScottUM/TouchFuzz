package dev.touchfuzz.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String MODULE = "/data/adb/modules/touchfuzz";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView rootState, deviceInfo, values, status;
    private Spinner devices;
    private SeekBar xSlider, ySlider;
    private EditText xInput, yInput;
    private Switch link, advanced;
    private final List<Device> found = new ArrayList<>();
    private Device selected;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(32,36,32,48);
        scroll.addView(content); setContentView(scroll);
        LinearLayout header = flow();
        TextView headerTitle = new TextView(this); headerTitle.setText("TouchFuzz"); headerTitle.setTextSize(30); headerTitle.setTextColor(Color.rgb(47,43,54)); headerTitle.setPadding(8,10,8,10);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0,-2,1));
        Button update = button("Update"); update.setContentDescription("Apply selected values, save for boot, and refresh"); update.setOnClickListener(v -> apply(true)); header.addView(update);
        content.addView(header); label("Rooted Android touch micro-jitter tuner");
        rootState = card("Root not checked"); content.addView(rootState);
        devices = new Spinner(this); content.addView(devices);
        deviceInfo = card("Tap Refresh to detect touchscreens."); content.addView(deviceInfo);
        values = card("Original: —    Current: —    Boot: —"); content.addView(values);
        link = new Switch(this); link.setText("Link X and Y"); link.setChecked(true); content.addView(link);
        xSlider = slider(); xInput = number(); row("X fuzz", xSlider, xInput);
        ySlider = slider(); yInput = number(); row("Y fuzz", ySlider, yInput);
        advanced = new Switch(this); advanced.setText("Advanced device selection"); content.addView(advanced);
        label("Quick presets"); LinearLayout presets = flow(); HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        for (String p : new String[]{"Stock","4","6","8","9","15","20","25","30"}) {
            Button b = button(p); b.setOnClickListener(v -> preset(p)); presets.addView(b);
        } presetScroll.addView(presets); content.addView(presetScroll);
        LinearLayout actions = flow(); actions.setOrientation(LinearLayout.VERTICAL);
        addAction(actions,"Refresh",v -> refresh()); addAction(actions,"Apply Live",v -> apply(false));
        addAction(actions,"Save for Boot",v -> apply(true)); addAction(actions,"Restore Stock",v -> restore()); content.addView(actions);
        status = card("Ready"); content.addView(status);
        xSlider.setOnSeekBarChangeListener(listener(true)); ySlider.setOnSeekBarChangeListener(listener(false));
        devices.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos >= 0 && pos < found.size() && (advanced.isChecked() || found.get(pos).name.equals("fst2"))) select(found.get(pos));
            }});
        advanced.setOnCheckedChangeListener((b,c) -> populateDevices());
        setValues(9,9); refresh();
    }

    private void refresh() { status("Requesting root and scanning input devices…"); background(() -> {
        Result root = root("id"); if (!root.ok || !root.out.contains("uid=0")) { ui(() -> { rootState.setText("Root denied"); status("Root access is required."); }); return; }
        if (!root("test -x " + MODULE + "/system/bin/fuzzctl").ok) { ui(() -> { rootState.setText("Root granted"); status("TouchFuzz Magisk module/helper is missing. Install the module first."); }); return; }
        Result scan = root("for e in /dev/input/event*; do n=$(cat /sys/class/input/${e##*/}/device/name 2>/dev/null); x=$('"+MODULE+"/system/bin/fuzzctl' \"$e\" 0x35 2>/dev/null) || continue; y=$('"+MODULE+"/system/bin/fuzzctl' \"$e\" 0x36 2>/dev/null) || continue; echo \"DEV|$e|$n|$x|$y\"; done");
        List<Device> list = parse(scan.out); ui(() -> { rootState.setText("Root granted"); found.clear(); found.addAll(list); populateDevices(); status(list.isEmpty()?"No compatible multitouch touchscreen found.":"Detected " + list.size() + " compatible touchscreen device(s)."); });
    }); }

    private List<Device> parse(String text) { List<Device> out = new ArrayList<>(); for (String line : text.split("\\R")) {
        if (!line.startsWith("DEV|")) continue; String[] p=line.split("\\|",5); if(p.length<5)continue;
        Device d=new Device(); d.path=p[1]; d.name=p[2].isEmpty()?"Unknown":p[2]; d.x=parseMeta(p[3]); d.y=parseMeta(p[4]); out.add(d);
    } out.sort((a,b)->Boolean.compare(!a.name.equals("fst2"),!b.name.equals("fst2"))); return out; }
    private Meta parseMeta(String s) { Meta m=new Meta(); for(String t:s.split(" ")) { String[] kv=t.split("=",2); if(kv.length<2)continue; try { int v=Integer.parseInt(kv[1]); if(kv[0].equals("min"))m.min=v; if(kv[0].equals("max"))m.max=v; if(kv[0].equals("fuzz"))m.fuzz=v; } catch(Exception ignored){} } return m; }
    private void populateDevices() { List<String> names=new ArrayList<>(); List<Device> shown=new ArrayList<>(); for(Device d:found) if(advanced.isChecked()||d.name.equals("fst2")){shown.add(d);names.add(d.name+(d.name.equals("fst2")?" (Pixel Fold inner display)":"")+" — "+d.path);} if(names.isEmpty()&&!found.isEmpty())names.add("Enable advanced selection to view other devices"); devices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names)); if(!shown.isEmpty())select(shown.get(0)); }
    private void select(Device d) { selected=d; deviceInfo.setText(d.name+"\n"+d.path+"\nX range: "+d.x.min+"…"+d.x.max+"   Y range: "+d.y.min+"…"+d.y.max); setValues(d.x.fuzz,d.y.fuzz); loadProfile(d); }
    private void loadProfile(Device d) { background(() -> { Result r=root(". "+MODULE+"/config.conf 2>/dev/null; echo \"${DEVICE_NAME}|${FUZZ_X}|${FUZZ_Y}|${ORIGINAL_X}|${ORIGINAL_Y}\""); ui(() -> { String[] p=r.out.trim().split("\\|",-1); String original="Unknown", boot="Not saved"; if(p.length>=5&&p[0].equals(d.name)){if(!p[3].isEmpty()&&!p[4].isEmpty())original=p[3]+" / "+p[4];if(!p[1].isEmpty()&&!p[2].isEmpty())boot=p[1]+" / "+p[2];} values.setText("Original: "+original+"\nCurrent: "+d.x.fuzz+" / "+d.y.fuzz+"\nBoot: "+boot); }); }); }
    private void apply(boolean save) { Device d=selected; if(d==null){status("Select a device first.");return;} int x=read(xInput),y=link.isChecked()?x:read(yInput); setValues(x,y); status("Applying…"); background(() -> {
        String h=MODULE+"/system/bin/fuzzctl"; Result a;
        if(save) a=root("'"+MODULE+"/system/bin/touchfuzz-config' save '"+d.path+"' '"+d.name+"' "+x+" "+y);
        else a=root("'"+h+"' '"+d.path+"' 0x35 "+x+" && '"+h+"' '"+d.path+"' 0x36 "+y);
        boolean ok=a.ok; String msg=ok?(save?"Saved for boot and applied successfully.":"Applied live successfully."):"Operation failed: "+a.out; ui(() -> {status(msg); if(ok)refresh();}); }); }
    private void restore() { Device d=selected;if(d==null){status("Select a device first.");return;} status("Restoring original values…");background(() -> {Result r=root("'"+MODULE+"/system/bin/touchfuzz-config' restore '"+d.path+"' '"+d.name+"'");ui(() -> {status(r.ok?"Original values restored.":"Original values are unknown; restore was not performed. "+r.out);if(r.ok)refresh();});}); }

    private void preset(String p){if(p.equals("Stock")){restore();return;}int v=Integer.parseInt(p);setValues(v,v);} private void setValues(int x,int y){x=Math.max(0,Math.min(40,x));y=Math.max(0,Math.min(40,y));xSlider.setProgress(x);ySlider.setProgress(y);xInput.setText(String.valueOf(x));yInput.setText(String.valueOf(y));}
    private SeekBar.OnSeekBarChangeListener listener(boolean x){return new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int v,boolean u){if(!u)return;(x?xInput:yInput).setText(String.valueOf(v));if(x&&link.isChecked()){ySlider.setProgress(v);yInput.setText(String.valueOf(v));}}};}
    private int read(EditText e){try{return Math.max(0,Math.min(40,Integer.parseInt(e.getText().toString())));}catch(Exception ex){return 9;}}
    private Result root(String command){try{Process p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();String out=readAll(p.getInputStream());int code=p.waitFor();return new Result(code==0,out.trim());}catch(Exception e){return new Result(false,e.getMessage());}}
    private String readAll(InputStream in)throws IOException{BufferedReader b=new BufferedReader(new InputStreamReader(in));StringBuilder s=new StringBuilder();String l;while((l=b.readLine())!=null)s.append(l).append('\n');return s.toString();}
    private void background(Runnable r){new Thread(r).start();}private void ui(Runnable r){ui.post(r);}private void status(String s){status.setText(s);Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void title(String s){TextView v=label(s);v.setTextSize(30);v.setTextColor(Color.rgb(47,43,54));}private TextView label(String s){TextView v=new TextView(this);v.setText(s);v.setTextSize(15);v.setPadding(8,10,8,10);content.addView(v);return v;}
    private TextView card(String s){TextView v=new TextView(this);v.setText(s);v.setTextSize(16);v.setPadding(24,22,24,22);v.setBackgroundColor(Color.rgb(234,221,255));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,12,0,12);v.setLayoutParams(p);return v;}
    private SeekBar slider(){SeekBar b=new SeekBar(this);b.setMax(40);return b;}private EditText number(){EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setEms(3);return e;}
    private void row(String name,SeekBar b,EditText e){LinearLayout r=flow();TextView n=new TextView(this);n.setText(name);n.setGravity(Gravity.CENTER_VERTICAL);r.addView(n,new LinearLayout.LayoutParams(160,-2));r.addView(b,new LinearLayout.LayoutParams(0,-2,1));r.addView(e);content.addView(r);}private LinearLayout flow(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}private Button button(String s){Button b=new Button(this);b.setText(s);return b;}private void addAction(LinearLayout l,String s,View.OnClickListener c){Button b=button(s);b.setOnClickListener(c);l.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
    static class Meta{int min,max,fuzz;}static class Device{String path,name;Meta x,y;}static class Result{boolean ok;String out;Result(boolean o,String s){ok=o;out=s==null?"":s;}}
}
