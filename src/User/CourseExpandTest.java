package User;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CourseExpandTest {

    WebDriver driver;
    WebDriverWait wait;

    private AtomicInteger failCount = new AtomicInteger(0);
    private String logFilePath;

    // Class để lưu thông tin chương và bài học
    private static class ChapterInfo {
        String chapterName;
        List<String> lessons;

        ChapterInfo(String chapterName) {
            this.chapterName = chapterName;
            this.lessons = new ArrayList<>();
        }
    }

    private void delay() throws InterruptedException {
        Thread.sleep(800);
    }

    // Chuẩn hóa chuỗi để so sánh:
    // - Bỏ dấu tiếng Việt
    // - Bỏ "Bài số X"
    // - Bỏ khoảng trắng, ký tự đặc biệt
    // - Giữ lại chữ và số
    private String normalize(String text) {

        text = text.toLowerCase()
                   .replaceAll("bài\\s*số\\s*\\d+\\s*:", "")
                   .replace("chương", "chuong");

        // Bỏ dấu tiếng Việt
        String temp = Normalizer.normalize(text, Normalizer.Form.NFD);
        temp = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
                      .matcher(temp)
                      .replaceAll("");

        // Chỉ giữ chữ và số
        temp = temp.replaceAll("[^a-z0-9]", "");

        return temp.trim();
    }

    // Đọc dữ liệu mong đợi từ src/resources/data.txt
    // Hỗ trợ các dòng bị xuống hàng giữa tên bài học
    private Set<String> readExpectedData() throws Exception {

        String projectPath = System.getProperty("user.dir");
        String filePath = projectPath + "/src/resources/data.txt";

        System.out.println("Đang tải dữ liệu mong đợi từ: " + filePath);

        List<String> lines = Files.readAllLines(Paths.get(filePath));
        Set<String> expected = new HashSet<>();

        StringBuilder block = new StringBuilder();

        for (String rawLine : lines) {

            String line = rawLine.trim();

            // Gặp header / separator / dòng trống -> kết thúc block hiện tại (nếu có)
            if (line.isEmpty()
                    || line.startsWith("===")
                    || line.startsWith("---")
                    || line.startsWith("DANH SÁCH")) {

                if (block.length() > 0) {
                    processDataBlock(block.toString(), expected);
                    block.setLength(0);
                }
                continue;
            }

            // Bắt đầu một chương mới -> flush block cũ trước
            if (line.toUpperCase().startsWith("CHƯƠNG")) {
                if (block.length() > 0) {
                    processDataBlock(block.toString(), expected);
                    block.setLength(0);
                }
            }

            if (block.length() > 0) {
                block.append(' ');
            }
            block.append(line);
        }

        // Flush block cuối cùng
        if (block.length() > 0) {
            processDataBlock(block.toString(), expected);
        }

        System.out.println("Tổng số mục mong đợi = " + expected.size());
        return expected;
    }

    // Xử lý một block dữ liệu (một chương + các bài, có thể trải ra nhiều dòng)
    private void processDataBlock(String block, Set<String> expected) {

        if (block.toUpperCase().contains("CHƯƠNG")) {

            String[] parts = block.split("\\[BÀI");

            // Chương
            String chapterPart = parts[0].trim();
            if (!chapterPart.isEmpty()) {
                expected.add(normalize(chapterPart));
            }

            // Các bài phía sau
            for (int i = 1; i < parts.length; i++) {

                String lesson = parts[i]
                        .replaceAll("\\d+\\]", "")
                        .trim();

                if (!lesson.isEmpty()) {
                    expected.add(normalize(lesson));
                }
            }
        } else {
            // Block chỉ chứa bài học (không có chữ "CHƯƠNG")
            String[] parts = block.split("\\[BÀI");

            for (int i = 1; i < parts.length; i++) {

                String lesson = parts[i]
                        .replaceAll("\\d+\\]", "")
                        .trim();

                if (!lesson.isEmpty()) {
                    expected.add(normalize(lesson));
                }
            }
        }
    }

    // Log PASS / FAIL
    private void logResult(String type, String name, boolean pass) {
        String line;
        if (pass) {
            line = "[PASS] " + type + " = " + name;
        } else {
            line = "[FAIL] " + type + " = " + name;
            failCount.incrementAndGet();
        }

        // In ra console
        System.out.println(line);

        // Ghi thêm vào log.txt (nếu đã được khởi tạo đường dẫn)
        try {
            if (logFilePath != null) {
                Files.write(
                        Paths.get(logFilePath),
                        (line + System.lineSeparator()).getBytes("UTF-8"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }
        } catch (IOException e) {
            // Không làm test fail, chỉ log lỗi ghi file
            System.out.println("Không thể ghi log vào file: " + e.getMessage());
        }
    }

    // Click an toàn bằng JavaScript
    private void safeClick(WebElement element) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView(true);", element);
        Thread.sleep(300);
        js.executeScript("arguments[0].click();", element);
        delay();
    }

    private String getReportsDir() {
        return System.getProperty("user.dir") + "/test-reports";
    }

    // Tạo tên log theo mẫu: TenClass_ddMMyy_solan.txt (vd: CourseExpandTest_290126_01.txt)
    private String buildNextLogFilePath() throws IOException {
        String reportsDir = getReportsDir();
        Files.createDirectories(Paths.get(reportsDir));

        String className = this.getClass().getSimpleName();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy"));

        Pattern p = Pattern.compile(Pattern.quote(className) + "_" + Pattern.quote(dateStr) + "_(\\d{2})\\.txt");
        int maxSeq = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(reportsDir), className + "_" + dateStr + "_*.txt")) {
            for (Path f : stream) {
                String fileName = f.getFileName().toString();
                Matcher m = p.matcher(fileName);
                if (m.matches()) {
                    int seq = Integer.parseInt(m.group(1));
                    if (seq > maxSeq) {
                        maxSeq = seq;
                    }
                }
            }
        }

        int nextSeq = maxSeq + 1;
        String seqStr = String.format("%02d", nextSeq);
        return reportsDir + "/" + className + "_" + dateStr + "_" + seqStr + ".txt";
    }

    // Ghi dữ liệu thực tế vào file compare.txt (trong thư mục test-reports)
    private void writeCompareFile(List<ChapterInfo> chapters) throws IOException {
        String reportsDir = getReportsDir();
        Files.createDirectories(Paths.get(reportsDir));
        String filePath = reportsDir + "/compare.txt";

        // Xóa file nếu đã tồn tại
        Files.deleteIfExists(Paths.get(filePath));

        StringBuilder content = new StringBuilder();
        content.append("DANH SÁCH CHƯƠNG & BÀI HỌC\n");
        content.append("\n");
        content.append("==============================\n");
        content.append("\n");

        for (int i = 0; i < chapters.size(); i++) {
            ChapterInfo chapter = chapters.get(i);

            // Tạo dòng chương với các bài học
            // Bỏ "Chương X:" ở đầu tên chương, chỉ giữ phần tên thực tế
            String chapterTitle = chapter.chapterName.replaceAll("^Chương\\s*\\d+\\s*:\\s*", "");
            
            StringBuilder chapterLine = new StringBuilder();
            chapterLine.append("CHƯƠNG ").append(i + 1).append(" | ").append(chapterTitle);

            // Thêm các bài học
            for (int j = 0; j < chapter.lessons.size(); j++) {
                String lessonName = chapter.lessons.get(j);
                // Bỏ "Bài số X:" khi ghi vào file (chỉ giữ tên bài)
                String lessonTitle = lessonName.replaceAll("^Bài\\s*số\\s*\\d+\\s*:\\s*", "");
                chapterLine.append(" [BÀI ").append(j + 1).append("] ").append(lessonTitle);
            }

            String chapterLineStr = chapterLine.toString();

            // Chia dòng nếu quá dài (giữ format tương tự data.txt)
            if (chapterLineStr.length() > 100) {
                // Tìm vị trí để xuống dòng một cách hợp lý
                int lastSpace = chapterLineStr.lastIndexOf(" [BÀI", 100);
                if (lastSpace > 0) {
                    content.append(chapterLineStr.substring(0, lastSpace)).append("\n");
                    String remaining = chapterLineStr.substring(lastSpace + 1);
                    // Tiếp tục chia nếu còn dài
                    while (remaining.length() > 100) {
                        int nextSpace = remaining.indexOf(" [BÀI", 100);
                        if (nextSpace > 0) {
                            content.append(remaining.substring(0, nextSpace)).append("\n");
                            remaining = remaining.substring(nextSpace + 1);
                        } else {
                            break;
                        }
                    }
                    content.append(remaining);
                } else {
                    content.append(chapterLineStr);
                }
            } else {
                content.append(chapterLineStr);
            }

            content.append("\n");
            content.append("\n");

            // Thêm separator (trừ chương cuối)
            if (i < chapters.size() - 1) {
                content.append("------------------------------------------------------------------------\n");
                content.append("\n");
            }
        }

        // Ghi file
        Files.write(Paths.get(filePath), content.toString().getBytes("UTF-8"));
        System.out.println("Đã tạo file so sánh (compare.txt): " + filePath);
    }

    // Đọc dữ liệu từ compare.txt và so sánh với data.txt
    private void compareFiles() throws Exception {
        String reportsDir = getReportsDir();
        Files.createDirectories(Paths.get(reportsDir));
        String comparePath = reportsDir + "/compare.txt";
        String projectPath = System.getProperty("user.dir");
        String dataPath = projectPath + "/src/resources/data.txt";

        System.out.println("Bắt đầu so sánh file dữ liệu:");
        System.out.println("  File thực tế (compare): " + comparePath);
        System.out.println("  File mong đợi (data): " + dataPath);

        // Đọc compare.txt
        Set<String> compareData = readDataFromFile(comparePath);
        // Đọc data.txt
        Set<String> expectedData = readDataFromFile(dataPath);

        // So sánh
        Set<String> missingInCompare = new HashSet<>(expectedData);
        missingInCompare.removeAll(compareData);

        Set<String> extraInCompare = new HashSet<>(compareData);
        extraInCompare.removeAll(expectedData);

        StringBuilder diffLog = new StringBuilder();

        if (missingInCompare.isEmpty() && extraInCompare.isEmpty()) {
            String msg = "Hai file dữ liệu TRÙNG KHỚP 100%.";
            System.out.println(msg);
            diffLog.append(msg).append(System.lineSeparator());
        } else {
            if (!missingInCompare.isEmpty()) {
                String msg = "Thiếu trong compare.txt (" + missingInCompare.size() + " mục): " + missingInCompare;
                System.out.println(msg);
                diffLog.append(msg).append(System.lineSeparator());
            }
            if (!extraInCompare.isEmpty()) {
                String msg = "Dư trong compare.txt (" + extraInCompare.size() + " mục): " + extraInCompare;
                System.out.println(msg);
                diffLog.append(msg).append(System.lineSeparator());
            }
        }

        // Ghi phần chênh lệch vào log.txt
        if (logFilePath != null && diffLog.length() > 0) {
            Files.write(
                    Paths.get(logFilePath),
                    (System.lineSeparator() + "=== CHÊNH LỆCH GIỮA HAI FILE ===" + System.lineSeparator()
                            + diffLog).getBytes("UTF-8"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        }
    }

    // Đọc dữ liệu từ một file (dùng chung cho cả compare.txt và data.txt)
    private Set<String> readDataFromFile(String filePath) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        Set<String> data = new HashSet<>();

        StringBuilder block = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.isEmpty()
                    || line.startsWith("===")
                    || line.startsWith("---")
                    || line.startsWith("DANH SÁCH")) {
                if (block.length() > 0) {
                    processDataBlock(block.toString(), data);
                    block.setLength(0);
                }
                continue;
            }

            if (line.toUpperCase().startsWith("CHƯƠNG")) {
                if (block.length() > 0) {
                    processDataBlock(block.toString(), data);
                    block.setLength(0);
                }
            }

            if (block.length() > 0) {
                block.append(' ');
            }
            block.append(line);
        }

        if (block.length() > 0) {
            processDataBlock(block.toString(), data);
        }

        return data;
    }

    @Test
    public void testExpandCourseAndVerify() throws Exception {

        System.out.println("START TEST");

        // Khởi tạo đường dẫn file log trong thư mục test-reports theo mẫu TenClass_ddMMyy_solan.txt
        logFilePath = buildNextLogFilePath();
        Files.write(
                Paths.get(logFilePath),
                "=== LOG KIỂM TRA KHÓA HỌC LẬP TRÌNH WEB ===\n\n".getBytes("UTF-8"),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println("Khởi tạo trình duyệt Chrome và WebDriverWait.");
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        driver.manage().window().maximize();

        // Login
        System.out.println("Mở trang đăng nhập hệ thống.");
        driver.get("https://elearning.plt.pro.vn/dang-nhap?redirect=%2Ftrang-chu");

        System.out.println("Nhập email đăng nhập.");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("input-10")))
                .sendKeys("test1.pltsolutions@gmail.com");

        System.out.println("Nhập mật khẩu đăng nhập.");
        driver.findElement(By.id("input-13"))
                .sendKeys("plt@intern_051224");

        System.out.println("Click nút 'Đăng nhập'.");
        driver.findElement(By.xpath("//span[contains(text(),'Đăng nhập')]")).click();

        System.out.println("Chờ chuyển hướng sang trang chủ.");
        wait.until(ExpectedConditions.urlContains("/trang-chu"));
        delay();

        // Click course
        System.out.println("Mở khóa học 'Lập trình Web cơ bản'.");
        WebElement course = wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.xpath("//span[contains(text(),'Lập trình Web')]")
                ));
        safeClick(course);

        // Thu thập dữ liệu từ website
        List<ChapterInfo> chaptersData = new ArrayList<>();

        // Lấy danh sách CHƯƠNG (không lấy lessons-panel)
        System.out.println("Lấy danh sách tất cả chương trong khóa học.");
        List<WebElement> chapters = wait.until(
                ExpectedConditions.presenceOfAllElementsLocatedBy(
                        By.cssSelector(".v-expansion-panel:not(.lessons-panel)")
                ));

        for (int i = 0; i < chapters.size(); i++) {

            chapters = driver.findElements(
                    By.cssSelector(".v-expansion-panel:not(.lessons-panel)")
            );

            WebElement chapter = chapters.get(i);

            WebElement chapterHeader =
                    chapter.findElement(By.cssSelector(
                            "button.v-expansion-panel-header div.white--text"));

            String chapterName = chapterHeader.getText().trim();
            System.out.println("Mở chương " + (i + 1) + ": " + chapterName);
            
            // Tạo ChapterInfo và lưu tên chương
            ChapterInfo chapterInfo = new ChapterInfo(chapterName);

            // Mở chương
            safeClick(chapterHeader);

            // Lấy danh sách bài trong chương
            System.out.println("  Lấy danh sách bài học trong chương này.");
            List<WebElement> lessons = chapter.findElements(
                    By.cssSelector(
                            ".lessons-panel button.v-expansion-panel-header div.white--text"));

            for (WebElement lesson : lessons) {
                String lessonName = lesson.getText().trim();
                System.out.println("    Đang ghi nhận bài học: " + lessonName);
                // Lưu tên bài học đầy đủ (bao gồm "Bài số X:")
                chapterInfo.lessons.add(lessonName);
            }

            chaptersData.add(chapterInfo);
        }

        // Ghi dữ liệu vào compare.txt
        System.out.println("Ghi danh sách chương/bài học lấy từ website vào file compare.txt.");
        writeCompareFile(chaptersData);

        // Đọc expected data từ data.txt để log chi tiết
        System.out.println("Đọc dữ liệu mong đợi từ file data.txt để so sánh.");
        Set<String> expectedData = readExpectedData();

        // Log kết quả chi tiết
        System.out.println("\n=== KẾT QUẢ SO SÁNH CHI TIẾT ===");
        
        for (ChapterInfo chapterInfo : chaptersData) {
            String normalizedChapter = normalize(chapterInfo.chapterName);
            boolean chapterPass = expectedData.contains(normalizedChapter);
            logResult("CHAPTER", chapterInfo.chapterName, chapterPass);

            for (String lessonName : chapterInfo.lessons) {
                // lessonName đã bao gồm "Bài số X:"
                String normalizedLesson = normalize(lessonName);
                boolean lessonPass = expectedData.contains(normalizedLesson);
                logResult("LESSON", lessonName, lessonPass);
            }
        }

        // So sánh compare.txt với data.txt
        System.out.println("\nBắt đầu bước so sánh 2 file dữ liệu (compare.txt và data.txt).");
        compareFiles();

        System.out.println("Tổng số mục kiểm tra FAILED = " + failCount.get());

        // Ghi tổng kết vào log.txt
        if (logFilePath != null) {
            StringBuilder summary = new StringBuilder();
            summary.append(System.lineSeparator())
                   .append("=== TỔNG KẾT ===").append(System.lineSeparator())
                   .append("Tổng số mục kiểm tra FAILED = ").append(failCount.get()).append(System.lineSeparator());

            // Nếu có lỗi, ghi thêm dòng lỗi tiếng Anh giống Assert để tiện tra cứu
            if (failCount.get() > 0) {
                summary.append(System.lineSeparator())
                       .append("java.lang.AssertionError: There are failed validations. Check console log. ")
                       .append("expected [0] but found [").append(failCount.get()).append("]")
                       .append(System.lineSeparator())
                       .append("    at User.CourseExpandTest.testExpandCourseAndVerify(CourseExpandTest.java)")
                       .append(System.lineSeparator());
            }

            Files.write(
                    Paths.get(logFilePath),
                    summary.toString().getBytes("UTF-8"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        }

        driver.quit();

        // Không làm test FAILED nữa, chỉ log ra kết quả
     // Đánh giá kết quả bằng TestNG Assert
        Assert.assertEquals(
            failCount.get(),
            0,
            "There are failed validations. Please check log file."
        );
    }
}
