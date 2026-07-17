LOAD DATA LOCAL INFILE '/home/rywang/c43/data/students.txt'
INTO TABLE student
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(student_id, name, email, dob, gpa);

LOAD DATA LOCAL INFILE '/home/rywang/c43/data/courses.txt'
INTO TABLE course
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(course_id, code, title, credits);

LOAD DATA LOCAL INFILE '/home/rywang/c43/data/enrollments.txt'
INTO TABLE enrollment
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(student_id, course_id, grade, enrolled_at);
