-- Read model for parents: per slot, how many places are still free.
CREATE VIEW slot_availability AS
SELECT s.id, s.evening_id, e.teacher_id, s.start_time, s.end_time, s.capacity,
       s.capacity - count(b.id) AS free_places
FROM slot s
JOIN evening e ON e.id = s.evening_id
LEFT JOIN booking b ON b.slot_id = s.id
GROUP BY s.id, e.teacher_id;
