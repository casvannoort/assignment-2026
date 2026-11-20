# Acceptance scenarios: parent-teacher conference evenings

These scenarios come only from the original assignment (assignment.md). The README is used only for endpoint paths and test users.

Coverage per assignment item:
- Item 1 (evening with date, start, end, conversation length; slots generated): S1, S2, S3
- Item 2 (maximum conversations per slot, default one): S4, S5
- Item 3 (parent sees free slots and books one for a child): S6, S7, S8
- Item 4 (parent moves or cancels a booking): S9, S10, S11
- Item 5 (child with more than one parent; every parent sees the appointment): S12, S13
- Item 6 (teacher overview: which child, when, with which parent): S14, S15
- Item 7 (teacher removes a slot, e.g. for a break): S16, S17

Conventions:
- Users are named as in the README; every request sends `X-User-Id` with that user's id. Jansen = `…0101`, Anna = `…0201`, Mark = `…0202`, Sara = `…0203`, Kees = `…0204`. Emma = `…0301`, Daan = `…0302`, Lisa = `…0303`.
- "Create evening E" means `POST /evenings` as Jansen with date 2035-03-12, start 19:00, end 20:00, conversation length 15 minutes, and no maximum given, unless the scenario says otherwise. The README does not name the body fields, so use whatever field names the backend documents for date, start, end, length and maximum.
- Dates are far in the future, so "the evening is in the past" never applies.

---

### S1: Slots are generated from start, end and conversation length
Covers: item 1
Given: no evenings exist.
When: Jansen creates evening E (2035-03-12, 19:00 to 20:00, 15 minutes).
Then: 2xx. `GET /evenings/{E}/overview` as Jansen shows exactly four slots: 19:00 to 19:15, 19:15 to 19:30, 19:30 to 19:45 and 19:45 to 20:00, all on 2035-03-12. The slots do not overlap and none ends after 20:00.

### S2: A different length gives a different number of slots
Covers: item 1
Given: no evenings exist.
When: Jansen creates an evening on 2035-03-13, 18:00 to 19:30, 10 minutes per conversation.
Then: 2xx. The overview shows nine consecutive 10-minute slots, the first starting at 18:00 and the last ending at 19:30.

### S3: The evening appears in the teacher's list
Covers: item 1
Given: evening E exists.
When: `GET /evenings` as Jansen.
Then: 2xx. The list contains E with date 2035-03-12, start 19:00, end 20:00 and length 15 minutes.
(Length that does not divide the evening evenly, for example 25 minutes over 19:00 to 20:00: assignment is silent; any consistent answer is acceptable.)

### S4: By default a slot holds one conversation
Covers: item 2
Given: evening E created with no maximum given. Anna has booked Emma into the 19:00 slot.
When: Sara books Daan into the 19:00 slot (`POST /evenings/{E}/bookings` as Sara, body `{childId: Daan, slotId: <19:00>}`).
Then: 4xx. `GET /evenings/{E}/slots` as Sara does not offer 19:00 as free. The overview shows only Emma in 19:00.

