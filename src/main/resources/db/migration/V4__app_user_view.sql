-- Everyone who can make a request, with their role.
CREATE VIEW app_user AS
SELECT id, 'TEACHER' AS role FROM teacher
UNION ALL
SELECT id, 'PARENT' AS role FROM parent;
