-- 운영 DB에 직접 적용하지 않는 수동 준비용 SQL.
-- application.properties의 ddl-auto=update가 활성화된 환경에서는 엔티티로 자동 생성될 수 있습니다.
CREATE TABLE IF NOT EXISTS profile_recommendations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recommender_user_id BIGINT NOT NULL,
    recommended_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_profile_recommendations_pair UNIQUE (recommender_user_id, recommended_user_id),
    INDEX idx_profile_recommendations_target (recommended_user_id),
    CONSTRAINT fk_profile_recommendations_actor FOREIGN KEY (recommender_user_id) REFERENCES users (id),
    CONSTRAINT fk_profile_recommendations_target FOREIGN KEY (recommended_user_id) REFERENCES users (id)
) ENGINE = InnoDB;
