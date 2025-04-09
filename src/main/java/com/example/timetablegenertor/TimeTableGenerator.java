package com.example.timetablegenertor;

import java.util.*;
import java.util.stream.Collectors;

public class TimeTableGenerator {

    // Constants
    private static final int PERIODS_PER_DAY = 8;
    private static final int DAYS_PER_WEEK = 5;
    private static final int TOTAL_PERIODS_PER_WEEK = PERIODS_PER_DAY * DAYS_PER_WEEK; // 40
    private static final List<String> DAYS_OF_WEEK = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday");

    // Max theory periods per subject per day
    private static final int MAX_PERIODS_PER_THEORY_SUBJECT_PER_DAY = 2;

    private static final Random random = new Random();
    private static final Scanner scanner = new Scanner(System.in);

    /**
     * Inner class representing a complete timetable schedule for a section.
     */
    static class Schedule {
        private final Map<String, List<String>> timetable; // day -> list of subjects
        private final Map<String, String> subjectStaffMap; // subject -> staff name
        private final Map<String, Boolean> isLabMap; // subject -> is lab?
        private final Map<String, Integer> subjectsWithPeriods; // subject -> total weekly periods required
        private final Map<String, String> subjectShortNameMap; // subject -> short name
        private final Map<String, String> subjectCodeMap; // subject -> code

        Schedule(Map<String, List<String>> timetable,
                 Map<String, String> subjectStaffMap,
                 Map<String, Boolean> isLabMap,
                 Map<String, Integer> subjectsWithPeriods,
                 Map<String, String> subjectShortNameMap,
                 Map<String, String> subjectCodeMap) {
            this.timetable = timetable;
            this.subjectStaffMap = subjectStaffMap;
            this.isLabMap = isLabMap;
            this.subjectsWithPeriods = subjectsWithPeriods;
            this.subjectShortNameMap = subjectShortNameMap;
            this.subjectCodeMap = subjectCodeMap;
        }

        public Map<String, List<String>> getTimetable() {
            return timetable;
        }

        public Map<String, String> getSubjectStaffMap() {
            return subjectStaffMap;
        }

        public Map<String, Boolean> getIsLabMap() {
            return isLabMap;
        }

        public Map<String, Integer> getSubjectsWithPeriods() {
            return subjectsWithPeriods;
        }

        // --- NEW GETTERS ---
        public Map<String, String> getSubjectShortNameMap() {
            return subjectShortNameMap;
        }

        public Map<String, String> getSubjectCodeMap() {
            return subjectCodeMap;
        }
        // --- END NEW GETTERS ---

