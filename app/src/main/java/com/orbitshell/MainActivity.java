package com.orbitshell;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.content.ClipData;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int REQ_WIDGET_PICK = 7001, REQ_WIDGET_CONFIG = 7002, REQ_PHOTO = 7003;
    final String PREFS = "orbit_shell";
    final int ORANGE = Color.rgb(255,107,26);
    SharedPreferences prefs;
    PackageManager pm;
    AppWidgetManager widgetManager;
    AppWidgetHost widgetHost;
    LinearLayout root, pagesRow, dock;
    TextView clock, date, battery, pageDots;
    EditText search;
    HorizontalScrollView pageScroll;
    ArrayList<ArrayList<Item>> pages = new ArrayList<>();
    ArrayList<String> dockPkgs = new ArrayList<>();
    HashSet<String> hidden = new HashSet<>();
    ArrayList<AppInfo> apps = new ArrayList<>();
    int currentPage = 0, columns = 5, rows = 6, iconScale = 100;
    boolean labels = true, glass = true;
    String accent = "#FF6B1A";
    boolean editMode = false;
    int dragPage = -1, dragIndex = -1;

    static class AppInfo { String pkg, label; AppInfo(String p,String l){pkg=p;label=l;} }
    static class Item {
        String type="app", pkg="", title="", uri=""; int widgetId=-1; ArrayList<String> folder=new ArrayList<>();
        static Item app(String p){ Item i=new Item(); i.pkg=p; return i; }
        static Item photo(String u,String t){ Item i=new Item(); i.type="photo";i.uri=u;i.title=t;return i; }
        static Item widget(int id){ Item i=new Item();i.type="widget";i.widgetId=id;return i; }
        static Item folder(ArrayList<String> p,String t){Item i=new Item();i.type="folder";i.folder.addAll(p);i.title=t;return i;}
    }

    @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(Color.rgb(8,9,11));getWindow().setNavigationBarColor(Color.rgb(8,9,11));
        pm=getPackageManager(); prefs=getSharedPreferences(PREFS,0); widgetManager=AppWidgetManager.getInstance(this); widgetHost=new AppWidgetHost(this,4200);
        load(); discoverApps(); buildUi(); widgetHost.startListening(); }
    @Override protected void onDestroy(){try{widgetHost.stopListening();}catch(Exception ignored){} super.onDestroy();}
    @Override public void onBackPressed(){ if(editMode){setEdit(false);return;} if(currentPage!=0){goPage(0);return;} super.onBackPressed(); }

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    GradientDrawable bg(int color,float r,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));if(strokeColor!=0)g.setStroke(dp(1),strokeColor);return g;}
    int accentColor(){try{return Color.parseColor(accent);}catch(Exception e){return ORANGE;}}
    int textColor(){return Color.WHITE;}
    int secondary(){return Color.rgb(170,174,182);}

    void buildUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(10));root.setBackgroundColor(Color.rgb(8,9,11));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(4),0,dp(4),dp(6));
        clock=label("09:41",22,Color.WHITE);clock.setTypeface(null,1);top.addView(clock,new LinearLayout.LayoutParams(0,dp(38),1));
        battery=label("100%",14,secondary());battery.setGravity(Gravity.CENTER_VERTICAL);top.addView(battery,new LinearLayout.LayoutParams(dp(60),dp(38)));
        TextView add=button("+",26);add.setOnClickListener(v->showAddMenu());top.addView(add,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView set=button("⋯",24);set.setOnClickListener(v->showSettings());top.addView(set,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(top);
        date=label("Orbit Shell",14,secondary());date.setPadding(dp(4),0,0,dp(8));root.addView(date,new LinearLayout.LayoutParams(-1,dp(28)));

        pageScroll=new HorizontalScrollView(this);pageScroll.setHorizontalScrollBarEnabled(false);pageScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pagesRow=new LinearLayout(this);pagesRow.setOrientation(LinearLayout.HORIZONTAL);pageScroll.addView(pagesRow,new HorizontalScrollView.LayoutParams(-1,-1));root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));
        pageScroll.setOnScrollChangeListener((v,sx,sy,osx,osy)->{int w=Math.max(1,pageScroll.getWidth());int p=Math.min(pages.size()-1,Math.max(0,Math.round((float)sx/w)));if(p!=currentPage){currentPage=p;updateDots();}});
        pageDots=label("●",12,accentColor());pageDots.setGravity(Gravity.CENTER);root.addView(pageDots,new LinearLayout.LayoutParams(-1,dp(30)));
        dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);dock.setPadding(dp(8),dp(7),dp(8),dp(7));dock.setBackground(bg(Color.argb(190,28,31,37),30,Color.argb(90,255,255,255)));root.addView(dock,new LinearLayout.LayoutParams(-1,dp(72)));
        rebuildPages();rebuildDock();updateClock();
        root.postDelayed(new Runnable(){public void run(){updateClock();root.postDelayed(this,1000);}},1000);
    }
    TextView label(String s,int sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    TextView button(String s,int sp){TextView t=label(s,sp,Color.WHITE);t.setGravity(Gravity.CENTER);t.setBackground(bg(Color.argb(100,45,48,54),22,Color.argb(80,255,255,255)));return t;}
    void updateClock(){Calendar c=Calendar.getInstance();clock.setText(String.format(Locale.getDefault(),"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)));date.setText(String.format(Locale.getDefault(),"%1$tA, %1$td %1$tB  •  %2$s",c,new Date()));battery.setText(getBattery());}
    String getBattery(){Intent i=registerReceiver(null,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));int l=i==null?100:i.getIntExtra("level",100);return l+"%";}

    void discoverApps(){apps.clear();Intent it=new Intent(Intent.ACTION_MAIN);it.addCategory(Intent.CATEGORY_LAUNCHER);for(android.content.pm.ResolveInfo r:pm.queryIntentActivities(it,0)){String p=r.activityInfo.packageName;if(p.equals(getPackageName())||hidden.contains(p))continue;CharSequence n=r.loadLabel(pm);apps.add(new AppInfo(p,n==null?p:n.toString()));}Collections.sort(apps,(a,b)->a.label.compareToIgnoreCase(b.label));}
    Intent launchIntent(String pkg){Intent i=pm.getLaunchIntentForPackage(pkg);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);return i;}return null;}
    Drawable icon(String pkg){try{ApplicationInfo a=pm.getApplicationInfo(pkg,0);return pm.getApplicationIcon(a);}catch(Exception e){return getDrawable(android.R.drawable.sym_def_app_icon);}}

    void rebuildPages(){pagesRow.removeAllViews();for(int p=0;p<pages.size();p++){GridLayout g=new GridLayout(this);g.setColumnCount(columns);g.setRowCount(rows);g.setUseDefaultMargins(false);g.setPadding(dp(4),dp(8),dp(4),dp(8));for(int x=0;x<columns*rows;x++){FrameLayout cell=new FrameLayout(this);cell.setPadding(dp(4),dp(4),dp(4),dp(4));cell.setTag("cell:"+p+":"+x);cell.setOnDragListener((v,e)->onCellDrag(v,e));if(x<pages.get(p).size())cell.addView(itemView(p,x,pages.get(p).get(x)));g.addView(cell,new GridLayout.LayoutParams(new ViewGroup.LayoutParams(0,0)){{columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);rowSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);}});}pagesRow.addView(g,new LinearLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels-dp(32),-1));}updateDots();}
    View itemView(int p,int idx,Item it){
        if("photo".equals(it.type)){
            FrameLayout card=new FrameLayout(this);
            card.setBackground(bg(Color.argb(95,38,41,47),24,Color.argb(55,255,255,255)));
            ImageView photo=new ImageView(this); photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try{photo.setImageURI(Uri.parse(it.uri));}catch(Exception ignored){}
            card.addView(photo,new FrameLayout.LayoutParams(-1,-1));
            TextView cap=label(itemTitle(it),10,Color.WHITE);cap.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cap.setPadding(dp(6),0,dp(6),dp(7));
            cap.setBackground(new android.graphics.drawable.GradientDrawable(){ {setColor(Color.TRANSPARENT);} });
            card.addView(cap,new FrameLayout.LayoutParams(-1,dp(28),Gravity.BOTTOM));
            card.setOnClickListener(v->showPhoto(it));
            card.setOnLongClickListener(v->{dragPage=p;dragIndex=idx;showItemMenu(p,idx,it);return true;});
            return card;
        }
        if("widget".equals(it.type)){AppWidgetHostView hv=null;try{hv=widgetHost.createView(this,it.widgetId,widgetManager.getAppWidgetInfo(it.widgetId));}catch(Exception ignored){}if(hv!=null){hv.setTag(p+":"+idx);hv.setOnLongClickListener(v->{dragPage=p;dragIndex=idx;return v.startDragAndDrop(ClipData.newPlainText("orbit",p+":"+idx),new View.DragShadowBuilder(v),v,View.DRAG_FLAG_GLOBAL);});FrameLayout wrap=new FrameLayout(this);wrap.setBackground(bg(Color.argb(120,25,28,34),24,Color.argb(55,255,255,255)));wrap.addView(hv,new FrameLayout.LayoutParams(-1,-1));return wrap;}}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(2),dp(2),dp(2),dp(2));box.setBackground(bg(glass?Color.argb(80,38,41,47):Color.TRANSPARENT,20,Color.argb(40,255,255,255)));
        ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_INSIDE);im.setImageDrawable(itemIcon(it));int sz=(int)(dp(48)*iconScale/100f);box.addView(im,new LinearLayout.LayoutParams(sz,sz));
        if(labels){TextView t=label(itemTitle(it),11,Color.WHITE);t.setGravity(Gravity.CENTER);t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(t,new LinearLayout.LayoutParams(-1,dp(22)));}
        box.setOnClickListener(v->{if("folder".equals(it.type))openFolder(p,idx);else if("photo".equals(it.type))showPhoto(it);else if("app".equals(it.type)){Intent li=launchIntent(it.pkg);if(li!=null)startActivity(li);}});
        box.setOnLongClickListener(v->{dragPage=p;dragIndex=idx;showItemMenu(p,idx,it);return true;});
        return box;
    }
    Drawable itemIcon(Item it){if("app".equals(it.type))return icon(it.pkg);if("folder".equals(it.type))return getDrawable(android.R.drawable.ic_menu_sort_by_size);if("photo".equals(it.type)){try{android.graphics.drawable.Drawable d=android.graphics.drawable.Drawable.createFromStream(getContentResolver().openInputStream(Uri.parse(it.uri)),"photo");return d;}catch(Exception ignored){}}return getDrawable(android.R.drawable.ic_menu_gallery);}
    String itemTitle(Item it){if("app".equals(it.type)){try{return pm.getApplicationLabel(pm.getApplicationInfo(it.pkg,0)).toString();}catch(Exception e){return it.pkg;}}if("folder".equals(it.type))return it.title.length()==0?"Folder":it.title;return it.title.length()==0?"Photo":it.title;}
    boolean onCellDrag(View v,android.view.DragEvent e){if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return e.getClipDescription()!=null&&e.getClipDescription().hasMimeType("text/plain");if(e.getAction()==DragEvent.ACTION_DROP){String[] a=e.getClipData().getItemAt(0).getText().toString().split(":");int sp=Integer.parseInt(a[0]),si=Integer.parseInt(a[1]);String tag=(String)v.getTag();String[] b=tag.split(":");int tp=Integer.parseInt(b[1]),ti=Integer.parseInt(b[2]);moveItem(sp,si,tp,ti);return true;}return true;}
    void moveItem(int sp,int si,int tp,int ti){if(sp<0||sp>=pages.size()||si<0||si>=pages.get(sp).size())return;Item moving=pages.get(sp).remove(si);if(sp==tp&&si<ti)ti--;if(tp<0||tp>=pages.size())tp=0;ArrayList<Item> dest=pages.get(tp);if(ti<dest.size()&&"app".equals(dest.get(ti).type)&&"app".equals(moving.type)){ArrayList<String> f=new ArrayList<>();f.add(dest.get(ti).pkg);f.add(moving.pkg);dest.set(ti,Item.folder(f,"Folder"));}else{ti=Math.max(0,Math.min(ti,dest.size()));dest.add(ti,moving);}save();rebuildPages();}

    void showItemMenu(int p,int idx,Item it){String[] opts={"Удалить","Переименовать","Переместить на страницу","Отмена"};new AlertDialog.Builder(this).setTitle(itemTitle(it)).setItems(opts,(d,w)->{if(w==0){if("widget".equals(it.type))try{widgetHost.deleteAppWidgetId(it.widgetId);}catch(Exception ignored){}pages.get(p).remove(idx);if(pages.get(p).isEmpty()&&pages.size()>1)pages.remove(p);save();rebuildPages();}else if(w==1){final EditText e=new EditText(this);e.setText(itemTitle(it));new AlertDialog.Builder(this).setTitle("Название").setView(e).setPositiveButton("Сохранить",(x,y)->{it.title=e.getText().toString();save();rebuildPages();}).setNegativeButton("Отмена",null).show();}else if(w==2){showMoveDialog(p,idx);}}).show();}
    void showMoveDialog(int p,int idx){String[] a=new String[pages.size()];for(int i=0;i<a.length;i++)a[i]="Рабочий стол "+(i+1);new AlertDialog.Builder(this).setTitle("Переместить").setItems(a,(d,w)->{Item it=pages.get(p).remove(idx);pages.get(w).add(it);save();rebuildPages();}).setNegativeButton("Отмена",null).show();}
    void openFolder(int p,int idx){Item f=pages.get(p).get(idx);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);for(String pkg:f.folder){TextView t=label("  "+appLabel(pkg),16,Color.WHITE);t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setOnClickListener(v->{Intent i=launchIntent(pkg);if(i!=null)startActivity(i);});box.addView(t);}new AlertDialog.Builder(this).setTitle(f.title.length()==0?"Folder":f.title).setView(box).setPositiveButton("Закрыть",null).setNeutralButton("Переименовать",(d,w)->{final EditText e=new EditText(this);e.setText(f.title);new AlertDialog.Builder(this).setTitle("Название папки").setView(e).setPositiveButton("Сохранить",(x,y)->{f.title=e.getText().toString();save();rebuildPages();}).show();}).show();}
    String appLabel(String p){try{return pm.getApplicationLabel(pm.getApplicationInfo(p,0)).toString();}catch(Exception e){return p;}}

    void rebuildDock(){dock.removeAllViews();for(String p:dockPkgs){if(p.length()==0)continue;ImageButton b=new ImageButton(this);b.setImageDrawable(icon(p));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setBackgroundColor(Color.TRANSPARENT);b.setOnClickListener(v->{Intent i=launchIntent(p);if(i!=null)startActivity(i);});b.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(appLabel(p)).setItems(new String[]{"Убрать из Dock","Отмена"},(d,w)->{if(w==0){dockPkgs.remove(p);save();rebuildDock();}}).show();return true;});dock.addView(b,new LinearLayout.LayoutParams(0,dp(56),1));}}

    void showAddMenu(){String[] x={"Приложение","Виджет Android","Фотография","Новая страница","Настройки"};new AlertDialog.Builder(this).setTitle("Добавить на экран").setItems(x,(d,w)->{if(w==0)showAppPicker();else if(w==1)addWidget();else if(w==2)addPhoto();else if(w==3){pages.add(new ArrayList<>());save();rebuildPages();goPage(pages.size()-1);}else showSettings();}).show();}
    void showAppPicker(){
        final ArrayList<AppInfo> all=new ArrayList<>(apps);
        EditText q=new EditText(this);q.setHint("Поиск приложения");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(q,new LinearLayout.LayoutParams(-1,dp(52)));
        ListView list=new ListView(this);box.addView(list,new LinearLayout.LayoutParams(-1,dp(430)));
        ArrayAdapter<String> ad=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1){
            ArrayList<AppInfo> shown=new ArrayList<>(all);
            @Override public int getCount(){return shown.size();}
            @Override public String getItem(int p){return shown.get(p).label;}
            void filter(String z){shown.clear();String q=z.toLowerCase(Locale.ROOT);for(AppInfo ai:all)if(ai.label.toLowerCase(Locale.ROOT).contains(q))shown.add(ai);notifyDataSetChanged();}
            AppInfo app(int p){return shown.get(p);}
        };
        list.setAdapter(ad);
        q.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){ad.filter(s.toString());}public void afterTextChanged(android.text.Editable e){}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Приложения").setView(box).setNegativeButton("Закрыть",null).create();
        list.setOnItemClickListener((parent,v,pos,id)->{String pkg=ad.app(pos).pkg;pages.get(currentPage).add(Item.app(pkg));save();rebuildPages();dialog.dismiss();});
        list.setOnItemLongClickListener((parent,v,pos,id)->{String pkg=ad.app(pos).pkg;if(dockPkgs.size()<7&&!dockPkgs.contains(pkg)){dockPkgs.add(pkg);save();rebuildDock();Toast.makeText(this,"Добавлено в Dock",Toast.LENGTH_SHORT).show();}return true;});
        dialog.show();
    }

    void addWidget(){int id=widgetHost.allocateAppWidgetId();Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);startActivityForResult(i,REQ_WIDGET_PICK);}
    void addPhoto(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_PHOTO);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);
        if(c!=RESULT_OK||d==null)return;
        if(r==REQ_PHOTO){Uri u=d.getData();if(u!=null){try{getContentResolver().takePersistableUriPermission(u,d.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}pages.get(currentPage).add(Item.photo(u.toString(),"Photo"));save();rebuildPages();}}
        else if(r==REQ_WIDGET_PICK||r==REQ_WIDGET_CONFIG){int id=d.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);if(id>0){if(r==REQ_WIDGET_PICK){AppWidgetProviderInfo info=widgetManager.getAppWidgetInfo(id);if(info!=null&&info.configure!=null){Intent ci=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);ci.setComponent(info.configure);ci.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);startActivityForResult(ci,REQ_WIDGET_CONFIG);return;}}pages.get(currentPage).add(Item.widget(id));save();rebuildPages();}}
        else if(r==8001){Uri u=d.getData();if(u!=null){try{String json=prefs.getString("pending_export",saveJson().toString());try(OutputStream out=getContentResolver().openOutputStream(u)){out.write(json.getBytes("UTF-8"));}Toast.makeText(this,"Резервная копия сохранена",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Ошибка экспорта",Toast.LENGTH_SHORT).show();}}}
        else if(r==8002){Uri u=d.getData();if(u!=null){try(InputStream in=getContentResolver().openInputStream(u);BufferedReader br=new BufferedReader(new InputStreamReader(in,"UTF-8"))){StringBuilder z=new StringBuilder();String line;while((line=br.readLine())!=null)z.append(line);prefs.edit().putString("state",z.toString()).apply();load();discoverApps();rebuildPages();rebuildDock();Toast.makeText(this,"Резервная копия восстановлена",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Ошибка импорта",Toast.LENGTH_SHORT).show();}}}
    }

    void showPhoto(Item it){ImageView im=new ImageView(this);im.setImageDrawable(itemIcon(it));im.setScaleType(ImageView.ScaleType.CENTER_INSIDE);new AlertDialog.Builder(this).setTitle(it.title).setView(im).setPositiveButton("Закрыть",null).show();}
    void showSettings(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(4),dp(16),dp(4));String[] opts={"Сетка: "+columns+" × "+rows,"Масштаб иконок: "+iconScale+"%","Подписи: "+(labels?"Вкл":"Выкл"),"Стиль карточек: "+(glass?"Glass":"Solid"),"Акцент: "+accent,"Управление Dock","Скрытые приложения","Добавить страницу","Удалить текущую страницу","Экспорт настроек","Импорт настроек","Системные настройки лаунчера"};ListView l=new ListView(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,opts);l.setAdapter(a);box.addView(l,new LinearLayout.LayoutParams(-1,dp(520)));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Orbit Shell").setView(box).setNegativeButton("Готово",null).create();l.setOnItemClickListener((pa,v,pos,id)->{dialog.dismiss();switch(pos){case 0:changeGrid();break;case 1:changeScale();break;case 2:labels=!labels;save();rebuildPages();showSettings();break;case 3:glass=!glass;save();rebuildPages();showSettings();break;case 4:changeAccent();break;case 5:customDock();break;case 6:hideApps();break;case 7:pages.add(new ArrayList<>());save();rebuildPages();goPage(pages.size()-1);break;case 8:if(pages.size()>1){pages.remove(currentPage);currentPage=Math.max(0,currentPage-1);save();rebuildPages();}break;case 9:exportSettings();break;case 10:importSettings();break;case 11:startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));break;}});dialog.show();}
    void changeGrid(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);NumberPicker c=new NumberPicker(this);c.setMinValue(3);c.setMaxValue(8);c.setValue(columns);NumberPicker r=new NumberPicker(this);r.setMinValue(4);r.setMaxValue(10);r.setValue(rows);b.addView(label("Колонки",14,secondary()));b.addView(c);b.addView(label("Строки",14,secondary()));b.addView(r);new AlertDialog.Builder(this).setTitle("Сетка").setView(b).setPositiveButton("Применить",(d,w)->{columns=c.getValue();rows=r.getValue();save();rebuildPages();}).setNegativeButton("Отмена",null).show();}
    void changeScale(){NumberPicker n=new NumberPicker(this);n.setMinValue(70);n.setMaxValue(140);n.setValue(iconScale);new AlertDialog.Builder(this).setTitle("Масштаб иконок").setView(n).setPositiveButton("Сохранить",(d,w)->{iconScale=n.getValue();save();rebuildPages();}).setNegativeButton("Отмена",null).show();}
    void changeAccent(){String[] c={"Оранжевый","Синий","Фиолетовый","Зелёный","Белый"};new AlertDialog.Builder(this).setTitle("Акцент").setItems(c,(d,w)->{String[] v={"#FF6B1A","#4DA3FF","#A66CFF","#55D68A","#FFFFFF"};accent=v[w];save();rebuildPages();});}
    void customDock(){showAppPicker();}
    void hideApps(){String[] n=new String[apps.size()];for(int i=0;i<apps.size();i++)n[i]=apps.get(i).label;boolean[] checked=new boolean[apps.size()];for(int i=0;i<apps.size();i++)checked[i]=hidden.contains(apps.get(i).pkg);new AlertDialog.Builder(this).setTitle("Скрыть приложения").setMultiChoiceItems(n,checked,(d,w,c)->{String p=apps.get(w).pkg;if(c)hidden.add(p);else hidden.remove(p);}).setPositiveButton("Сохранить",(d,w)->{save();discoverApps();}).setNegativeButton("Отмена",null).show();}
    void setEdit(boolean e){editMode=e;Toast.makeText(this,e?"Режим редактирования":"Готово",Toast.LENGTH_SHORT).show();}
    void goPage(int p){currentPage=Math.max(0,Math.min(p,pages.size()-1));pageScroll.post(()->pageScroll.smoothScrollTo(currentPage*pageScroll.getWidth(),0));updateDots();}
    void updateDots(){if(pageDots==null)return;StringBuilder s=new StringBuilder();for(int i=0;i<pages.size();i++)s.append(i==currentPage?"● ":"○ ");pageDots.setText(s.toString());pageDots.setTextColor(accentColor());}

    void exportSettings(){try{String json=saveJson().toString(2);Intent s=new Intent(Intent.ACTION_CREATE_DOCUMENT);s.setType("application/json");s.putExtra(Intent.EXTRA_TITLE,"orbit-shell-backup.json");startActivityForResult(s,8001);prefs.edit().putString("pending_export",json).apply();}catch(Exception e){Toast.makeText(this,"Не удалось подготовить экспорт",Toast.LENGTH_SHORT).show();}}
    void importSettings(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,8002);}
    void save(){prefs.edit().putString("state",saveJson().toString()).apply();}
    JSONObject saveJson(){JSONObject o=new JSONObject();try{o.put("columns",columns);o.put("rows",rows);o.put("iconScale",iconScale);o.put("labels",labels);o.put("glass",glass);o.put("accent",accent);JSONArray d=new JSONArray();for(String p:dockPkgs)d.put(p);o.put("dock",d);JSONArray h=new JSONArray();for(String p:hidden)h.put(p);o.put("hidden",h);JSONArray ps=new JSONArray();for(ArrayList<Item> list:pages){JSONArray ar=new JSONArray();for(Item it:list){JSONObject x=new JSONObject();x.put("type",it.type);x.put("pkg",it.pkg);x.put("title",it.title);x.put("uri",it.uri);x.put("widgetId",it.widgetId);JSONArray f=new JSONArray();for(String p:it.folder)f.put(p);x.put("folder",f);ar.put(x);}ps.put(ar);}o.put("pages",ps);}catch(Exception ignored){}return o;}
    void load(){pages.clear();String s=prefs==null?null:prefs.getString("state",null);if(s==null){pages.add(new ArrayList<>());return;}try{JSONObject o=new JSONObject(s);columns=o.optInt("columns",5);rows=o.optInt("rows",6);iconScale=o.optInt("iconScale",100);labels=o.optBoolean("labels",true);glass=o.optBoolean("glass",true);accent=o.optString("accent","#FF6B1A");JSONArray d=o.optJSONArray("dock");if(d!=null)for(int i=0;i<d.length();i++)dockPkgs.add(d.getString(i));JSONArray h=o.optJSONArray("hidden");if(h!=null)for(int i=0;i<h.length();i++)hidden.add(h.getString(i));JSONArray ps=o.optJSONArray("pages");if(ps!=null)for(int p=0;p<ps.length();p++){ArrayList<Item> list=new ArrayList<>();JSONArray ar=ps.getJSONArray(p);for(int j=0;j<ar.length();j++){JSONObject x=ar.getJSONObject(j);Item it=new Item();it.type=x.optString("type","app");it.pkg=x.optString("pkg","");it.title=x.optString("title","");it.uri=x.optString("uri","");it.widgetId=x.optInt("widgetId",-1);JSONArray f=x.optJSONArray("folder");if(f!=null)for(int k=0;k<f.length();k++)it.folder.add(f.getString(k));list.add(it);}pages.add(list);}if(pages.isEmpty())pages.add(new ArrayList<>());}catch(Exception e){pages.clear();pages.add(new ArrayList<>());}}
}
