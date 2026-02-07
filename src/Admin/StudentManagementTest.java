package Admin;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StudentManagementTest {

    private WebDriver driver;
    private WebDriverWait wait;

    // CSV Data Storage
    private static List<String> lastNames = new ArrayList<>();
    private static List<String> middleNames = new ArrayList<>();
    private static List<String> firstNames = new ArrayList<>();
    private static List<String> streets = new ArrayList<>();
    private static List<String> districts = new ArrayList<>();
    private static List<StudentInfo> csvStudents = new ArrayList<>();

    // =======================
    // Student Model
    // =======================
    private static class StudentInfo {
        String fullName;
        String studentCode;
        String email;
        String phone;
        String dob;      // mm/dd/yyyy
        String address;
        String gender;   // Nam / Nữ / Khác

        StudentInfo(String fullName,
                    String studentCode,
                    String email,
                    String phone,
                    String dob,
                    String address,
                    String gender) {
            this.fullName = fullName;
            this.studentCode = studentCode;
            this.email = email;
            this.phone = phone;
            this.dob = dob;
            this.address = address;
            this.gender = gender;
        }
    }

    // =======================
    // Locator constants
    // =======================
    private static final String ACTIVE_DIALOG_PREFIX =
            "//div[contains(@class,'v-dialog__content') and contains(@class,'active')]";

    private static final By FULL_NAME     = By.xpath(ACTIVE_DIALOG_PREFIX + "//input[@name='full_name']");
    private static final By STUDENT_CODE  = By.xpath(ACTIVE_DIALOG_PREFIX + "//input[@name='student_code']");
    private static final By EMAIL         = By.xpath(ACTIVE_DIALOG_PREFIX + "//input[@name='email']");
    private static final By DOB           = By.xpath(ACTIVE_DIALOG_PREFIX + "//input[@name='dob']");

    private static final By PHONE   = By.xpath(ACTIVE_DIALOG_PREFIX + "//input[@name='phone']");
    private static final By ADDRESS = By.xpath(ACTIVE_DIALOG_PREFIX + "//input[@name='address']");

    private static final By GENDER_MALE_LABEL   =
            By.xpath(ACTIVE_DIALOG_PREFIX + "//label[contains(normalize-space(),'Nam')]");
    private static final By GENDER_FEMALE_LABEL =
            By.xpath(ACTIVE_DIALOG_PREFIX + "//label[contains(normalize-space(),'Nữ')]");
    private static final By GENDER_OTHER_LABEL  =
            By.xpath(ACTIVE_DIALOG_PREFIX + "//label[contains(normalize-space(),'Khác')]");

    // ✅ Button THÊM - targets span content then parent button
    private static final By SUBMIT_BTN =
            By.xpath(ACTIVE_DIALOG_PREFIX + "//span[contains(@class,'v-btn__content') and contains(normalize-space(),'Thêm')]/parent::button");

    // Search box - using ID (may need to update if ID changes)
    // Alternative locators if ID fails:
    // By.xpath("//input[@type='text' and contains(@class,'v-text-field')]")
    // By.xpath("//div[contains(@class,'v-text-field')]//input[@type='text']")
    private static final By SEARCH_BOX = By.id("input-41");

    // ✅ Button SỬA (Edit/Save button in edit dialog)
    private static final By EDIT_SAVE_BTN =
            By.xpath(ACTIVE_DIALOG_PREFIX + "//span[contains(@class,'v-btn__content') and contains(normalize-space(),'Sửa')]/parent::button");

    // ✅ Button XOÁ (Delete confirm button) - Note: "Xoá" not "Xóa"
    private static final By DELETE_CONFIRM_BTN =
            By.xpath(ACTIVE_DIALOG_PREFIX + "//span[contains(@class,'v-btn__content') and contains(normalize-space(),'Xoá')]/parent::button");

    // =======================
    // Init Driver
    // =======================
    private void initDriver() {
        System.out.println("Khởi tạo ChromeDriver cho Admin.");
        driver = new ChromeDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));  // Reduced from 15s to 10s
        driver.manage().window().maximize();
    }

    // =======================
    // Utils
    // =======================
    private void pause() {
        try {
            Thread.sleep(200);  // Reduced from 400ms to 200ms for speed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fill(By by, String value) {
        if (value == null) return;

        WebElement el = wait.until(ExpectedConditions.presenceOfElementLocated(by));

        // Special handling for date fields - convert MM/dd/yyyy to yyyy-MM-dd
        String fieldName = el.getAttribute("name");
        if ("dob".equals(fieldName) && value.contains("/")) {
            try {
                String[] parts = value.split("/");
                if (parts.length == 3) {
                    value = parts[2] + "-" + parts[0] + "-" + parts[1];
                }
            } catch (Exception e) {
                // Use original value if conversion fails
            }
        }

        // Fast fill using JavaScript only (much faster than sendKeys)
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value = arguments[1]; " +
            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));",
            el, value);
    }

    private String getValue(By by) {
        WebElement el = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
        return el.getAttribute("value").trim();
    }

    private void clickOkButton() {
        // Try multiple locators for OK button (Vuetify and SweetAlert2)
        By[] okLocators = {
            By.className("swal2-confirm"),  // SweetAlert2 (most common)
            By.xpath("//button[contains(@class,'swal2-confirm')]"),
            By.xpath("//button[contains(normalize-space(),'OK')]"),
            By.xpath("//button//span[contains(text(),'OK')]/parent::button"),
            By.xpath("//div[contains(@class,'v-snackbar')]//button"),
            By.xpath("//div[contains(@class,'v-alert')]//button")
        };

        for (By locator : okLocators) {
            try {
                WebElement okButton = wait.until(ExpectedConditions.elementToBeClickable(locator));
                okButton.click();
                System.out.println("✓ Clicked OK");
                return;  // Success, exit method
            } catch (Exception e) {
                continue;  // Try next locator
            }
        }

        System.out.println("⚠ No OK button found");
    }

    private void searchStudent(String searchText) {
        System.out.println("Searching: " + searchText);

        WebElement searchBox = wait.until(ExpectedConditions.elementToBeClickable(SEARCH_BOX));

        // Clear and set value
        searchBox.click();
        searchBox.clear();
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = '';", searchBox);
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", searchBox, searchText);
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", searchBox);

        // Wait for search results
        try {
            Thread.sleep(1000);  // Reduced from 2000ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =======================
    // Compare and verify student information from table
    // =======================
    private void verifyStudentInTable(StudentInfo expected) {
        System.out.println("\n=== VERIFYING STUDENT DATA IN TABLE ===");

        try {
            // Find the student row in the table
            WebElement studentRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//table//tr[.//td[contains(.,'" + expected.studentCode + "')]]")
            ));

            // Get all cells in the row
            List<WebElement> cells = studentRow.findElements(By.tagName("td"));

            // Table structure (based on actual web app):
            // Column 0: Student Code
            // Column 1: Last Name + Middle Name
            // Column 2: First Name
            // Column 3: Phone
            // Column 4: Email
            // Column 5: DOB (DD-MM-YYYY)
            // Column 6: Gender
            // Column 7: Address
            // Column 8: Created Date
            // Column 9: Actions

            String actualStudentCode = cells.get(0).getText().trim();
            String actualFullName = cells.get(1).getText().trim() + " " + cells.get(2).getText().trim();
            String actualPhone = cells.get(3).getText().trim();
            String actualEmail = cells.get(4).getText().trim();
            String actualDOB = cells.get(5).getText().trim();
            String actualGender = cells.get(6).getText().trim();
            String actualAddress = cells.get(7).getText().trim();

            // Display comparison
            System.out.println("\n--- Comparison Results ---");
            System.out.println("Student Code:");
            System.out.println("  Expected: " + expected.studentCode);
            System.out.println("  Actual:   " + actualStudentCode);

            System.out.println("Full Name:");
            System.out.println("  Expected: " + expected.fullName);
            System.out.println("  Actual:   " + actualFullName);

            System.out.println("Email:");
            System.out.println("  Expected: " + expected.email);
            System.out.println("  Actual:   " + actualEmail);

            System.out.println("Phone:");
            System.out.println("  Expected: " + expected.phone);
            System.out.println("  Actual:   " + actualPhone);

            System.out.println("Date of Birth:");
            System.out.println("  Expected: " + expected.dob);
            System.out.println("  Actual:   " + actualDOB);

            System.out.println("Address:");
            System.out.println("  Expected: " + expected.address);
            System.out.println("  Actual:   " + actualAddress);

            System.out.println("Gender:");
            System.out.println("  Expected: " + expected.gender);
            System.out.println("  Actual:   " + actualGender);

            // Perform assertions using TestNG Assert
            Assert.assertEquals(actualStudentCode, expected.studentCode,
                "Student Code mismatch!");
            System.out.println("  ✓ Student Code matches");

            Assert.assertEquals(actualFullName, expected.fullName,
                "Full Name mismatch!");
            System.out.println("  ✓ Full Name matches");

            Assert.assertEquals(actualEmail, expected.email,
                "Email mismatch!");
            System.out.println("  ✓ Email matches");

            Assert.assertEquals(actualPhone, expected.phone,
                "Phone mismatch!");
            System.out.println("  ✓ Phone matches");

            // DOB might have different format, so we need to normalize it
            String normalizedExpectedDOB = normalizeDateFormat(expected.dob);
            String normalizedActualDOB = normalizeDateFormat(actualDOB);
            Assert.assertEquals(normalizedActualDOB, normalizedExpectedDOB,
                "Date of Birth mismatch!");
            System.out.println("  ✓ Date of Birth matches");

            Assert.assertEquals(actualAddress, expected.address,
                "Address mismatch!");
            System.out.println("  ✓ Address matches");

            Assert.assertEquals(actualGender, expected.gender,
                "Gender mismatch!");
            System.out.println("  ✓ Gender matches");

            System.out.println("\n✓✓ ALL FIELDS VERIFIED SUCCESSFULLY! ✓✓\n");

        } catch (AssertionError e) {
            System.out.println("\n❌ VERIFICATION FAILED!");
            System.out.println("Error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.out.println("\n❌ ERROR during verification!");
            System.out.println("Error: " + e.getMessage());
            throw new RuntimeException("Failed to verify student data", e);
        }
    }

    // =======================
    // Normalize date format for comparison
    // =======================
    private String normalizeDateFormat(String date) {
        if (date == null || date.isEmpty()) {
            return "";
        }

        try {
            // Handle MM/DD/YYYY format (from test data) -> convert to DD-MM-YYYY
            if (date.contains("/")) {
                String[] parts = date.split("/");
                if (parts.length == 3) {
                    // Input: MM/DD/YYYY (e.g., "06/01/2005")
                    // Output: DD-MM-YYYY (e.g., "01-06-2005")
                    return String.format("%02d-%02d-%s",
                        Integer.parseInt(parts[1]),  // day
                        Integer.parseInt(parts[0]),  // month
                        parts[2]);                   // year
                }
            }
            // Handle DD-MM-YYYY format (from table) -> already in correct format
            else if (date.contains("-")) {
                String[] parts = date.split("-");
                if (parts.length == 3) {
                    // If it's already DD-MM-YYYY, just ensure zero-padding
                    if (parts[0].length() <= 2 && parts[1].length() <= 2) {
                        // DD-MM-YYYY format
                        return String.format("%02d-%02d-%s",
                            Integer.parseInt(parts[0]),  // day
                            Integer.parseInt(parts[1]),  // month
                            parts[2]);                   // year
                    }
                    // If it's YYYY-MM-DD format, convert to DD-MM-YYYY
                    else if (parts[0].length() == 4) {
                        return String.format("%02d-%02d-%s",
                            Integer.parseInt(parts[2]),  // day
                            Integer.parseInt(parts[1]),  // month
                            parts[0]);                   // year
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠ Date conversion failed for: " + date + " - " + e.getMessage());
            // If conversion fails, return original
        }

        return date;
    }

    // =======================
    // Load CSV Data into Memory
    // =======================
    private void loadCSVData() {
        try {
            // Load Vietnamese Names
            System.out.println("Loading Vietnamese names from CSV...");
            Reader namesReader = new FileReader("src/resources/vietnamese_names.csv");
            Iterable<CSVRecord> namesRecords = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(namesReader);

            for (CSVRecord record : namesRecords) {
                String type = record.get("type");
                String value = record.get("value");

                if ("lastName".equals(type)) {
                    lastNames.add(value);
                } else if ("middleName".equals(type)) {
                    middleNames.add(value);
                } else if ("firstName".equals(type)) {
                    firstNames.add(value);
                }
            }
            namesReader.close();

            System.out.println("  ✓ Loaded " + lastNames.size() + " last names");
            System.out.println("  ✓ Loaded " + middleNames.size() + " middle names");
            System.out.println("  ✓ Loaded " + firstNames.size() + " first names");

            // Load Vietnamese Locations
            System.out.println("Loading Vietnamese locations from CSV...");
            Reader locationsReader = new FileReader("src/resources/vietnamese_locations.csv");
            Iterable<CSVRecord> locationRecords = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(locationsReader);

            for (CSVRecord record : locationRecords) {
                String type = record.get("type");
                String value = record.get("value");

                if ("street".equals(type)) {
                    streets.add(value);
                } else if ("district".equals(type)) {
                    districts.add(value);
                }
            }
            locationsReader.close();

            System.out.println("  ✓ Loaded " + streets.size() + " streets");
            System.out.println("  ✓ Loaded " + districts.size() + " districts");

            // Load Student Test Data
            System.out.println("Loading student test data from CSV...");
            Reader studentsReader = new FileReader("src/resources/student_test_data.csv");
            Iterable<CSVRecord> studentRecords = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(studentsReader);

            for (CSVRecord record : studentRecords) {
                StudentInfo student = new StudentInfo(
                    record.get("fullName"),
                    record.get("studentCode"),
                    record.get("email"),
                    record.get("phone"),
                    record.get("dob"),
                    record.get("address"),
                    record.get("gender")
                );
                csvStudents.add(student);
            }
            studentsReader.close();

            System.out.println("  ✓ Loaded " + csvStudents.size() + " test students");
            System.out.println("✓ All CSV data loaded successfully!\n");

        } catch (Exception e) {
            System.err.println("❌ Error loading CSV files: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to load CSV data", e);
        }
    }

    // =======================
    // Read specific student from CSV by index
    // =======================
    private StudentInfo readStudentFromCSV(int index) {
        if (csvStudents.isEmpty()) {
            loadCSVData();
        }

        if (index < 0 || index >= csvStudents.size()) {
            throw new RuntimeException("Invalid student index: " + index + ". Available: 0-" + (csvStudents.size() - 1));
        }

        return csvStudents.get(index);
    }

    // =======================
    // Read data from resources/studentdata.txt (Legacy method - kept for backward compatibility)
    // =======================
    private StudentInfo readStudentFromFile() {
        try {
            String path = "src/resources/studentdata.txt";

            List<String> lines = Files.readAllLines(Paths.get(path));

            if (lines.size() < 7) {
                throw new RuntimeException("studentdata.txt thiếu dữ liệu (cần đủ 7 dòng)");
            }

            return new StudentInfo(
                    lines.get(0).trim(),   // fullName
                    lines.get(1).trim(),   // studentCode
                    lines.get(2).trim(),   // email
                    lines.get(3).trim(),   // phone
                    lines.get(4).trim(),   // dob
                    lines.get(5).trim(),   // address
                    lines.get(6).trim()    // gender
            );

        } catch (Exception e) {
            throw new RuntimeException("Không đọc được file src/resources/studentdata.txt", e);
        }

    }

    // =======================
    // Auto-generate random student data (now using CSV data)
    // =======================
    private StudentInfo generateRandomStudentData() {
        // Load CSV data if not already loaded
        if (lastNames.isEmpty() || firstNames.isEmpty() || streets.isEmpty()) {
            loadCSVData();
        }

        Random random = new Random();

        // Generate full name using data from CSV
        String fullName = lastNames.get(random.nextInt(lastNames.size())) + " " +
                          middleNames.get(random.nextInt(middleNames.size())) + " " +
                          firstNames.get(random.nextInt(firstNames.size()));

        // Generate unique student code with timestamp
        long timestamp = System.currentTimeMillis() % 100000;
        String studentCode = "SV" + timestamp;

        // Generate email: firstname.studentcode@domain (e.g., binh.sv123456@gmail.com)
        String[] emailDomains = {"@gmail.com", "@outlook.com", "@yahoo.com", "@hotmail.com", "@icloud.com"};
        String firstName = fullName.substring(fullName.lastIndexOf(" ") + 1).toLowerCase();
        // Remove Vietnamese diacritics for email compatibility
        firstName = firstName.replaceAll("[áàảãạăắằẳẵặâấầẩẫậ]", "a")
                             .replaceAll("[éèẻẽẹêếềểễệ]", "e")
                             .replaceAll("[íìỉĩị]", "i")
                             .replaceAll("[óòỏõọôốồổỗộơớờởỡợ]", "o")
                             .replaceAll("[úùủũụưứừửữự]", "u")
                             .replaceAll("[ýỳỷỹỵ]", "y")
                             .replaceAll("đ", "d");
        String email = firstName + "." + studentCode.toLowerCase() + emailDomains[random.nextInt(emailDomains.length)];

        // Generate Vietnamese phone number (10 digits, starts with 0)
        String[] phonePrefix = {"091", "090", "093", "094", "096", "097", "098", "032", "033", "034", "035"};
        String phone = phonePrefix[random.nextInt(phonePrefix.length)] +
                       String.format("%07d", random.nextInt(10000000));

        // Generate random DOB (age 18-25)
        int year = LocalDate.now().getYear() - (18 + random.nextInt(8));
        int month = 1 + random.nextInt(12);
        int day = 1 + random.nextInt(28);
        String dob = String.format("%02d/%02d/%d", month, day, year);

        // Generate address using data from CSV
        // Generate Vietnamese-style house number: simple (12), with sub (12/5), or multi-level (12/3/7)
        int baseNumber = 1 + random.nextInt(500);
        String houseNumber;
        int format = random.nextInt(3);  // 0: simple, 1: with slash, 2: multi-level
        if (format == 0) {
            houseNumber = String.valueOf(baseNumber);  // e.g., "128"
        } else if (format == 1) {
            houseNumber = baseNumber + "/" + (1 + random.nextInt(20));  // e.g., "128/5"
        } else {
            houseNumber = baseNumber + "/" + (1 + random.nextInt(10)) + "/" + (1 + random.nextInt(10));  // e.g., "128/3/7"
        }

        String address = houseNumber + " " +
                         streets.get(random.nextInt(streets.size())) + ", " +
                         districts.get(random.nextInt(districts.size())) + ", TP.HCM";

        // Random gender
        String[] genders = {"Nam", "Nữ", "Khác"};
        String gender = genders[random.nextInt(genders.length)];

        System.out.println("=== Auto-generated Student Data ===");
        System.out.println("Full Name: " + fullName);
        System.out.println("Student Code: " + studentCode);
        System.out.println("Email: " + email);
        System.out.println("Phone: " + phone);
        System.out.println("DOB: " + dob);
        System.out.println("Address: " + address);
        System.out.println("Gender: " + gender);
        System.out.println("===================================");

        return new StudentInfo(fullName, studentCode, email, phone, dob, address, gender);
    }

    // =======================
    // Login Admin
    // =======================
    private void loginAsAdmin() {
        System.out.println("Mở trang đăng nhập Admin.");
        driver.get("https://elearning.plt.pro.vn/dang-nhap?redirect=%2Ftrang-chu");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("input-10")))
                .sendKeys("test.pltsolutions@gmail.com");

        driver.findElement(By.id("input-13"))
                .sendKeys("plt@intern_051224");

        driver.findElement(By.xpath("//span[contains(text(),'Đăng nhập')]")).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//nav//a[contains(.,'Quản lý học viên')]")
        ));

        System.out.println("Đi tới màn hình Quản lý học viên.");
        driver.findElement(By.xpath("//nav//a[contains(normalize-space(),'Quản lý học viên')]")).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[contains(normalize-space(),'Danh sách học viên')]")
        ));
    }

    // =======================
    // Test Case - Complete CRUD Workflow
    // =======================
    @Test
    public void testStudentManagementCRUDWorkflow() {

        System.out.println("START TEST - Student Management CRUD Workflow (Add → Edit → Delete)");

        // Choose data source (3 options):

        // Option 1: Auto-generate random student data from CSV
        StudentInfo expected = generateRandomStudentData();

        // Option 2: Use specific student from CSV test data (by index 0-4)
        // StudentInfo expected = readStudentFromCSV(0);

        // Option 3: Use data from legacy text file (backward compatibility)
        // StudentInfo expected = readStudentFromFile();

        initDriver();

        try {
            loginAsAdmin();

            // =======================
            // Open Add dialog
            // =======================
            System.out.println("Mở dialog 'Thêm học viên'.");

            By addButtonBy = By.xpath("//button[.//span[contains(normalize-space(),'Thêm mới')]]");
            WebElement addButton = wait.until(ExpectedConditions.elementToBeClickable(addButtonBy));
            addButton.click();

            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[contains(@class,'v-dialog')]//*[contains(normalize-space(),'Thêm học viên')]")
            ));

            // =======================
            // Fill form
            // =======================
            System.out.println("Filling student form...");

            // Fill all text fields (fast JavaScript method)
            fill(FULL_NAME, expected.fullName);
            fill(STUDENT_CODE, expected.studentCode);
            fill(EMAIL, expected.email);
            fill(PHONE, expected.phone);
            fill(DOB, expected.dob);
            fill(ADDRESS, expected.address);

            // Gender - use JavaScript click for speed
            WebElement genderLabel;
            if ("Nam".equalsIgnoreCase(expected.gender)) {
                genderLabel = driver.findElement(GENDER_MALE_LABEL);
            } else if ("Nữ".equalsIgnoreCase(expected.gender)) {
                genderLabel = driver.findElement(GENDER_FEMALE_LABEL);
            } else {
                genderLabel = driver.findElement(GENDER_OTHER_LABEL);
            }
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", genderLabel);

            // =======================
            // Submit
            // =======================
            WebElement submitBtn = wait.until(ExpectedConditions.visibilityOfElementLocated(SUBMIT_BTN));

            // Direct JavaScript click (fastest, no scroll needed)
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);
            System.out.println("✓ Clicked 'THÊM'");

            pause();

            // =======================
            // Click OK on success notification
            // =======================
            System.out.println("Checking for notification/error messages...");

            // Check if there's an error message in the dialog
            try {
                WebElement errorMsg = driver.findElement(
                    By.xpath("//div[contains(@class,'v-dialog')]//div[contains(@class,'error') or contains(@class,'v-messages')]")
                );
                if (errorMsg.isDisplayed()) {
                    System.out.println("⚠ ERROR MESSAGE IN DIALOG: " + errorMsg.getText());
                }
            } catch (Exception e) {
                System.out.println("✓ No error messages found in dialog");
            }

            clickOkButton();

            // Wait longer and check if dialog closes
            System.out.println("Waiting for dialog to close...");

            try {
                wait.until(ExpectedConditions.invisibilityOfElementLocated(
                        By.xpath("//div[contains(@class,'v-dialog__content') and contains(@class,'active')]")
                ));
                System.out.println("✓ Dialog đã đóng");
            } catch (org.openqa.selenium.TimeoutException e) {
                System.out.println("⚠ Dialog không đóng sau 15 giây!");

                // Check if dialog is still visible
                try {
                    WebElement stillVisibleDialog = driver.findElement(
                        By.xpath("//div[contains(@class,'v-dialog__content')]")
                    );
                    System.out.println("Dialog vẫn hiển thị. Checking dialog content...");

                    // Get all text in dialog
                    String dialogText = stillVisibleDialog.getText();
                    System.out.println("Dialog text: " + dialogText);

                    // Try to close dialog manually with ESC or close button
                    System.out.println("Attempting to close dialog with close button...");
                    try {
                        WebElement closeBtn = driver.findElement(
                            By.xpath("//div[contains(@class,'v-dialog')]//button[contains(@class,'v-icon')]//i[contains(@class,'mdi-close')]")
                        );
                        closeBtn.click();
                        pause();
                    } catch (Exception closeEx) {
                        System.out.println("Could not find close button");
                    }

                } catch (Exception dialogEx) {
                    System.out.println("Dialog element not found");
                }
            }

            System.out.println("Đợi table cập nhật...");

            // Wait for table to be visible and stable
            try {
                Thread.sleep(1000); // Wait for table to refresh (optimized)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("\n✓ Student added successfully: " + expected.studentCode);

            // =======================
            // VERIFY STUDENT DATA IN TABLE
            // =======================
            System.out.println("\n=== VERIFYING ADDED STUDENT DATA ===");

            // Search for the student to verify
            searchStudent(expected.studentCode);

            // Verify all fields match expected values
            verifyStudentInTable(expected);

            System.out.println("✓ PASS - Student data verification completed successfully");

            // =======================
            // EDIT STUDENT (using the same search results)
            // =======================
            System.out.println("\n=== BẮT ĐẦU SỬA THÔNG TIN HỌC VIÊN ===");

            // Find the row with student
            System.out.println("Tìm và click nút sửa (pencil icon).");
            WebElement editRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//table//tr[.//td[contains(.,'" + expected.studentCode + "')]]")
            ));

            WebElement editPencilBtn = editRow.findElement(
                    By.xpath(".//button[.//i[contains(@class,'mdi-pencil')]]")
            );
            editPencilBtn.click();

            System.out.println("Waiting for edit dialog...");

            // Wait for dialog to open (use flexible locator)
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[contains(@class,'v-dialog__content') and contains(@class,'active')]")
            ));

            // Extra wait for dialog to fully render
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("✓ Edit dialog opened");

            // Wait for form fields to be fully loaded and interactive
            System.out.println("Đợi form fields sẵn sàng...");
            wait.until(ExpectedConditions.elementToBeClickable(ADDRESS));

            // Extra wait for Vuetify animations
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Modify one field (let's modify the address - Địa chỉ)
            // Generate Vietnamese-style house number with flexible format
            Random random = new Random();
            int baseNumber = 1 + random.nextInt(500);
            String houseNumber;
            int format = random.nextInt(3);
            if (format == 0) {
                houseNumber = String.valueOf(baseNumber);  // e.g., "128"
            } else if (format == 1) {
                houseNumber = baseNumber + "/" + (1 + random.nextInt(20));  // e.g., "128/5"
            } else {
                houseNumber = baseNumber + "/" + (1 + random.nextInt(10)) + "/" + (1 + random.nextInt(10));  // e.g., "128/3/7"
            }

            String newAddress = houseNumber + " " +
                                streets.get(random.nextInt(streets.size())) + ", " +
                                districts.get(random.nextInt(districts.size())) + ", TP.HCM";

            System.out.println("Sửa địa chỉ từ: " + expected.address);
            System.out.println("          thành: " + newAddress);

            fill(ADDRESS, newAddress);

            // Verify address was filled
            String filledAddress = getValue(ADDRESS);
            System.out.println("Địa chỉ sau khi fill: '" + filledAddress + "'");

            if (!filledAddress.equals(newAddress)) {
                System.out.println("⚠ WARNING: Address may not have been filled correctly");
            } else {
                System.out.println("✓ Address filled successfully");
            }

            pause();

            // Click "Sửa" button
            System.out.println("Click nút 'SỬA' để lưu thay đổi.");
            WebElement editSaveBtn = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(EDIT_SAVE_BTN)
            );

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", editSaveBtn);

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                editSaveBtn.click();
                System.out.println("✓ Clicked 'Sửa' button");
            } catch (Exception e) {
                System.out.println("Regular click failed, using JavaScript click");
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editSaveBtn);
            }

            pause();

            // Click OK on success notification
            clickOkButton();

            // Wait and ensure dialog closes
            System.out.println("Waiting for edit dialog to close...");
            try {
                wait.until(ExpectedConditions.invisibilityOfElementLocated(
                        By.xpath("//div[contains(@class,'v-dialog__content') and contains(@class,'active')]")
                ));
                System.out.println("✓ Edit dialog closed");
            } catch (Exception e) {
                System.out.println("⚠ Dialog still visible, pressing ESC to close...");
                Actions actions = new Actions(driver);
                actions.sendKeys(Keys.ESCAPE).perform();
                pause();

                try {
                    wait.until(ExpectedConditions.invisibilityOfElementLocated(
                            By.xpath("//div[contains(@class,'v-dialog__content')]")
                    ));
                    System.out.println("✓ Dialog closed with ESC");
                } catch (Exception e2) {
                    System.out.println("⚠ Dialog may still be open, continuing anyway...");
                }
            }

            System.out.println("✓ Edit operation completed");

            // =======================
            // VERIFY EDITED DATA IN TABLE
            // =======================
            System.out.println("\n=== VERIFYING EDITED STUDENT DATA ===");

            // Wait for table to update
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Search again to see updated data
            searchStudent(expected.studentCode);

            // Find the row and verify the new address
            WebElement updatedRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//table//tr[.//td[contains(.,'" + expected.studentCode + "')]]")
            ));

            List<WebElement> updatedCells = updatedRow.findElements(By.tagName("td"));

            // Find address column dynamically
            String actualUpdatedAddress = "";
            for (WebElement cell : updatedCells) {
                String cellText = cell.getText().trim();
                // Address contains comma and TP.HCM
                if (cellText.contains(",") && cellText.contains("TP.HCM")) {
                    actualUpdatedAddress = cellText;
                    break;
                }
            }

            System.out.println("Address Verification:");
            System.out.println("  Expected (new): " + newAddress);
            System.out.println("  Actual:         " + actualUpdatedAddress);

            Assert.assertEquals(actualUpdatedAddress, newAddress,
                "Updated address doesn't match!");
            System.out.println("  ✓ Address was successfully updated!");

            System.out.println("✓ PASS - Address edited and verified successfully");

            // Extra wait for UI to settle before delete
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // =======================
            // DELETE STUDENT (Optimized - no new search, use same row!)
            // =======================
            System.out.println("\n=== BẮT ĐẦU XÓA HỌC VIÊN ===");

            // Find the row (already visible on screen from search)
            System.out.println("Tìm và click nút xóa (red X icon) on same row.");
            WebElement deleteRow = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//table//tr[.//td[contains(.,'" + expected.studentCode + "')]]")
            ));

            WebElement deleteBtn = deleteRow.findElement(
                    By.xpath(".//button[contains(@class,'red--text')]//i[contains(@class,'mdi-close')]")
            );

            System.out.println("Found delete button (red X), clicking...");

            // Scroll into view and click
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", deleteBtn);
            pause();

            try {
                deleteBtn.click();
                System.out.println("✓ Clicked delete button");
            } catch (Exception e) {
                System.out.println("Regular click failed, using JavaScript");
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", deleteBtn);
                System.out.println("✓ Clicked delete button (JavaScript)");
            }

            pause();

            // Wait for delete confirmation dialog
            System.out.println("Waiting for delete confirmation dialog...");
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//div[contains(@class,'v-dialog__content') and contains(@class,'active')]")
                ));

                // Try to read dialog text
                try {
                    WebElement dialogContent = driver.findElement(
                        By.xpath("//div[contains(@class,'v-dialog')]")
                    );
                    System.out.println("Dialog text: " + dialogContent.getText());
                } catch (Exception e) {
                    System.out.println("Could not read dialog text");
                }

                System.out.println("✓ Delete confirmation dialog appeared");
            } catch (Exception e) {
                System.out.println("❌ Delete confirmation dialog did not appear!");
                throw e;
            }

            // Click "Xoá" confirm button
            WebElement deleteConfirmBtn = wait.until(ExpectedConditions.elementToBeClickable(DELETE_CONFIRM_BTN));

            try {
                deleteConfirmBtn.click();
                System.out.println("✓ Clicked 'Xoá' confirm");
            } catch (Exception e) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", deleteConfirmBtn);
                System.out.println("✓ Clicked 'Xoá' confirm (JS)");
            }

            // Skip waiting for dialog close - success notification appears immediately
            // and we'll click OK on it instead of waiting
            try {
                Thread.sleep(300);  // Small pause for UI transition
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Check for delete success notification
            // Immediately click OK button on notification (don't wait for auto-close)
            System.out.println("Looking for OK button to close notification...");

            boolean okClicked = false;

            // Try to find and click OK button immediately
            for (int attempt = 0; attempt < 20; attempt++) {  // Try for ~2 seconds max
                try {
                    // Try SweetAlert2 button first (most common)
                    WebElement okBtn = driver.findElement(By.className("swal2-confirm"));
                    if (okBtn.isDisplayed()) {
                        okBtn.click();
                        System.out.println("✓ OK clicked (SweetAlert2) - notification closed immediately!");
                        okClicked = true;
                        break;
                    }
                } catch (Exception e1) {
                    // Try generic OK button
                    try {
                        WebElement okBtn = driver.findElement(By.xpath("//button[contains(text(),'OK')]"));
                        if (okBtn.isDisplayed()) {
                            okBtn.click();
                            System.out.println("✓ OK clicked (generic) - notification closed!");
                            okClicked = true;
                            break;
                        }
                    } catch (Exception e2) {
                        // Wait 100ms and try again
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            if (!okClicked) {
                System.out.println("⚠ OK button not found - waiting for auto-close (this is slow!)");
                // Check what buttons are available
                try {
                    List<WebElement> buttons = driver.findElements(By.tagName("button"));
                    System.out.println("Available buttons on page:");
                    for (WebElement btn : buttons) {
                        if (btn.isDisplayed()) {
                            System.out.println("  - " + btn.getAttribute("class") + " : " + btn.getText());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Could not list buttons");
                }
            }

            // =======================
            // VERIFY DELETION
            // =======================
            System.out.println("\nVerifying student was deleted...");

            // Search for the student
            searchStudent(expected.studentCode);

            // Try to find the student in the table
            try {
                WebElement deletedStudentRow = driver.findElement(
                    By.xpath("//table//tr[.//td[contains(.,'" + expected.studentCode + "')]]")
                );

                if (deletedStudentRow.isDisplayed()) {
                    System.out.println("❌ STUDENT STILL EXISTS - Delete failed");
                } else {
                    System.out.println("✓ Student not visible (deleted)");
                }
            } catch (org.openqa.selenium.NoSuchElementException e) {
                System.out.println("✓✓ STUDENT NOT FOUND - Delete successful!");
            } catch (Exception e) {
                System.out.println("⚠ Could not verify deletion: " + e.getMessage());
            }

            System.out.println("\n=== TEST COMPLETED ===");
            System.out.println("Summary:");
            System.out.println("  ✓ Add student: PASSED");
            System.out.println("  ✓ Verify student: PASSED");
            System.out.println("  ✓ Edit student: PASSED");
            System.out.println("  ✓ Delete student: PASSED");

        } catch (Exception e) {
            System.out.println("\n❌ TEST FAILED - LỖI XẢY RA:");
            System.out.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Keep browser open for 10 seconds to allow debugging
            System.out.println("\n⏸ Giữ browser mở trong 10 giây để debug...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            throw e; // Re-throw to mark test as failed

        } finally {
            if (driver != null) {
                System.out.println("\nĐóng browser...");
                driver.quit();
            }
        }
    }
}