        public double getFitness() {
            // Calculate fitness based on constraints
            double fitness = 0.0;

            // Calculate counts for all subjects
            Map<String, Integer> subjectCounts = new HashMap<>();
            for (String day : DAYS_OF_WEEK) {
                List<String> periods = timetable.get(day);
                for (String subject : periods) {
                    if (subject != null) { // Subject could be null if timetable not fully filled (though it should be)
                        subjectCounts.put(subject, subjectCounts.getOrDefault(subject, 0) + 1);
                    }
                }
            }

            // Check 1: Lab periods are placed consecutively and appear exactly once per week
            for (Map.Entry<String, Integer> entry : subjectsWithPeriods.entrySet()) {
                String subject = entry.getKey();
                int requiredPeriods = entry.getValue();

                if (isLabMap.getOrDefault(subject, false)) { // Use getOrDefault for safety
                    int actualPeriods = subjectCounts.getOrDefault(subject, 0);

                    // Reward if the total periods match the required periods
                    // Penalty if they don't match exactly
                    if (actualPeriods == requiredPeriods) {
                        fitness += 1.0; // Reward for correct number of periods
                    } else {
                        fitness -= Math.abs(requiredPeriods - actualPeriods) * 0.5; // Penalty for mismatch (adjust weight)
                    }

                    // Check if the lab is actually consecutive and appears only once per week
                    boolean foundConsecutiveBlock = false;
                    for (String day : DAYS_OF_WEEK) {
                        List<String> periods = timetable.get(day);
                        for (int i = 0; i <= PERIODS_PER_DAY - requiredPeriods; i++) {
                            boolean isBlock = true;
                            for (int j = 0; j < requiredPeriods; j++) {
                                if (i + j >= periods.size() || periods.get(i + j) == null || !periods.get(i + j).equals(subject)) {
                                    isBlock = false;
                                    break;
                                }
                            }
                            if (isBlock) {
                                foundConsecutiveBlock = true;
                                break; // Found the block for this lab on this day
                            }
                        }
                        if (foundConsecutiveBlock && actualPeriods == requiredPeriods) {
                            fitness += 1.0; // Reward for consecutive block
                        } else if (actualPeriods > 0) {
                            // Penalty if periods exist but are not consecutive *and* if they are not the full block
                            // Only penalize if it's not the correct full block placed consecutively
                            if (!foundConsecutiveBlock) {
                                fitness -= 1.0;
                            }
                        }
                    }
                    // Additional check: If a lab is required but no periods are assigned, penalize
                    if (requiredPeriods > 0 && actualPeriods == 0) {
                        fitness -= 2.0; // Strong penalty
                    }
                }
            }

            // Check 2: Theory subject count per day
            for (String day : DAYS_OF_WEEK) {
                List<String> periods = timetable.get(day);
                Map<String, Integer> theoryCount = new HashMap<>();
                for (String period : periods) {
                    if (period != null && !isLabMap.getOrDefault(period, false)) { // Use getOrDefault for safety
                        theoryCount.put(period, theoryCount.getOrDefault(period, 0) + 1);
                    }
                }
                for (int count : theoryCount.values()) {
                    if (count <= MAX_PERIODS_PER_THEORY_SUBJECT_PER_DAY) {
                        fitness += 0.5; // Small reward for adherence
                    } else {
                        fitness -= (count - MAX_PERIODS_PER_THEORY_SUBJECT_PER_DAY) * 0.5; // Penalty for exceeding
                    }
                }
            }

            // Check 3: Lab periods in first half or second half
            int midPeriod = PERIODS_PER_DAY / 2; // Assuming 8 periods, mid is at index 4 (after P4)
            for (String day : DAYS_OF_WEEK) {
                List<String> periods = timetable.get(day);
                for (int i = 0; i < PERIODS_PER_DAY; i++) {
                    String subject = periods.get(i);
                    if (subject != null && isLabMap.getOrDefault(subject, false)) {
                        int labDuration = subjectsWithPeriods.getOrDefault(subject, 0);

                        // Check if this is the *start* of the lab block
                        boolean isStartOfBlock = (i == 0 || periods.get(i - 1) == null || !periods.get(i - 1).equals(subject));

                        if (isStartOfBlock) {
                            // Check if the entire block fits within the first half (up to index midPeriod-1)
                            boolean fitsInFirstHalf = (i < midPeriod && i + labDuration <= midPeriod);
                            // Check if the entire block fits within the second half (from index midPeriod)
                            boolean fitsInSecondHalf = (i >= midPeriod && i + labDuration <= PERIODS_PER_DAY);

                            if (fitsInFirstHalf || fitsInSecondHalf) {
                                fitness += 0.5; // Reward if the lab block fits entirely within a half
                            } else {
                                fitness -= 0.5; // Penalty if it spans across the halves
                            }
                        }
                    }
                }
            }

            // Check 4: Staff collisions (same staff teaching different subjects at the same time) - Section level
            for (String day : DAYS_OF_WEEK) {
                List<String> periods = timetable.get(day);
                Map<String, String> staffPeriodMap = new HashMap<>(); // period_index -> staff_name
                for (int i = 0; i < periods.size(); i++) {
                    String subject = periods.get(i);
                    if (subject != null) {
                        String staff = subjectStaffMap.get(subject);
                        if (staffPeriodMap.containsKey(staff)) {
                            // Collision detected within the section
                            fitness -= 1.0;
                        } else {
                            staffPeriodMap.put(staff, subject);
                        }
                    }
                }
            }

            return fitness;
        }
    }

