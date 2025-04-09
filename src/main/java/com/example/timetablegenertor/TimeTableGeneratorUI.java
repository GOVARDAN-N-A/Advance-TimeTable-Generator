package com.example.timetablegenertor;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.beans.binding.Bindings;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.event.EventHandler;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.sql.*;
import java.util.*;

public class TimeTableGeneratorUI extends Application {

    // Constants (remain the same)
    private static final int PERIODS_PER_DAY = 8;
    private static final int DAYS_PER_WEEK = 5;
    private static final int TOTAL_PERIODS_PER_WEEK = PERIODS_PER_DAY * DAYS_PER_WEEK; // 40
    private static final List<String> DAYS_OF_WEEK = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
    private static final String PRIMARY_COLOR = "#2C3E50";
    private static final String SECONDARY_COLOR = "#3498DB";
    private static final String ACCENT_COLOR = "#1ABC9C";
    private static final String WARNING_COLOR = "#E74C3C";
    private static final String SUCCESS_COLOR = "#2ECC71";
    private static final String TEXT_COLOR = "#34495E";
    private static final String LIGHT_BG_COLOR = "#ECF0F1";
    private static final String BORDER_COLOR = "#BDC3C7";
    private static final String THEORY_COLOR = "#D5F5E3";
    private static final String LAB_COLOR = "#D4E6F1";

    // Global staff schedule for collision avoidance across years/sections
    private final Map<String, Set<String>> globalStaffSchedule = new HashMap<>();
    private Connection connection;

    // --- NEW: Years and per-year subject input containers ---
    private static final List<String> YEARS = Arrays.asList("2nd Year", "3rd Year", "4th Year");
    private TabPane yearInputTabs;
    // Each year will have its own subject input container and list.
    private final Map<String, VBox> subjectsContainerMap = new HashMap<>();
    private final Map<String, ObservableList<SubjectRow>> subjectRowsMap = new HashMap<>();
    private TextField academicYearField;
    private final Map<String, TextField> academicYearFields = new HashMap<>();

    // UI nodes for the results area.
    // We will create one tab per (year, section)
    private TabPane resultTabs;

    // Progress and status controls
    private Button generateBtn;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;

    private Node currentTimetableContainer;
    private TableView<SubjectSummary> currentSummaryTable;

    // Database connection
    private void connectToDatabase() {
        try {
            // Load the SQLite JDBC driver (not mandatory for recent versions, but safe to include)
            Class.forName("org.sqlite.JDBC");

            // SQLite DB file will be created in your project directory if it doesn't exist
            String dbUrl = "jdbc:sqlite:timetable.db";
            connection = DriverManager.getConnection(dbUrl);

            System.out.println("Connected to SQLite successfully.");
        } catch (Exception e) {
            System.out.println("Error connecting to SQLite: " + e.getMessage());
        }
    }

    @Override
    public void start(Stage primaryStage) {
         connectToDatabase();
        primaryStage.setTitle("TimeTable Generator Pro");
        primaryStage.getIcons().add(new Image("file:src/main/resources/logo.png"));

        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(createHeader());
        mainLayout.setBottom(createFooter());

        // The central layout is a SplitPane with inputs on the left and results on the right.
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.45);

        // --- NEW: Input Section now is a TabPane for the 3 years ---
        ScrollPane inputScrollPane = new ScrollPane(createInputSection());
        inputScrollPane.setFitToWidth(true);
        inputScrollPane.getStyleClass().add("custom-scroll-pane");

        // Results area remains as a ScrollPane (whose contents will be built after generation)
        ScrollPane outputScrollPane = new ScrollPane(createOutputSection());
        outputScrollPane.setFitToWidth(true);
        outputScrollPane.getStyleClass().add("custom-scroll-pane");
        outputScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        outputScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Add an empty timetable tab on start
        GridPane emptyGrid = createEmptyTimetableGrid();
        Label emptyFitnessLabel = new Label("Fitness: N/A");
        TableView<SubjectSummary> emptySummaryTable = createSubjectSummaryTable();
        Tab emptyTab = new Tab("Empty Timetable", createTimetableTabContent("Empty Timetable", emptyGrid, emptyFitnessLabel, emptySummaryTable));
        emptyTab.setClosable(false);
        resultTabs.getTabs().add(emptyTab);

        splitPane.getItems().addAll(inputScrollPane, outputScrollPane);
        mainLayout.setCenter(splitPane);

        Scene scene = new Scene(mainLayout, 1450, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createHeader() {
        VBox header = new VBox();
        header.setStyle("-fx-background-color: linear-gradient(to bottom, " + PRIMARY_COLOR + ", " + darkerColor(PRIMARY_COLOR) + ");");
        header.setPadding(new Insets(5));
        header.setSpacing(5);

        // Title


        Label title = new Label("Time Table Generator");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: white; -fx-font-weight: bold;");
        HBox titleBox = new HBox(title);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPrefWidth(Double.MAX_VALUE);

        // Icon Bar (align left now)
        HBox iconBar = new HBox(30);
        iconBar.setAlignment(Pos.CENTER_LEFT); // ← align icons to the left
        iconBar.setPadding(new Insets(10, 0, 0, 10)); // some left margin



        iconBar.getChildren().addAll(
                createIconWithLabel("file:src/main/resources/gear-icon.png", "Generate", e -> generateTimetables()),
                createIconWithLabel("file:src/main/resources/database-icon.png", "Save", e -> saveToDatabase()),
                createIconWithLabel("file:src/main/resources/excel-icon.png", "Export Excel", e -> exportToExcel()),
                createIconWithLabel("file:src/main/resources/print-icon.png", "Print", e -> {
                    if (currentTimetableContainer != null) {
                        printNode(currentTimetableContainer);
                    } else {
                        showAlert("Print Error", "No timetable to print!", Alert.AlertType.WARNING);
                    }
                }),
                createIconWithLabel("file:src/main/resources/staff-icon.png", "Staff TimeTable", e -> exportToExcel()),

                createIconWithLabel("file:src/main/resources/help-icon.png", "Help", e -> showFullHelpDialog())

                );

        header.getChildren().addAll(titleBox, iconBar);
        return header;
    }



    private void showFullHelpDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Help & Information");
        alert.setHeaderText("📘 Timetable Generator Help");

        StringBuilder content = new StringBuilder();
        content.append("🔹 **How to Use:**\n")
                .append("• Click 'Generate' to auto-create optimized timetables.\n")
                .append("• Use 'Save' to store the result into the database.\n")
                .append("• 'Print' lets you print the timetable in landscape mode.\n")
                .append("• 'Export' saves the subject-staff summary as Excel.\n\n")
                .append("🔹 **About:**\n")
                .append("• Developed for M.I.E.T Engineering College - Dept. of CSE.\n")
                .append("• Version 1.0 | Powered by JavaFX & Apache POI.\n\n")
                .append("🔹 **Support:**\n")
                .append("• For technical help, contact: timetable-support@miet.edu\n")
                .append("• Or visit the department office during working hours.");

        Label label = new Label(content.toString());
        label.setWrapText(true);
        label.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        label.setTextFill(Color.web("#333"));

        // Resize dialog if needed
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setMinHeight(Region.USE_PREF_SIZE);
        dialogPane.setContent(label);
        alert.showAndWait();
    }






