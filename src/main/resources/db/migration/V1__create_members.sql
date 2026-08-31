CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    nickname VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    student_no VARCHAR(30),
    role VARCHAR(20) NOT NULL,
    university_email VARCHAR(255),
    university_verified_at DATETIME,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT uk_members_student_no UNIQUE (student_no),
    CONSTRAINT uk_members_university_email UNIQUE (university_email)
);
