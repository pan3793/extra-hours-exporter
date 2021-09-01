import com.deepoove.poi.XWPFTemplate
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

object OAService {
    private const val BASE_URL: String = "https://oa.wenjuan.net"

    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    val om: ObjectMapper = ObjectMapper()

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

    fun export(year: Int, month: Int, reason: String, folder: Path): Path {
        baseInfo()
        val workDays = calculate(monthRecords(year, month))
        val outputPath = Paths.get(folder.toString(), "加班申请单_${fullName}_%04d%02d.docx".format(year, month))
        renderDocx(year, month, reason, workDays, outputPath)
        return outputPath
    }

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

    private fun baseInfo() {
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

    private fun monthRecords(year: Int, month: Int): JsonNode =
        client.newCall(monthRecordsRequest(year, month)).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Request Failed. ${response.message()}")
            om.readTree(response.body()!!.string())
        }

    fun calculate(json: JsonNode): List<WorkDay> = json.get("result")
        .filter { it.get("isWorkDay").asBoolean() }
        .filter { it.get("types").size() == 1 && it.get("types").get(0).asText() == "NORMAL" }
        .sortedBy { it.get("date").asText() }
        .map {
            WorkDay(
                date = LocalDate.parse(it.get("date").asText(), dateFmt),
                startTime = it.get("signInfo")
                    .first { it.get("title").asText() == "上班打卡" }
                    .let { LocalTime.parse(it.get("signTime").asText(), timeFmt) },
                offTime = it.get("signInfo")
                    .first { it.get("title").asText() == "下班打卡" }
                    .let { LocalTime.parse(it.get("signTime").asText(), timeFmt) }
            )
        }

    private fun renderDocx(year: Int, month: Int, reason: String, workDays: List<WorkDay>, outputPath: Path): Unit {
        val model = mapOf<String, String>(
            "apply_date" to dateFmt.format(LocalDateTime.now()),
            "employee" to fullName,
            "depart" to department,
            "reason" to reason,
            "year" to year.toString(),
            "month" to month.toString(),
            "details" to workDays.joinToString("\n")
        )
        val outputFile = Files.newOutputStream(outputPath)
        XWPFTemplate
            .compile(javaClass.classLoader.getResourceAsStream("template.docx"))
            .render(model)
            .writeAndClose(outputFile)
        outputFile.close()
    }

    data class WorkDay(val date: LocalDate, val startTime: LocalTime, val offTime: LocalTime) {

        private val adjustedStartTime: LocalTime =
            if (startTime.isBefore(LocalTime.parse("09:00:00"))) LocalTime.parse("09:00:00") else startTime

        private val startOvertime: LocalTime = adjustedStartTime.plusHours(10)

        private val overtime: Duration = run {
            val overMinutes = (offTime.toSecondOfDay() - startOvertime.toSecondOfDay()) / 60
            val overHours = overMinutes / 60
            val additionMinutes: Long = if (overMinutes % 60 > 55) 60 else if (overMinutes % 60 > 25) 30 else 0
            Duration.of(overHours * 60 + additionMinutes, ChronoUnit.MINUTES)
        }

        private val renderDay = "$date [${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ROOT)}]"

        private val renderTimeRange = "${timeFmt.format(startOvertime)} - ${timeFmt.format(offTime)}"

        private val renderOvertime = "${overtime.toMinutes().toDouble() / 60.0} Hours"

        override fun toString(): String = "$renderDay $renderTimeRange | ~$renderOvertime"
    }
}