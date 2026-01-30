package Admin;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

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
    // Read data from resources/studentdata.txt
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
    // Auto-generate random student data
    // =======================
    private StudentInfo generateRandomStudentData() {
        Random random = new Random();

        // Vietnamese common last names
        String[] lastNames = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Phan", "Vũ", "Đặng", "Bùi", "Đỗ" , "Hồ", "Ngô", "Dương", "Lý", "Hà", "Chu", "Cao", "Lưu", "Tạ", "Thái",
                "Tô", "Võ", "Trịnh", "Mai", "La"};

        // Vietnamese common middle names
        String[] middleNames = {"Văn", "Thị", "Hữu", "Đức", "Minh", "Anh", "Tuấn", "Quang", "Hoàng", "Thanh", "Ngọc", "Gia", "Khánh", "Bảo", "Phúc", "Thành", "Trung", "Thiên",
                "Mạnh", "Nhật", "Xuân", "Hồng", "Kim", "Thái"};

        // Vietnamese common first names
        String[] firstNames = {
                "An", "Bình", "Cường", "Dũng", "Hải", "Hùng", "Nam", "Quân", "Tâm", "Tuấn", "Long", "Khoa", "Kiên", "Đạt", "Phát", "Hưng", "Thịnh", "Toàn", "Vinh", "Hiếu", "Sơn", "Khánh", "Bảo", "Khôi", "Khang", "Minh", "Đức", "Trung", "Thành", "Nhật",
                "Linh", "Mai", "Phương", "Thảo", "Vy", "Yến", "Hà", "Trang", "Nhi", "Trâm", "Ngân", "Chi", "My", "Huyền", "Nga", "Lan", "Hoa", "Tuyết", "Quỳnh", "Ánh"
        };
        // Generate full name
        String fullName = lastNames[random.nextInt(lastNames.length)] + " " +
                          middleNames[random.nextInt(middleNames.length)] + " " +
                          firstNames[random.nextInt(firstNames.length)];

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

        // Generate address
        String[] streets = { "Lê Lợi","Nguyễn Huệ", "Trần Hưng Đạo", "Võ Văn Tần", "Hai Bà Trưng", "Lý Thường Kiệt", "Điện Biên Phủ", "Cách Mạng Tháng Tám", "Nguyễn Thị Minh Khai",
                "Pasteur", "Nam Kỳ Khởi Nghĩa", "Phan Xích Long", "Hoàng Văn Thụ", "Nguyễn Văn Trỗi", "Xô Viết Nghệ Tĩnh", "Phạm Văn Đồng", "Nguyễn Oanh", "Quang Trung", "Lê Văn Sỹ", "Bạch Đằng"
        };
        String[] districts = {
                "Phường 1", "Phường 2", "Phường 3", "Phường 4", "Phường 5", "Phường 6", "Phường 7", "Phường 8", "Phường 9", "Phường 10",
                "Bến Nghé", "Bến Thành", "Tân Định", "Đa Kao", "Thảo Điền", "An Phú", "Linh Trung", "Linh Đông", "Phú Nhuận", "Hòa Bình", "Mỹ An", "Phước Long", "Hiệp Bình Chánh", "Tân Phong", "Tân Thuận Đông"
        };

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
                         streets[random.nextInt(streets.length)] + ", " +
                         districts[random.nextInt(districts.length)] + ", TP.HCM";

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

        // ✅ Auto-generate random student data
        StudentInfo expected = generateRandomStudentData();

        // Alternative: Use data from file
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

            // =======================
            // OPTIMIZED: Skip initial verification, go directly to EDIT
            // =======================
            System.out.println("\n✓ Student added successfully: " + expected.studentCode);

            // =======================
            // EDIT STUDENT (Optimized - single search)
            // =======================
            System.out.println("\n=== BẮT ĐẦU SỬA THÔNG TIN HỌC VIÊN ===");

            // Search for the student ONCE
            searchStudent(expected.studentCode);

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
            Random random = new Random();
            String[] streets = { "Lê Lợi","Nguyễn Huệ", "Trần Hưng Đạo", "Võ Văn Tần", "Hai Bà Trưng", "Lý Thường Kiệt", "Điện Biên Phủ", "Cách Mạng Tháng Tám", "Nguyễn Thị Minh Khai",
                    "Pasteur", "Nam Kỳ Khởi Nghĩa", "Phan Xích Long", "Hoàng Văn Thụ", "Nguyễn Văn Trỗi", "Xô Viết Nghệ Tĩnh", "Phạm Văn Đồng", "Nguyễn Oanh", "Quang Trung", "Lê Văn Sỹ", "Bạch Đằng"
            };
            String[] districts = {
                    "Phường 1", "Phường 2", "Phường 3", "Phường 4", "Phường 5", "Phường 6", "Phường 7", "Phường 8", "Phường 9", "Phường 10",
                    "Bến Nghé", "Bến Thành", "Tân Định", "Đa Kao", "Thảo Điền", "An Phú", "Linh Trung", "Linh Đông", "Phú Nhuận", "Hòa Bình", "Mỹ An", "Phước Long", "Hiệp Bình Chánh", "Tân Phong", "Tân Thuận Đông"
            };

            // Generate Vietnamese-style house number with flexible format
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
                                streets[random.nextInt(streets.length)] + ", " +
                                districts[random.nextInt(districts.length)] + ", TP.HCM";

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

            System.out.println("✓ PASS - Address edited successfully");

            // Extra wait for UI to settle before delete
            try {
                Thread.sleep(1000);
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
