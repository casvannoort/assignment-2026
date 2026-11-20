-- Read model for teachers: every slot of an evening (empty ones too), with who comes and with which parent.
CREATE VIEW evening_overview AS
SELECT s.evening_id, s.id AS slot_id, s.start_time, s.end_time, s.capacity,
       b.id AS booking_id, c.name AS child_name, p.name AS parent_name
FROM slot s
LEFT JOIN booking b ON b.slot_id = s.id
LEFT JOIN child c ON c.id = b.child_id
LEFT JOIN parent p ON p.id = b.booked_by;
