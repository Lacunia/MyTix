DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS student;

CREATE TABLE student (
    student_id  INT             AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    dob         DATE            NOT NULL,
    gpa         DECIMAL(3, 2)
);

CREATE TABLE course (
    course_id   INT             AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(10)     NOT NULL UNIQUE,
    title       VARCHAR(200)    NOT NULL,
    credits     DECIMAL(2, 1) NOT NULL DEFAULT 1.0
);

CREATE TABLE enrollment (
    student_id  INT             NOT NULL,
    course_id   INT             NOT NULL,
    grade       DECIMAL(4, 1),
    enrolled_at DATE            NOT NULL DEFAULT (CURDATE()),
    PRIMARY KEY (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES student(student_id),
    FOREIGN KEY (course_id)  REFERENCES course(course_id)
);