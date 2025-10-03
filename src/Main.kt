import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.Period
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() {
    // Указываем путь к файлу с данными клиента
    val jsonFilePath = "client_Ivan.txt"
    try{
        //Читаем файл в одну строку
        val jsonString = File(jsonFilePath).readText(Charsets.UTF_8)
        println("Файл успешно прочитан.")
        //Передаём содержимое файла в функцию для проверок
        val isApproved = performStopChecks(jsonString)
        if(isApproved) println("Заявка одобрена")
        else println("Отказ, сработала стоп проверка")

    } catch (e: FileNotFoundException) {
        // Если файл не будет найден
        println("Файл не найден по пути $jsonFilePath")
    } catch (e: Exception) {
        // Отлов всех других возможных ошибок
        println("Произошла непредвиденная ошибка: ${e.message}")
    }
}
//Основная функция всех стоп-проверок
fun performStopChecks(json: String): Boolean {
    try{
        //Извлекаем нужные данные из JSON
        val birthDateStr = findStringValue(json, "birthDate") ?: return false
        val passportJson = findObject(json, "passport") ?: return false
        val passportIssuedAtStr = findStringValue(passportJson, "issuedAt") ?: return false
        val creditHistoryJson = findArray(json, "creditHistory") ?: return false

        // Преобразуем строки в даты
        val birthDate = parseDate(birthDateStr)
        val passportIssuedAt = parseDate(passportIssuedAtStr)
        val today = LocalDate.now()
        val age = Period.between(birthDate, today).years
        if (!checkAge(age)) {
            println("Отказ: возраст меньше 20 лет.")
            return false
        }
        if (!checkPassportValidity(age, birthDate, passportIssuedAt)) {
            println("Отказ: паспорт недействителен.")
            return false
        }
        if (!checkCreditHistory(creditHistoryJson)) {
            return false
        }
        return true
    }
    catch (e: Exception) {
        println("Ошибка при обработке JSON: ${e.message}")
        return false // В случае любой ошибки
    }
}
//Функции для парсинга json
private fun findStringValue(json: String, key: String): String? {
    val regex = "\"$key\"\\s*:\\s*\"(.*?)\"".toRegex()
    return regex.find(json)?.groups?.get(1)?.value
}

private fun findLongValue(json: String, key: String): Long? {
    val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
    return regex.find(json)?.groups?.get(1)?.value?.toLongOrNull()
}

private fun findObject(json: String, key: String): String? {
    val regex = "\"$key\"\\s*:\\s*\\{(.*?)\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
    return regex.find(json)?.groups?.get(1)?.value
}

private fun findArray(json: String, key: String): String? {
    val regex = "\"$key\"\\s*:\\s*\\[(.*?)\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
    return regex.find(json)?.groups?.get(1)?.value
}
private fun parseDate(dateString: String): LocalDate {
    return LocalDate.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
//1-ая проверка: минимальный возраст
private fun checkAge(age: Int): Boolean {
    return age >= 20
}
//2-ая проверка: действительность паспорта
private fun checkPassportValidity(age: Int, birthDate: LocalDate, passportIssuedAt: LocalDate): Boolean {
    val dateOf20thBirthday = birthDate.plusYears(20)
    val dateOf45thBirthday = birthDate.plusYears(45)

    if (age >= 45 && passportIssuedAt.isBefore(dateOf45thBirthday)) {
        return false // Отказ: паспорт не был заменен после 45 лет
    }
    else if (age >= 20 && passportIssuedAt.isBefore(dateOf20thBirthday)) {
        return false // Отказ: паспорт не был заменен после 20 лет
    }
    return true
}
//3-ая проверка: кредитная история
private fun checkCreditHistory(historyJson: String): Boolean {
    val creditRegex = "\\{(.*?)\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
    val creditMatches = creditRegex.findAll(historyJson)
    var nonCreditCardOverdue15daysCount = 0

    for (matchResult in creditMatches) {
        val creditStr = matchResult.value
        val type = findStringValue(creditStr, "type") ?: ""
        val currentOverdueDebt = findLongValue(creditStr, "currentOverdueDebt") ?: 0
        val numberOfDaysOnOverdue = findLongValue(creditStr, "numberOfDaysOnOverdue") ?: 0

        if (type == "Кредитная карта") {
            if (currentOverdueDebt > 0) {
                println("Отказ: по кредитной карте есть непогашенная просроченная задолженность.")
                return false
            }
            if (numberOfDaysOnOverdue > 30){
                println("Отказ: по кредитной карте была просрочка более 30 дней.")
                return false
            }
        } else { // Другие типы кредитов
            if (currentOverdueDebt > 0) {
                println("Отказ: по кредиту \"" + type + "\" есть непогашенная просроченная задолженность.")
                return false
            }
            if (numberOfDaysOnOverdue > 60){
                println("Отказ: по кредиту \"" + type + "\" была просрочка более 60 дней.")
                return false
            }
            if (numberOfDaysOnOverdue > 15) {
                nonCreditCardOverdue15daysCount++
            }
        }
    }
    if (nonCreditCardOverdue15daysCount > 2) {
        println("Отказ: есть больше двух кредитов с просроченной задолженность более 15 дней")
        return false
    }
    return true
}