    private VBox createIconWithLabel(String imagePath, String labelText, EventHandler<ActionEvent> action) {
        ImageView icon = new ImageView(new Image(imagePath));
        icon.setFitWidth(32);
        icon.setFitHeight(32);

        Button button = new Button();
        button.setGraphic(icon);
        button.setStyle("-fx-background-color: transparent;");
        button.setOnAction(action);
        button.setCursor(Cursor.HAND);

        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        label.setAlignment(Pos.CENTER);

        VBox iconBox = new VBox(5, button, label);
        iconBox.setAlignment(Pos.CENTER);

        // Add hover effect
        iconBox.setOnMouseEntered(e -> iconBox.setStyle("-fx-opacity: 0.8;"));
        iconBox.setOnMouseExited(e -> iconBox.setStyle("-fx-opacity: 1;"));

        return iconBox;
    }



    // ----------------- Helpers -----------------
    private HBox createTitleAndSubtitle() {
        HBox titleBox = new HBox(20);
        titleBox.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("TimeTable Generator Pro");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);

        Label subtitleLabel = new Label("Genetic Algorithm Scheduler (Multi-Year Edition)");
        subtitleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        subtitleLabel.setTextFill(Color.web(LIGHT_BG_COLOR));

        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        titleBox.setPadding(new Insets(10, 0, 10, 0));

