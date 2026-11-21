-- Add the evening's date, so the parent's slot list can leave out slots that have started.
CREATE OR REPLACE VIEW slot_availability AS
SELECT s.id, s.evening_id, e.teacher_id, s.start_time, s.end_time, s.capacity,
       s.capacity - count(b.id) AS free_places,
       e.date
FROM slot s
JOIN evening e ON e.id = s.evening_id
LEFT JOIN booking b ON b.slot_id = s.id
GROUP BY s.id, e.teacher_id, e.date;
