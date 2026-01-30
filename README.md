# POL Automation - Student Management Test

Automated testing suite for the POL e-learning platform's student management system.

## Overview

This project contains automated tests for the complete CRUD (Create, Read, Update, Delete) workflow of student management in the POL platform.

## Test Workflow

The `StudentManagementTest.java` performs a complete student lifecycle test:

1. **Add Student** - Creates a new student with auto-generated Vietnamese data
2. **Edit Student** - Modifies the student's address information
3. **Delete Student** - Removes the student and verifies deletion

### Execution Flow

```
Login as Admin
    ↓
Navigate to Student Management
    ↓
Add New Student (auto-generated data)
    ↓
Search for Student
    ↓
Edit Student Information (modify address)
    ↓
Delete Student
    ↓
Verify Deletion
```

## Features

### Auto-Generated Test Data

The test automatically generates realistic Vietnamese student data:

- **Full Name**: Random Vietnamese names (e.g., "Nguyễn Văn An", "Trần Thị Linh")
- **Student Code**: Unique timestamp-based code (e.g., "SV12345")
- **Email**: Format `firstname.studentcode@domain` (e.g., "an.sv12345@gmail.com")
  - Supports multiple domains: Gmail, Outlook, Yahoo, Hotmail, iCloud
  - Automatically removes Vietnamese diacritics for email compatibility
- **Phone**: Vietnamese mobile format (10 digits, valid prefixes)
- **Date of Birth**: Random age between 18-25 years old
- **Address**: Realistic Vietnamese addresses with flexible house numbers
  - Simple format: `128 Lê Lợi, Quận 1, TP.HCM`
  - With sub-number: `128/5 Nguyễn Huệ, Bến Nghé, TP.HCM`
  - Multi-level: `128/3/7 Trần Hưng Đạo, Phường 2, TP.HCM`
- **Gender**: Random selection (Nam/Nữ/Khác)

### Vietnamese Address Features

- **20 street names**: Major streets in Ho Chi Minh City
- **25 districts/wards**: Mix of numbered wards (Phường 1-10) and named wards (Bến Nghé, Tân Định, etc.)
- **Flexible house numbering**: Simple (128), with slash (128/5), or multi-level (128/3/7)

## Test Configuration

- **Browser**: Chrome (maximized window)
- **Wait Timeout**: 10 seconds
- **Execution Time**: ~17 seconds (optimized)
- **Test Framework**: TestNG
- **Automation Tool**: Selenium WebDriver

## Prerequisites

- Java 8 or higher
- Maven
- Chrome browser
- ChromeDriver (managed automatically)

## How to Run

```bash
# Run the test
mvn test

# Or run specific test class
mvn test -Dtest=StudentManagementTest
```

## Test Results

Upon completion, the test displays a summary:

```
=== TEST COMPLETED ===
Summary:
  ✓ Add student: PASSED
  ✓ Verify student: PASSED
  ✓ Edit student: PASSED
  ✓ Delete student: PASSED
```

## Project Structure

```
POL_Automation/
├── src/
│   ├── Admin/
│   │   └── StudentManagementTest.java    # Main CRUD test
│   ├── User/
│   │   └── CourseExpandTest.java         # Course tests
│   └── resources/
│       ├── data.txt
│       └── studentdata.txt
├── pom.xml
└── README.md
```

## Optimization Features

- **Fast field filling**: Uses JavaScript for instant input (vs. slow sendKeys)
- **Smart waits**: Optimized wait times (200ms pauses, 1s for UI updates)
- **Direct JavaScript clicks**: Bypasses scroll and animation delays
- **Single search reuse**: Searches once, reuses results for edit/delete

## Notes

- Test data is randomly generated on each run
- All Vietnamese diacritics are handled properly
- The test automatically handles all confirmation dialogs
- Clean browser shutdown after test completion

## Credentials

- **Login URL**: https://elearning.plt.pro.vn/dang-nhap
- **Admin Email**: test.pltsolutions@gmail.com
- **Password**: plt@intern_051224

---

**Generated with Claude Code** 🤖