        return titleBox;
    }

    // **Color Helper: Darker Shade**
    private String darkerColor(String color) {
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);

        r = (int) (r * 0.9);
        g = (int) (g * 0.9);
        b = (int) (b * 0.9);

        return String.format("#%02x%02x%02x", r, g, b);
    }

    private HBox createFooter() {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 20, 8, 20));
        footer.setStyle("-fx-background-color: " + LIGHT_BG_COLOR + "; -fx-border-width: 1 0 0 0; -fx-border-color: " + BORDER_COLOR + ";");
        Label versionLabel = new Label("Version 2.4 2025");
        versionLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        versionLabel.setTextFill(Color.web(TEXT_COLOR));
        footer.getChildren().add(versionLabel);
        return footer;
    }

    private VBox createInputSection() {
        VBox container = new VBox(18);
        container.setPadding(new Insets(25));
        container.setStyle("-fx-background-color: white;");

        Label headerLabel = new Label("Input Subject Details");
        headerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        headerLabel.setTextFill(Color.web(PRIMARY_COLOR));

        Label instrLabel = new Label(
                "For each year, add subjects with Full Name, Short Name, Code, Periods, Type, and Staff.\n" +
                        "Total periods MUST sum to exactly " + TOTAL_PERIODS_PER_WEEK + " per week for that year."
        );
        instrLabel.setWrapText(true);
        instrLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        instrLabel.setStyle("-fx-text-fill: " + TEXT_COLOR + ";");

        // Create a TabPane to hold input for each year.
        yearInputTabs = new TabPane();
        for (String year : YEARS) {
            VBox yearBox = new VBox(10);
            yearBox.setPadding(new Insets(10));
            // Each year gets its own list of subject rows.
            ObservableList<SubjectRow> rows = FXCollections.observableArrayList();
            subjectRowsMap.put(year, rows);
            // Also each year gets its own container for nodes.
            VBox subjectBox = new VBox(10);
            subjectsContainerMap.put(year, subjectBox);
            // Add the first row by default.
            SubjectRow firstRow = new SubjectRow(1, r -> removeSubjectRow(year, r));
            rows.add(firstRow);
            subjectBox.getChildren().add(firstRow.getContainer());

            TextField academicYearField = new TextField();
            academicYearField.setPromptText("Academic Year (e.g. 2021-2025)");
            academicYearField.setStyle("-fx-font-size: 14px;");
            academicYearFields.put(year, academicYearField);

            // Add “add subject” button for this year.
            Button addBtn = new Button("Add Another Subject");
            addBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");            addBtn.setOnAction(e -> addSubjectRow(year));
            // addBtn.setStyle("-fx-background-color: " + SECONDARY_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
            addBtn.setPrefWidth(200);
            addBtn.setCursor(javafx.scene.Cursor.HAND);

            // Ensure the button is always visible by adding it to a persistent container
            VBox yearContainer = new VBox(10, academicYearField, subjectBox, addBtn);
            yearContainer.setPadding(new Insets(10));
            yearContainer.setStyle("-fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1; -fx-background-color: white;");
            Tab yearTab = new Tab(year, yearContainer);
            yearTab.setClosable(false);
            yearInputTabs.getTabs().add(yearTab);
        }

        // Add a listener to ensure the "Add Subject" button is visible when switching tabs
        yearInputTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                VBox yearContainer = (VBox) newTab.getContent();
                yearContainer.getChildren().stream()
                        .filter(node -> node instanceof Button)
                        .forEach(node -> node.setVisible(true));
            }
        });

        // Global control button (generate for all years)
        generateBtn = new Button("Generate All Timetables");
        generateBtn.setOnAction(e -> generateTimetables());
        generateBtn.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold;");
        generateBtn.setPrefWidth(220);
        generateBtn.setPrefHeight(45);
        generateBtn.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        generateBtn.setCursor(javafx.scene.Cursor.HAND);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(35, 35);

        HBox controlsBox = new HBox(20, generateBtn, progressIndicator);
        controlsBox.setAlignment(Pos.CENTER);

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
        statusLabel.setTextFill(Color.web(TEXT_COLOR));
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setManaged(false);

        container.getChildren().addAll(headerLabel, instrLabel, new Separator(), yearInputTabs, new Separator(), controlsBox, statusLabel);
        return container;
    }


    private void saveToDatabase() {
        // Full table creation with proper UNIQUE constraint
        String createTableQuery = """
        CREATE TABLE IF NOT EXISTS subjects (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            year TEXT,
            academic_year TEXT,
            subject_name TEXT,
            short_name TEXT,
            code TEXT,
            periods INTEGER,
            is_lab INTEGER,
            staff TEXT,
            UNIQUE(year, code)
        );
    """;

        // UPSERT query using the UNIQUE(year, code) constraint
        String upsertQuery = """
        INSERT INTO subjects (year, academic_year, subject_name, short_name, code, periods, is_lab, staff)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(year, code) DO UPDATE SET
            academic_year = excluded.academic_year,
            subject_name = excluded.subject_name,
            short_name = excluded.short_name,
            periods = excluded.periods,
            is_lab = excluded.is_lab,
            staff = excluded.staff;
    """;

        try (Statement createStatement = connection.createStatement()) {
            // Drop old table to fix schema issues
            createStatement.execute("DROP TABLE IF EXISTS subjects;");

            // Create new table with full structure and UNIQUE constraint
            createStatement.execute(createTableQuery);

            try (PreparedStatement statement = connection.prepareStatement(upsertQuery)) {
                for (String year : YEARS) {
                    ObservableList<SubjectRow> rows = subjectRowsMap.get(year);
                    if (rows == null || rows.isEmpty()) continue;

                    // Get academic year for the current year
                    String academicYear = "";
                    if (academicYearFields.containsKey(year)) {
                        academicYear = academicYearFields.get(year).getText().trim();
                    }

                    // Insert or update each subject row
                    for (SubjectRow row : rows) {
                        statement.setString(1, year);
                        statement.setString(2, academicYear);
                        statement.setString(3, row.getSubjectName());
                        statement.setString(4, row.getSubjectShortName());
                        statement.setString(5, row.getSubjectCode());
                        statement.setInt(6, row.getPeriods());
                        statement.setInt(7, row.isLab() ? 1 : 0);
                        statement.setString(8, row.getStaffName());
                        statement.addBatch();

                        // Optional debug output
                        System.out.println("Saving: " + year + " | " + academicYear + " | " + row.getSubjectName());
                    }
                }

                // Execute batch insert/update
                statement.executeBatch();
            }

            // Show success notification
            showNotification("Success", "Subjects & Academic Year saved!", "#28a745");

        } catch (SQLException e) {
            // Show error notification with message
            showNotification("Database Error", "Error saving data: " + e.getMessage(), "#dc3545");
        }
    }



    // Add a subject row for the given year.
    private void addSubjectRow(String year) {
        ObservableList<SubjectRow> rows = subjectRowsMap.get(year);
        VBox subjectContainer = (VBox) subjectsContainerMap.get(year);
        int nextNumber = rows.size() + 1;
        SubjectRow newRow = new SubjectRow(nextNumber, r -> removeSubjectRow(year, r));
        rows.add(newRow);
        Node rowNode = newRow.getContainer();
        subjectContainer.getChildren().add(rowNode);

        // **Fade In Effect**
        animateFadeIn(rowNode, 400);

        // **Scroll to the Added Row (Maintain ScrollBar)**
        ScrollPane scrollPane = (ScrollPane) subjectContainer.getParent();
        scrollPane.layout();
        scrollPane.setVvalue(1.0);

        // No need to dynamically adjust height; ScrollPane handles it
    }
    private void animateFadeIn(Node node, int duration) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(duration), node);
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);
        fadeTransition.setInterpolator(Interpolator.EASE_IN); // Smoother animation
        fadeTransition.play();
    }

