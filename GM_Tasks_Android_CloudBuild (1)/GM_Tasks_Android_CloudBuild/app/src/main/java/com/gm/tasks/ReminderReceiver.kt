package com.gm.tasks
import android.app.*; import android.content.*; import android.os.Build
class ReminderReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val title=intent.getStringExtra("title")?:"Task reminder"
  val ch="gm_task_reminders"
  val n=Notification.Builder(context,ch).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("GM Tasks").setContentText("⏰ $title").setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH).build()
  context.getSystemService(NotificationManager::class.java).notify(title.hashCode(),n)
 }
}