### S5: A larger maximum allows that many conversations, and no more
Covers: item 2
Given: Jansen creates an evening on 2035-03-14, 19:00 to 20:00, 15 minutes, maximum 2 conversations per slot.
When: (a) Anna books Emma into 19:00. (b) Sara books Daan into 19:00.
Then: (a) 2xx. After (a), `GET /evenings/{id}/slots` as Sara still lists 19:00 as having a free place. (b) 2xx. After (b), the overview shows Emma and Daan both in 19:00, and the slots list no longer offers 19:00 as free. (Jansen teaches only two test children, so a third booking cannot be tried with the README's users.)

### S6: A parent sees the free slots of an evening
Covers: item 3
Given: evening E exists with no bookings.
When: `GET /evenings/{E}/slots` as Anna.
Then: 2xx. All four slots (19:00, 19:15, 19:30, 19:45) are listed as free.

### S7: A parent books a slot for a child
Covers: item 3
Given: evening E exists with no bookings.
When: `POST /evenings/{E}/bookings` as Anna, body `{childId: Emma, slotId: <19:15>}`.
Then: 2xx, and the response identifies the booking. `GET /bookings` as Anna shows Emma at 19:15 on 2035-03-12 for evening E.

### S8: Free slots shrink as they fill
Covers: item 3
Given: evening E (default maximum 1) with no bookings.
When: Anna books Emma into 19:00, then Sara books Daan into 19:15, then `GET /evenings/{E}/slots` as Sara.
Then: Both bookings 2xx. The free slots list contains only 19:30 and 19:45. Booking 19:00 again with another child returns 4xx.

### S9: A parent moves a booking to another slot
Covers: item 4
Given: evening E; Anna has booked Emma into 19:00 (booking B).
When: `PUT /evenings/{E}/bookings/{B}` as Anna, body `{slotId: <19:45>}`.
Then: 2xx. `GET /bookings` as Anna shows Emma at 19:45, not 19:00. The free slots list now includes 19:00 and no longer includes 19:45. The overview shows Emma at 19:45 only.

### S10: Moving into a full slot is refused and the booking stays put
Covers: item 4, item 2
Given: evening E; Anna has booked Emma into 19:00 (booking B); Sara has booked Daan into 19:15.
When: `PUT /evenings/{E}/bookings/{B}` as Anna, body `{slotId: <19:15>}`.
Then: 4xx. `GET /bookings` as Anna still shows Emma at 19:00. The overview still shows Daan alone at 19:15.

### S11: A parent cancels a booking
Covers: item 4
Given: evening E; Anna has booked Emma into 19:30 (booking B).
When: `DELETE /evenings/{E}/bookings/{B}` as Anna.
Then: 2xx. `GET /bookings` as Anna no longer shows the booking. 19:30 is listed as free again, and the overview shows no child in 19:30. Another parent (Sara, for Daan) can now book 19:30 with 2xx.

### S12: The other parent sees the appointment
Covers: item 5
Given: evening E; Anna has booked Emma into 19:15.
When: `GET /bookings` as Mark (Emma's other parent).
Then: 2xx. The list contains Emma's appointment at 19:15 on 2035-03-12 for evening E.

### S13: The other parent sees later changes too
Covers: item 5, item 4
Given: evening E; Anna has booked Emma into 19:15 (booking B).
When: Anna moves B to 19:45, then `GET /bookings` as Mark.
Then: Mark sees Emma at 19:45, not 19:15. After Anna cancels B, Mark no longer sees the appointment.
(Whether Mark may himself move or cancel the booking Anna made: assignment is silent; any consistent answer is acceptable.)

### S14: The teacher overview shows child, time and parent
Covers: item 6
Given: evening E; Anna has booked Emma into 19:00; Sara has booked Daan into 19:30.
When: `GET /evenings/{E}/overview` as Jansen.
Then: 2xx. The overview lists 19:00 with child Emma and parent Anna Bakker, 19:30 with child Daan and parent Sara Visser, and 19:15 and 19:45 with no child.

### S15: The overview reflects moves and cancellations
Covers: item 6, item 4
Given: as S14.
When: Anna moves Emma's booking to 19:45; Sara cancels Daan's booking; then `GET /evenings/{E}/overview` as Jansen.
Then: The overview shows Emma with Anna at 19:45, nothing at 19:00 or 19:30, and no trace of Daan.

### S16: The teacher removes a slot for a break
Covers: item 7
Given: evening E with no bookings.
When: `DELETE /evenings/{E}/slots/{<19:30>}` as Jansen.
Then: 2xx. The overview shows three slots (19:00, 19:15, 19:45); 19:30 is gone. `GET /evenings/{E}/slots` as Anna does not list 19:30.

### S17: A removed slot cannot be booked
Covers: item 7, item 3
Given: evening E; Jansen has removed the 19:30 slot.
When: Anna books Emma into the removed 19:30 slot id.
Then: 4xx. `GET /bookings` as Anna shows no booking for Emma, and the overview shows no 19:30 slot.
(Removing a slot that already has a booking: assignment is silent; any consistent answer is acceptable, as long as the overview and the parents' booking lists agree afterwards.)