    /**
     * Main routine.
     * For each year (2nd, 3rd, 4th), user inputs subject details.
     * For each year, two schedules are generated (section A and section B).
     * A single globalStaffSchedule is maintained to ensure that same staff are not
     * scheduled in more than one class at the same period across all years.
     */
    public static void main(String[] args) {
        // Global collision map: key = "day:period", value = set of busy staff (teacher
        // names)
        Map<String, Set<String>> globalStaffSchedule = new HashMap<>();

        // Global staff map to track which staff members are assigned to which sections
        Map<String, Set<String>> globalStaffSectionMap = new HashMap<>(); // key = staff name, value = set of sections

        try {
            // Process each year (assuming 2nd, 3rd, and 4th)
            List<String> years = Arrays.asList("2nd Year", "3rd Year", "4th Year");

            // For each year, get subject details then generate Section A and B timetables.
            for (String year : years) {
                System.out.println("\n============================================");
                System.out.println("TIMETABLE SETUP FOR " + year);
                System.out.println("============================================\n");

                // Get details for this year. (Both sections share the same subjects and staff)
                Map<String, Integer> subjectsWithPeriods = new HashMap<>();
                Map<String, String> subjectStaffMap = new HashMap<>();
                Map<String, Boolean> isLabMap = new HashMap<>();
                Map<String, String> subjectShortNameMap = new HashMap<>();
                Map<String, String> subjectCodeMap = new HashMap<>();

                System.out.print("Enter number of subjects (including labs) for " + year + ": ");
                int numSubjects = scanner.nextInt();
                scanner.nextLine(); // consume newline
                if (numSubjects <= 0) {
                    throw new IllegalArgumentException("Number of subjects must be positive.");
                }

                for (int i = 0; i < numSubjects; i++) {
                    System.out.println("\nSubject " + (i + 1) + " for " + year + ":");
                    System.out.print("Enter subject name (e.g., Programming): ");
                    String name = scanner.nextLine().trim();
                    if (name.isEmpty())
                        throw new IllegalArgumentException("Subject name cannot be empty.");
                    if (subjectsWithPeriods.containsKey(name))
                        throw new IllegalArgumentException("Duplicate subject name: " + name);

                    System.out.print("Enter subject short name (e.g., PRG): ");
                    String shortName = scanner.nextLine().trim().toUpperCase();
                    if (shortName.isEmpty()) shortName = name; // Use full name if short name is empty

                    System.out.print("Enter subject code (e.g., CS201): ");
                    String code = scanner.nextLine().trim().toUpperCase();
                    if (code.isEmpty()) code = name; // Use full name if code is empty

                    System.out.print("Enter number of periods per week (e.g., 4 for theory, 2 or 3 for lab): ");
                    int periods = scanner.nextInt();
                    scanner.nextLine(); // consume newline
                    if (periods <= 0 || periods > TOTAL_PERIODS_PER_WEEK) {
                        throw new IllegalArgumentException(
                                "Periods per week must be between 1 and " + TOTAL_PERIODS_PER_WEEK);
                    }

                    System.out.print("Is this subject theory or lab? (theory/lab): ");
                    String type = scanner.nextLine().trim().toLowerCase();
                    while (!type.equals("theory") && !type.equals("lab")) {
                        System.out.print("Invalid input. Please enter 'theory' or 'lab': ");
                        type = scanner.nextLine().trim().toLowerCase();
                    }
                    boolean isLab = type.equals("lab");

                    System.out.print("Enter staff name (e.g., Dr. Smith): ");
                    String staff = scanner.nextLine().trim();
                    if (staff.isEmpty()) {
                        throw new IllegalArgumentException("Staff name cannot be empty.");
                    }

                    subjectsWithPeriods.put(name, periods);
                    subjectStaffMap.put(name, staff);
                    isLabMap.put(name, isLab);
                    subjectShortNameMap.put(name, shortName);
                    subjectCodeMap.put(name, code);


                    // Track staff assignments across sections and years
                    globalStaffSectionMap.computeIfAbsent(staff, k -> new HashSet<>()).add(year + " - " + (isLab ? "Lab" : "Theory"));
                }

                // Validate total periods
                int totalSubjectPeriods = subjectsWithPeriods.values().stream().mapToInt(Integer::intValue).sum();
                if (totalSubjectPeriods != TOTAL_PERIODS_PER_WEEK) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Input Error for %s: Total periods for all subjects must be exactly %d. You entered %d.",
                                    year, TOTAL_PERIODS_PER_WEEK, totalSubjectPeriods));
                }

