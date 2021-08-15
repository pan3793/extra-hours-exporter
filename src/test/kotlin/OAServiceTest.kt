import kotlin.test.Test

class OAServiceTest {
    @Test
    fun testCalculate() {
        val retJson = OAService.om
            .readTree(javaClass.classLoader.getResourceAsStream("data/005_response_month_data.json"))
        OAService.calculate(retJson).forEach { println(it) }
    }
}