-- Read model for evening lists (CQRS read side).
CREATE VIEW evening_summary AS
SELECT e.id, e.teacher_id, t.name AS teacher_name, e.date, e.start_time, e.end_time, e.conversation_minutes
FROM evening e
JOIN teacher t ON t.id = e.teacher_id;
