package com.giut.server.profile.seed;

import com.giut.server.profile.dto.request.CreateSkillTagRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultSkillTagCatalog {

    private static final List<CreateSkillTagRequest> SKILLS = List.of(
            skill("Java", "BACKEND_DEVELOPER"),
            skill("Spring Boot", "BACKEND_DEVELOPER"),
            skill("Spring Security", "BACKEND_DEVELOPER"),
            skill("JPA", "BACKEND_DEVELOPER"),
            skill("Python", "DATA_ANALYST", "DATA_ENGINEER", "AI_ENGINEER"),
            skill("Node.js", "BACKEND_DEVELOPER"),
            skill("TypeScript", "BACKEND_DEVELOPER", "FRONTEND_DEVELOPER", "MOBILE_DEVELOPER"),
            skill("JavaScript", "BACKEND_DEVELOPER", "FRONTEND_DEVELOPER", "MOBILE_DEVELOPER"),
            skill("React", "FRONTEND_DEVELOPER"),
            skill("Next.js", "FRONTEND_DEVELOPER"),
            skill("Vue.js", "FRONTEND_DEVELOPER"),
            skill("HTML", "FRONTEND_DEVELOPER", "UI_UX_DESIGNER"),
            skill("CSS", "FRONTEND_DEVELOPER", "UI_UX_DESIGNER"),
            skill("Flutter", "MOBILE_DEVELOPER"),
            skill("Kotlin", "MOBILE_DEVELOPER"),
            skill("Swift", "MOBILE_DEVELOPER"),
            skill("React Native", "MOBILE_DEVELOPER"),
            skill("MySQL", "BACKEND_DEVELOPER", "DATA_ENGINEER", "DATA_ANALYST"),
            skill("PostgreSQL", "BACKEND_DEVELOPER", "DATA_ENGINEER", "DATA_ANALYST"),
            skill("Redis", "BACKEND_DEVELOPER", "DATA_ENGINEER"),
            skill("Docker", "BACKEND_DEVELOPER", "DATA_ENGINEER", "AI_ENGINEER"),
            skill("AWS", "BACKEND_DEVELOPER", "DATA_ENGINEER", "AI_ENGINEER"),
            skill("Git", "BACKEND_DEVELOPER", "FRONTEND_DEVELOPER", "MOBILE_DEVELOPER"),
            skill("SQL", "DATA_ANALYST", "DATA_ENGINEER", "DIGITAL_MARKETER"),
            skill("Pandas", "DATA_ANALYST", "DATA_ENGINEER", "AI_ENGINEER"),
            skill("NumPy", "DATA_ANALYST", "AI_ENGINEER"),
            skill("PyTorch", "AI_ENGINEER"),
            skill("TensorFlow", "AI_ENGINEER"),
            skill("Apache Spark", "DATA_ENGINEER"),
            skill("Airflow", "DATA_ENGINEER"),
            skill("Figma", "UI_UX_DESIGNER", "PRODUCT_DESIGNER", "SERVICE_PLANNER"),
            skill("Adobe Photoshop", "GRAPHIC_DESIGNER", "CONTENT_MARKETER"),
            skill("Adobe Illustrator", "GRAPHIC_DESIGNER", "BRAND_MARKETER"),
            skill("Blender", "PRODUCT_DESIGNER", "GRAPHIC_DESIGNER"),
            skill("Notion", "SERVICE_PLANNER", "BUSINESS_PLANNER", "PROJECT_MANAGER"),
            skill("Jira", "SERVICE_PLANNER", "PROJECT_MANAGER"),
            skill("Excel", "DATA_ANALYST", "BUSINESS_PLANNER", "DIGITAL_MARKETER"),
            skill("PowerPoint", "BUSINESS_PLANNER", "PROJECT_MANAGER", "BRAND_MARKETER"),
            skill("Google Analytics", "DIGITAL_MARKETER", "CONTENT_MARKETER", "BRAND_MARKETER"),
            skill("Google Ads", "DIGITAL_MARKETER", "BRAND_MARKETER")
    );

    public List<CreateSkillTagRequest> skills() {
        return SKILLS;
    }

    private static CreateSkillTagRequest skill(String name, String... roleCodes) {
        return new CreateSkillTagRequest(name, List.of(roleCodes));
    }
}