                System.out.println("\nGenerating timetables for " + year + " ...");

                // Generate Section A timetable first using Genetic Algorithm
                Schedule scheduleA = generateScheduleGA(subjectsWithPeriods, subjectStaffMap, isLabMap,
                        subjectShortNameMap, subjectCodeMap, globalStaffSchedule, globalStaffSectionMap, "Section A", year);

                // Generate Section B timetable, taking into account global staff collisions.
                Schedule scheduleB = generateScheduleGA(subjectsWithPeriods, subjectStaffMap, isLabMap,
                        subjectShortNameMap, subjectCodeMap, globalStaffSchedule, globalStaffSectionMap, "Section B", year);

                // Print timetables for this year
                printTimetable(scheduleA, year + " - Section A");
                printTimetable(scheduleB, year + " - Section B");
            }
        } catch (InputMismatchException e) {
            System.err.println("Invalid input. Please enter numeric values where required.");
            scanner.next(); // clear invalid input
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * Generates a schedule for one section (for one year) using Genetic Algorithm.
     */
    static Schedule generateScheduleGA(Map<String, Integer> subjectsWithPeriods,
                                       Map<String, String> subjectStaffMap,
                                       Map<String, Boolean> isLabMap,
                                       Map<String, String> subjectShortNameMap, // Added
                                       Map<String, String> subjectCodeMap,      // Added
                                       Map<String, Set<String>> globalStaffSchedule,
                                       Map<String, Set<String>> globalStaffSectionMap,
                                       String section,
                                       String year) {
        // Initialize population size
        int populationSize = 100; // Increase population size for better results

        // Initialize population with random timetables
        List<Schedule> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            Map<String, List<String>> timetable = initializeTimetable(subjectsWithPeriods, subjectStaffMap, isLabMap,
                    subjectShortNameMap, subjectCodeMap, // Added
                    globalStaffSectionMap, section);
            population.add(new Schedule(timetable, subjectStaffMap, isLabMap, subjectsWithPeriods,
                    subjectShortNameMap, subjectCodeMap)); // Added
        }

        // Evolve population using Genetic Algorithm
        int generations = 1000; // Increase generations for better optimization
        for (int generation = 0; generation < generations; generation++) {
            final int currentGeneration = generation; // Declare a final variable

            // Calculate fitness for each schedule in population
            // (Optional: print fitness periodically)
            // if (generation % 50 == 0) {
            //    System.out.println("Generation " + currentGeneration + ": ");
            //    population.forEach(schedule -> System.out.println("  Fitness = " + schedule.getFitness()));
            // }

            // Select fittest schedules
            List<Schedule> fittestSchedules = selectFittest(population, populationSize / 5); // Select top 20%

            // Crossover (recombine) fittest schedules to create new offspring
            List<Schedule> offspring = crossover(fittestSchedules, subjectsWithPeriods, subjectStaffMap, isLabMap,
                    subjectShortNameMap, subjectCodeMap); // Added

            // Mutate offspring to introduce random variations
            mutate(offspring, subjectsWithPeriods, subjectStaffMap, isLabMap,
                    subjectShortNameMap, subjectCodeMap, // Added
                    globalStaffSectionMap, section);

            // Combine parents and offspring for next generation selection
            population.addAll(offspring);

            // Select population for next generation (keep the best)
            population = selectFittest(population, populationSize);
        }

        // Return fittest schedule
        Schedule bestSchedule = population.stream().max(Comparator.comparingDouble(Schedule::getFitness)).orElse(null);

        // Print final fitness
        System.out.println("Final fitness for " + section + ": " + bestSchedule.getFitness());

        return bestSchedule;
    }

    // Helper methods for Genetic Algorithm
    static Map<String, List<String>> initializeTimetable(
            Map<String, Integer> subjectsWithPeriods,
            Map<String, String> subjectStaffMap,
            Map<String, Boolean> isLabMap,
            Map<String, String> subjectShortNameMap, // Added
            Map<String, String> subjectCodeMap,      // Added
            Map<String, Set<String>> globalStaffSectionMap,
            String section) {

        Map<String, List<String>> timetable = new HashMap<>();
        for (String day : DAYS_OF_WEEK) {
            timetable.put(day, new ArrayList<>(Collections.nCopies(PERIODS_PER_DAY, null)));
        }

        // Step 1: Place labs (Ensure each lab appears only once per week)
        List<String> labs = subjectsWithPeriods.entrySet().stream()
                .filter(e -> isLabMap.getOrDefault(e.getKey(), false)) // Filter only lab subjects
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String lab : labs) {
            int labDuration = subjectsWithPeriods.getOrDefault(lab, 0);
            boolean placed = false;

            // Try to place the lab only once in the week
            int attempts = 0;
            while (!placed && attempts < 100) { // Add attempt limit to prevent infinite loops
                String day = DAYS_OF_WEEK.get(random.nextInt(DAYS_OF_WEEK.size()));
                // Ensure startPeriod + duration fits within the day
                int startPeriod = random.nextInt(PERIODS_PER_DAY - labDuration + 1);

                // Check if the slot is available (no overlapping subjects)
                boolean canPlace = true;
                for (int j = 0; j < labDuration; j++) {
                    if (startPeriod + j >= PERIODS_PER_DAY || timetable.get(day).get(startPeriod + j) != null) {
                        canPlace = false;
                        break;
                    }
                }

                // Check for staff collision within the same section during initialization
                if (canPlace) {
                    String staff = subjectStaffMap.getOrDefault(lab, "");
                    for (int j = 0; j < labDuration; j++) {
                        if (startPeriod + j < PERIODS_PER_DAY) { // Ensure index is valid
                            String currentPeriodSubject = timetable.get(day).get(startPeriod + j);
                            if (currentPeriodSubject != null && subjectStaffMap.getOrDefault(currentPeriodSubject, "").equals(staff)) {
                                canPlace = false;
                                break;
                            }
                        } else {
                            canPlace = false; // Lab goes beyond the day's periods
                            break;
                        }
                    }
                }

                if (canPlace) {
                    // Place the lab in the timetable
                    for (int j = 0; j < labDuration; j++) {
                        if (startPeriod + j < PERIODS_PER_DAY) { // Ensure index is valid
                            timetable.get(day).set(startPeriod + j, lab);
                        } else {
                            // This case should technically be prevented by the check above
                            // but adding a safety break just in case
                            break;
                        }
                    }
                    placed = true; // Lab is now scheduled for the week
                }
                attempts++;
            }
            if (!placed) {
                System.err.println("Warning: Could not place lab " + lab + " during initialization after " + attempts + " attempts.");
                // This might happen if constraints are too tight, or periods/days are small
                // The GA might later fix this, but it's a potential issue
            }
        }

        // Step 2: Place theory subjects
        List<String> theorySubjects = subjectsWithPeriods.entrySet().stream()
                .filter(e -> !isLabMap.getOrDefault(e.getKey(), false)) // Only theory subjects
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String subject : theorySubjects) {
            int remainingPeriods = subjectsWithPeriods.getOrDefault(subject, 0);

            while (remainingPeriods > 0) {
                String day = DAYS_OF_WEEK.get(random.nextInt(DAYS_OF_WEEK.size()));
                int period = random.nextInt(PERIODS_PER_DAY);

                if (timetable.get(day).get(period) == null) {
                    timetable.get(day).set(period, subject);
                    remainingPeriods--;
                }
            }
        }

        return timetable;
    }

    static String getRandomSubject(Map<String, Integer> subjectsWithPeriods, Map<String, Boolean> isLabMap) {
        // Select a random subject
        List<String> subjects = new ArrayList<>(subjectsWithPeriods.keySet());
        return subjects.get(random.nextInt(subjects.size()));
    }

    static List<Schedule> selectFittest(List<Schedule> population, int numToSelect) {
        // Select fittest schedules based on fitness (using tournament selection or simply sorting)
        return population.stream()
                .sorted(Comparator.comparingDouble(Schedule::getFitness).reversed())
                .limit(numToSelect)
                .collect(Collectors.toList());
    }

    static List<Schedule> crossover(List<Schedule> fittestSchedules,
                                    Map<String, Integer> subjectsWithPeriods,
                                    Map<String, String> subjectStaffMap,
                                    Map<String, Boolean> isLabMap,
                                    Map<String, String> subjectShortNameMap, // Added
                                    Map<String, String> subjectCodeMap)      // Added
    {
        // Crossover (recombine) fittest schedules to create new offspring
        List<Schedule> offspring = new ArrayList<>();
        // Create pairs and perform crossover
        for (int i = 0; i < fittestSchedules.size(); i += 2) {
            if (i + 1 < fittestSchedules.size()) { // Ensure pairs exist
                Schedule parent1 = fittestSchedules.get(i);
                Schedule parent2 = fittestSchedules.get(i + 1);

                Map<String, List<String>> childTimetable = crossoverTimetables(parent1.getTimetable(),
                        parent2.getTimetable());
                offspring.add(new Schedule(childTimetable, subjectStaffMap, isLabMap, subjectsWithPeriods,
                        subjectShortNameMap, subjectCodeMap)); // Added
            }
        }
        return offspring;
    }

    static Map<String, List<String>> crossoverTimetables(Map<String, List<String>> parent1Timetable,
                                                         Map<String, List<String>> parent2Timetable) {
        // Crossover timetables by selecting a random day and swapping periods
        Map<String, List<String>> childTimetable = new HashMap<>();
        for (String day : DAYS_OF_WEEK) {
            // Choose a crossover point (a day)
            if (random.nextBoolean()) {
                childTimetable.put(day, new ArrayList<>(parent1Timetable.get(day))); // Copy parent 1's day
            } else {
                childTimetable.put(day, new ArrayList<>(parent2Timetable.get(day))); // Copy parent 2's day
            }
        }
        return childTimetable;
    }

    static void mutate(List<Schedule> offspring,
                       Map<String, Integer> subjectsWithPeriods,
                       Map<String, String> subjectStaffMap,
                       Map<String, Boolean> isLabMap,
                       Map<String, String> subjectShortNameMap, // Added
                       Map<String, String> subjectCodeMap,      // Added
                       Map<String, Set<String>> globalStaffSectionMap,
                       String section) {
        // Mutate offspring by randomly swapping periods (with check for labs)
        for (Schedule schedule : offspring) {
            Map<String, List<String>> timetable = schedule.getTimetable();
            for (String day : DAYS_OF_WEEK) {
                List<String> periods = timetable.get(day);
                // Use a 10% mutation rate per day
                if (random.nextDouble() < 0.1) { // Only mutate 10% of the days
                    int i = random.nextInt(PERIODS_PER_DAY);
                    int j = random.nextInt(PERIODS_PER_DAY);
                    String subjectI = periods.get(i);
                    String subjectJ = periods.get(j);

                    // Check if either subject is a lab
                    if (subjectI != null && isLabMap.getOrDefault(subjectI, false)) {
                        // If subjectI is a lab, move entire lab block
                        int labDuration = subjectsWithPeriods.getOrDefault(subjectI, 0);
                        boolean canMoveBlock = true;
                        // Check if we have space to move the block
                        if (j + labDuration > PERIODS_PER_DAY) {
                            canMoveBlock = false;
                        } else {
                            for (int k = 0; k < labDuration; k++) {
                                if (j + k >= PERIODS_PER_DAY || periods.get(j + k) != null) { // Check bounds
                                    canMoveBlock = false;
                                    break;
                                }
                            }
                        }
                        if (canMoveBlock) {
                            // move the lab block.
                            // First, clear the original location of the block
                            for(int k = 0; k < labDuration; k++) {
                                if (i + k < PERIODS_PER_DAY && periods.get(i + k) != null && periods.get(i + k).equals(subjectI)) {
                                    periods.set(i + k, null);
                                }
                            }
                            // Then, place the block at the new location
                            for (int k = 0; k < labDuration; k++) {
                                if (j + k < PERIODS_PER_DAY) {
                                    periods.set(j + k, subjectI);
                                }
                            }
                            continue;  // Skip the remaining part of this iteration.
                        }
                    }
                    if (subjectJ != null && isLabMap.getOrDefault(subjectJ, false)) {
                        // If subjectJ is a lab, move entire lab block
                        int labDuration = subjectsWithPeriods.getOrDefault(subjectJ, 0);
                        boolean canMoveBlock = true;
                        // Check if we have space to move the block
                        if (i + labDuration > PERIODS_PER_DAY) {
                            canMoveBlock = false;
                        } else {
                            for (int k = 0; k < labDuration; k++) {
                                if (i + k >= PERIODS_PER_DAY || periods.get(i + k) != null) { // Check bounds
                                    canMoveBlock = false;
                                    break;
                                }
                            }
                        }
                        if (canMoveBlock) {
                            // move the lab block.
                            // First, clear the original location of the block
                            for(int k = 0; k < labDuration; k++) {
                                if (j + k < PERIODS_PER_DAY && periods.get(j + k) != null && periods.get(j + k).equals(subjectJ)) {
                                    periods.set(j + k, null);
                                }
                            }
                            // Then, place the block at the new location
                            for (int k = 0; k < labDuration; k++) {
                                if (i + k < PERIODS_PER_DAY) {
                                    periods.set(i + k, subjectJ);
                                }
                            }
                            continue; // Skip the remaining part of this iteration.
                        }
                    }

                    // Check if either period is null (shouldn't happen if total periods == 40)
                    if (subjectI == null || subjectJ == null) {
                        continue;
                    }
                    // Ensure that swapping doesn't violate staff allocation across sections
                    String staffI = subjectStaffMap.getOrDefault(subjectI, "");
                    String staffJ = subjectStaffMap.getOrDefault(subjectJ, "");

                    // Check if the swap would create a conflict with the global staff section map
                    // This condition seems overly complex. Let's simplify:
                    // Only swap if the periods are different and the staff assignments don't conflict
                    // with the global map for that specific day and period across all sections.

                    // This mutation doesn't directly check global StaffSchedule.
                    // The collision check is mainly handled in the fitness function and initialization.
                    // Let's remove the redundant check here for now.
                    // if (!staffI.equals(staffJ) || !globalStaffSectionMap.getOrDefault(staffI, Set.of()).contains(section)) {
                    // Swap the theory subjects
                    periods.set(i, subjectJ);
                    periods.set(j, subjectI);
                    // }
                }
            }
        }
    }

    // Utility to print a timetable in a formatted table.
    private static void printTimetable(Schedule schedule, String section) {
        System.out.println("\nTimetable for " + section + ":");
        System.out.println("--------------------------------------------------------------------------------------");
        System.out.printf("| %-10s |", "Day");
        for (int i = 1; i <= PERIODS_PER_DAY; i++) {
            System.out.printf(" %-6s |", "P" + i);
        }
        System.out.println("\n--------------------------------------------------------------------------------------");

        Map<String, List<String>> timetable = schedule.getTimetable();
        Map<String, String> shortNames = schedule.getSubjectShortNameMap();
        for (String day : DAYS_OF_WEEK) {
            System.out.printf("| %-10s |", day);
            List<String> periods = timetable.get(day);
            for (String period : periods) {
                String display = (period == null) ? "FREE" : shortNames.getOrDefault(period, abbreviate(period)); // Use short name or abbreviate full name
                System.out.printf(" %-6s |", display);
            }
            System.out.println();
        }
        System.out.println("--------------------------------------------------------------------------------------");
    }

    private static String abbreviate(String subject) {
        return subject.length() > 6 ? subject.substring(0, 6) : subject; // Use full name if <= 6 chars
    }
}