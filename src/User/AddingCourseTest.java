package User;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AddingCourseTest {

    private WebDriver driver;
    private WebDriverWait wait;

    // =======================
    // Data Models
    // =======================
    private static class ChapterData {
        String title;
        String description;

        ChapterData(String title, String description) {
            this.title = title;
            this.description = description;
        }

        @Override
        public String toString() {
            return "Chapter: " + title;
        }
    }

    private static class LessonData {
        String title;
        String description;

        LessonData(String title, String description) {
            this.title = title;
            this.description = description;
        }

        @Override
        public String toString() {
            return "Lesson: " + title;
        }
    }

    // =======================
    // Locators
    // =======================
    // Tab: Nội dung môn học
    private static final By TAB_NOI_DUNG_MON_HOC =
        By.xpath("//div[@role='tab' and contains(., 'Nội dung môn học')]");

    // Button: Thêm chương học
    private static final By BTN_THEM_CHUONG_HOC =
        By.xpath("//span[contains(text(),'Thêm chương học')]/parent::button");

    // Button: Thêm bài học
    private static final By BTN_THEM_BAI_HOC =
        By.xpath("//span[contains(text(),'Thêm bài học')]/parent::button");

    // Input: Tiêu đề chương (use actual field name discovered from debug)
    private static By getInputTieuDeChuong() {
        return By.name("title_course_item");
    }

    // Textarea: Mô tả về chương học
    private static By getTextareaMoTaChuong() {
        return By.name("description_course_item");
    }

    // Input: Tiêu đề bài học
    private static By getInputTieuDeBaiHoc() {
        return By.name("title_course_item");
    }

    // Textarea: Mô tả về bài học
    private static By getTextareaMoTaBaiHoc() {
        return By.name("description_course_item");
    }

    // Button: Lưu (generic save button)
    private static final By BTN_LUU =
        By.xpath("//span[contains(text(),'Lưu')]/parent::button");

    // Button: OK (notification confirmation)
    private static final By BTN_OK =
        By.xpath("//span[contains(text(),'OK')]/parent::button | //button[contains(text(),'OK')]");

    // Expansion panel header
    private static final By EXPANSION_PANEL_HEADER =
        By.cssSelector("button.v-expansion-panel-header");

    // =======================
    // Utility Methods
    // =======================
    private void delay(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void delay() {
        delay(800);
    }

    private void safeClick(WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
        delay(300);
        js.executeScript("arguments[0].click();", element);
        delay();
    }

    private void safeFill(By locator, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));

        // Clear field using multiple methods to ensure it's empty
        element.clear();
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        element.sendKeys(Keys.DELETE);

        // Fill with new value
        element.sendKeys(value);
        delay(300);
    }

    // Fill field within a specific parent element (more precise)
    private void safeFillWithinParent(WebElement parent, By locator, String value) {
        // Find ALL matching elements within parent
        List<WebElement> elements = parent.findElements(locator);

        if (elements.isEmpty()) {
            throw new RuntimeException("No elements found within parent with locator: " + locator);
        }

        // Use the LAST visible element (most recently added field)
        WebElement element = null;
        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i).isDisplayed()) {
                element = elements.get(i);
                if (elements.size() > 1) {
                    System.out.println("  Found " + elements.size() + " matching fields, using LAST visible one (index " + i + ")");
                }
                break;
            }
        }

        if (element == null) {
            throw new RuntimeException("No visible elements found within parent");
        }

        // Scroll into view
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
        delay(200);

        // Verify current value before clearing
        String currentValue = element.getAttribute("value");
        System.out.println("  Current value: '" + (currentValue.length() > 40 ? currentValue.substring(0, 40) + "..." : currentValue) + "'");

        // Clear field thoroughly
        element.clear();
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        element.sendKeys(Keys.DELETE);
        delay(100);

        // Verify it's empty
        String afterClear = element.getAttribute("value");
        if (!afterClear.isEmpty()) {
            System.out.println("  ⚠ Field not fully cleared (still has: '" + afterClear + "'), trying JS clear");
            ((JavascriptExecutor) driver).executeScript("arguments[0].value = '';", element);
        }

        // Fill with new value
        element.sendKeys(value);
        delay(200);

        // Verify the value was set correctly
        String finalValue = element.getAttribute("value");
        System.out.println("  → Filled: '" + (value.length() > 50 ? value.substring(0, 50) + "..." : value) + "'");
        System.out.println("  → Verified: '" + (finalValue.length() > 50 ? finalValue.substring(0, 50) + "..." : finalValue) + "'");

        if (!finalValue.equals(value)) {
            System.out.println("  ⚠ WARNING: Filled value doesn't match expected!");
            System.out.println("    Expected: " + value);
            System.out.println("    Got: " + finalValue);
        }

        delay(300);
    }

    // =======================
    // JSON Loading Methods
    // =======================
    private List<ChapterData> loadChaptersFromJSON() throws Exception {
        List<ChapterData> chapters = new ArrayList<>();
        String projectPath = System.getProperty("user.dir");
        String filePath = projectPath + "/src/resources/chapters.json";

        System.out.println("Loading chapters from: " + filePath);

        JSONParser parser = new JSONParser();
        JSONArray jsonArray = (JSONArray) parser.parse(new FileReader(filePath));

        for (Object obj : jsonArray) {
            JSONObject jsonObj = (JSONObject) obj;
            String title = (String) jsonObj.get("title");
            String description = (String) jsonObj.get("description");
            chapters.add(new ChapterData(title, description));
        }

        System.out.println("✓ Loaded " + chapters.size() + " chapters from JSON");
        return chapters;
    }

    private List<LessonData> loadLessonsFromJSON() throws Exception {
        List<LessonData> lessons = new ArrayList<>();
        String projectPath = System.getProperty("user.dir");
        String filePath = projectPath + "/src/resources/lessons.json";

        System.out.println("Loading lessons from: " + filePath);

        JSONParser parser = new JSONParser();
        JSONArray jsonArray = (JSONArray) parser.parse(new FileReader(filePath));

        for (Object obj : jsonArray) {
            JSONObject jsonObj = (JSONObject) obj;
            String title = (String) jsonObj.get("title");
            String description = (String) jsonObj.get("description");
            lessons.add(new LessonData(title, description));
        }

        System.out.println("✓ Loaded " + lessons.size() + " lessons from JSON");
        return lessons;
    }

    // =======================
    // Test Methods
    // =======================
    private void loginAsAdmin() {
        System.out.println("Opening login page...");
        driver.get("https://elearning.plt.pro.vn/dang-nhap?redirect=%2Ftrang-chu");

        System.out.println("Entering admin email...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("input-10")))
            .sendKeys("test.pltsolutions@gmail.com");

        System.out.println("Entering admin password...");
        driver.findElement(By.id("input-13"))
            .sendKeys("plt@intern_051224");

        System.out.println("Clicking login button...");
        driver.findElement(By.xpath("//span[contains(text(),'Đăng nhập')]")).click();

        System.out.println("Waiting for redirect to home page...");
        wait.until(ExpectedConditions.urlContains("/trang-chu"));
        delay();
    }

    private void navigateToCourseManagement() {
        System.out.println("\n=== Navigating to Course Management ===");

        // Option 1: Direct navigation to course management page
        driver.get("https://elearning.plt.pro.vn/quan-tri-vien/khoa-hoc");

        // Wait for page to load
        wait.until(ExpectedConditions.urlContains("/quan-tri-vien/khoa-hoc"));

        // Alternative Option 2: Click on the sidebar link (if direct navigation doesn't work)
        // WebElement courseManagementLink = wait.until(
        //     ExpectedConditions.elementToBeClickable(
        //         By.xpath("//a[@href='/quan-tri-vien/khoa-hoc']//div[contains(text(), 'Quản lý khoá học')]")
        //     )
        // );
        // safeClick(courseManagementLink);

        System.out.println("✓ Navigated to Course Management");
        delay();
    }

    private String selectRandomCourse() {
        System.out.println("\n=== Selecting a Random Course ===");

        // Wait for table to load
        delay(2000);

        // Get list of all course links from the table
        // Structure: <tbody><tr><td><p><a href="/quan-tri-vien/khoa-hoc/quan-ly/XXXXX">Course Name</a></p></td></tr>
        List<WebElement> courseLinks = wait.until(
            ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.xpath("//tbody//tr//a[contains(@href, '/quan-tri-vien/khoa-hoc/quan-ly/')]")
            )
        );

        if (courseLinks.isEmpty()) {
            throw new RuntimeException("No courses found on the page!");
        }

        System.out.println("Found " + courseLinks.size() + " courses in the table");

        // Print all available courses
        System.out.println("\nAvailable courses:");
        for (int i = 0; i < courseLinks.size(); i++) {
            System.out.println("  [" + i + "] " + courseLinks.get(i).getText().trim());
        }

        // Select random course
        Random random = new Random();
        int randomIndex = random.nextInt(courseLinks.size());
        WebElement selectedCourseLink = courseLinks.get(randomIndex);
        String courseName = selectedCourseLink.getText().trim();

        System.out.println("\nRandomly selected course [" + randomIndex + "]: " + courseName);

        // Click the course link
        safeClick(selectedCourseLink);
        delay(1500);

        return courseName;
    }

    private void clickNoiDungMonHocTab() {
        System.out.println("\n=== Clicking 'Nội dung môn học' Tab ===");

        WebElement tab = wait.until(
            ExpectedConditions.elementToBeClickable(TAB_NOI_DUNG_MON_HOC)
        );

        safeClick(tab);
        System.out.println("✓ Clicked 'Nội dung môn học' tab");
        delay(1000);
    }

    private void addChapter(ChapterData chapter) {
        System.out.println("\n=== Adding Chapter: " + chapter.title + " ===");

        // Count existing chapters before adding
        int existingChapterCount = 0;
        try {
            List<WebElement> existingChapters = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header')]//div[contains(text(), 'Chương')]")
            );
            existingChapterCount = existingChapters.size();
            System.out.println("Existing chapters in course: " + existingChapterCount);
        } catch (Exception e) {
            System.out.println("Could not count existing chapters");
        }

        // Click "Thêm chương học" button
        WebElement btnThemChuong = wait.until(
            ExpectedConditions.elementToBeClickable(BTN_THEM_CHUONG_HOC)
        );
        safeClick(btnThemChuong);
        System.out.println("✓ Clicked 'Thêm chương học'");
        delay(1500);

        // Verify a new chapter panel was created (not editing existing)
        try {
            List<WebElement> chaptersAfter = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header')]//div[contains(text(), 'Chương')]")
            );
            int newChapterCount = chaptersAfter.size();
            System.out.println("Chapters after clicking 'Thêm chương học': " + newChapterCount);

            if (newChapterCount <= existingChapterCount) {
                System.out.println("⚠ WARNING: No new chapter created! May be editing existing chapter.");
                System.out.println("  Expected: " + (existingChapterCount + 1) + ", Got: " + newChapterCount);
            } else {
                System.out.println("✓ New chapter panel created (Chapter " + newChapterCount + ")");
            }
        } catch (Exception e) {
            System.out.println("Could not verify new chapter creation");
        }

        // IMPORTANT: After clicking "Thêm chương học", a NEW expansion panel is created
        // We need to expand the LAST panel (the newly created one) to reveal the form fields
        System.out.println("Looking for the newly created chapter panel...");

        try {
            // Find ALL expansion panel headers
            List<WebElement> expansionPanels = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header')]")
            );

            System.out.println("Found " + expansionPanels.size() + " expansion panels total");

            if (expansionPanels.isEmpty()) {
                throw new Exception("No expansion panels found!");
            }

            // The LAST panel should be the newly created chapter
            int lastPanelIndex = expansionPanels.size() - 1;
            WebElement newChapterPanel = expansionPanels.get(lastPanelIndex);
            String ariaExpanded = newChapterPanel.getAttribute("aria-expanded");
            String panelText = newChapterPanel.getText();

            System.out.println("LAST panel (index " + lastPanelIndex + "): aria-expanded=" + ariaExpanded);
            System.out.println("  Text: " + (panelText.length() > 50 ? panelText.substring(0, 50) + "..." : panelText));

            // If the new chapter panel is NOT expanded, click it to expand
            if (ariaExpanded == null || "false".equals(ariaExpanded) || ariaExpanded.isEmpty()) {
                System.out.println("  → Expanding NEW chapter panel (index " + lastPanelIndex + ")...");
                safeClick(newChapterPanel);
                delay(1500);
                System.out.println("✓ New chapter panel expanded");
            } else {
                System.out.println("✓ New chapter panel already expanded");
            }

        } catch (Exception e) {
            System.out.println("⚠ Error during expansion: " + e.getMessage());
            e.printStackTrace();
        }

        // Debug: Print all available inputs AFTER expanding
        try {
            System.out.println("\n--- DEBUG: Available inputs after expansion ---");
            List<WebElement> allInputs = driver.findElements(By.tagName("input"));
            int displayedCount = 0;
            for (int i = 0; i < allInputs.size() && displayedCount < 10; i++) {
                WebElement input = allInputs.get(i);
                if (input.isDisplayed()) {
                    System.out.println("Input " + displayedCount + ": type=" + input.getAttribute("type") +
                                     ", name=" + input.getAttribute("name") +
                                     ", placeholder=" + input.getAttribute("placeholder") +
                                     ", id=" + input.getAttribute("id"));
                    displayedCount++;
                }
            }

            System.out.println("\n--- DEBUG: Available textareas after expansion ---");
            List<WebElement> allTextareas = driver.findElements(By.tagName("textarea"));
            displayedCount = 0;
            for (int i = 0; i < allTextareas.size() && displayedCount < 5; i++) {
                WebElement textarea = allTextareas.get(i);
                if (textarea.isDisplayed()) {
                    System.out.println("Textarea " + displayedCount + ": name=" + textarea.getAttribute("name") +
                                     ", placeholder=" + textarea.getAttribute("placeholder") +
                                     ", id=" + textarea.getAttribute("id"));
                    displayedCount++;
                }
            }
            System.out.println("--- End DEBUG ---\n");
        } catch (Exception e) {
            System.out.println("Debug failed: " + e.getMessage());
        }

        // Find the expanded panel for chapter (use LAST one = newest)
        try {
            List<WebElement> expandedPanels = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header') and @aria-expanded='true']/following-sibling::div[contains(@class, 'v-expansion-panel-content')]")
            );

            if (expandedPanels.isEmpty()) {
                throw new Exception("No expanded panels found for chapter!");
            }

            // Use the LAST expanded panel (most recently created = new chapter)
            WebElement expandedPanel = expandedPanels.get(expandedPanels.size() - 1);
            System.out.println("Found " + expandedPanels.size() + " expanded panel(s), using LAST one (index " + (expandedPanels.size() - 1) + ") for NEW chapter");

            // Verify this is the correct panel by checking its content
            try {
                String panelText = expandedPanel.getText();
                System.out.println("Panel content preview: " +
                                 (panelText.length() > 100 ? panelText.substring(0, 100) + "..." : panelText));
            } catch (Exception e) {
                // Ignore
            }

            // Fill chapter title within the expanded panel
            System.out.println("Filling chapter title:");
            safeFillWithinParent(expandedPanel, By.name("title_course_item"), chapter.title);

            // Fill chapter description within the expanded panel
            System.out.println("Filling chapter description:");
            safeFillWithinParent(expandedPanel, By.name("description_course_item"), chapter.description);

            System.out.println("✓ Chapter form filled successfully");

        } catch (Exception e) {
            System.out.println("⚠ Error: Could not find expanded panel, using global locators");
            // Fallback
            System.out.println("Filling chapter title: " + chapter.title);
            safeFill(getInputTieuDeChuong(), chapter.title);

            System.out.println("Filling chapter description: " + chapter.description);
            safeFill(getTextareaMoTaChuong(), chapter.description);

            System.out.println("✓ Chapter form filled (fallback)");
        }

        delay(500);
    }

    private void addLesson(LessonData lesson, int lessonNumber) {
        System.out.println("\n--- Adding Lesson " + lessonNumber + ": " + lesson.title + " ---");

        // Click "Thêm bài học" button
        WebElement btnThemBaiHoc = wait.until(
            ExpectedConditions.elementToBeClickable(BTN_THEM_BAI_HOC)
        );
        safeClick(btnThemBaiHoc);
        System.out.println("✓ Clicked 'Thêm bài học'");
        delay(1500);

        // IMPORTANT: After clicking "Thêm bài học", an expansion panel appears for the lesson
        // We need to expand it to fill the form fields
        System.out.println("Looking for lesson expansion panel (Bài số " + lessonNumber + ")...");

        try {
            // Find expansion panel for this lesson (Bài số X)
            List<WebElement> expansionPanels = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header')]//strong[contains(text(), 'Bài số " + lessonNumber + "')]")
            );

            System.out.println("Found " + expansionPanels.size() + " panels matching 'Bài số " + lessonNumber + "'");

            if (!expansionPanels.isEmpty()) {
                WebElement lessonPanel = expansionPanels.get(0).findElement(By.xpath("./ancestor::button"));
                String ariaExpanded = lessonPanel.getAttribute("aria-expanded");

                System.out.println("  Lesson panel " + lessonNumber + ": aria-expanded=" + ariaExpanded);

                // Expand if not already expanded (aria-expanded is null, false, or empty)
                if (ariaExpanded == null || "false".equals(ariaExpanded) || ariaExpanded.isEmpty()) {
                    System.out.println("  → Expanding lesson panel " + lessonNumber + "...");
                    safeClick(lessonPanel);
                    delay(1000);
                    System.out.println("✓ Lesson panel expanded");
                } else {
                    System.out.println("  Panel already expanded");
                }
            } else {
                System.out.println("⚠ No expansion panel found for Bài số " + lessonNumber);
                System.out.println("  Trying to find any visible expansion panel...");

                // Fallback: Find any expansion panel that's not expanded
                List<WebElement> allPanels = driver.findElements(
                    By.xpath("//button[contains(@class, 'v-expansion-panel-header')]")
                );

                for (WebElement panel : allPanels) {
                    String ariaExpanded = panel.getAttribute("aria-expanded");
                    if (panel.isDisplayed() && (ariaExpanded == null || "false".equals(ariaExpanded) || ariaExpanded.isEmpty())) {
                        System.out.println("  → Expanding fallback panel...");
                        safeClick(panel);
                        delay(1000);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠ Error finding/expanding lesson panel: " + e.getMessage());
        }

        delay(1000);

        // DEBUG: Show all expansion panels and their states
        System.out.println("\n--- DEBUG: Checking all expansion panels ---");
        try {
            List<WebElement> allPanelHeaders = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header')]")
            );
            System.out.println("Total panels found: " + allPanelHeaders.size());
            for (int i = 0; i < allPanelHeaders.size(); i++) {
                WebElement header = allPanelHeaders.get(i);
                String ariaExpanded = header.getAttribute("aria-expanded");
                String headerText = header.getText().trim();
                System.out.println("  Panel " + i + ": aria-expanded=" + ariaExpanded +
                                 ", visible=" + header.isDisplayed() +
                                 ", text=" + (headerText.length() > 40 ? headerText.substring(0, 40) + "..." : headerText));
            }
        } catch (Exception e) {
            System.out.println("Debug panel check failed: " + e.getMessage());
        }

        // DEBUG: Show all visible input fields
        System.out.println("\n--- DEBUG: All visible input/textarea fields ---");
        try {
            List<WebElement> allInputs = driver.findElements(By.name("title_course_item"));
            System.out.println("Found " + allInputs.size() + " inputs with name='title_course_item'");
            for (int i = 0; i < allInputs.size(); i++) {
                WebElement input = allInputs.get(i);
                System.out.println("  Input " + i + ": displayed=" + input.isDisplayed() +
                                 ", value='" + input.getAttribute("value") + "'");
            }

            List<WebElement> allTextareas = driver.findElements(By.name("description_course_item"));
            System.out.println("Found " + allTextareas.size() + " textareas with name='description_course_item'");
            for (int i = 0; i < allTextareas.size(); i++) {
                WebElement textarea = allTextareas.get(i);
                String value = textarea.getAttribute("value");
                System.out.println("  Textarea " + i + ": displayed=" + textarea.isDisplayed() +
                                 ", value='" + (value.length() > 40 ? value.substring(0, 40) + "..." : value) + "'");
            }
        } catch (Exception e) {
            System.out.println("Debug field check failed: " + e.getMessage());
        }
        System.out.println("--- End DEBUG ---\n");

        // Find the specific expansion panel content for this lesson
        try {
            // Find ALL expanded panels (there might be multiple)
            List<WebElement> expandedPanels = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header') and @aria-expanded='true']/following-sibling::div[contains(@class, 'v-expansion-panel-content')]")
            );

            System.out.println("Found " + expandedPanels.size() + " expanded panel(s)");

            if (expandedPanels.isEmpty()) {
                throw new Exception("No expanded panels found!");
            }

            // Use the LAST expanded panel (most recently expanded = current lesson)
            WebElement expandedPanel = expandedPanels.get(expandedPanels.size() - 1);
            System.out.println("Using panel " + (expandedPanels.size() - 1) + " (last/most recent) for lesson " + lessonNumber);

            // Find fields within THIS specific panel
            List<WebElement> inputsInPanel = expandedPanel.findElements(By.name("title_course_item"));
            List<WebElement> textareasInPanel = expandedPanel.findElements(By.name("description_course_item"));

            System.out.println("Within this panel: " + inputsInPanel.size() + " input(s), " + textareasInPanel.size() + " textarea(s)");

            if (inputsInPanel.isEmpty() || textareasInPanel.isEmpty()) {
                throw new Exception("No fields found within the expanded panel!");
            }

            // Fill lesson title within this specific panel
            System.out.println("Filling lesson title in panel:");
            safeFillWithinParent(expandedPanel, By.name("title_course_item"), lesson.title);

            // Fill lesson description within this specific panel
            System.out.println("Filling lesson description in panel:");
            safeFillWithinParent(expandedPanel, By.name("description_course_item"), lesson.description);

            System.out.println("✓ Lesson " + lessonNumber + " filled successfully in specific panel");

        } catch (Exception e) {
            System.out.println("⚠ Error: " + e.getMessage());
            System.out.println("Stack trace:");
            e.printStackTrace();
            throw new RuntimeException("Failed to fill lesson " + lessonNumber, e);
        }

        // Collapse the panel after filling
        try {
            List<WebElement> expansionPanels = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header')]//strong[contains(text(), 'Bài số " + lessonNumber + "')]")
            );

            if (!expansionPanels.isEmpty()) {
                WebElement lessonPanel = expansionPanels.get(0).findElement(By.xpath("./ancestor::button"));
                System.out.println("Collapsing lesson panel...");
                safeClick(lessonPanel);
                delay(500);
            }
        } catch (Exception e) {
            System.out.println("⚠ Could not collapse panel: " + e.getMessage());
        }
    }

    private void collapseAllPanels() {
        System.out.println("\n=== Collapsing All Panels ===");

        try {
            // Find all expanded panels (aria-expanded="true")
            List<WebElement> expandedPanels = driver.findElements(
                By.xpath("//button[contains(@class, 'v-expansion-panel-header') and @aria-expanded='true']")
            );

            System.out.println("Found " + expandedPanels.size() + " expanded panel(s) to collapse");

            // Collapse each one
            for (int i = expandedPanels.size() - 1; i >= 0; i--) {  // Collapse from bottom to top
                try {
                    WebElement panel = expandedPanels.get(i);
                    String panelText = panel.getText().trim();
                    System.out.println("  Collapsing panel " + i + ": " +
                                     (panelText.length() > 40 ? panelText.substring(0, 40) + "..." : panelText));
                    safeClick(panel);
                    delay(500);
                } catch (Exception e) {
                    System.out.println("  ⚠ Could not collapse panel " + i + ": " + e.getMessage());
                }
            }

            System.out.println("✓ All panels collapsed");
        } catch (Exception e) {
            System.out.println("⚠ Error collapsing panels: " + e.getMessage());
        }
    }

    private void saveChapter() {
        System.out.println("\n=== Saving Chapter ===");

        WebElement btnLuu = wait.until(
            ExpectedConditions.elementToBeClickable(BTN_LUU)
        );
        safeClick(btnLuu);
        System.out.println("✓ Clicked 'Lưu' button");

        // Wait for notification
        delay(2000);

        // Check for notifications (prioritize success over error)
        System.out.println("\n--- Checking Save Notifications ---");
        try {
            // Check for success notification FIRST
            // Success messages: "Đã lưu thông thành công"
            List<WebElement> successNotifications = driver.findElements(
                By.xpath("//*[contains(text(), 'Đã lưu') and contains(text(), 'thành công')]")
            );

            boolean hasSuccess = false;
            if (!successNotifications.isEmpty()) {
                for (WebElement notif : successNotifications) {
                    if (notif.isDisplayed()) {
                        hasSuccess = true;
                        System.out.println("✓ SUCCESS: " + notif.getText());
                    }
                }
            }

            // Only check for error if NO success notification was found
            if (!hasSuccess) {
                List<WebElement> errorNotifications = driver.findElements(
                    By.xpath("//*[contains(text(), 'KHÔNG thành công') or contains(text(), 'không đúng')]")
                );

                if (!errorNotifications.isEmpty()) {
                    System.out.println("❌ ERROR NOTIFICATION FOUND:");
                    for (WebElement notif : errorNotifications) {
                        if (notif.isDisplayed()) {
                            System.out.println("  - " + notif.getText());
                        }
                    }
                } else {
                    System.out.println("⚠ No notification found (may have auto-closed)");
                }
            }

        } catch (Exception e) {
            System.out.println("⚠ Error checking notifications: " + e.getMessage());
        }

        // Click OK button to close the notification
        try {
            System.out.println("\nLooking for OK button...");
            WebElement btnOK = wait.until(
                ExpectedConditions.elementToBeClickable(BTN_OK)
            );
            safeClick(btnOK);
            System.out.println("✓ Clicked OK button");
        } catch (Exception e) {
            System.out.println("⚠ OK button not found or not clickable: " + e.getMessage());
        }

        // Wait 5 seconds before continuing
        System.out.println("Waiting 5 seconds...");
        delay(5000);
    }

    private void verifyChapterExists(ChapterData chapter) {
        System.out.println("\n=== Verifying Chapter Exists ===");

        delay(1000);

        // Search for the chapter by title
        List<WebElement> chapterElements = driver.findElements(
            By.xpath("//div[contains(@class, 'v-expansion-panel')]//div[contains(text(), '" + chapter.title + "')]")
        );

        Assert.assertFalse(chapterElements.isEmpty(),
            "Chapter not found: " + chapter.title);

        System.out.println("✓ Chapter verified: " + chapter.title);
    }

    private void verifyLessonsExist(List<LessonData> lessons) {
        System.out.println("\n=== Verifying Lessons Exist ===");

        delay(1000);

        for (LessonData lesson : lessons) {
            List<WebElement> lessonElements = driver.findElements(
                By.xpath("//div[contains(@class, 'lessons-panel') or contains(@class, 'v-expansion-panel')]//div[contains(text(), '" + lesson.title + "')]")
            );

            Assert.assertFalse(lessonElements.isEmpty(),
                "Lesson not found: " + lesson.title);

            System.out.println("✓ Lesson verified: " + lesson.title);
        }
    }

    // =======================
    // Main Test
    // =======================
    @Test
    public void testAddingCourseContent() throws Exception {
        System.out.println("======================================");
        System.out.println("START TEST - Adding Course Content");
        System.out.println("======================================\n");

        // Load data from JSON
        List<ChapterData> allChapters = loadChaptersFromJSON();
        List<LessonData> allLessons = loadLessonsFromJSON();

        // Randomly select 1 chapter from 3 chapters
        Random random = new Random();
        int randomChapterIndex = random.nextInt(allChapters.size());
        ChapterData selectedChapter = allChapters.get(randomChapterIndex);

        System.out.println("\n=== Selected Random Chapter ===");
        System.out.println("Chapter " + (randomChapterIndex + 1) + "/" + allChapters.size() + ": " + selectedChapter.title);

        // Select 2 lessons randomly from available lessons
        List<LessonData> selectedLessons = new ArrayList<>();
        List<Integer> usedIndices = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            int randomLessonIndex;
            do {
                randomLessonIndex = random.nextInt(allLessons.size());
            } while (usedIndices.contains(randomLessonIndex));

            usedIndices.add(randomLessonIndex);
            selectedLessons.add(allLessons.get(randomLessonIndex));
        }

        System.out.println("\n=== Selected 2 Random Lessons ===");
        for (int i = 0; i < selectedLessons.size(); i++) {
            System.out.println("Lesson " + (i + 1) + ": " + selectedLessons.get(i).title);
        }

        // Initialize browser
        System.out.println("\n=== Initializing Chrome Browser ===");
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        driver.manage().window().maximize();

        try {
            // Login as Admin
            loginAsAdmin();

            // Navigate to Course Management
            navigateToCourseManagement();

            // Select random course
            String courseName = selectRandomCourse();

            // Click "Nội dung môn học" tab
            clickNoiDungMonHocTab();

            // Add chapter
            addChapter(selectedChapter);

            // Add 2 lessons
            for (int i = 0; i < selectedLessons.size(); i++) {
                addLesson(selectedLessons.get(i), i + 1);
            }

            // Collapse all lesson and chapter panels before saving
            collapseAllPanels();

            // Save chapter
            saveChapter();

            // Verify chapter exists
            verifyChapterExists(selectedChapter);

            // Verify lessons exist
            verifyLessonsExist(selectedLessons);

            System.out.println("\n======================================");
            System.out.println("=== TEST COMPLETED SUCCESSFULLY ===");
            System.out.println("======================================");
            System.out.println("Summary:");
            System.out.println("  ✓ Course selected: " + courseName);
            System.out.println("  ✓ Chapter added: " + selectedChapter.title);
            System.out.println("  ✓ Lessons added: " + selectedLessons.size());
            for (int i = 0; i < selectedLessons.size(); i++) {
                System.out.println("    - Lesson " + (i + 1) + ": " + selectedLessons.get(i).title);
            }
            System.out.println("======================================\n");

        } catch (Exception e) {
            System.out.println("\n❌ TEST FAILED");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (driver != null) {
                System.out.println("\nClosing browser...");
                delay(3000); // Keep browser open for 3 seconds to see result
                driver.quit();
            }
        }
    }
}
