import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Condition.*;

// java -jar ./artifacts/app-card-delivery.jar &

public class TestWebForm {

    private static final String BASE_URL = "http://localhost:9999";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static List<String> validCities;
    private static final int seconds= 10; // Время ожидания, в течение которого Selenide будет выполнять проверку.
    private static final int addedDays= 3; // количество дней добавленных к текущей дате

    @BeforeAll
    static void setup() throws Exception {
        Configuration.browserSize = "1920x1080";

        /**
         * Инициализация тестового окружения перед всеми тестами
         */
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(TestWebForm.class.getClassLoader().getResourceAsStream("cities.json")))) {
            // Парсинг JSON с использованием Gson
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            Type listType = new TypeToken<List<String>>() {
            }.getType();
            validCities = new Gson().fromJson(json.get("cities"), listType);
        }
    }

    // Действия перед каждым тестом: открытие формы
    @BeforeEach
    void openForm() {
        open(BASE_URL);
    }

    /**
     * Метод для установки даты
     *
     * @param date - дата в формате dd.MM.yyyy
     */
    private void clearAndSetDate(String date) {
        SelenideElement dateInput = $x("//input[@placeholder='Дата встречи']");
        // 1. Гарантированная очистка поля (через комбинацию клавиш)
        dateInput.sendKeys(Keys.CONTROL + "a", Keys.BACK_SPACE);
        // 2. Установка нового значения
        dateInput.setValue(date);
    }


    /**
     * Заполнение формы валидными данными с возможностью пропуска одного поля
     *
     * @param fieldToSkip - название поля, которое НЕ нужно заполнять ("city", "date", "name", "phone", "agreement")
     */
    private void validValues(String fieldToSkip) {
        // 1. Город
        if (!fieldToSkip.equals("city")) {
            // Получаем случайный город из списка
            String city = validCities.get(new Random().nextInt(validCities.size()));

            // Вводим город и выбираем его из выпадающего списка
            SelenideElement cityInput = $x("//input[@placeholder='Город']");
            cityInput.setValue(city);

        }

        // 2. Дата
        if (!fieldToSkip.equals("date")) {
            String validDate = LocalDate.now().plusDays(addedDays).format(FORMATTER);
            clearAndSetDate(validDate);
        }

        // 3. Имя
        if (!fieldToSkip.equals("name")) {
            $("[name='name']").setValue("Иванов Иван-Петр");
        }

        // 4. Телефон
        if (!fieldToSkip.equals("phone")) {
            $("[name='phone']").setValue("+79998887766");
        }

        // 5. Чекбокс
        if (!fieldToSkip.equals("agreement")) {
            $(".checkbox__box").click();
        }
    }

    @Test
        // 1. Тест с не валидным городом
    void invalidCity() {
        validValues("city");
        $x("//input[@placeholder='Город']").setValue("Ура");
        $("button.button").click();

        $("[data-test-id='city'].input_invalid .input__sub")
                .shouldBe(visible, Duration.ofSeconds(seconds))
                .shouldHave(text("Доставка в выбранный город недоступна"), Duration.ofSeconds(seconds));
    }

    @Test
        // 1. Тест с не валидными Фамилией и именем
    void invalidName() {
        validValues("name");
        $("[name='name']").setValue("John Smith");
        $("button.button").click();

        $("[data-test-id='name'].input_invalid .input__sub")
                .shouldBe(visible, Duration.ofSeconds(seconds))
                .shouldHave(text("Имя и Фамилия указаные неверно. Допустимы только русские буквы, пробелы и дефисы."),
                        Duration.ofSeconds(seconds));
    }

    @Test
    /**
     * Тест: невалидный телефон
     * - Заполняем все поля валидными данными, кроме телефона
     * - Устанавливаем некорректный номер
     * - Проверяем сообщение об ошибке
     */
    void invalidPhone() {
        validValues("phone");
        $("[name='phone']").setValue("12345");
        $("button.button").click();

        $("[data-test-id='phone'].input_invalid .input__sub")
                .shouldBe(visible, Duration.ofSeconds(seconds))
                .shouldHave(text("Телефон указан неверно"), Duration.ofSeconds(seconds));
    }

    @Test
    /**
     * Тест: не активированный чекбокс согласия
     * - Заполняем все поля валидными данными
     * - НЕ активируем чекбокс
     * - Проверяем подсветку ошибки
     */
    void rejectUnchecked() {
        validValues("agreement"); // Пропускаем активацию чекбокса
        $("button.button").click();

        $("[data-test-id='agreement'].input_invalid")
                .shouldBe(visible, Duration.ofSeconds(seconds));
    }

    @Test
    /**
     * Тест: успешная отправка формы
     * - Заполняем ВСЕ поля валидными данными
     * - Явно устанавливаем дату (для точности проверки)
     * - Проверяем сообщение об успехе
     */
    void submitFormSuccessfully() {
        // Генерируем ожидаемую дату ДО заполнения формы
        String expectedDate = LocalDate.now().plusDays(3).format(FORMATTER);

        // Заполняем форму (включая установку даты через validValues)
        validValues("none");

        // Проверка вывода уведомления с датой
        $x("//input[@placeholder='Дата встречи']").shouldHave(value(expectedDate), Duration.ofSeconds(seconds));

        // Нажатие кнопки
        $("button.button").click();

        // Проверка уведомления
        $(".notification__title")
                .shouldBe(visible, Duration.ofSeconds(15))
                .shouldHave(text("Успешно"), Duration.ofSeconds(seconds));
        $(".notification__content")
                .shouldHave(text("Встреча успешно забронирована на " + expectedDate), Duration.ofSeconds(seconds));
    }
}
