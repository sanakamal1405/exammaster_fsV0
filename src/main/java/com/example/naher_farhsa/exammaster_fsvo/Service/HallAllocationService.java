package com.example.naher_farhsa.exammaster_fsvo.Service;

import com.example.naher_farhsa.exammaster_fsvo.DTO.AllocationSummaryDTO;
import com.example.naher_farhsa.exammaster_fsvo.DTO.HallAllocationDTO;
import com.example.naher_farhsa.exammaster_fsvo.Entity.Exam;
import com.example.naher_farhsa.exammaster_fsvo.Entity.HallAllocation;
import com.example.naher_farhsa.exammaster_fsvo.Enum.Course;
import com.example.naher_farhsa.exammaster_fsvo.Enum.Hall;
import com.example.naher_farhsa.exammaster_fsvo.Enum.Shift;
import com.example.naher_farhsa.exammaster_fsvo.Enum.Student;
import com.example.naher_farhsa.exammaster_fsvo.Repository.ExamRepository;
import com.example.naher_farhsa.exammaster_fsvo.Repository.HallAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class HallAllocationService {

    @Autowired
    ExamRepository examRepository;

    @Autowired
    private HallAllocationRepository hallAllocationRepository;

    private static final int ROWS = 10;
    private static final int COLS = 10;

    @Transactional
    public void addHallAllocation(Exam exam) {
        List<Student> enrolledStudents = filterStudentsByCourse(exam.getCourseId());
        assignSets(exam, enrolledStudents);
    }

    private List<Student> filterStudentsByCourse(Course course) {
        List<Student> result = new ArrayList<>();
        for (Student s : Student.values()) {
            if (s.getEnrolledCourses().contains(course)) {
                result.add(s);
            }
        }
        return result;
    }

    private void assignSets(Exam exam, List<Student> students) {
        int studentIndex = 0;

        for (int row = 0; row < ROWS && studentIndex < students.size(); row++) {
            boolean startWithA = row % 2 == 0;

            for (int col = 0; col < COLS && studentIndex < students.size(); col++) {
                Student student = students.get(studentIndex);

                String set = (startWithA == (col % 2 == 0)) ? "A" : "B";
                String hallAllocId = exam.getHallId().name() + set + student.getStudentId();

                HallAllocation alloc = new HallAllocation();
                alloc.setHallAllocId(hallAllocId);
                alloc.setExam(exam);
                alloc.setStudent(student);

                hallAllocationRepository.save(alloc);
                studentIndex++;
            }
        }
    }

    public List<HallAllocationDTO> getHallAllocationByExamCourse(String courseId) {
        Course course = getCourseByCode(courseId);

        List<HallAllocation> allocations = hallAllocationRepository.findByExam_CourseId(course);
        List<HallAllocationDTO> result = new ArrayList<>();

        for (HallAllocation alloc : allocations) {
            result.add(new HallAllocationDTO(
                    alloc.getHallAllocId(),
                    alloc.getExam().getDate(),
                    alloc.getExam().getShift(),
                    new HallAllocationDTO.ExamInfo(
                            alloc.getExam().getExamId(),
                            course.getCourseCode(),
                            course.getCourseName()
                    ),
                    new HallAllocationDTO.StudentInfo(
                            alloc.getStudent().getStudentId(),
                            alloc.getStudent().getStudentName()
                    )
            ));
        }

        return result;
    }

    public List<HallAllocation> getHallSeatAllocationsByCourse(Course course) {
        return hallAllocationRepository.findByExam_CourseId(course);
    }


    public List<List<String>> getSeatMatrix(String courseCode) {
        Course course;
        try {
            course = Course.valueOf(courseCode);  // Convert courseCode to Course enum
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid course code: " + courseCode);  // Handle invalid course code
        }

        List<HallAllocation> allocations = getHallSeatAllocationsByCourse(course);  // Fetch seat allocations for the course
        if (allocations.isEmpty()) return Collections.emptyList();  // Return empty list if no allocations

        // Initialize an empty seat matrix with ROWS and COLS
        List<List<String>> seatMatrix = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            List<String> row = new ArrayList<>(Collections.nCopies(COLS, ""));  // Initialize each row with empty values
            seatMatrix.add(row);
        }

        // Fill matrix with student roll numbers or placeholders ("X")
        int index = 0;
        for (HallAllocation alloc : allocations) {
            if (index < ROWS * COLS) {
                int row = index / COLS;  // Determine the row index
                int col = index % COLS;  // Determine the column index
                String student = alloc.getStudent() != null ? alloc.getStudent().getStudentId() : "X";  // Get student ID or "X" if null
                seatMatrix.get(row).set(col, student);  // Set the value in the seat matrix
                index++;
            }
        }

        return seatMatrix;  // Return the filled seat matrix
    }


    @Transactional
    public void deleteHallAllocationByExamCourse(Course courseId) {
        Exam exam = examRepository.findByCourseId(courseId);
        hallAllocationRepository.deleteByExam(exam);
    }


    public AllocationSummaryDTO getAllocationSummary(String courseId) {
        Course course = getCourseByCode(courseId);

        List<Student> studentsEnrolledInCourse = getStudentsByCourse(course);
        int totalAllocations = studentsEnrolledInCourse.size();
        String courseName = course.getCourseName();

        Exam exam = examRepository.findByCourseId(course);
        LocalDate examDate = exam.getDate();
        Shift examShift = exam.getShift();
        Hall hallNumber = exam.getHallId();

        AllocationSummaryDTO summary = new AllocationSummaryDTO();
        summary.setCourseId(courseId);
        summary.setCourseName(courseName);
        summary.setTotalAllocations(totalAllocations);
        summary.setHallNumber(hallNumber);
        summary.setExamDate(examDate);
        summary.setExamShift(examShift);

        return summary;
    }

    private Course getCourseByCode(String courseId) {
        for (Course c : Course.values()) {
            if (c.getCourseCode().equals(courseId)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Invalid course code: " + courseId);
    }

    private List<Student> getStudentsByCourse(Course course) {
        List<Student> list = new ArrayList<>();
        for (Student student : Student.values()) {
            if (student.getEnrolledCourses().contains(course)) {
                list.add(student);
            }
        }
        return list;
    }

    public List<Course> getAvailableCourses() {
        List<Course> assignedCourses = examRepository.findAllAssignedCourses();
        List<Course> courses = new ArrayList<>();

        for (Course course : Course.values()) {
            if (assignedCourses.contains(course)) {
                courses.add(course);
            }
        }

        return courses;
    }
}
