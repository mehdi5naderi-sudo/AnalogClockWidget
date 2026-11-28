package com.mehdi.analogclock

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object IrimoWeatherData {
 data class Station(val name:String,val url:String){override fun toString()=name}
 private val pool=Executors.newSingleThreadExecutor()
 private val main=Handler(Looper.getMainLooper())
 private const val HOME="https://www.irimo.ir/far/index.php"
 private const val TEHRAN="https://www.irimo.ir/far/wd/701-%D9%88%D8%B6%D8%B9-%D9%87%D9%88%D8%A7%DB%8C-%D8%AA%D9%87%D8%B1%D8%A7%D9%86.html?id=17561"
 private const val FORECAST="https://www.irimo.ir/far/wd/2816-%D9%88%D8%B6%D8%B9-%D9%87%D9%88%D8%A7-%D9%BE%DB%8C%D8%B4-%D8%A8%DB%8C%D9%86%DB%8C-%D8%B1%D9%88%D8%B2%D9%87.html"
 private const val ROADS="https://www.irimo.ir/far/wd/2817-%D9%88%D8%B6%D8%B9-%D9%87%D9%88%D8%A7%DB%8C-%D8%AC%D8%A7%D8%AF%D9%87.html"
 private const val WARNINGS="https://www.irimo.ir/far/wd/592-%D9%87%D8%B4%D8%AF%D8%A7%D8%B1%D9%87%D8%A7%DB%8C-%D9%87%D9%88%D8%A7%D8%B4%D9%86%D8%A7%D8%B3%DB%8C.html"
 fun loadStations(ctx:Context, cb:(List<Station>)->Unit){pool.execute{
  val found=linkedMapOf<String,Station>()
  try{val h=get(HOME); Regex("(?is)<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>").findAll(h).forEach{
   val href=it.groupValues[1]; val name=clean(it.groupValues[2])
   if(name.isNotBlank()&&(href.contains("wd_id=701")||href.contains("/wd/701-")||href.contains("id=17561")))found[name]=Station(name,absolute(href))
  }}catch(_:Exception){}
  if(found.values.none{it.url.contains("17561")})found["تهران"]=Station("تهران",TEHRAN)
  main.post{cb(found.values.toList())}
 }}
 fun fetch(ctx:Context,s:Station,cb:(String)->Unit){pool.execute{
  val b=StringBuilder()
  fun add(t:String,v:String){b.append("\n\n").append(t).append("\n").append(v.ifBlank{"اطلاعاتی پیدا نشد."})}
  try{
   add("۱) مشاهدات ایستگاه "+s.name,stationData(get(s.url)))
   try{add("۲) پیش‌بینی سه‌روزه",relevant(get(FORECAST),listOf("24 ساعت","۲۴ ساعت","درجه","ابری","بارش","صاف"),18))}catch(e:Exception){add("۲) پیش‌بینی سه‌روزه","خطا: "+e.message)}
   try{add("۳) وضعیت جاده‌ها",relevant(get(ROADS),listOf("جاده","محور","بارش","برف","باران","مه","یخ"),24))}catch(e:Exception){add("۳) وضعیت جاده‌ها","خطا: "+e.message)}
   try{add("۴) هشدارها و اطلاعیه‌ها",warnings(get(WARNINGS)))}catch(e:Exception){add("۴) هشدارها","خطا: "+e.message)}
   add("۵) زمان دریافت","ایستگاه: "+s.name+"\nزمان دریافت: "+java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss",java.util.Locale("fa")).format(java.util.Date())+"\nمنبع IRIMO | HTTPS و اعتبارسنجی SSL فعال")
  }catch(e:Exception){add("خطای دریافت",e.javaClass.simpleName+": "+e.message)}
  main.post{cb(b.toString().trim())}
 }}
 private fun get(u:String):String{var c:HttpURLConnection?=null;try{
  c=URL(u).openConnection() as HttpURLConnection
  c.requestMethod="GET";c.connectTimeout=18000;c.readTimeout=22000;c.instanceFollowRedirects=true
  c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
  c.setRequestProperty("Accept","text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
  c.setRequestProperty("Accept-Language","fa-IR,fa;q=0.9,en;q=0.8");c.setRequestProperty("Referer",HOME);c.setRequestProperty("Cache-Control","no-cache")
  val code=c.responseCode;if(code !in 200..299)error("HTTP "+code)
  return c.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText().take(2000000)}
 }finally{c?.disconnect()}}
 private fun absolute(h:String)=when{h.startsWith("http")->h;h.startsWith("//")->"https:"+h;h.startsWith("/") ->"https://www.irimo.ir"+h;else->"https://www.irimo.ir/far/"+h}
 private fun clean(h:String)=h.replace(Regex("(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>")," ").replace(Regex("(?i)<br\\s*/?>|</(td|th|tr|li|p|div|h[1-6])\\s*>"),"\n").replace(Regex("<[^>]+>")," ").replace("&nbsp;"," ").replace("&zwnj;","‌").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace(Regex("[\\t ]+")," ").replace(Regex(" *\n *"),"\n").trim()
 private fun stationData(h:String):String{
  val txt=clean(h);val keys=listOf("هوای حاضر","دید افقی","جهت باد","سرعت باد","نم نسبی","رطوبت نسبی","بارندگی 24 ساعت گذشته","بارندگی ۲۴ ساعت گذشته","بیشینه دما در 24 ساعت گذشته","کمینه دما در 24 ساعت گذشته","آخرین بروز رسانی","فشار")
  val lines=txt.split("\n").map{it.trim()}.filter{it.isNotBlank()};val out=mutableListOf<String>();val seen=mutableSetOf<String>()
  lines.forEachIndexed{i,line->val k=keys.firstOrNull{line.contains(it,true)};if(k!=null&&seen.add(k)){val v=line.substringAfter(":").trim().ifBlank{lines.drop(i+1).firstOrNull{n->keys.none{n.contains(it,true)}}.orEmpty()};if(v.isNotBlank())out.add(k+": "+v)}}
  val rows=Regex("(?is)<tr\\b[^>]*>.*?</tr>").findAll(h).map{clean(it.value)}.filter{it.contains("24 ساعت")||it.contains("۲۴ ساعت")}.distinct().take(8).toList()
  return buildString{if(out.isEmpty())append("مقادیر کلیدی شناسایی نشدند.\n")else out.forEach{append("• ").append(it).append("\n")};if(rows.isNotEmpty()){append("\nپیش‌بینی‌های صفحه ایستگاه:\n");rows.forEach{append("• ").append(it.take(500)).append("\n")}}}.trim()
 }
 private fun relevant(h:String,keys:List<String>,max:Int):String{val rows=Regex("(?is)<tr\\b[^>]*>.*?</tr>").findAll(h).map{clean(it.value)};val lines=clean(h).split("\n");val found=(rows+lines).filter{v->keys.any{v.contains(it,true)}}.distinct().take(max);return if(found.none())"مطلب مرتبطی پیدا نشد." else found.joinToString("\n"){"• "+it.take(500)}}
 private fun warnings(h:String):String{val links=Regex("(?is)<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>").findAll(h).map{clean(it.groupValues[2]) to absolute(it.groupValues[1])}.filter{it.first.contains("هشدار")||it.first.contains("اطلاعیه")}.distinctBy{it.first}.take(25).toList();return if(links.isNotEmpty())links.joinToString("\n"){"• "+it.first.take(180)+"\n  "+it.second}else relevant(h,listOf("هشدار","سطح زرد","سطح نارنجی","سطح قرمز","زمان شروع","زمان پایان"),20)}
}