package com.gm.tasks

import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Task(val id: String, var title: String, var due: Long, var reminderMin: Int, var done: Boolean)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("gm_tasks", MODE_PRIVATE) }
    private val tasks = mutableListOf<Task>()
    private lateinit var adapter: TaskAdapter
    private val channelId = "gm_task_reminders"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createChannel()
        load()
        adapter = TaskAdapter(tasks, { toggle(it) }, { delete(it) })
        findViewById<RecyclerView>(R.id.list).layoutManager = LinearLayoutManager(this)
        findViewById<RecyclerView>(R.id.list).adapter = adapter
        findViewById<Button>(R.id.addButton).setOnClickListener { addTaskDialog() }
        findViewById<Button>(R.id.notifyButton).setOnClickListener { requestNotifications() }
        updateCounts()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 22)
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
        Toast.makeText(this, "Reminder permission enabled", Toast.LENGTH_SHORT).show()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, "Task Reminders", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "GM Tasks due-date reminders"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun addTaskDialog() {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(30,10,30,10)
        val title = EditText(this); title.hint = "Task name"
        val mins = EditText(this); mins.hint = "Reminder minutes before (0 / 15 / 60 / 1440)"; mins.inputType=2
        val date = EditText(this); date.hint = "Due date: YYYY-MM-DD"
        val time = EditText(this); time.hint = "Due time: HH:MM"
        box.addView(title); box.addView(date); box.addView(time); box.addView(mins)
        AlertDialog.Builder(this).setTitle("Add Task").setView(box).setNegativeButton("Cancel",null)
            .setPositiveButton("Save") { _, _ ->
                val sdf=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US)
                val due=try{sdf.parse("${date.text} ${time.text}")!!.time}catch(_:Exception){System.currentTimeMillis()+3600000}
                val m=mins.text.toString().toIntOrNull()?:0
                val t=Task(UUID.randomUUID().toString(),title.text.toString().trim(),due,m,false)
                if(t.title.isNotEmpty()){tasks.add(t);save();schedule(t);adapter.notifyDataSetChanged();updateCounts()}
            }.show()
    }

    private fun schedule(t:Task){
        val alarm=getSystemService(AlarmManager::class.java)
        val intent=Intent(this,ReminderReceiver::class.java).apply{
            putExtra("title",t.title);putExtra("id",t.id)
        }
        val pi=PendingIntent.getBroadcast(this,t.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val whenMs=t.due - t.reminderMin*60000L
        if(Build.VERSION.SDK_INT>=23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,whenMs.coerceAtLeast(System.currentTimeMillis()+1000),pi)
        else alarm.setExact(AlarmManager.RTC_WAKEUP,whenMs,pi)
    }
    private fun toggle(t:Task){t.done=!t.done;save();adapter.notifyDataSetChanged();updateCounts()}
    private fun delete(t:Task){tasks.remove(t);save();adapter.notifyDataSetChanged();updateCounts()}
    private fun updateCounts(){findViewById<TextView>(R.id.pendingText).text="Pending: ${tasks.count{!it.done}}";findViewById<TextView>(R.id.completedText).text="Done: ${tasks.count{it.done}}"}
    private fun save(){val a=JSONArray();tasks.forEach{a.put(JSONObject().put("id",it.id).put("title",it.title).put("due",it.due).put("rem",it.reminderMin).put("done",it.done))};prefs.edit().putString("tasks",a.toString()).apply()}
    private fun load(){tasks.clear();val a=JSONArray(prefs.getString("tasks","[]"));for(i in 0 until a.length()){val o=a.getJSONObject(i);tasks.add(Task(o.getString("id"),o.getString("title"),o.getLong("due"),o.optInt("rem",0),o.optBoolean("done",false)))}}
}

class TaskAdapter(private val items:MutableList<Task>,private val toggle:(Task)->Unit,private val delete:(Task)->Unit):RecyclerView.Adapter<TaskVH>(){
    override fun onCreateViewHolder(p:ViewGroup,v:Int)=TaskVH(LinearLayout(p.context).apply{orientation=LinearLayout.VERTICAL;setPadding(18,18,18,18)})
    override fun onBindViewHolder(h:TaskVH,i:Int){val t=items[i];h.bind(t,toggle,delete)}
    override fun getItemCount()=items.size
}
class TaskVH(v:View):RecyclerView.ViewHolder(v){
    private val box=v as LinearLayout
    fun bind(t:Task,toggle:(Task)->Unit,delete:(Task)->Unit){
        box.removeAllViews()
        val title=TextView(box.context);title.text=(if(t.done)"✓ " else "○ ")+t.title;title.textSize=17f;title.setTextColor(0xFFFFFFFF.toInt());box.addView(title)
        val date=TextView(box.context);date.text=SimpleDateFormat("dd MMM yyyy • HH:mm",Locale.getDefault()).format(Date(t.due))+" • Reminder "+t.reminderMin+" min before";date.setTextColor(0xFF94A3B8.toInt());box.addView(date)
        val row=LinearLayout(box.context);row.orientation=LinearLayout.HORIZONTAL
        val done=Button(box.context);done.text=if(t.done)"Undo" else "Done";done.setOnClickListener{toggle(t)}
        val del=Button(box.context);del.text="Delete";del.setOnClickListener{delete(t)}
        row.addView(done);row.addView(del);box.addView(row)
    }
}