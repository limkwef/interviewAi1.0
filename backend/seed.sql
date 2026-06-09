USE interview;

INSERT IGNORE INTO user (username, email, phone, password, target_position, tech_stack, role) VALUES
('test', 'test@example.com', '13800138000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Java后端开发', JSON_ARRAY('Java', 'Spring Boot', 'MySQL'), 'user');

INSERT IGNORE INTO user_favorite (user_id, question_id) VALUES
(1, 1), (1, 2), (1, 4);
