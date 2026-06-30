package org.backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 简历结构化数据 VO — 与 parsed_data JSON 结构 1:1
 * 入参（在线填写）和出参（AI 解析）共用
 */
@Data
public class ResumeData {
    private BasicInfo basicInfo;
    private List<Education> education;
    private List<WorkExperience> workExperience;
    private List<Project> projects;
    private List<Skill> skills;
    private List<String> certifications;
    private String selfEvaluation;

    @Data
    public static class BasicInfo {
        private String name;
        private String email;
        private String phone;
        private String targetPosition;
        private String location;
        private Integer yearsOfExperience;
    }

    @Data
    public static class Education {
        private String school;
        private String degree;
        private String major;
        private String startDate;
        private String endDate;
    }

    @Data
    public static class WorkExperience {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }

    @Data
    public static class Project {
        private String name;
        private String role;
        private List<String> techStack;
        private String description;
    }

    @Data
    public static class Skill {
        private String name;
        private String level;   // 精通/熟练/熟悉/了解
        private Integer years;
    }
}
