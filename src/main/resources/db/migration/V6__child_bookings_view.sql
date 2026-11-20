-- Read model for parents: every booking with the names a parent needs to know when to show up.
CREATE VIEW child_bookings AS
SELECT b.id, b.evening_id, b.slot_id, b.child_id, c.name AS child_name, t.name AS teacher_name,
       e.date, s.start_time, s.end_time, p.name AS booked_by_name
FROM booking b
JOIN child c ON c.id = b.child_id
JOIN slot s ON s.id = b.slot_id
JOIN evening e ON e.id = b.evening_id
JOIN teacher t ON t.id = e.teacher_id
JOIN parent p ON p.id = b.booked_by;
