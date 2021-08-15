import com.deepoove.poi.XWPFTemplate
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object OAService {
    private const val BASE_URL: String = "https://oa.wenjuan.net"

    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val log: Logger = LoggerFactory.getLogger(OAService::class.java)

    private val om: ObjectMapper = ObjectMapper()

    var isLogin: Boolean = false
    var userId: Int = 0
    var fullName: String = ""
    var department: String = ""

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cache: MutableMap<String, MutableList<Cookie>> = mutableMapOf()
            override fun saveFromResponse(url: HttpUrl, cookies: MutableList<Cookie>) {
                cache[url.host()] = cookies
            }

            override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                return cache[url.host()] ?: mutableListOf()
            }
        })
        .build()

    private fun loginRequest(username: String, password: String): Request {
        val form = FormBody.Builder()
            .add("loginid", username)
            .add("userpassword", password)
            .build()
        return Request.Builder()
            .url("$BASE_URL/api/hrm/login/checkLogin")
            .post(form)
            .build()
    }

    fun login(username: String, password: String) {
        return client.newCall(loginRequest(username, password)).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Request Failed. ${response.message()}")
            val retJson = om.readTree(response.body()!!.string())
            if (retJson.get("loginstatus").asBoolean()) {
                isLogin = true
                userId = retJson.get("userid").asInt()
            } else {
                throw IllegalStateException(retJson.get("msg").asText())
            }
        }
    }

    private fun baseInfoRequest(): Request {
        val form = FormBody.Builder()
            .add("ismobile", "1")
            .build()
        return Request.Builder()
            .url("$BASE_URL/api/hrm/kq/attendanceButton/getButtonBaseInfo")
            .post(form)
            .build()
    }

    private fun baseInfo2Request(): Request {
        return Request.Builder()
            .url("$BASE_URL/api/hrm/resource/getQRCode?id=$userId")
            .get()
            .build()
    }

    fun baseInfo() {
        fullName = client.newCall(baseInfoRequest()).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Request Failed. ${response.message()}")
            val retJson = om.readTree(response.body()!!.string())
            retJson.get("lastname").asText()
        }
        department = client.newCall(baseInfo2Request()).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Request Failed. ${response.message()}")
            val retJson = om.readTree(response.body()!!.string())
            retJson.get("options").get("department").asText()
        }
    }

    private fun monthRecordsRequest(year: Int, month: Int): Request {
        val form = FormBody.Builder()
            .add("type", "2")
            .add("typevalue", LocalDate.of(year, month, 1).format(monthFmt))
            .build()
        return Request.Builder()
            .url("$BASE_URL/api/kq/myattendance/getHrmKQMonthReportInfo")
            .post(form)
            .build()
    }

    fun monthRecords(year: Int, month: Int) {
        return client.newCall(monthRecordsRequest(year, month)).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Request Failed. ${response.message()}")
            val retJson = om.readTree(response.body()!!.string())
            retJson
//            om.readTree(retJson).get("lastname").asText()
        }
    }

    fun calculateOvertimeDays(year: Int, month: Int): List<WorkDay> {
        val from = LocalDate.of(year, month, 1)
        val until = from.plusMonths(1)
        dateRange(from, until).map { date ->
            val request: Request = Request.Builder()
                .url("$BASE_URL/api/kq/myattendance/getHrmKQSignInfo")
                .post(
                    FormBody.Builder()
                        .add("date", dateFmt.format(date))
                        .build()
                )
                .build()
            val response: Response = client.newCall(request).execute()
            val retJson = response.body()!!.string()
            if (!response.isSuccessful) {
                log.error("request error, response code[${response.code()}]")
                log.error(response.toString())
            }

            log.info(retJson)

            om.readTree(retJson).get("data").get("signInfo")

//            val signTimes: List<Pair<String, String>> = (parse(retJson) \ "data" \ "signInfo"). as [Seq[JValue]]
//            .flatMap {
//            sign ->
//            (sign \ "item"). as [Seq[JValue]]
//            .map { iv => (iv \ "title"). as [String] -> (iv \ "value"). as [String] }
//            .filter(_._1.contains("打卡"))
//        }
//            WorkDay(date,
//                signTimes.filter(_._2 != "未打卡").find(_._1 == "上班打卡")
//                    .map { case(_, dt) => LocalDateTime.parse(dt, dtFmt).toLocalTime }.getOrElse(LocalTime.MIDNIGHT),
//                signTimes.filter(_._2 != "未打卡").find(_._1 == "下班打卡")
//                    .map { case(_, dt) => LocalDateTime.parse(dt, dtFmt).toLocalTime }.getOrElse(LocalTime.MIDNIGHT)
//            )
//        }
//            .filter(_.isOvertime)
        }
        return emptyList()
    }

    fun renderDocx(outputPath: Path): Unit {
        val model = mapOf<String, String>(
//            "apply_date" to dateFmt.format(params.applyDate),
//            "employee" to params.employee,
//            "depart" to params.depart,
//            "reason" to params.reason,
//            "year" to params.year.toString,
//            "month" to params.month.toString,
//            "details" to overtimeDays.mkString("\n")
        )
//        val outputPath =
//            Paths.get("", "加班申请单_${params.employee}_${year}%04d${month}%02d.docx")
        val outputFile = Files.newOutputStream(outputPath)
        XWPFTemplate
            .compile(javaClass.classLoader.getResourceAsStream("template.docx"))
            .render(model)
            .writeAndClose(outputFile)
        outputFile.close()
        log.info("Exported to ${outputPath.toAbsolutePath()}")
    }

    private fun dateRange(from: LocalDate, until: LocalDate): List<LocalDate> =
        (from.toEpochDay() until until.toEpochDay()).map(LocalDate::ofEpochDay)

    data class WorkDay(val date: LocalDate, val startTime: LocalTime, val offTime: LocalTime) {

        val adjustedStartTime: LocalTime =
            if (startTime.isBefore(LocalTime.parse("09:00:00"))) LocalTime.parse("09:00:00") else startTime

        val startOvertime: LocalTime = adjustedStartTime.plusHours(10)

        val overtime: Duration = run {
            val overMinutes = (offTime.toSecondOfDay() - startOvertime.toSecondOfDay()) / 60
            val additionMinutes: Long = if (overMinutes % 60 > 55) 60 else if (overMinutes % 60 > 25) 30 else 0
            Duration.of(overMinutes + additionMinutes, ChronoUnit.MINUTES)
        }

        val isOvertime: Boolean = overtime > Duration.ZERO

        val renderDay =
            "$date [${date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ROOT)}]"

        val renderTimeRange = "${timeFmt.format(startOvertime)} - ${timeFmt.format(offTime)}"

        val renderOvertime = "${overtime.toMinutes().toDouble() / 60.0} Hours"

        override fun toString(): String = "$renderDay $renderTimeRange | $renderOvertime"

    }
}