// Assume your layout structure is:
// subjectsContainerMap.get(year) => VBox (holding rows)
// subjectsContainerMap.get(year).getParent() => ScrollPane (with VBox as content)

    private void setupSubjectsContainer(String year) {
        VBox subjectContainer = new VBox(10);
        subjectContainer.setPrefHeight(300); // Initial height, adjust as needed
        ScrollPane scrollPane = new ScrollPane(subjectContainer);
        scrollPane.setFitToWidth(true); // Fit scroll content width

        // **Add "Add Subject" Button at the BOTTOM (Outside ScrollPane)**

        Button addButton = new Button("Add Subject");
        addButton.getStyleClass().add("add-subject-button");

        // Fixed button styling
        String baseStyle = String.format(
                "-fx-background-color: %s;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 12 20;" +
                        "-fx-border-radius: 5;" +
                        "-fx-background-radius: 5;" +
                        "-fx-cursor: hand;" +
                        "-fx-min-width: 150px;",
                SECONDARY_COLOR
        );

        addButton.setStyle(baseStyle);
        addButton.setCursor(Cursor.HAND);

        BorderPane container = new BorderPane();
        container.setCenter(scrollPane);
        container.setBottom(addButton);
        container.setPadding(new Insets(10)); // Some spacing

        // Store references for later use
        subjectsContainerMap.put(year, subjectContainer);
        subjectRowsMap.put(year, FXCollections.observableArrayList());
    }
    // Remove a subject row for a given year.
    private void removeSubjectRow(String year, SubjectRow rowToRemove) {
        VBox subjectContainer = subjectsContainerMap.get(year);
        ObservableList<SubjectRow> rows = subjectRowsMap.get(year);
        Node rowNode = rowToRemove.getContainer();
        FadeTransition ft = new FadeTransition(Duration.millis(300), rowNode);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(event -> {
            subjectContainer.getChildren().remove(rowNode);
            rows.remove(rowToRemove);
        });
        ft.play();
    }

    public static class SubjectRow {
        private final HBox container;
        private final TextField subjectNameField = new TextField();
        private final TextField shortNameField = new TextField();
        private final TextField codeField = new TextField();
        private final TextField periodsField = new TextField();
        private final ComboBox<String> typeBox = new ComboBox<>();
        private final TextField staffField = new TextField();
        private final Button removeBtn;

        public SubjectRow(int serialNo, java.util.function.Consumer<SubjectRow> onRemove) {
            container = new HBox(10);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPrefHeight(50); // Set a fixed height for better layout

            // **Styled Border and Background**
            container.setStyle(String.format(
                    "-fx-background-color: white; " +
                            "-fx-border-color: %s; " +
                            "-fx-border-width: 1; " +
                            "-fx-border-radius: 5; " +
                            "-fx-background-radius: 5;",
                    BORDER_COLOR
            ));

            // **Individual Field Setup**
            subjectNameField.setPromptText("Full Name");
            subjectNameField.setPrefWidth(150); // Reasonable width for full name

            shortNameField.setPromptText("Short Name (Optional)");
            shortNameField.textProperty().addListener((observable, oldValue, newValue) ->
                    shortNameField.setText(newValue.toUpperCase()));
            shortNameField.setPrefWidth(100);

            codeField.setPromptText("Code (Optional)");
            codeField.textProperty().addListener((observable, oldValue, newValue) ->
                    codeField.setText(newValue.toUpperCase()));
            codeField.setPrefWidth(80);

            periodsField.setPromptText("Periods (Numeric)");
            periodsField.setPrefWidth(60);

            staffField.setPromptText("Staff Name");
            staffField.setPrefWidth(150);

            typeBox.getItems().addAll("theory", "lab");
            typeBox.setValue("theory");
            typeBox.setPrefWidth(80);

            // **Serial Number Label (Styled)**
            Label serialLabel = new Label(String.valueOf(serialNo) + ".");
            serialLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            serialLabel.setStyle("-fx-text-fill: #333;"); // Darker text for better contrast

            // **Remove Button (Centered Vertically)**
            removeBtn = new Button("X");
            removeBtn.setStyle(String.format(
                    "-fx-background-color: %s; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 5; " +
                            "-fx-padding: 5 10;",
                    WARNING_COLOR
            ));
            removeBtn.setOnAction(e -> onRemove.accept(this));

            // **Add Fields to Container with Spacing**
            container.getChildren().addAll(
                    serialLabel,
                    subjectNameField,
                    shortNameField,
                    codeField,
                    periodsField,
                    typeBox,
                    staffField,
                    removeBtn
            );

            // **Validation Highlighting on Focus Lost**
            addValidationHighlighting();
        }

        public HBox getContainer() {
            return container;
        }

        // **Enhanced Validation Logic**
        public boolean isValid() {
            return !subjectNameField.getText().trim().isEmpty() &&
                    !periodsField.getText().trim().isEmpty() &&
                    isValidPeriods(periodsField.getText().trim()) &&
                    !staffField.getText().trim().isEmpty();
        }

        public String getSubjectName() {
            return subjectNameField.getText().trim();
        }

        public String getSubjectShortName() {
            return shortNameField.getText().trim().isEmpty() ? getSubjectName() : shortNameField.getText().trim();
        }

        public String getSubjectCode() {
            return codeField.getText().trim().isEmpty() ? getSubjectName() : codeField.getText().trim();
        }

        public int getPeriods() {
            return Integer.parseInt(periodsField.getText().trim());
        }

        public boolean isLab() {
            return "lab".equalsIgnoreCase(typeBox.getValue());
        }

        public String getStaffName() {
            return staffField.getText().trim();
        }

        // **Numeric Validation with Feedback**
        private boolean isValidPeriods(String input) {
            try {
                int periods = Integer.parseInt(input);
                return periods > 0;
            } catch (NumberFormatException e) {
                periodsField.setStyle("-fx-background-color: #FFC0CB;"); // Light red for invalid input
                return false;
            }
        }

        // **Add Focus Lost Event for Real-time Validation Feedback**
        private void addValidationHighlighting() {
            subjectNameField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused) {
                    if (subjectNameField.getText().trim().isEmpty()) {
                        subjectNameField.setStyle("-fx-background-color: #FFC0CB;");
                    } else {
                        subjectNameField.setStyle("-fx-background-color: white;");
                    }
                }
            });

            periodsField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused) {
                    isValidPeriods(periodsField.getText().trim());
                }
            });

            staffField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused) {
                    if (staffField.getText().trim().isEmpty()) {
                        staffField.setStyle("-fx-background-color: #FFC0CB;");
                    } else {
                        staffField.setStyle("-fx-background-color: white;");
                    }
                }
            });
        }
    }

    // ----------------- Output Section -----------------
    // In this multi-year version we create six tabs (one for each Year-Section combination)
    private TabPane createOutputSection() {
        resultTabs = new TabPane();
        resultTabs.getStyleClass().add("custom-tab-pane");
        // Initially empty; after generation tabs will be added.
        return resultTabs;
    }

    // ----------------- Timetable Tab Content Creator -----------------
    // For each (year, section) we create a VBox that contains the timetable grid, a fitness label, a print button and a summary table.
    private Node createTimetableTabContent(String tabTitle, GridPane grid, Label fitnessLabel, TableView<SubjectSummary> summaryTable) {
        VBox container = new VBox(15);
        container.setPadding(new Insets(25));
        container.setStyle("-fx-background-color: white;");

        Label titleLabel = new Label(tabTitle);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web(PRIMARY_COLOR));

        HBox legend = createLegend();
        VBox titleAndLegend = new VBox(10, titleLabel, legend);

        fitnessLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        fitnessLabel.setTextFill(Color.web(TEXT_COLOR));

        Button printButton = new Button("Print");
        printButton.setStyle("-fx-background-color: " + SECONDARY_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold;");
        printButton.setCursor(javafx.scene.Cursor.HAND);
        currentTimetableContainer = container;
        currentSummaryTable = summaryTable;
        printButton.setOnAction(e -> printNode(container));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controlsBox = new HBox(15, fitnessLabel, spacer, printButton);
        controlsBox.setAlignment(Pos.CENTER_LEFT);
        controlsBox.setPadding(new Insets(5, 0, 5, 0));

        Label summaryTitle = new Label("Subject & Staff Summary");
        summaryTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        summaryTitle.setTextFill(Color.web(PRIMARY_COLOR));
        summaryTitle.setPadding(new Insets(10, 0, 5, 0));

        container.getChildren().addAll(titleAndLegend, grid, controlsBox, summaryTitle, summaryTable);
        return container;
    }

    // ----------------- Create Subject Summary Table (same as before) -----------------
    private TableView<SubjectSummary> createSubjectSummaryTable() {
        TableView<SubjectSummary> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Generate a timetable to see the summary."));
        table.setMinHeight(150);
        table.setPrefHeight(250);

        TableColumn<SubjectSummary, Integer> serialCol = new TableColumn<>("S.No.");
        serialCol.setCellValueFactory(new PropertyValueFactory<>("serialNo"));
        serialCol.setMinWidth(50);
        serialCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<SubjectSummary, String> codeCol = new TableColumn<>("CODE");
        codeCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSubjectCode().toUpperCase())
        );
        codeCol.setMinWidth(80);

        TableColumn<SubjectSummary, String> nameCol = new TableColumn<>("SUBJECT NAME (SHORT)");
        nameCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSubjectNameDisplay().toUpperCase())
        );
        nameCol.setMinWidth(200);


        TableColumn<SubjectSummary, String> staffCol = new TableColumn<>("Staff Name");
        staffCol.setCellValueFactory(new PropertyValueFactory<>("staffName"));
        staffCol.setMinWidth(150);

        TableColumn<SubjectSummary, Integer> periodsCol = new TableColumn<>("Periods");
        periodsCol.setCellValueFactory(new PropertyValueFactory<>("totalPeriods"));
        periodsCol.setMinWidth(60);
        periodsCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().addAll(serialCol, codeCol, nameCol, staffCol, periodsCol);
        return table;
    }

    // ----------------- Printing method (same as before) -----------------
    private void printNode(Node nodeToPrint) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showAlert("Printing Error", "Could not create a printer job.", Alert.AlertType.ERROR);
            return;
        }

        Window owner = (nodeToPrint != null && nodeToPrint.getScene() != null)
                ? nodeToPrint.getScene().getWindow()
                : null;

        if (!job.showPrintDialog(owner)) {
            showNotification("Printing Cancelled", "Printing was cancelled by the user.", String.valueOf(Alert.AlertType.INFORMATION));
            return;
        }

        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(Paper.A4, PageOrientation.LANDSCAPE, Printer.MarginType.DEFAULT);

        double scaleX = 1.0, scaleY = 1.0;
        if (pageLayout != null) {
            double printableWidth = pageLayout.getPrintableWidth();
            double printableHeight = pageLayout.getPrintableHeight();
            double nodeWidth = nodeToPrint.getBoundsInParent().getWidth();
            double nodeHeight = nodeToPrint.getBoundsInParent().getHeight();

            double scale = Math.min(printableWidth / nodeWidth, printableHeight / nodeHeight);
            scaleX = scaleY = scale;

            // Apply scale transform safely
            javafx.scene.transform.Scale scaleTransform = new javafx.scene.transform.Scale(scaleX, scaleY);
            nodeToPrint.getTransforms().add(scaleTransform);

            try {
                boolean printed = job.printPage(pageLayout, nodeToPrint);
                if (printed) {
                    if (job.endJob()) {
                        showNotification("Printing Complete", "Timetable was successfully sent to the printer.", String.valueOf(Alert.AlertType.INFORMATION));
                    } else {
                        showAlert("Printing Error", "Print job did not complete successfully.", Alert.AlertType.ERROR);
                    }
                } else {
                    showAlert("Printing Failed", "Could not print the page.", Alert.AlertType.ERROR);
                    job.endJob();
                }
            } finally {
                // Always remove the transform, even if print fails
                nodeToPrint.getTransforms().remove(scaleTransform);
            }
        } else {
            showAlert("Layout Error", "Could not create page layout for printing.", Alert.AlertType.ERROR);
        }
    }


    // ----------------- Helper: Legend, Grid, Cells (same as before) -----------------
    private HBox createLegend() {
        HBox legend = new HBox(25);
        legend.setPadding(new Insets(5, 0, 15, 0));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getChildren().addAll(
                createLegendItem("Theory", THEORY_COLOR),
                createLegendItem("Lab", LAB_COLOR)
        );
        return legend;
    }
    private HBox createLegendItem(String text, String color) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        Pane colorRect = new Pane();
        colorRect.setPrefSize(18, 18);
        colorRect.setStyle("-fx-background-color: " + color + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
        item.getChildren().addAll(colorRect, label);
        return item;
    }
    private GridPane createEmptyTimetableGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(3);
        grid.setVgap(3);
        grid.setPadding(new Insets(10));
        grid.setStyle("-fx-background-color: " + BORDER_COLOR + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1;");

        ColumnConstraints dayCol = new ColumnConstraints();
        dayCol.setPrefWidth(110);
        dayCol.setMinWidth(90);
        grid.getColumnConstraints().add(dayCol);

        for (int i = 1; i <= PERIODS_PER_DAY; i++) {
            ColumnConstraints periodCol = new ColumnConstraints();
            periodCol.setPrefWidth(150); // increased from 130
            periodCol.setMinWidth(130);
            periodCol.setHgrow(Priority.SOMETIMES);
            grid.getColumnConstraints().add(periodCol);
        }

        // Header: Day / Period
        Label dayHeader = new Label("Day\n/ Period");
        dayHeader.setWrapText(true);
        dayHeader.setTextAlignment(TextAlignment.CENTER);
        dayHeader.setAlignment(Pos.CENTER);
        dayHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        dayHeader.setPadding(new Insets(5));
        dayHeader.setPrefSize(110, 60); // Adjust size as needed
        dayHeader.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #ccc; -fx-border-width: 0.5px;");
        GridPane.setHalignment(dayHeader, HPos.CENTER);
        GridPane.setValignment(dayHeader, VPos.CENTER);
        grid.add(dayHeader, 0, 0);


        // Header: Period labels with time ranges
        for (int i = 1; i <= PERIODS_PER_DAY; i++) {
            String timeRange = getTimeRangeLabel(i - 1); // 0-based
            Label periodLabel = createHeaderCell("P" + i + "\n" + timeRange); // line break added
            periodLabel.setWrapText(true);
            periodLabel.setTextAlignment(TextAlignment.CENTER);
            periodLabel.setAlignment(Pos.CENTER);
            periodLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            periodLabel.setMinHeight(60); // taller for full visibility
            periodLabel.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHalignment(periodLabel, HPos.CENTER); // center in cell
            grid.add(periodLabel, i, 0);
        }

        // Fill each row for days
        for (int i = 0; i < DAYS_OF_WEEK.size(); i++) {
            Label dayLabel = createDayCell(DAYS_OF_WEEK.get(i));
            grid.add(dayLabel, 0, i + 1);

            for (int j = 1; j <= PERIODS_PER_DAY; j++) {
                Label emptyCell = createEmptyDataCell();
                grid.add(emptyCell, j, i + 1);
            }
        }

        // Remove this block: Adding break/lunch labels – no longer needed
    /*
    for (int i = 0; i < PERIODS_PER_DAY; i++) {
        if (i == 1 || i == 3) {
            Label breakLabel = new Label("Break");
            breakLabel.setRotate(-90);
            breakLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
            grid.add(breakLabel, 0, i + 1);
        } else if (i == 4) {
            Label lunchLabel = new Label("Lunch");
            lunchLabel.setRotate(-90);
            lunchLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
            grid.add(lunchLabel, 0, i + 1);
        }
    }
    */

        DropShadow ds = new DropShadow();
        ds.setRadius(6.0);
        ds.setOffsetY(4.0);
        ds.setColor(Color.rgb(0, 0, 0, 0.2));
        grid.setEffect(ds);
        return grid;
    }


    private String getTimeRangeLabel(int periodIndex) {
        int startHour = 9;
        int startMinute = 15;
        int minutes = startHour * 60 + startMinute;
        int periodDuration = 45;
        int breakDuration = 15;
        int lunchDuration = 60;

        // Add durations of previous periods + breaks/lunches
        for (int i = 0; i < periodIndex; i++) {
            minutes += periodDuration;
            if (i == 1 || i == 3) minutes += breakDuration; // After 2nd and 4th period
            if (i == 4) minutes += lunchDuration;           // After 5th period
        }

        int startHours = minutes / 60;
        int startMins = minutes % 60;

        minutes += periodDuration;
        int endHours = minutes / 60;
        int endMins = minutes % 60;

        return String.format("%02d:%02d - %02d:%02d", startHours, startMins, endHours, endMins);
    }

    private String formatTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    private Label createHeaderCell(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        label.setTextFill(Color.WHITE);
        label.setPadding(new Insets(8));
        label.setStyle("-fx-background-color: " + PRIMARY_COLOR + ";");
        return label;
    }
    private Label createDayCell(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMaxHeight(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        label.setTextFill(Color.WHITE);
        label.setPadding(new Insets(8));
        label.setStyle("-fx-background-color: " + SECONDARY_COLOR + ";");
        return label;
    }
    private Label createEmptyDataCell() {
        Label label = new Label("-");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMaxHeight(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
        label.setPadding(new Insets(10));
        label.setMinHeight(60);
        label.setStyle("-fx-background-color: #f9f9f9;");
        return label;
    }

    // ----------------- Generate Timetables across Years -----------------
    private void generateTimetables() {
        Map<String, String> academicYears = new HashMap<>();
        for (String year : YEARS) {
            TextField academicYearField = academicYearFields.get(year);
            String academicYear = academicYearField.getText().trim();
            if (!academicYear.matches("\\d{4}-\\d{4}")) {
                showAlert("Input Error", "Invalid academic year for " + year + ". Use YYYY-YYYY.", Alert.AlertType.ERROR);
                return;
            }
            academicYears.put(year, academicYear);
        }


        // --- 1. Validate and Collect input for all years ---
        // For each year we construct maps for subjectsWithPeriods, subjectStaffMap, isLabMap,
        // subjectShortNameMap and subjectCodeMap.
        Map<String, Map<String, Integer>> allSubjectsWithPeriods = new HashMap<>();
        Map<String, Map<String, String>> allSubjectStaff = new HashMap<>();
        Map<String, Map<String, Boolean>> allIsLab = new HashMap<>();
        Map<String, Map<String, String>> allSubjectShortName = new HashMap<>();
        Map<String, Map<String, String>> allSubjectCode = new HashMap<>();
//     Map<String, String> academicYears = new HashMap<>();
//  for (String year : YEARS) {
//             TextField academicYearField = academicYearFields.get(year);
//             String academicYear = academicYearField.getText().trim();
//             academicYears.put(year, academicYear);
//         }

        boolean valid = true;
        for (String year : YEARS) {



            ObservableList<SubjectRow> rows = subjectRowsMap.get(year);
            Map<String, Integer> subjectPeriods = new HashMap<>();
            Map<String, String> subjectStaff = new HashMap<>();
            Map<String, Boolean> isLabLocal = new HashMap<>();
            Map<String, String> subjectShort = new HashMap<>();
            Map<String, String> subjectCodes = new HashMap<>();
            int totalPeriods = 0;
            Set<String> subjectNames = new HashSet<>();



            for (SubjectRow row : rows) {
                if (!row.isValid()) {
                    valid = false;
                    row.getContainer().setStyle("-fx-border-color: " + WARNING_COLOR + "; -fx-border-width: 1.5px;");
                    continue;
                }
                String subjectName = row.getSubjectName();
                String shortName = row.getSubjectShortName();
                String code = row.getSubjectCode();
                int periods = row.getPeriods();
                boolean isLab = row.isLab();
                String staff = row.getStaffName();

                if (!subjectNames.add(subjectName)) {
                    valid = false;
                    row.getContainer().setStyle("-fx-border-color: " + WARNING_COLOR + "; -fx-border-width: 1.5px;");
                    continue;
                }
                subjectPeriods.put(subjectName, periods);
                subjectStaff.put(subjectName, staff);
                isLabLocal.put(subjectName, isLab);
                subjectShort.put(subjectName, shortName);
                subjectCodes.put(subjectName, code);

                totalPeriods += periods;
            }
            if (totalPeriods != TOTAL_PERIODS_PER_WEEK) {
                valid = false;
                showAlert("Input Error", "For " + year + ", total periods must equal exactly " + TOTAL_PERIODS_PER_WEEK, Alert.AlertType.ERROR);
            }
            allSubjectsWithPeriods.put(year, subjectPeriods);
            allSubjectStaff.put(year, subjectStaff);
            allIsLab.put(year, isLabLocal);
            allSubjectShortName.put(year, subjectShort);
            allSubjectCode.put(year, subjectCodes);
        }
        if (!valid) {
            showAlert("Validation Error", "Please correct the highlighted fields.", Alert.AlertType.ERROR);
            return;
        }

        // --- 2. Disable controls and clear previous results ---
        generateBtn.setDisable(true);
        generateBtn.setText("Generating...");
        progressIndicator.setVisible(true);
        statusLabel.setText("Generating timetables for all years...");
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        resultTabs.getTabs().clear(); // clear any previous generated tabs

        // --- 3. For each year generate Section A and Section B timetables.
        // (The TimeTableGenerator.generateSchedule(...) method is used here.)
        // For each generated timetable we create a grid, a fitness label and a summary table.
        // In a real system you might run these in background threads. For brevity we do it here sequentially.

        for (String year : YEARS) {
            Map<String, Integer> subjectsCopy = allSubjectsWithPeriods.get(year);
            Map<String, String> staffCopy = allSubjectStaff.get(year);
            Map<String, Boolean> labCopy = allIsLab.get(year);
            // ----------------- SECTION A -----------------
            statusLabel.setText("Generating " + year + " Section A timetable...");
            TimeTableGenerator.Schedule scheduleA = TimeTableGenerator.generateScheduleGA(subjectsCopy, staffCopy, labCopy, globalStaffSchedule, globalStaffSchedule, "Section A", year);
            GridPane gridA = createEmptyTimetableGrid();
            updateTimetableGrid(gridA, scheduleA);
            Label fitnessA = new Label("Fitness: " + scheduleA.getFitness());
            TableView<SubjectSummary> summaryTableA = createSubjectSummaryTable();
            updateSummaryTable(summaryTableA, subjectsCopy, staffCopy);
            String tabAName = year + " - Section A";
            Tab tabA = new Tab(tabAName, createTimetableTabContent(tabAName, gridA, fitnessA, summaryTableA));
            tabA.setClosable(false);
            resultTabs.getTabs().add(tabA);

            // ----------------- SECTION B -----------------
            statusLabel.setText("Generating " + year + " Section B timetable...");
            TimeTableGenerator.Schedule scheduleB = TimeTableGenerator.generateScheduleGA(subjectsCopy, staffCopy, labCopy, globalStaffSchedule, globalStaffSchedule, "Section B", year);
            GridPane gridB = createEmptyTimetableGrid();
            updateTimetableGrid(gridB, scheduleB);
            Label fitnessB = new Label("Fitness: " + scheduleB.getFitness());
            TableView<SubjectSummary> summaryTableB = createSubjectSummaryTable();
            updateSummaryTable(summaryTableB, subjectsCopy, staffCopy);
            String tabBName = year + " - Section B";
            Tab tabB = new Tab(tabBName, createTimetableTabContent(tabBName, gridB, fitnessB, summaryTableB));
            tabB.setClosable(false);
            resultTabs.getTabs().add(tabB);
        }

        statusLabel.setText("Timetable generation complete!");
        progressIndicator.setVisible(false);
        generateBtn.setDisable(false);
        generateBtn.setText("Generate All Timetables");
    }

    // ----------------- Update Timetable Grid -----------------
// This method fills the given grid with subject abbreviations based on the schedule.
    private void updateTimetableGrid(GridPane grid, TimeTableGenerator.Schedule schedule) {
        if (schedule == null) return;

        // Clear previous timetable cells (except headers)
        grid.getChildren().removeIf(node -> {
            Integer col = GridPane.getColumnIndex(node);
            Integer row = GridPane.getRowIndex(node);
            return col != null && row != null && (col > 0 || row > 0);
        });

        Map<String, List<String>> timetable = schedule.getTimetable();

        // Fill timetable data
        for (int i = 0; i < DAYS_OF_WEEK.size(); i++) {
            String day = DAYS_OF_WEEK.get(i);
            List<String> daySchedule = timetable.getOrDefault(day, new ArrayList<>());

            for (int j = 0; j < daySchedule.size(); j++) {
                String subject = daySchedule.get(j);
                Label cell = new Label(subject == null ? "FREE" : abbreviate(subject));
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.setMaxHeight(Double.MAX_VALUE);
                cell.setAlignment(Pos.CENTER);
                cell.setFont(Font.font("Segoe UI", FontWeight.MEDIUM, 13));
                cell.setPadding(new Insets(8));
                cell.setMinHeight(50);

                String color = (subject != null && schedule.getIsLabMap().getOrDefault(subject, false))
                        ? LAB_COLOR : THEORY_COLOR;
                if (subject == null) color = "#e0e0e0";
                cell.setStyle("-fx-background-color: " + color + "; -fx-border-color: #ccc; -fx-border-width: 0.5px;");

                grid.add(cell, j + 1, i + 1); // +1 to offset for header row/column
            }
        }

        // Clear previous header cells
        grid.getChildren().removeIf(node -> {
            Integer row = GridPane.getRowIndex(node);
            Integer col = GridPane.getColumnIndex(node);
            return (row != null && row == 0) || (col != null && col == 0);
        });

        // Add time headers
        for (int i = 0; i < PERIODS_PER_DAY; i++) {
            String timeRange = getTimeRangeLabel(i);
            Label timeLabel = new Label("P" + (i + 1) + "\n" + timeRange);
            timeLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            timeLabel.setWrapText(true);
            timeLabel.setTextAlignment(TextAlignment.CENTER);
            timeLabel.setPadding(new Insets(5));
            timeLabel.setMinHeight(60);
            timeLabel.setAlignment(Pos.CENTER);
            GridPane.setHalignment(timeLabel, HPos.CENTER);
            grid.add(timeLabel, i + 1, 0);
        }

        // Add day headers
        for (int i = 0; i < DAYS_OF_WEEK.size(); i++) {
            Label dayLabel = new Label(DAYS_OF_WEEK.get(i));
            dayLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            dayLabel.setPadding(new Insets(5));
            dayLabel.setAlignment(Pos.CENTER_LEFT);
            dayLabel.setMinHeight(50);
            grid.add(dayLabel, 0, i + 1);
        }
    }


    private String abbreviate(String subject) {
        return subject.length() > 6 ? subject.substring(0, 6) + "..." : subject;
    }

    private String getTimeLabel(int period) {
        int startHour = 9;
        int startMinute = 15;
        int minutes = startHour * 60 + startMinute;
        int periodDuration = 45;

        minutes += period * periodDuration;

        int hours = minutes / 60;
        int mins = minutes % 60;

        return String.format("%02d:%02d", hours, mins);
    }

    // ----------------- Update Summary Table -----------------
    // For simplicity we simply list each subject information.
    private void updateSummaryTable(TableView<SubjectSummary> table, Map<String,Integer> subjects, Map<String,String> staff) {
        if (subjects == null || staff == null) return;
        ObservableList<SubjectSummary> data = FXCollections.observableArrayList();
        int serial = 1;
        for (String subject : subjects.keySet()) {
            String code = subject; // For simplicity we use subject name as code here.
            String shortName = subject; // Likewise.
            String staffName = staff.get(subject);
            int periods = subjects.get(subject);
            data.add(new SubjectSummary(serial++, code, shortName, staffName, periods));
        }
        table.setItems(data);
    }

    // ----------------- Utility Alert and Notification -----------------
    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            // Custom graphic/icon
            String iconPath = switch (type) {
                case INFORMATION -> "file:src/main/resources/info.png";
                case WARNING -> "file:src/main/resources/warning.png";
                case ERROR -> "file:src/main/resources/error.png";
                case CONFIRMATION -> "file:src/main/resources/confirm.png";
                default -> "";
            };
            if (!iconPath.isEmpty()) {
                ImageView icon = new ImageView(new Image(iconPath));
                icon.setFitWidth(40);
                icon.setFitHeight(40);
                alert.setGraphic(icon);
            }

            // Inline CSS styling
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.setStyle("""
            -fx-font-family: 'Segoe UI';
            -fx-font-size: 14px;
            -fx-background-color: linear-gradient(to bottom, #ffffff, #f9f9f9);
        """);

            dialogPane.lookup(".content.label").setStyle("""
            -fx-text-fill: #333333;
            -fx-wrap-text: true;
        """);

            dialogPane.lookupButton(ButtonType.OK).setStyle("""
            -fx-background-color: #4CAF50;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-background-radius: 6px;
        """);

            alert.showAndWait();
        });
    }
    private void showNotification(String title, String message, String hexColor) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.setStyle(
                    "-fx-background-color: " + hexColor + ";" +
                            "-fx-font-family: 'Segoe UI';" +
                            "-fx-font-size: 14px;"
            );

            Label contentLabel = (Label) dialogPane.lookup(".content.label");
            if (contentLabel != null) {
                contentLabel.setStyle("-fx-text-fill: white; -fx-wrap-text: true;");
            }

            Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
            if (okButton != null) {
                okButton.setStyle("""
                -fx-background-color: #ffffff33;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 6px;
                -fx-cursor: hand;
            """);
            }

            ImageView icon = new ImageView(new Image("file:src/main/resources/notify.png"));
            icon.setFitWidth(40);
            icon.setFitHeight(40);
            alert.setGraphic(icon);

            alert.showAndWait();
        });
    }


    private void exportToPdf() {
        // Placeholder implementation for exporting to PDF
        showNotification("Export to PDF", "PDF export functionality is not yet implemented.", WARNING_COLOR);
    }

    private void exportToExcel() {
        if (currentSummaryTable == null) {
            showNotification("Export Failed", "No summary table found.", "#F44336");
            return;
        }

        Platform.runLater(() -> {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Subject Summary");

                // Create header row
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("S.No");
                headerRow.createCell(1).setCellValue("Subject Code");
                headerRow.createCell(2).setCellValue("Subject Name (Short)");
                headerRow.createCell(3).setCellValue("Staff Name");
                headerRow.createCell(4).setCellValue("Total Periods");

                // Fill data rows
                int rowNum = 1;
                for (SubjectSummary summary : currentSummaryTable.getItems()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(summary.getSerialNo());
                    row.createCell(1).setCellValue(summary.getSubjectCode());
                    row.createCell(2).setCellValue(summary.getSubjectNameDisplay());
                    row.createCell(3).setCellValue(summary.getStaffName());
                    row.createCell(4).setCellValue(summary.getTotalPeriods());
                }

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Excel File");
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
                fileChooser.setInitialFileName("SubjectSummary.xlsx");
                File file = fileChooser.showSaveDialog(null);

                if (file != null) {
                    try (FileOutputStream fileOut = new FileOutputStream(file)) {
                        workbook.write(fileOut);
                        showNotification("Export Successful", "Data exported to Excel successfully.", "#4CAF50");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                showNotification("Export Failed", "An error occurred while exporting data to Excel.", "#F44336");
            }
        });
    }

    private void exportToWord() {
        // Placeholder implementation for exporting to Word
        showNotification("Export to Word", "Word export functionality is not yet implemented.", WARNING_COLOR);
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ----------------- SubjectSummary class -----------------
    public static class SubjectSummary {
        private final SimpleIntegerProperty serialNo;
        private final SimpleStringProperty subjectCode;
        private final SimpleStringProperty subjectNameDisplay;
        private final SimpleStringProperty staffName;
        private final SimpleIntegerProperty totalPeriods;

        public SubjectSummary(int serialNo, String subjectCode, String subjectNameDisplay, String staffName, int totalPeriods) {
            this.serialNo = new SimpleIntegerProperty(serialNo);
            this.subjectCode = new SimpleStringProperty(subjectCode);
            this.subjectNameDisplay = new SimpleStringProperty(subjectNameDisplay);
            this.staffName = new SimpleStringProperty(staffName);
            this.totalPeriods = new SimpleIntegerProperty(totalPeriods);
        }

        public int getSerialNo() {
            return serialNo.get();
        }

        public String getSubjectCode() {
            return subjectCode.get();
        }

        public String getSubjectNameDisplay() {
            return subjectNameDisplay.get();
        }

        public String getStaffName() {
            return staffName.get();
        }

        public int getTotalPeriods() {
            return totalPeriods.get();
        }
    }

    // ----------------- Dummy SubjectRow class -----------------
    // (This duplicate class definition has been removed to resolve the error.)
}
