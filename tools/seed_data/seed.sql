DELETE FROM local_events WHERE dayId BETWEEN '2026-02-10' AND '2026-04-20';
DELETE FROM task WHERE dayDate BETWEEN '2026-02-10' AND '2026-04-20';
DELETE FROM text_annotations WHERE pageId IN (SELECT id FROM page WHERE id IN (SELECT json_each.value FROM daypage, json_each(pageIds) WHERE date BETWEEN '2026-02-10' AND '2026-04-20'));
DELETE FROM unified_ocr_text WHERE sourceType = 'MEMO' AND sourceId BETWEEN '2026-02-10' AND '2026-04-20';
DELETE FROM unified_ocr_page WHERE sourceType = 'MEMO' AND sourceId BETWEEN '2026-02-10' AND '2026-04-20';
DELETE FROM page WHERE id IN (SELECT json_each.value FROM daypage, json_each(pageIds) WHERE date BETWEEN '2026-02-10' AND '2026-04-20');
DELETE FROM daypage WHERE date BETWEEN '2026-02-10' AND '2026-04-20';

INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '40240d66-f58a-4fc0-bd25-2ee974663305', 'Quarterly Goals Review', '2026-02-10', '2026-02-10',
    1770761700000, 1770763500000, 0,
    'Client Office', 'Notes for Quarterly Goals Review', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1fa9571f-1ed6-4345-85e3-fda7c351334c', '1:1 with Sarah', '2026-02-10', '2026-02-10',
    1770742800000, 1770744600000, 0,
    'Zoom', 'Notes for 1:1 with Sarah', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f42a4e23-34f8-4751-b162-cce42f8e43b0', 'Community Coding Workshop', '2026-02-10', '2026-02-10',
    1770771600000, 1770775200000, 0,
    'Microsoft Teams', 'Notes for Community Coding Workshop', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '79151609-0dbd-4988-b9a3-b784e38a0b14', 'Work From Home', '2026-02-10', '2026-02-10',
    1770710400000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '228b99d0-2ac0-4fed-a127-c50e7ebb0ac0', 'Team Standup', '2026-02-10', '2026-02-10',
    1770753600000, 1770760800000, 0,
    'Microsoft Teams', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4626fb00-c009-495d-909d-9c7f9e387c3f', 'API Review', '2026-02-11', '2026-02-11',
    1770839100000, 1770842700000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b29f7466-2cd0-4796-aef3-686068082f4a', 'Mentorship Session', '2026-02-11', '2026-02-11',
    1770849000000, 1770850800000, 0,
    'Main Office', 'Notes for Mentorship Session', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c886b2f8-c8b2-415a-ae43-dde275344efc', 'UX Review', '2026-02-11', '2026-02-11',
    1770858900000, 1770860700000, 0,
    'Zoom', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cb4e6cd1-9945-4b50-b640-fa2872264663', 'Gym Session', '2026-02-11', '2026-02-11',
    1770839100000, 1770842700000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9523d5a2-6767-4d20-994d-ebf9044be9b5', 'Vacation Day', '2026-02-11', '2026-02-11',
    1770796800000, NULL, 1,
    NULL, 'Notes for Vacation Day', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b3d710d4-d756-416a-8e55-dac341e5ef20', 'Jake''s Basketball Practice', '2026-02-11', '2026-02-11',
    1770842700000, 1770849900000, 0,
    'Downtown Office', 'Notes for Jake''s Basketball Practice', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6f13bff7-35a6-46d3-834e-d540c51cfc14', '1:1 with Alex', '2026-02-11', '2026-02-11',
    1770831900000, 1770835500000, 0,
    'Building 2, Floor 3', 'Notes for 1:1 with Alex', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f89b8e88-1ed9-4d35-a9ad-bb808cc4ffee', 'Skip Level', '2026-02-12', '2026-02-12',
    1770945300000, 1770952500000, 0,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4bfbf1c1-61ed-403a-86da-24dacb95136c', 'Visit Grandparents', '2026-02-12', '2026-02-12',
    1770883200000, NULL, 1,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e9624c68-722a-48be-9e5a-e92050c2ff27', 'Code2Community Mentoring', '2026-02-12', '2026-02-12',
    1770942600000, 1770957000000, 0,
    '123 Main Street', 'Notes for Code2Community Mentoring', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ee2de074-5594-4ccf-83fb-6f3edce8b823', '1:1 with Alex', '2026-02-12', '2026-02-12',
    1770946200000, 1770948000000, 0,
    NULL, 'Notes for 1:1 with Alex', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9b5886f9-77e7-491b-9e64-05b2e1a8f8a3', 'Work From Home', '2026-02-13', '2026-02-13',
    1770969600000, NULL, 1,
    NULL, 'Notes for Work From Home', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964960, 1773109964960,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'de47ca82-cc13-4cbb-aeb7-295ad900c436', 'Hackathon', '2026-02-13', '2026-02-13',
    1770969600000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b8eea2f9-1eb9-4431-be19-83fb2e054656', 'Training Workshop', '2026-02-13', '2026-02-13',
    1770969600000, NULL, 1,
    NULL, 'Notes for Training Workshop', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c89d253b-b6ce-4f5b-90ba-5bd6243e8a77', 'Code2Community Mentoring', '2026-02-13', '2026-02-13',
    1771008300000, 1771010100000, 0,
    'Microsoft Teams', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f85ed3ad-73d1-4839-a2d5-5890be19e0f6', 'Family Bike Ride', '2026-02-13', '2026-02-13',
    1771026300000, 1771033500000, 0,
    'Conference Room A', 'Notes for Family Bike Ride', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a8089d8c-05a2-431a-8360-5d1940945e4a', 'Budget Review', '2026-02-14', '2026-02-14',
    1771118100000, 1771119900000, 0,
    'Client Office', 'Notes for Budget Review', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8c679ad7-51c3-4e10-9227-ddf0b6e45ec8', 'Family Reunion', '2026-02-14', '2026-02-14',
    1771056000000, NULL, 1,
    NULL, 'Notes for Family Reunion', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6e222c7b-34f9-40dc-9973-12d46602f3d2', 'Work From Home', '2026-02-14', '2026-02-14',
    1771056000000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '73e8efd9-b140-4f06-8688-ec1b5dc9c357', 'Town Hall', '2026-02-15', '2026-02-15',
    1771171200000, 1771185600000, 0,
    'Coffee Shop', 'Notes for Town Hall', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6e156f70-b6cd-49a9-864e-08fa1a240ed8', 'Mentorship Session', '2026-02-15', '2026-02-15',
    1771175700000, 1771182900000, 0,
    'Conference Room B', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a643670f-668c-4770-a4a7-fe44a52ff284', 'Mentee Check-in Call', '2026-02-15', '2026-02-15',
    1771184700000, 1771199100000, 0,
    'Google Meet', 'Notes for Mentee Check-in Call', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0898a09b-b794-4ea0-bb04-ea726f1e6d48', 'OKR Review', '2026-02-15', '2026-02-15',
    1771172100000, 1771173900000, 0,
    '123 Main Street', 'Notes for OKR Review', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6bebf20a-fed2-4360-8980-341ae7f9c052', 'Interview - Backend Dev', '2026-02-15', '2026-02-15',
    1771182000000, 1771183800000, 0,
    'Client Office', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c97e48ee-ef25-415b-ad4d-00f75c758e91', 'Code Review Session', '2026-02-15', '2026-02-15',
    1771176600000, 1771178400000, 0,
    'Zoom', 'Notes for Code Review Session', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c1b955b0-156c-4f8b-a7b4-5a0f4b7c3aff', 'Code Review Session', '2026-02-15', '2026-02-15',
    1771200900000, 1771208100000, 0,
    'Conference Room B', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2fe041de-798e-4d73-9758-efd7a18dc392', '1:1 with Taylor', '2026-02-15', '2026-02-15',
    1771182000000, 1771196400000, 0,
    'Downtown Office', 'Notes for 1:1 with Taylor', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a720061f-8916-45f4-b5af-3bfb2af7b06b', 'Design Review', '2026-02-15', '2026-02-15',
    1771179300000, 1771182900000, 0,
    'Client Office', 'Notes for Design Review', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '16104f9e-3d23-4f1a-8668-c15393708b0a', '1:1 with Alex', '2026-02-15', '2026-02-15',
    1771176600000, 1771178400000, 0,
    'Zoom', 'Notes for 1:1 with Alex', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '52535e53-8384-4463-8179-50f061623820', 'Demo Day', '2026-02-15', '2026-02-15',
    1771142400000, NULL, 1,
    NULL, 'Notes for Demo Day', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c427f26c-2b9f-458c-ae8e-cbcaf5ec39b4', 'Vacation Day', '2026-02-15', '2026-02-15',
    1771142400000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9e2308e1-f79e-4394-aad3-2d97bacc5890', 'Retrospective', '2026-02-16', '2026-02-16',
    1771289100000, 1771303500000, 0,
    'Phone Call', 'Notes for Retrospective', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e7e73abc-10f4-48b2-b924-f4a9c7a438da', 'Lunch with Friends', '2026-02-16', '2026-02-16',
    1771262100000, 1771269300000, 0,
    'Zoom', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1b9f6502-a6e2-4f2c-b94a-9fcff2b3b1a9', 'Family Trip to Beach', '2026-02-16', '2026-02-16',
    1771228800000, NULL, 1,
    NULL, 'Notes for Family Trip to Beach', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '65c915d8-af2c-4efa-bbe3-ac5de253e015', 'Morning Journaling', '2026-02-16', '2026-02-16',
    1771288200000, 1771290000000, 0,
    'Client Office', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3acfdf82-2749-4c2e-929a-813bd995cfc6', 'UX Review', '2026-02-16', '2026-02-16',
    1771272000000, 1771286400000, 0,
    'Microsoft Teams', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '090f056e-e409-4059-861c-960b5403bf06', '1:1 with Alex', '2026-02-16', '2026-02-16',
    1771278300000, 1771285500000, 0,
    'Phone Call', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '325584ad-df25-4609-a879-c7cd3c96814f', 'Jake''s Basketball Practice', '2026-02-16', '2026-02-16',
    1771267500000, 1771274700000, 0,
    'Building 2, Floor 3', 'Notes for Jake''s Basketball Practice', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '10483768-0708-4777-b256-f1ca2dbfdc9c', 'Team Standup', '2026-02-16', '2026-02-16',
    1771278300000, 1771281900000, 0,
    NULL, 'Notes for Team Standup', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e6a9f52f-cb3a-41f4-b5a7-78ee8a3887e0', 'Mental Health Day', '2026-02-16', '2026-02-16',
    1771228800000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '67fdc4e4-f969-4e6d-9097-6e2ee19f3c5c', 'Birthday - Sarah', '2026-02-16', '2026-02-16',
    1771228800000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '18a0a5ef-dca7-4580-92ba-f8ae2015252a', 'Weekly Sync', '2026-02-16', '2026-02-16',
    1771262100000, 1771269300000, 0,
    'Downtown Office', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '98013ad9-1bfd-4efd-9863-f92616935b39', 'School Holiday - Presidents Day', '2026-02-16', '2026-02-16',
    1771228800000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2a9fda31-2b43-4c87-bdcf-f399280d217e', 'Game Night at Marcus''s', '2026-02-17', '2026-02-17',
    1771372800000, 1771374600000, 0,
    'Google Meet', 'Notes for Game Night at Marcus''s', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '37097426-56f4-4c16-b000-6e7aef46b2c3', 'Weekly Sync', '2026-02-17', '2026-02-17',
    1771378200000, 1771392600000, 0,
    'Building 2, Floor 3', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3b839eba-f222-4df0-90df-980ee5422e80', 'Sales Demo', '2026-02-17', '2026-02-17',
    1771352100000, 1771353900000, 0,
    'Downtown Office', 'Notes for Sales Demo', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '581d7994-85fe-494a-ba15-2e092ea0db34', 'Peer Feedback Session', '2026-02-18', '2026-02-18',
    1771465500000, 1771467300000, 0,
    'Phone Call', 'Notes for Peer Feedback Session', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a39aaad0-f78f-4ca9-8794-695bdd244296', 'Work From Home', '2026-02-18', '2026-02-18',
    1771401600000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '94152e9b-8bab-4a2f-811e-0089b4c6e1e0', 'Code2Community Mentoring', '2026-02-18', '2026-02-18',
    1771462800000, 1771464600000, 0,
    'Google Meet', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd19db5b8-dbb0-42c9-ac00-be9f26dbf2db', 'Training Workshop', '2026-02-18', '2026-02-18',
    1771401600000, NULL, 1,
    NULL, 'Notes for Training Workshop', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '45f0deee-cfaa-4be3-8cbf-29a7145739ed', 'Vacation Day', '2026-02-18', '2026-02-18',
    1771401600000, NULL, 1,
    NULL, 'Notes for Vacation Day', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd0462817-9773-47b8-8b9e-4d91e40aaffe', '1:1 with Mike', '2026-02-18', '2026-02-18',
    1771459200000, 1771461000000, 0,
    'Google Meet', 'Notes for 1:1 with Mike', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3312b631-572b-4ef4-be5d-e345de118492', 'Performance Review', '2026-02-18', '2026-02-18',
    1771442100000, 1771449300000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a0a65999-55b6-4ea6-90c5-0d0b7951f6a7', 'Birthday - Mom', '2026-02-18', '2026-02-18',
    1771401600000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '25766368-f217-4d50-b7fd-592c3490b84f', 'Company Holiday', '2026-02-18', '2026-02-18',
    1771401600000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b4644b1e-85f3-44ae-9d22-51d3334ab81a', 'Strategy Session', '2026-02-18', '2026-02-18',
    1771437600000, 1771452000000, 0,
    'Main Office', 'Notes for Strategy Session', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964961, 1773109964961,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '455d67fc-8b67-45a8-8d55-a8b3984aef26', 'Vendor Discussion', '2026-02-19', '2026-02-19',
    1771517700000, 1771524900000, 0,
    'Downtown Office', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '53b9f170-b353-4fe0-9b97-e3ad6be96971', '1:1 with Jordan', '2026-02-19', '2026-02-19',
    1771524000000, 1771525800000, 0,
    'Conference Room A', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '07de32b3-3942-4cb4-a302-be640a9b20a5', 'Conference Day', '2026-02-19', '2026-02-19',
    1771488000000, NULL, 1,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ea985bec-bef1-4d2d-ae31-7996019f6eb2', 'Career Advice Session', '2026-02-19', '2026-02-19',
    1771547400000, 1771554600000, 0,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a93b4c56-01eb-4f66-8d7e-d2d6361bc6ee', 'Code2Community Mentoring', '2026-02-19', '2026-02-19',
    1771534800000, 1771538400000, 0,
    'Downtown Office', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3074b0b1-54a9-4b13-a920-77991426ce21', 'Work From Home', '2026-02-19', '2026-02-19',
    1771488000000, NULL, 1,
    NULL, 'Notes for Work From Home', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '24a45b81-c0e1-4dd0-a346-bfd32e152b99', 'Sales Demo', '2026-02-19', '2026-02-19',
    1771518600000, 1771525800000, 0,
    'Conference Room B', 'Notes for Sales Demo', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '569150b2-04fd-4375-96d3-5f812c961f76', 'Architecture Review', '2026-02-19', '2026-02-19',
    1771527600000, 1771531200000, 0,
    'Conference Room A', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f13717f4-1acc-4311-9a4e-2deeb67eaa69', 'Morning Journaling', '2026-02-20', '2026-02-20',
    1771609500000, 1771611300000, 0,
    NULL, 'Notes for Morning Journaling', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f0d2259b-87b3-4f92-8256-cfd0ccbd41d6', 'Mentee Check-in Call', '2026-02-20', '2026-02-20',
    1771628400000, 1771630200000, 0,
    'Google Meet', 'Notes for Mentee Check-in Call', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '203ef5f2-c122-406d-981c-0232ce42f5a9', 'Knowledge Share', '2026-02-20', '2026-02-20',
    1771612200000, 1771619400000, 0,
    'Zoom', 'Notes for Knowledge Share', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1b7bcb75-17ad-42cc-b691-27545e0444c0', 'Code Review Session', '2026-02-20', '2026-02-20',
    1771608600000, 1771615800000, 0,
    'Phone Call', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd122d26a-254e-4300-9a4d-11c7814f12e0', 'Birthday - Emma', '2026-02-20', '2026-02-20',
    1771574400000, NULL, 1,
    NULL, 'Notes for Birthday - Emma', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4981e8f3-45df-405e-9e5a-d81a14627be3', 'Gym Session', '2026-02-20', '2026-02-20',
    1771627500000, 1771634700000, 0,
    'Coffee Shop', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '435b71a4-c10f-49f6-9bd3-849f6e5b859f', 'Morning Journaling', '2026-02-20', '2026-02-20',
    1771636500000, 1771650900000, 0,
    'Conference Room A', 'Notes for Morning Journaling', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'de90d75b-1a58-4757-9a95-65cb8285acdd', 'Meditation Session', '2026-02-20', '2026-02-20',
    1771627500000, 1771641900000, 0,
    NULL, 'Notes for Meditation Session', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a7a292e9-69c3-4c14-99b1-d623be251a00', 'Code Review Session', '2026-02-20', '2026-02-20',
    1771621200000, 1771624800000, 0,
    'Building 2, Floor 3', 'Notes for Code Review Session', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9426563f-e692-41bf-b87e-c75d2624cb77', 'Meditation Session', '2026-02-20', '2026-02-20',
    1771609500000, 1771611300000, 0,
    'Downtown Office', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7437f647-724b-4631-85e0-f662e47d2cb0', 'Interview - Designer', '2026-02-20', '2026-02-20',
    1771634700000, 1771636500000, 0,
    'Building 2, Floor 3', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a68ee9c2-51ce-4d58-ac4a-d3fa503f282a', 'Documentation Review', '2026-02-21', '2026-02-21',
    1771722900000, 1771737300000, 0,
    NULL, 'Notes for Documentation Review', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b1eb924c-c58a-4d3e-b410-594403beafe4', 'Code Review Session', '2026-02-21', '2026-02-21',
    1771697700000, 1771704900000, 0,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f1e7444a-3a04-4c63-b48d-fe9338716b19', 'Sprint Planning', '2026-02-21', '2026-02-21',
    1771701300000, 1771703100000, 0,
    'Downtown Office', 'Notes for Sprint Planning', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '75e5ce3a-fc8b-4f11-95f5-18f44005df7f', 'Anniversary', '2026-02-21', '2026-02-21',
    1771660800000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5ac5b4c6-d727-4ae4-bdf8-395314e443f5', 'Meditation Session', '2026-02-21', '2026-02-21',
    1771706700000, 1771708500000, 0,
    'Downtown Office', 'Notes for Meditation Session', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '243e68a9-7c19-49ce-82c8-f71b629dd070', 'Tech Volunteer Orientation', '2026-02-21', '2026-02-21',
    1771702200000, 1771716600000, 0,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c4522ff2-b14d-4ee9-bc66-e1dbe9a9dee3', 'Design Review', '2026-02-21', '2026-02-21',
    1771692300000, 1771706700000, 0,
    'Conference Room A', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1db4bf00-232e-4e18-bbfe-8ab825d97380', '1:1 with Taylor', '2026-02-21', '2026-02-21',
    1771714800000, 1771722000000, 0,
    NULL, 'Notes for 1:1 with Taylor', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ceed34fa-7cd5-4855-9032-d61583aee1a4', 'Visit Grandparents', '2026-02-22', '2026-02-22',
    1771747200000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '20cd743c-3fd9-4368-bda2-3008859920b2', 'Company Holiday', '2026-02-22', '2026-02-22',
    1771747200000, NULL, 1,
    NULL, 'Notes for Company Holiday', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a0b74fe7-1927-413a-9bec-2e3ea3e7d112', 'Security Audit', '2026-02-22', '2026-02-22',
    1771777800000, 1771781400000, 0,
    'Zoom', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0f4c4112-b6ec-4a29-b982-d1f8b141b744', 'Team Offsite', '2026-02-22', '2026-02-22',
    1771747200000, NULL, 1,
    NULL, 'Notes for Team Offsite', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd23cdcca-dc96-4402-baf9-2e84daf9cbaa', 'Community Coding Workshop', '2026-02-22', '2026-02-22',
    1771776900000, 1771791300000, 0,
    'Zoom', 'Notes for Community Coding Workshop', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '479559e4-b9eb-4487-b7bf-a1239929d0c7', 'Town Hall', '2026-02-22', '2026-02-22',
    1771776900000, 1771780500000, 0,
    'Client Office', 'Notes for Town Hall', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '38a6c8cf-0f7a-487e-b09c-16e50ec30be3', 'Design Review', '2026-02-22', '2026-02-22',
    1771798500000, 1771802100000, 0,
    'Phone Call', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '21693880-83fa-4b37-b868-62fccbe02045', 'Morning Journaling', '2026-02-22', '2026-02-22',
    1771802100000, 1771805700000, 0,
    '123 Main Street', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4cc9bb94-bffc-4bca-b535-578609f80973', 'Client Call - TechStart', '2026-02-23', '2026-02-23',
    1771892100000, 1771899300000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cad57776-aa2f-4f42-8256-7cc4fcf91b88', 'Documentation Review', '2026-02-23', '2026-02-23',
    1771876800000, 1771880400000, 0,
    'Microsoft Teams', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6c2b069e-21ba-4f7a-9c19-b611f09c129e', 'Code2Community Mentoring', '2026-02-23', '2026-02-23',
    1771878600000, 1771893000000, 0,
    NULL, 'Notes for Code2Community Mentoring', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cf0d9960-6e2c-4bd0-8fd8-4a54e77bb224', 'Help Emma with Science Fair', '2026-02-23', '2026-02-23',
    1771882200000, 1771889400000, 0,
    'Main Office', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a0409ea5-24a1-4f3d-a5a8-c354887c5cd0', 'Dinner with Family', '2026-02-23', '2026-02-23',
    1771862400000, 1771876800000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a5b9f85c-ff7d-40ee-adfa-4358e84c8293', 'Product Launch', '2026-02-23', '2026-02-23',
    1771833600000, NULL, 1,
    NULL, 'Notes for Product Launch', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9dc451ff-a396-469c-a3b1-a2babbb975b5', 'Career Discussion', '2026-02-24', '2026-02-24',
    1771958700000, 1771962300000, 0,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '62700fae-7a82-4e50-b493-96803c07356a', '1:1 with Mike', '2026-02-24', '2026-02-24',
    1771949700000, 1771964100000, 0,
    'Phone Call', 'Notes for 1:1 with Mike', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3075f55f-4a99-43dd-9756-f3feae2b16d2', 'Interview - PM', '2026-02-24', '2026-02-24',
    1771962300000, 1771964100000, 0,
    'Zoom', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ee71a84d-17ff-4812-a4c1-f47ca7ca1ff4', 'Life Purpose Reflection', '2026-02-24', '2026-02-24',
    1771964100000, 1771978500000, 0,
    'Client Office', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1d0fa276-8bd8-4327-9470-8126212c8ce5', 'Code2Community Mentoring', '2026-02-24', '2026-02-24',
    1771973100000, 1771980300000, 0,
    '123 Main Street', 'Notes for Code2Community Mentoring', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '37646057-1da6-4f84-a13e-83cd32696e34', 'Mentorship Session', '2026-02-24', '2026-02-24',
    1771953300000, 1771967700000, 0,
    '123 Main Street', 'Notes for Mentorship Session', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964962, 1773109964962,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cc74bcb7-aabf-4266-a813-9afb8ca6ed1f', 'Gym Session', '2026-02-24', '2026-02-24',
    1771982100000, 1771985700000, 0,
    'Coffee Shop', 'Notes for Gym Session', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9b9364d9-730e-4773-8050-bac8f038114c', 'Camping Weekend', '2026-02-24', '2026-02-24',
    1771920000000, NULL, 1,
    NULL, 'Notes for Camping Weekend', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e654dd1d-f742-4eac-8291-737288ad95a9', 'Meditation Session', '2026-02-25', '2026-02-25',
    1772037000000, 1772044200000, 0,
    'Conference Room A', 'Notes for Meditation Session', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '82c61a33-9b39-4f4d-bb03-4b2fde18dc06', 'Code2Community Mentoring', '2026-02-25', '2026-02-25',
    1772044200000, 1772047800000, 0,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ba7d7276-ab40-4f01-8a96-a5694744a738', 'Sales Demo', '2026-02-25', '2026-02-25',
    1772069400000, 1772073000000, 0,
    'Phone Call', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9c67a484-1dc5-4026-bea5-2135e59d9072', 'Conference Day', '2026-02-25', '2026-02-25',
    1772006400000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a13f406f-ff2f-4f1b-8803-2485acf4ebb3', 'Demo Day', '2026-02-25', '2026-02-25',
    1772006400000, NULL, 1,
    NULL, 'Notes for Demo Day', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c4185f5d-9e13-471d-8ee2-1108cbc3a025', 'Morning Journaling', '2026-02-25', '2026-02-25',
    1772070300000, 1772077500000, 0,
    'Phone Call', 'Notes for Morning Journaling', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '32ced74b-f69d-48f9-847e-8c85090e51b9', 'Team Standup', '2026-02-25', '2026-02-25',
    1772037900000, 1772039700000, 0,
    'Building 2, Floor 3', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '18e3e01e-e82f-4f63-b675-bfa8dbe87644', 'Birthday - Jake', '2026-02-25', '2026-02-25',
    1772006400000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '568ff122-60dc-49ac-a36d-5f7c901f6264', '1:1 with Mike', '2026-02-25', '2026-02-25',
    1772051400000, 1772058600000, 0,
    'Building 2, Floor 3', 'Notes for 1:1 with Mike', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b355527c-b9dc-4fa8-a541-716bbf5d33b8', 'API Review', '2026-02-25', '2026-02-25',
    1772048700000, 1772052300000, 0,
    'Microsoft Teams', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '34d76334-5102-4aef-9407-8ae39438e278', 'Accessibility Review', '2026-02-25', '2026-02-25',
    1772046000000, 1772049600000, 0,
    'Microsoft Teams', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '75c19f0e-538a-4ff3-83d9-df03ef7937bb', '1:1 with Mike', '2026-02-25', '2026-02-25',
    1772044200000, 1772046000000, 0,
    'Phone Call', 'Notes for 1:1 with Mike', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '77ece210-0870-46a4-a047-7d630ac6ce65', 'Project Review', '2026-02-26', '2026-02-26',
    1772153100000, 1772167500000, 0,
    'Main Office', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '79587a08-aa01-4144-89d0-9f3b7c76ac84', 'Weekly Sync', '2026-02-26', '2026-02-26',
    1772136000000, 1772150400000, 0,
    '123 Main Street', 'Notes for Weekly Sync', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '37b979f6-3fed-46bb-aa03-110ed32ee313', 'Morning Journaling', '2026-02-26', '2026-02-26',
    1772127000000, 1772141400000, 0,
    'Client Office', 'Notes for Morning Journaling', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3e1d4565-1191-4c46-9ae8-6c3966dc4649', 'Performance Review', '2026-02-26', '2026-02-26',
    1772147700000, 1772151300000, 0,
    'Microsoft Teams', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd0166c05-f68a-4745-b065-cbc95056ae33', 'Emma''s Soccer Game', '2026-02-26', '2026-02-26',
    1772145000000, 1772148600000, 0,
    'Conference Room A', 'Notes for Emma''s Soccer Game', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '30b039d7-8f78-4d4a-ad12-ad036f29248a', '1:1 with Manager', '2026-02-26', '2026-02-26',
    1772136000000, 1772137800000, 0,
    'Coffee Shop', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '543729df-1f73-4ac9-a174-669de210458f', '1:1 with Manager', '2026-02-26', '2026-02-26',
    1772125200000, 1772128800000, 0,
    'Coffee Shop', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e725e36e-8944-4ece-835e-fc06c91286d6', 'Partner Call', '2026-02-26', '2026-02-26',
    1772147700000, 1772154900000, 0,
    'Building 2, Floor 3', 'Notes for Partner Call', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'eb083199-0965-42e3-b8e8-c7d65d3d7400', 'Vacation Day', '2026-02-26', '2026-02-26',
    1772092800000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cd93a74a-af02-4454-b6c8-e5cc6c9e2c00', 'Volunteer Day', '2026-02-27', '2026-02-27',
    1772179200000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b7bccdd9-a028-494d-831b-e4c88affbad9', 'Meditation Session', '2026-02-27', '2026-02-27',
    1772221500000, 1772235900000, 0,
    'Main Office', 'Notes for Meditation Session', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5c27be46-bb10-4176-a400-1f6839a25aee', 'Design Review', '2026-02-27', '2026-02-27',
    1772235000000, 1772249400000, 0,
    'Zoom', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f635e9b0-c7eb-4bcd-a2b7-e534e6aada6d', 'Emma''s Soccer Game', '2026-02-28', '2026-02-28',
    1772321400000, 1772335800000, 0,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3e1a23fa-4aaa-4075-a56c-db97475f3d3d', 'Code Review Session', '2026-02-28', '2026-02-28',
    1772302500000, 1772316900000, 0,
    'Client Office', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8463cbf8-166d-48e0-9691-a5a4c31f53bb', 'Retrospective', '2026-02-28', '2026-02-28',
    1772307900000, 1772315100000, 0,
    'Conference Room B', 'Notes for Retrospective', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cbb5d6ba-cc5c-4a32-97d1-ed3e9c95a3b7', 'Knowledge Share', '2026-02-28', '2026-02-28',
    1772315100000, 1772316900000, 0,
    'Client Office', 'Notes for Knowledge Share', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b4d8fbd2-c6a7-4986-bfdd-b652b728e5bc', '1:1 with Manager', '2026-02-28', '2026-02-28',
    1772320500000, 1772334900000, 0,
    NULL, 'Notes for 1:1 with Manager', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '371a79ad-bc3e-414e-8392-5681dea257fc', 'Code Review Session', '2026-02-28', '2026-02-28',
    1772315100000, 1772316900000, 0,
    'Conference Room A', 'Notes for Code Review Session', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f759c0dc-ac58-498a-b574-3f400ec4c7b2', 'Mentee Check-in Call', '2026-02-28', '2026-02-28',
    1772316900000, 1772320500000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7698cece-3f73-4f78-a3fe-3fe799b7fb19', 'Spring Break - Kids Off', '2026-02-28', '2026-02-28',
    1772265600000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fa5846c7-3059-4e0d-a698-cc42dc8330b1', 'Mental Health Day', '2026-02-28', '2026-02-28',
    1772265600000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9b8b9430-9d66-4dac-8ff4-055d90d2be2b', 'Gym Session', '2026-02-28', '2026-02-28',
    1772307900000, 1772315100000, 0,
    'Conference Room B', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a88a05a0-7d33-4a9c-ad7c-de5591ce4c7e', 'Grocery Run', '2026-02-28', '2026-02-28',
    1772314200000, 1772328600000, 0,
    'Conference Room A', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e22d785b-40ca-4f66-9872-51ec9bd34aaf', 'Project Review', '2026-03-01', '2026-03-01',
    1772398800000, 1772413200000, 0,
    'Conference Room A', 'Notes for Project Review', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '50b627fe-5c7d-40a4-97a0-31b4dcb0eb5f', 'Code2Community Mentoring', '2026-03-01', '2026-03-01',
    1772388900000, 1772396100000, 0,
    'Google Meet', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5e5c52fa-c556-4a45-a069-62d330d01e12', 'Interview - Backend Dev', '2026-03-01', '2026-03-01',
    1772406000000, 1772413200000, 0,
    'Google Meet', 'Notes for Interview - Backend Dev', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c3803d41-4e35-46f1-801b-c2da1f7e853f', 'School Holiday - MLK Day', '2026-03-02', '2026-03-02',
    1772438400000, NULL, 1,
    NULL, 'Notes for School Holiday - MLK Day', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fd384737-a49f-43f8-9975-a15defb06e76', 'Morning Journaling', '2026-03-02', '2026-03-02',
    1772471700000, 1772478900000, 0,
    'Downtown Office', 'Notes for Morning Journaling', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8cbd1574-82c9-459a-9cb0-7d8e1762d755', 'All-Hands Meeting', '2026-03-02', '2026-03-02',
    1772482500000, 1772489700000, 0,
    'Conference Room A', 'Notes for All-Hands Meeting', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '47ec7526-8bc8-426b-9d44-9e51c031aa14', 'Work From Home', '2026-03-02', '2026-03-02',
    1772438400000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '599a1fb3-e736-44de-84e5-90c1d11f63d2', 'Vacation Day', '2026-03-02', '2026-03-02',
    1772438400000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '210edcfe-24f9-460e-8079-a330c037fb53', 'Birthday - Mike', '2026-03-02', '2026-03-02',
    1772438400000, NULL, 1,
    NULL, 'Notes for Birthday - Mike', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c4e631a7-826c-4f6b-8bb6-571487158012', 'Work From Home', '2026-03-02', '2026-03-02',
    1772438400000, NULL, 1,
    NULL, 'Notes for Work From Home', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'eb80ea29-49cf-4e32-bf3f-fe00b2cdf242', 'Project Review', '2026-03-02', '2026-03-02',
    1772478900000, 1772486100000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bd98edd9-c170-4c6f-9377-ab4013cf8b5f', 'Architecture Review', '2026-03-02', '2026-03-02',
    1772487000000, 1772490600000, 0,
    'Coffee Shop', 'Notes for Architecture Review', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964963, 1773109964963,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'afadf16c-4873-4e1f-94f2-b127ee44ff72', '1:1 with Jordan', '2026-03-02', '2026-03-02',
    1772476200000, 1772483400000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9319b4ec-6d92-4895-ad6c-6431def03aa7', 'Jake''s Basketball Practice', '2026-03-02', '2026-03-02',
    1772487900000, 1772495100000, 0,
    'Conference Room A', 'Notes for Jake''s Basketball Practice', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7ccc9ecc-8ef1-401c-ab30-68fe2ad5acaa', 'Mentee Check-in Call', '2026-03-02', '2026-03-02',
    1772468100000, 1772482500000, 0,
    'Zoom', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6fc1ec64-3b82-499f-9b1a-a9518f7a2b26', 'Work From Home', '2026-03-03', '2026-03-03',
    1772524800000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f7034a2b-c2b8-43bf-ace7-ba73fb3ede66', '1:1 with Sarah', '2026-03-03', '2026-03-03',
    1772524800000, NULL, 1,
    NULL, 'Notes for 1:1 with Sarah', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9a9dee10-256d-42b1-af0a-9f6427935b84', '1:1 with Jordan', '2026-03-03', '2026-03-03',
    1772524800000, NULL, 1,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4e2b40b8-280e-42b5-91b8-ecac6def2582', '1:1 with Jordan', '2026-03-03', '2026-03-03',
    1772577000000, 1772584200000, 0,
    'Coffee Shop', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '16c959da-d812-4158-84e7-5c39df5399a9', 'Emma''s Soccer Game', '2026-03-03', '2026-03-03',
    1772587800000, 1772595000000, 0,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e66df198-edad-4064-8664-9271091ea868', 'Movie Night', '2026-03-04', '2026-03-04',
    1772658000000, 1772659800000, 0,
    'Zoom', 'Notes for Movie Night', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '524da07c-a4f3-4401-9c07-76a7804dac06', 'Client Call - Acme Corp', '2026-03-04', '2026-03-04',
    1772667000000, 1772681400000, 0,
    '123 Main Street', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3ea7bf15-5671-40f7-9267-b795035abbc8', '1:1 with Taylor', '2026-03-04', '2026-03-04',
    1772611200000, NULL, 1,
    NULL, 'Notes for 1:1 with Taylor', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f69c0906-705e-4faa-a2a3-142e53b396fa', 'Morning Journaling', '2026-03-04', '2026-03-04',
    1772643600000, 1772647200000, 0,
    NULL, 'Notes for Morning Journaling', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8194284b-0052-4706-aef3-1223b501572f', 'Code Review Session', '2026-03-04', '2026-03-04',
    1772661600000, 1772665200000, 0,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ce7c50a0-8785-4ec0-bcba-3c6116507ace', 'Morning Journaling', '2026-03-04', '2026-03-04',
    1772640900000, 1772642700000, 0,
    'Main Office', 'Notes for Morning Journaling', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9a039199-7331-418a-8d2e-779c729929c9', 'Book Club Meeting', '2026-03-04', '2026-03-04',
    1772668800000, 1772670600000, 0,
    'Building 2, Floor 3', 'Notes for Book Club Meeting', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6a8f58ca-33ef-4a1a-8b84-510299ba1cbd', 'Physical Therapy', '2026-03-04', '2026-03-04',
    1772611200000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0c0a4015-72e5-47c7-aed9-b5c893ed8587', '1:1 with Sarah', '2026-03-04', '2026-03-04',
    1772671500000, 1772673300000, 0,
    'Google Meet', 'Notes for 1:1 with Sarah', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '801cb11d-6fbd-4ddf-825d-c5e2dbbd94d2', 'Client Call - TechStart', '2026-03-05', '2026-03-05',
    1772746200000, 1772760600000, 0,
    NULL, 'Notes for Client Call - TechStart', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '055016bf-b1d7-410d-9dfa-ca130402b1f5', 'Book Club Meeting', '2026-03-05', '2026-03-05',
    1772697600000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2d7fca39-e250-49f8-935e-f706b8c289d2', 'Meditation Session', '2026-03-05', '2026-03-05',
    1772726400000, 1772733600000, 0,
    'Coffee Shop', 'Notes for Meditation Session', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fb8f08bf-b8f6-4d0a-8dad-e2c80d4dbddf', 'Life Purpose Reflection', '2026-03-05', '2026-03-05',
    1772749800000, 1772757000000, 0,
    'Client Office', 'Notes for Life Purpose Reflection', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0e41e435-781c-4a18-8fcc-1e7a48102094', 'Career Advice Session', '2026-03-05', '2026-03-05',
    1772739000000, 1772753400000, 0,
    'Google Meet', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bc0aee0b-4552-4c54-8c19-19da016d0dc2', 'Morning Journaling', '2026-03-05', '2026-03-05',
    1772760600000, 1772767800000, 0,
    NULL, 'Notes for Morning Journaling', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '63695827-1e44-4d85-877c-704104431c10', 'Client Call - Acme Corp', '2026-03-05', '2026-03-05',
    1772697600000, NULL, 1,
    NULL, 'Notes for Client Call - Acme Corp', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3331a825-c5e5-412b-b5bc-68ae40371ef7', 'UX Review', '2026-03-05', '2026-03-05',
    1772739900000, 1772743500000, 0,
    'Building 2, Floor 3', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'dc604f27-8f02-4f85-95a9-ba23a4d12ea1', 'Partner Call', '2026-03-06', '2026-03-06',
    1772844300000, 1772858700000, 0,
    'Zoom', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '638039b6-777d-499c-a9f5-8f41723cf6ef', 'Skip Level', '2026-03-06', '2026-03-06',
    1772823600000, 1772830800000, 0,
    'Downtown Office', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '412ae5ba-e4fd-474f-a933-39991b92bec1', 'Emma''s Soccer Game', '2026-03-06', '2026-03-06',
    1772830800000, 1772845200000, 0,
    'Main Office', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ec1d947c-c2e8-4562-af5f-25097eeb0f62', 'Morning Journaling', '2026-03-06', '2026-03-06',
    1772829000000, 1772843400000, 0,
    'Building 2, Floor 3', 'Notes for Morning Journaling', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '24b5358d-2de3-4b1c-aa6e-98fd2a7c711b', 'School Play - West Side Story', '2026-03-06', '2026-03-06',
    1772846100000, 1772849700000, 0,
    'Google Meet', 'Notes for School Play - West Side Story', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0038ae1b-fe08-4f81-b6ce-7d8b12381a19', 'Weekly Sync', '2026-03-06', '2026-03-06',
    1772819100000, 1772833500000, 0,
    'Coffee Shop', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2f2e7522-3b14-4e0a-a135-71f9b8fd268a', 'Emma''s Piano Recital', '2026-03-07', '2026-03-07',
    1772870400000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1125c14e-1f8a-4ac2-94c2-5c2340352763', 'Client Call - TechStart', '2026-03-07', '2026-03-07',
    1772910900000, 1772925300000, 0,
    'Main Office', 'Notes for Client Call - TechStart', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '94b842df-7b5e-47d5-832e-accd54120b1d', 'Book Club Meeting', '2026-03-07', '2026-03-07',
    1772917200000, 1772924400000, 0,
    'Main Office', 'Notes for Book Club Meeting', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964964, 1773109964964,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '545c656d-7c1d-41ed-99b7-afe6944dd006', 'Onboarding Call', '2026-03-07', '2026-03-07',
    1772919000000, 1772933400000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4235f7cf-575a-4f07-88cb-1feda53215a9', 'Meditation Session', '2026-03-07', '2026-03-07',
    1772906400000, 1772908200000, 0,
    'Conference Room B', 'Notes for Meditation Session', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '20d358ae-6dd7-439c-b429-f95951718728', 'Game Night at Marcus''s', '2026-03-07', '2026-03-07',
    1772870400000, NULL, 1,
    NULL, 'Notes for Game Night at Marcus''s', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '779c7b6d-6698-4fa2-a41f-106e87cdca30', 'Department Meeting', '2026-03-07', '2026-03-07',
    1772916300000, 1772918100000, 0,
    'Client Office', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5f5aa994-0e7c-4f79-9c0f-d2a6cd61c70d', 'Morning Journaling', '2026-03-07', '2026-03-07',
    1772923500000, 1772927100000, 0,
    NULL, 'Notes for Morning Journaling', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '013f14ed-9d4b-4095-b2f3-05f1c4b0e652', 'Customer Success Check-in', '2026-03-07', '2026-03-07',
    1772921700000, 1772923500000, 0,
    'Zoom', 'Notes for Customer Success Check-in', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5df32c08-5829-4a70-9626-c7ef4b7da07b', 'Sales Demo', '2026-03-07', '2026-03-07',
    1772870400000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '28393b54-ee95-4856-9049-7ce1ee902702', 'Client Call - Acme Corp', '2026-03-08', '2026-03-08',
    1772956800000, NULL, 1,
    NULL, 'Notes for Client Call - Acme Corp', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ef6f9174-e953-4157-942f-3e6c2396020e', 'Meditation Session', '2026-03-08', '2026-03-08',
    1773013500000, 1773020700000, 0,
    'Phone Call', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ab65c1b2-a818-4980-97b4-68399cbb1fc2', 'Team Standup', '2026-03-08', '2026-03-08',
    1773011700000, 1773013500000, 0,
    'Microsoft Teams', 'Notes for Team Standup', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '64759d5f-6d1e-41a6-a2e3-2b118dccd112', '1:1 with Manager', '2026-03-09', '2026-03-09',
    1773102600000, 1773117000000, 0,
    'Downtown Office', 'Notes for 1:1 with Manager', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ef3be740-8371-47fc-b239-93b283ad9405', 'Customer Success Check-in', '2026-03-09', '2026-03-09',
    1773079200000, 1773086400000, 0,
    'Google Meet', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '48032726-a3ea-47dc-86e5-d7f7a5c8f9a8', 'Gym Session', '2026-03-09', '2026-03-09',
    1773039600000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '428388d2-9aaa-48dd-a1c1-f3b9403bda17', 'Dinner with Family', '2026-03-09', '2026-03-09',
    1773081900000, 1773096300000, 0,
    'Coffee Shop', 'Notes for Dinner with Family', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e74fd620-901a-4572-905b-89d8c1206f87', 'Mentorship Session', '2026-03-09', '2026-03-09',
    1773098100000, 1773105300000, 0,
    'Zoom', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6d8dd791-7485-4dfd-8ce9-bac5891f585f', 'Team Lunch', '2026-03-09', '2026-03-09',
    1773081000000, 1773084600000, 0,
    'Downtown Office', 'Notes for Team Lunch', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f708f9d9-7147-4170-82d6-1431b1382f3b', 'Gym Session', '2026-03-09', '2026-03-09',
    1773101700000, 1773103500000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '875d21b4-096f-4b32-968f-d5e79b0a42f0', 'Weekly Sync', '2026-03-09', '2026-03-09',
    1773076500000, 1773078300000, 0,
    '123 Main Street', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2fd0319e-b3ad-4616-a48c-b39bcb7875ca', 'Neighborhood BBQ', '2026-03-09', '2026-03-09',
    1773039600000, NULL, 1,
    NULL, 'Notes for Neighborhood BBQ', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8f3f8428-6510-4afe-9ee1-8eda109bdebf', 'Client Call - DataFlow', '2026-03-09', '2026-03-09',
    1773093600000, 1773097200000, 0,
    NULL, 'Notes for Client Call - DataFlow', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ce4bec58-4925-40a0-b95b-770c9061d8c1', 'Client Call - DataFlow', '2026-03-09', '2026-03-09',
    1773085500000, 1773092700000, 0,
    'Microsoft Teams', 'Notes for Client Call - DataFlow', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd77258cc-9ffe-4875-a141-4db7a071f187', 'Jake''s Basketball Practice', '2026-03-09', '2026-03-09',
    1773089100000, 1773096300000, 0,
    'Conference Room B', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9eeb40ab-dd25-4271-972a-8318c77ed587', 'Team Lunch', '2026-03-10', '2026-03-10',
    1773184500000, 1773198900000, 0,
    'Microsoft Teams', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '254fc36e-a780-40f4-a5ec-5a04294c7cd0', 'Vendor Discussion', '2026-03-10', '2026-03-10',
    1773171900000, 1773173700000, 0,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1e832694-d8e2-4fc4-a614-ed89f624d2b3', 'Food Bank Volunteering', '2026-03-10', '2026-03-10',
    1773171000000, 1773178200000, 0,
    'Coffee Shop', 'Notes for Food Bank Volunteering', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c718f566-a1f2-4a80-a890-8252e9083f9f', 'Department Meeting', '2026-03-10', '2026-03-10',
    1773181800000, 1773189000000, 0,
    'Conference Room A', 'Notes for Department Meeting', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1544a235-b134-4421-bc72-7c8259761a5f', 'OKR Review', '2026-03-11', '2026-03-11',
    1773276300000, 1773278100000, 0,
    'Coffee Shop', 'Notes for OKR Review', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9695e1ae-59fe-4f0d-8f67-42368f8afa5c', 'Visit Dave at Hospital', '2026-03-11', '2026-03-11',
    1773264600000, 1773266400000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '00078e03-492e-4355-a04d-2398602ef740', 'Sprint Planning', '2026-03-11', '2026-03-11',
    1773212400000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '40ac83a5-6a53-463a-8e82-3768929c1d85', '1:1 with Taylor', '2026-03-11', '2026-03-11',
    1773264600000, 1773271800000, 0,
    'Zoom', 'Notes for 1:1 with Taylor', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '52776691-571c-4ef7-9fd6-8824506eedb3', 'Peer Feedback Session', '2026-03-11', '2026-03-11',
    1773249300000, 1773251100000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964965, 1773109964965,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f72d601c-0058-4155-a073-a2523c697608', 'Physical Therapy', '2026-03-12', '2026-03-12',
    1773333900000, 1773348300000, 0,
    'Downtown Office', 'Notes for Physical Therapy', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3adf8d9a-f562-4186-96d3-d9349b6ed800', '1:1 with Sarah', '2026-03-12', '2026-03-12',
    1773298800000, NULL, 1,
    NULL, 'Notes for 1:1 with Sarah', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9ae5cdfe-7a9e-4364-9d06-9fa0e98f3740', 'Parent-Teacher Conference', '2026-03-12', '2026-03-12',
    1773359100000, 1773362700000, 0,
    'Building 2, Floor 3', 'Notes for Parent-Teacher Conference', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7d814c05-f58f-46f2-977b-5505b363b1bc', 'Retrospective', '2026-03-12', '2026-03-12',
    1773354600000, 1773358200000, 0,
    'Client Office', 'Notes for Retrospective', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8d9b4869-b5f7-46da-8664-863cfaba7bc1', 'Gym Session', '2026-03-12', '2026-03-12',
    1773334800000, 1773349200000, 0,
    'Downtown Office', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6b32ab96-aca5-4272-845a-076cfabfb21b', 'Emma''s Soccer Game', '2026-03-12', '2026-03-12',
    1773343800000, 1773347400000, 0,
    'Zoom', 'Notes for Emma''s Soccer Game', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ecb1c480-3581-4b24-9b76-34ef6c855f34', 'Knowledge Share', '2026-03-13', '2026-03-13',
    1773444600000, 1773459000000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c4c1cdaa-67f9-45da-811c-0620bdd3d1b4', 'Jake''s Basketball Practice', '2026-03-13', '2026-03-13',
    1773438300000, 1773441900000, 0,
    'Zoom', 'Notes for Jake''s Basketball Practice', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ce286aea-44fd-4df1-a6f2-e6c8388ee7cd', 'Emma''s Soccer Game', '2026-03-13', '2026-03-13',
    1773439200000, 1773453600000, 0,
    'Microsoft Teams', 'Notes for Emma''s Soccer Game', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bc8d6c7e-8a29-4cd3-9742-337d55a961ce', 'Book Club Meeting', '2026-03-13', '2026-03-13',
    1773420300000, 1773422100000, 0,
    '123 Main Street', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'de7b1fbc-3fcf-4c27-8677-f86e1291a290', 'Team Lunch', '2026-03-13', '2026-03-13',
    1773385200000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '12b215fe-edc2-4259-94ef-dcb574250a72', 'Coffee Chat', '2026-03-13', '2026-03-13',
    1773385200000, NULL, 1,
    NULL, 'Notes for Coffee Chat', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '52148e81-0c81-4d80-ab81-215d0e781ca0', 'Coffee Chat', '2026-03-13', '2026-03-13',
    1773427500000, 1773429300000, 0,
    'Main Office', 'Notes for Coffee Chat', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '26dc4d46-3f30-4420-9a3c-83f379fdd2e9', 'Dinner with Family', '2026-03-13', '2026-03-13',
    1773432000000, 1773435600000, 0,
    'Zoom', 'Notes for Dinner with Family', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '90572e9a-c5d7-4e46-a325-1c43d166e338', 'Career Discussion', '2026-03-13', '2026-03-13',
    1773423000000, 1773437400000, 0,
    'Phone Call', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '05d21bef-857e-474f-b268-6c65758dcdaa', 'Weekly Sync', '2026-03-13', '2026-03-13',
    1773385200000, NULL, 1,
    NULL, 'Notes for Weekly Sync', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cfb067ae-207f-498e-a690-f72b265cd580', 'Grocery Run', '2026-03-14', '2026-03-14',
    1773510300000, 1773517500000, 0,
    'Coffee Shop', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd6c5117a-82a6-4147-893a-b52857efcd48', 'Game Night at Marcus''s', '2026-03-14', '2026-03-14',
    1773471600000, NULL, 1,
    NULL, 'Notes for Game Night at Marcus''s', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '15a72ae6-b55f-472a-b2ff-4ad27e776a68', 'Gym Session', '2026-03-14', '2026-03-14',
    1773471600000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '68e405d5-eac8-41e2-a8d7-d3121811b26f', 'Customer Success Check-in', '2026-03-14', '2026-03-14',
    1773513900000, 1773517500000, 0,
    'Microsoft Teams', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e1176b3b-62c8-4739-a089-c048f9bc6019', 'Grocery Run', '2026-03-14', '2026-03-14',
    1773506700000, 1773508500000, 0,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e93419fd-3f63-4a68-a4e3-d200d0829168', 'Knowledge Share', '2026-03-14', '2026-03-14',
    1773522900000, 1773526500000, 0,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '091aa20e-afbe-4353-978f-6adce8d9e87e', 'School Play - West Side Story', '2026-03-14', '2026-03-14',
    1773471600000, NULL, 1,
    NULL, 'Notes for School Play - West Side Story', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6042b696-0c96-4808-b574-c238d777a7f8', 'Lunch with Friends', '2026-03-14', '2026-03-14',
    1773471600000, NULL, 1,
    NULL, 'Notes for Lunch with Friends', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd2c9ff14-2f5e-48bb-99bf-bf23b043c90c', 'Weekly Sync', '2026-03-14', '2026-03-14',
    1773500400000, 1773504000000, 0,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8d28aa2f-6efe-4418-bb05-cfc36f391a1d', 'Jake''s Basketball Practice', '2026-03-14', '2026-03-14',
    1773522900000, 1773526500000, 0,
    '123 Main Street', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c5ec0503-4929-45dd-9efc-ef536e7a8803', 'Emma''s Soccer Game', '2026-03-14', '2026-03-14',
    1773519300000, 1773526500000, 0,
    'Zoom', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964966, 1773109964966,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4cbd09ba-0939-4b74-bf88-9283c144897d', 'Team Standup', '2026-03-15', '2026-03-15',
    1773558000000, NULL, 1,
    NULL, 'Notes for Team Standup', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'dfd0aa38-3c63-481f-a738-b05a19f3b735', 'Gym Session', '2026-03-15', '2026-03-15',
    1773558000000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '90994104-f450-4f5f-aed9-9141d15b1157', 'Physical Therapy', '2026-03-15', '2026-03-15',
    1773621000000, 1773635400000, 0,
    'Conference Room B', 'Notes for Physical Therapy', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5901af11-b8c7-4c89-9e34-7de0fb0230ed', 'Client Call - Acme Corp', '2026-03-16', '2026-03-16',
    1773701100000, 1773704700000, 0,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1e1d9299-6485-4211-8acd-525d54210b6b', 'Team Standup', '2026-03-16', '2026-03-16',
    1773690300000, 1773693900000, 0,
    'Building 2, Floor 3', 'Notes for Team Standup', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '60986606-dc69-4c95-8b78-5f4b1d7da10f', 'Physical Therapy', '2026-03-16', '2026-03-16',
    1773685800000, 1773700200000, 0,
    'Microsoft Teams', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a3272f3c-6239-4c6f-b75f-0cd3c707983a', 'Coffee Chat', '2026-03-16', '2026-03-16',
    1773644400000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ad1b0571-77a8-4561-bb65-6d15a700f244', 'Vet Appointment', '2026-03-17', '2026-03-17',
    1773775800000, 1773777600000, 0,
    'Phone Call', 'Notes for Vet Appointment', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e08ec3d4-2246-47ab-a79a-29722172a6d4', 'Weekly Sync', '2026-03-17', '2026-03-17',
    1773774000000, 1773788400000, 0,
    'Conference Room B', 'Notes for Weekly Sync', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4cb95dbc-8f27-4022-adae-fb41007debab', 'Weekly Sync', '2026-03-17', '2026-03-17',
    1773768600000, 1773783000000, 0,
    'Zoom', 'Notes for Weekly Sync', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '119ffbf6-7f2f-4811-84a7-7d96fd5d4c80', 'Jake''s Basketball Practice', '2026-03-17', '2026-03-17',
    1773792900000, 1773800100000, 0,
    'Downtown Office', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b0e04372-b13f-4ed8-b03c-f26d16d725b2', 'Eye Exam', '2026-03-17', '2026-03-17',
    1773774000000, 1773775800000, 0,
    'Phone Call', 'Notes for Eye Exam', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e5e1c6b2-a4ec-4d38-9499-06643c0a3d0a', 'Date Night', '2026-03-17', '2026-03-17',
    1773730800000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd4acabed-3794-4562-8eca-96fd8ebd40ab', 'Onboarding Call', '2026-03-17', '2026-03-17',
    1773787500000, 1773789300000, 0,
    'Conference Room B', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0245a1fd-91de-4c70-91bb-deca1da2fdc2', 'Retrospective', '2026-03-17', '2026-03-17',
    1773730800000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4800c68b-daf6-4901-98a9-cc4c31d77e86', 'Gym Session', '2026-03-17', '2026-03-17',
    1773730800000, NULL, 1,
    NULL, 'Notes for Gym Session', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd491b81a-29d8-444a-838b-a192e6977e96', 'Partner Call', '2026-03-17', '2026-03-17',
    1773767700000, 1773771300000, 0,
    'Microsoft Teams', 'Notes for Partner Call', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ac8dfeb5-6ee8-4b65-aa0b-c159968d1597', 'Dentist Appointment', '2026-03-18', '2026-03-18',
    1773817200000, NULL, 1,
    NULL, 'Notes for Dentist Appointment', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '009c456d-d5cf-49ce-a305-4c2141d71904', 'Investor Update', '2026-03-18', '2026-03-18',
    1773817200000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2705ff54-4745-414d-9218-24a29e95506c', 'Gym Session', '2026-03-18', '2026-03-18',
    1773817200000, NULL, 1,
    NULL, 'Notes for Gym Session', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3f867ffd-7be9-4ebb-949e-383c94383ca5', 'Interview - Designer', '2026-03-18', '2026-03-18',
    1773864000000, 1773871200000, 0,
    'Conference Room B', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '783fde6d-0193-47b5-8942-a67b3674d817', 'Sprint Planning', '2026-03-18', '2026-03-18',
    1773849600000, 1773856800000, 0,
    'Main Office', 'Notes for Sprint Planning', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd846126c-1d4d-4e4e-9b34-355cba22373d', 'Haircut', '2026-03-18', '2026-03-18',
    1773817200000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fbe104d2-920a-4177-97f5-4f37ce7c7658', 'Strategy Session', '2026-03-18', '2026-03-18',
    1773846000000, 1773849600000, 0,
    'Zoom', 'Notes for Strategy Session', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1d0861cd-ce41-4830-b68c-63a24195e078', 'Jake''s Robotics Competition', '2026-03-18', '2026-03-18',
    1773871200000, 1773885600000, 0,
    'Client Office', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '82cfd52a-b57f-4533-b789-a01e4e0a3236', 'Budget Review', '2026-03-18', '2026-03-18',
    1773863100000, 1773877500000, 0,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2ed6c42c-4a95-4e9c-8a0c-65d734f27624', 'Coffee Chat', '2026-03-18', '2026-03-18',
    1773881100000, 1773888300000, 0,
    'Phone Call', 'Notes for Coffee Chat', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '602e50f0-69f9-449d-bcc0-f6db19a40a8d', 'Family Bike Ride', '2026-03-18', '2026-03-18',
    1773817200000, NULL, 1,
    NULL, 'Notes for Family Bike Ride', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964967, 1773109964967,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6a4193b1-f4c6-4006-afcf-eaa3d76c0d87', 'Department Meeting', '2026-03-19', '2026-03-19',
    1773933300000, 1773940500000, 0,
    '123 Main Street', 'Notes for Department Meeting', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '87adb2f2-5e3b-4517-b2f2-52d4846c9a83', 'Kids'' Doctor Checkup', '2026-03-19', '2026-03-19',
    1773949500000, 1773953100000, 0,
    '123 Main Street', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fe392d8e-e659-44fc-b61e-1f950b486fb7', 'Project Review', '2026-03-19', '2026-03-19',
    1773945000000, 1773952200000, 0,
    'Coffee Shop', 'Notes for Project Review', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '64222332-4413-46c6-b20a-cf929e842609', 'Coffee Chat', '2026-03-19', '2026-03-19',
    1773944100000, 1773945900000, 0,
    NULL, 'Notes for Coffee Chat', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd88a0bbd-dc79-4fdb-b2c5-87f77116eee5', 'Sprint Planning', '2026-03-19', '2026-03-19',
    1773935100000, 1773949500000, 0,
    'Client Office', 'Notes for Sprint Planning', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6abb20f8-173a-4606-aa9a-319514bfbb40', 'Gym Session', '2026-03-19', '2026-03-19',
    1773903600000, NULL, 1,
    NULL, 'Notes for Gym Session', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6050fcc7-131c-4557-aa72-489bf97462ee', 'Date Night', '2026-03-19', '2026-03-19',
    1773903600000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2a3332fe-3d5d-4923-b743-1707016a8076', 'Team Standup', '2026-03-19', '2026-03-19',
    1773903600000, NULL, 1,
    NULL, 'Notes for Team Standup', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8e64bebd-d47d-480b-99b1-709a39a10ccd', 'Host Dinner Party', '2026-03-19', '2026-03-19',
    1773948600000, 1773955800000, 0,
    'Coffee Shop', 'Notes for Host Dinner Party', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0860e5e0-c8ce-469d-aec4-fb7d28a4be0c', 'Movie Night', '2026-03-20', '2026-03-20',
    1774043100000, 1774050300000, 0,
    'Microsoft Teams', 'Notes for Movie Night', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '64ae78ba-2d11-4c97-add6-619717b09540', 'All-Hands Meeting', '2026-03-20', '2026-03-20',
    1774019700000, 1774023300000, 0,
    'Zoom', 'Notes for All-Hands Meeting', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4581ca92-52e4-4bf7-9910-09ebd27342fe', 'Grocery Run', '2026-03-20', '2026-03-20',
    1774053900000, 1774068300000, 0,
    'Conference Room B', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ae95fdcc-8bd2-49b3-9a15-a39d7b33be91', 'Date Night', '2026-03-20', '2026-03-20',
    1773990000000, NULL, 1,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e7ddd355-8c97-4067-8606-0863351a53b7', 'Coffee Chat', '2026-03-20', '2026-03-20',
    1774035900000, 1774050300000, 0,
    NULL, 'Notes for Coffee Chat', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cf89d5b3-2d48-4b02-8a53-b5b1079216de', 'Food Bank Volunteering', '2026-03-20', '2026-03-20',
    1774038600000, 1774040400000, 0,
    'Phone Call', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd864d61e-27fb-4c85-964b-c6a5cb040cc0', 'Team Standup', '2026-03-20', '2026-03-20',
    1774041300000, 1774043100000, 0,
    'Coffee Shop', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e50c31fe-7107-4a76-bbfb-54f3cb29d07a', 'Haircut', '2026-03-20', '2026-03-20',
    1774048500000, 1774050300000, 0,
    NULL, 'Notes for Haircut', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '12d0d15a-5262-47b1-9e05-6835e82c7d4b', 'Lunch with Friends', '2026-03-20', '2026-03-20',
    1774044000000, 1774051200000, 0,
    'Phone Call', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '979d6927-1a2b-4ded-b69f-e8347c10e8c5', 'Date Night', '2026-03-20', '2026-03-20',
    1773990000000, NULL, 1,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'eebedfdf-8175-4090-a421-dd0eb03e25ad', 'Dinner with Family', '2026-03-20', '2026-03-20',
    1774036800000, 1774044000000, 0,
    'Conference Room A', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '87852de9-082d-45fc-96d0-aec40eef3cf8', 'Department Meeting', '2026-03-20', '2026-03-20',
    1774044900000, 1774048500000, 0,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '57118aca-fe0c-46d6-ac39-3f3f36213679', 'Team Lunch', '2026-03-21', '2026-03-21',
    1774134000000, 1774148400000, 0,
    'Building 2, Floor 3', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cdb738a7-1e41-40b7-bfdc-ac6051e99395', 'Grocery Run', '2026-03-21', '2026-03-21',
    1774105200000, 1774112400000, 0,
    'Microsoft Teams', 'Notes for Grocery Run', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '32412015-1315-4d2a-9428-975e1e5377ad', 'Car Service', '2026-03-21', '2026-03-21',
    1774125900000, 1774133100000, 0,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4876193b-c5e9-451f-96eb-7e933c171e39', 'Quarterly Planning', '2026-03-22', '2026-03-22',
    1774191600000, 1774198800000, 0,
    'Downtown Office', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8b99ecc0-c7e8-432c-9bc8-c94889b4d4a1', 'Jake''s Basketball Practice', '2026-03-22', '2026-03-22',
    1774203300000, 1774210500000, 0,
    'Client Office', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '07ee5eea-fb91-4e90-bc28-27c09f8cc043', 'Host Dinner Party', '2026-03-22', '2026-03-22',
    1774210500000, 1774217700000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'dbb81644-6cfc-42a1-98d3-8797db96a78a', 'Lunch with Friends', '2026-03-22', '2026-03-22',
    1774162800000, NULL, 1,
    NULL, 'Notes for Lunch with Friends', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f8c1e43f-41c5-4d5d-9360-f0b2251b97db', 'Doctor Visit', '2026-03-23', '2026-03-23',
    1774279800000, 1774283400000, 0,
    'Zoom', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964968, 1773109964968,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4e3acdf7-36d4-40d2-8edb-e24d059e9b56', 'Take Jake to Scout Camp', '2026-03-23', '2026-03-23',
    1774297800000, 1774312200000, 0,
    'Zoom', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '744ee775-71c0-40e7-adaf-4d87d2164319', 'Emma''s Soccer Game', '2026-03-23', '2026-03-23',
    1774249200000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'aa5843e0-5ee0-4e24-8eab-73af39b5a114', 'Grocery Run', '2026-03-23', '2026-03-23',
    1774249200000, NULL, 1,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e14e08f2-8235-433a-9f97-fc41e52f5e4c', 'Family Bike Ride', '2026-03-23', '2026-03-23',
    1774249200000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '04140a95-3731-4429-9588-d03dc5deb9b8', 'Family Bike Ride', '2026-03-23', '2026-03-23',
    1774249200000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3a4b0500-6724-4f81-95a7-26c15bc2cbf6', 'Grocery Run', '2026-03-23', '2026-03-23',
    1774286100000, 1774287900000, 0,
    NULL, 'Notes for Grocery Run', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1f875769-baa9-4a6b-9b84-a22dfbdcfeee', 'Team Standup', '2026-03-23', '2026-03-23',
    1774280700000, 1774284300000, 0,
    'Google Meet', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '19df7117-c31f-4c52-90b1-6d512c24d479', 'Grocery Run', '2026-03-23', '2026-03-23',
    1774249200000, NULL, 1,
    NULL, 'Notes for Grocery Run', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c2bdb8b8-f214-4fe9-ab8c-4fa726e4c855', 'Movie Night', '2026-03-23', '2026-03-23',
    1774286100000, 1774293300000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '146c908e-afd1-4b20-ba8c-6de5e09d7025', 'Meeting #8136', '2026-03-24', '2026-03-24',
    1774335600000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '36e720e9-f808-4fb8-9ed4-b6d346bf3298', 'Meeting #8170', '2026-03-24', '2026-03-24',
    1774383300000, 1774390500000, 0,
    'Microsoft Teams', 'Notes for Meeting #8170', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bf59e422-bad7-4eec-be77-7deaa1c0888e', 'Meeting #9896', '2026-03-24', '2026-03-24',
    1774335600000, NULL, 1,
    NULL, 'Notes for Meeting #9896', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '80c7d99f-6c5c-4be4-b985-3d581842c8df', 'Meeting #6834', '2026-03-25', '2026-03-25',
    1774484100000, 1774485900000, 0,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b4774c88-d6b5-4988-8de3-1cf8376b3ab7', 'Meeting #7957', '2026-03-25', '2026-03-25',
    1774422000000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cc6a382b-35f5-4a2d-bdcb-fad1fa0392d8', 'Meeting #6347', '2026-03-25', '2026-03-25',
    1774458000000, 1774465200000, 0,
    NULL, 'Notes for Meeting #6347', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '644b0843-c472-4931-8a94-e42db0304616', 'Meeting #6145', '2026-03-25', '2026-03-25',
    1774481400000, 1774483200000, 0,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '19d5137c-73c0-4767-8f59-64b69e2db7df', 'Meeting #1100', '2026-03-26', '2026-03-26',
    1774508400000, NULL, 1,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b74b64d4-438d-4155-bbe6-2d0cd34fbc29', 'Meeting #5725', '2026-03-26', '2026-03-26',
    1774557000000, 1774564200000, 0,
    'Microsoft Teams', 'Notes for Meeting #5725', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ef05942d-82d4-4f39-8be9-70bf1b93489c', 'Meeting #7967', '2026-03-26', '2026-03-26',
    1774558800000, 1774560600000, 0,
    'Downtown Office', 'Notes for Meeting #7967', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c40aff96-8b2e-4620-93e4-f53695132140', 'Meeting #9005', '2026-03-26', '2026-03-26',
    1774508400000, NULL, 1,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8a6063f4-52ee-49ac-ab08-2c672f56b325', 'Meeting #3885', '2026-03-27', '2026-03-27',
    1774594800000, NULL, 1,
    NULL, 'Notes for Meeting #3885', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a5df8cef-a21a-4461-ac3d-698a6d763391', 'Meeting #9687', '2026-03-27', '2026-03-27',
    1774633500000, 1774640700000, 0,
    'Phone Call', 'Notes for Meeting #9687', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2fbb5cd3-07aa-487f-b1e5-9935d8755b24', 'Meeting #3765', '2026-03-27', '2026-03-27',
    1774627200000, 1774641600000, 0,
    NULL, 'Notes for Meeting #3765', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '102066c3-0cc1-414c-9f5a-8facd398e13f', 'Meeting #5795', '2026-03-27', '2026-03-27',
    1774644300000, 1774658700000, 0,
    'Client Office', 'Notes for Meeting #5795', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2ad6f9ba-2c16-459d-bb68-2edf99da327a', 'Meeting #7297', '2026-03-27', '2026-03-27',
    1774594800000, NULL, 1,
    NULL, 'Notes for Meeting #7297', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '085e558e-3a88-4d89-8bf5-985924a1aac0', 'Meeting #9876', '2026-03-27', '2026-03-27',
    1774594800000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f547a1b5-ee8f-4805-a054-c1140c0066da', 'Meeting #2987', '2026-03-27', '2026-03-27',
    1774594800000, NULL, 1,
    NULL, 'Notes for Meeting #2987', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '61beebc1-890d-4c05-b605-afa0c4fea871', 'Meeting #9544', '2026-03-28', '2026-03-28',
    1774742400000, 1774749600000, 0,
    NULL, 'Notes for Meeting #9544', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964969, 1773109964969,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '488b74fe-6574-43c9-83c8-aa555770028e', 'Meeting #9976', '2026-03-28', '2026-03-28',
    1774731600000, 1774746000000, 0,
    'Conference Room B', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6dfb4516-1413-49a4-8875-82892877cffb', 'Meeting #5437', '2026-03-28', '2026-03-28',
    1774742400000, 1774746000000, 0,
    'Building 2, Floor 3', 'Notes for Meeting #5437', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '09cbfdf1-8b2f-40d1-9354-6285a7502217', 'Meeting #2844', '2026-03-28', '2026-03-28',
    1774728900000, 1774743300000, 0,
    'Google Meet', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '20dff4a7-b9dd-46ec-a988-205452d581cf', 'Meeting #9137', '2026-03-28', '2026-03-28',
    1774726200000, 1774728000000, 0,
    'Main Office', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7d17618f-071a-4538-9cec-6bf9fa6e261d', 'Meeting #9638', '2026-03-28', '2026-03-28',
    1774717200000, 1774731600000, 0,
    'Conference Room B', 'Notes for Meeting #9638', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '797652b6-cd47-4246-9ea4-ee24c80ef0be', 'Meeting #5093', '2026-03-28', '2026-03-28',
    1774715400000, 1774722600000, 0,
    'Client Office', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0d5718e7-59a5-484d-9730-7d6e4dccf816', 'Meeting #2724', '2026-03-28', '2026-03-28',
    1774725300000, 1774732500000, 0,
    'Google Meet', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cb7e303a-f6b0-4942-b955-b99e3aa0fca6', 'Meeting #2569', '2026-03-28', '2026-03-28',
    1774681200000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9c016870-c7b8-4cd1-9845-7f38ff920c9c', 'Meeting #6779', '2026-03-29', '2026-03-29',
    1774796400000, 1774798200000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'adf17765-a913-484c-bba6-3a894a3adad0', 'Meeting #4887', '2026-03-29', '2026-03-29',
    1774767600000, NULL, 1,
    NULL, 'Notes for Meeting #4887', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a9d79998-6f7c-431f-aba5-0c62ca9a7d16', 'Meeting #6278', '2026-03-29', '2026-03-29',
    1774811700000, 1774826100000, 0,
    'Coffee Shop', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cd185103-6ae5-4f25-b410-de1ea879e79d', 'Meeting #1379', '2026-03-29', '2026-03-29',
    1774800900000, 1774815300000, 0,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e3c0582d-08b8-4aca-b0c4-8ea6983cc225', 'Meeting #5013', '2026-03-29', '2026-03-29',
    1774767600000, NULL, 1,
    NULL, 'Notes for Meeting #5013', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd8cfa657-27d8-497d-a2c3-faff7e32623e', 'Meeting #9035', '2026-03-29', '2026-03-29',
    1774828800000, 1774830600000, 0,
    'Phone Call', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '11444033-2a12-484b-af72-8ede76dcdb13', 'Meeting #1004', '2026-03-29', '2026-03-29',
    1774767600000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '30bf5fcf-7719-49b1-adbf-254896920ae7', 'Meeting #3267', '2026-03-29', '2026-03-29',
    1774828800000, 1774843200000, 0,
    'Conference Room A', 'Notes for Meeting #3267', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '35ce8783-9b0c-4ec6-9f68-f3b56c8771f7', 'Meeting #5270', '2026-03-29', '2026-03-29',
    1774818900000, 1774822500000, 0,
    'Building 2, Floor 3', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9b27a16a-8f43-4630-80cf-454e4a9d4bee', 'Meeting #9076', '2026-03-29', '2026-03-29',
    1774818000000, 1774821600000, 0,
    'Conference Room B', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '151aec3f-6918-4080-b68a-4527666105a1', 'Meeting #5337', '2026-03-29', '2026-03-29',
    1774831500000, 1774845900000, 0,
    'Coffee Shop', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '22491116-494d-4293-a2c2-17e7879c4c50', 'Meeting #5908', '2026-03-29', '2026-03-29',
    1774767600000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '68ee3ff6-0583-47cb-9e9d-cb55c01509bb', 'Meeting #6428', '2026-03-30', '2026-03-30',
    1774885500000, 1774892700000, 0,
    'Coffee Shop', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0de9ae5f-6ba0-4658-bd32-051c4fc2a4bd', 'Meeting #7660', '2026-03-30', '2026-03-30',
    1774913400000, 1774917000000, 0,
    'Building 2, Floor 3', 'Notes for Meeting #7660', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b8eb3add-9370-420b-8376-ae085f78cba6', 'Meeting #4924', '2026-03-30', '2026-03-30',
    1774896300000, 1774903500000, 0,
    '123 Main Street', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a392681e-ce36-4740-9b63-76f9a7c168d2', 'Meeting #2261', '2026-03-30', '2026-03-30',
    1774915200000, 1774929600000, 0,
    'Phone Call', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7b771054-da4f-4e23-b85d-1691e37c8a6b', 'Meeting #1981', '2026-03-31', '2026-03-31',
    1774994400000, 1775001600000, 0,
    'Google Meet', 'Notes for Meeting #1981', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '61a26b9c-0e30-4e47-afac-a0d6ccc820e0', 'Meeting #3377', '2026-03-31', '2026-03-31',
    1774940400000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5540578c-2cdf-4621-81a2-3865c01fe489', 'Meeting #3739', '2026-03-31', '2026-03-31',
    1774999800000, 1775007000000, 0,
    'Zoom', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8b3725fe-7588-482f-b78a-bf942536acd1', 'Meeting #4286', '2026-03-31', '2026-03-31',
    1774987200000, 1774994400000, 0,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f715f964-ebc1-4ce0-82ab-d8b65cb59ab8', 'Meeting #3190', '2026-03-31', '2026-03-31',
    1774940400000, NULL, 1,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '00339844-2d55-457f-8b63-e29a34dd61c6', 'Meeting #8183', '2026-03-31', '2026-03-31',
    1774940400000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '22b97c63-1514-4e1f-8321-4c73a3122c4e', 'Meeting #4073', '2026-03-31', '2026-03-31',
    1774991700000, 1775006100000, 0,
    'Conference Room A', 'Notes for Meeting #4073', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fbe0cfd5-4771-40dd-9da4-231c93dae1a1', 'Meeting #7271', '2026-03-31', '2026-03-31',
    1774983600000, 1774990800000, 0,
    'Microsoft Teams', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964970, 1773109964970,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bddbed35-7ced-4680-bea3-a87a7bde8636', 'Meeting #2416', '2026-03-31', '2026-03-31',
    1774993500000, 1774995300000, 0,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7d9539c2-1d18-4244-b33c-47a73007744e', 'Meeting #3362', '2026-04-01', '2026-04-01',
    1775059200000, 1775066400000, 0,
    NULL, 'Notes for Meeting #3362', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fb16fa89-e69c-4870-943a-c72047ccdf4c', 'Meeting #3110', '2026-04-01', '2026-04-01',
    1775026800000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '74f6ed44-3adf-4809-a934-c7171c1e6dd1', 'Meeting #8964', '2026-04-01', '2026-04-01',
    1775081700000, 1775085300000, 0,
    'Building 2, Floor 3', 'Notes for Meeting #8964', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e8a2f195-afb1-4f05-a81a-18f044f59c1c', 'Meeting #7594', '2026-04-01', '2026-04-01',
    1775026800000, NULL, 1,
    NULL, 'Notes for Meeting #7594', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'dfa6c97c-f84d-44c9-9880-7fb9818b8c8d', 'Meeting #9790', '2026-04-01', '2026-04-01',
    1775065500000, 1775079900000, 0,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4c32a090-3da0-4f9d-bc82-22fee0c5be8d', 'Meeting #5099', '2026-04-01', '2026-04-01',
    1775058300000, 1775072700000, 0,
    'Conference Room B', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '59b3a2b5-eb7e-4444-8323-3896930fac2e', 'Meeting #2189', '2026-04-01', '2026-04-01',
    1775026800000, NULL, 1,
    NULL, 'Notes for Meeting #2189', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7af91ddd-dd51-4c94-92f1-a84c49e8f302', 'Meeting #7416', '2026-04-01', '2026-04-01',
    1775072700000, 1775087100000, 0,
    'Main Office', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bd55058d-5fd1-478d-82af-16699d80f6e3', 'Meeting #8132', '2026-04-01', '2026-04-01',
    1775026800000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7aa357e1-5f14-4161-9e0b-ba5b9e33fc9e', 'Meeting #3182', '2026-04-02', '2026-04-02',
    1775155500000, 1775162700000, 0,
    'Building 2, Floor 3', 'Notes for Meeting #3182', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8a794be4-23bb-4f5c-82b9-987d8dcd8d71', 'Meeting #2987', '2026-04-02', '2026-04-02',
    1775113200000, NULL, 1,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6a188e32-bd5e-40d6-9a66-aeb25d965e30', 'Meeting #9213', '2026-04-02', '2026-04-02',
    1775161800000, 1775165400000, 0,
    'Google Meet', 'Notes for Meeting #9213', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cfbe7af8-eb78-4021-9adb-a70e2a4c5878', 'Meeting #4914', '2026-04-02', '2026-04-02',
    1775145600000, 1775147400000, 0,
    'Main Office', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'abf0aa56-29c0-4305-b5b0-51acfac0adc4', 'Meeting #7032', '2026-04-02', '2026-04-02',
    1775173500000, 1775187900000, 0,
    'Microsoft Teams', 'Notes for Meeting #7032', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6c4e932b-404c-41d6-8a01-0dcd2ae15805', 'Meeting #8617', '2026-04-03', '2026-04-03',
    1775245500000, 1775249100000, 0,
    NULL, 'Notes for Meeting #8617', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '58913659-3eec-4420-bdf4-0f7d617a9951', 'Meeting #7342', '2026-04-03', '2026-04-03',
    1775199600000, NULL, 1,
    NULL, 'Notes for Meeting #7342', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ca9eb55c-624f-40d9-b656-b838a27661ca', 'Meeting #2585', '2026-04-03', '2026-04-03',
    1775241000000, 1775255400000, 0,
    NULL, NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'cefd0c23-0d09-4c1c-87dd-8ed34f59c087', 'Meeting #8226', '2026-04-03', '2026-04-03',
    1775259900000, 1775267100000, 0,
    'Phone Call', 'Notes for Meeting #8226', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '39f06bd8-5da4-46aa-9f2c-c0d9ee610d30', 'Meeting #5112', '2026-04-03', '2026-04-03',
    1775244600000, 1775259000000, 0,
    'Zoom', 'Notes for Meeting #5112', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '953dec0e-6582-4ec0-b1f5-5ff69d8a3858', 'Meeting #1265', '2026-04-03', '2026-04-03',
    1775246400000, 1775250000000, 0,
    '123 Main Street', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a9382418-0b14-4afb-9ef6-ea0f74b25ba7', 'Meeting #8935', '2026-04-03', '2026-04-03',
    1775199600000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a79e82c2-ae0f-45b0-9cb2-2d0ed4fad181', 'Meeting #5398', '2026-04-04', '2026-04-04',
    1775343600000, 1775345400000, 0,
    'Conference Room B', 'Notes for Meeting #5398', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1b22bd1e-68e9-4c13-a21e-9d6c85380bea', 'Meeting #5161', '2026-04-04', '2026-04-04',
    1775320200000, 1775327400000, 0,
    'Coffee Shop', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '24d20a7a-34e3-407c-8917-58a3c6c6e63b', 'Meeting #1066', '2026-04-04', '2026-04-04',
    1775321100000, 1775324700000, 0,
    'Downtown Office', 'Notes for Meeting #1066', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6754e387-4c61-41ae-b207-03d22592d5a0', 'Meeting #7885', '2026-04-04', '2026-04-04',
    1775332800000, 1775334600000, 0,
    'Main Office', 'Notes for Meeting #7885', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1097aa68-893f-4776-97ba-52efc225fe1e', 'Meeting #2137', '2026-04-05', '2026-04-05',
    1775432700000, 1775434500000, 0,
    'Google Meet', 'Notes for Meeting #2137', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '16abd807-d02c-41be-9547-6a1e6701adbe', 'Meeting #8420', '2026-04-05', '2026-04-05',
    1775415600000, 1775419200000, 0,
    'Building 2, Floor 3', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0480c059-c2e7-450b-ba7b-7b40b2ef85ce', 'Meeting #7507', '2026-04-05', '2026-04-05',
    1775372400000, NULL, 1,
    NULL, 'Notes for Meeting #7507', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9e5f2f20-61f3-41f9-9e20-a0d54146011d', 'Meeting #9993', '2026-04-05', '2026-04-05',
    1775372400000, NULL, 1,
    NULL, NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '10244afa-7dd1-4600-9e9e-afcc53647094', 'Meeting #1307', '2026-04-05', '2026-04-05',
    1775432700000, 1775436300000, 0,
    'Conference Room A', 'Notes for Meeting #1307', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e2ea204d-da5b-46c5-b051-00061ca5137d', 'Meeting #5698', '2026-04-05', '2026-04-05',
    1775401200000, 1775403000000, 0,
    'Zoom', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964971, 1773109964971,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1f12d9c4-4fa1-4cef-a32a-53a788acb95d', 'Meeting #3650', '2026-04-05', '2026-04-05',
    1775405700000, 1775407500000, 0,
    'Zoom', 'Notes for Meeting #3650', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4ba50c22-c961-44cd-8c0c-e28a28a5ae78', 'Meeting #5000', '2026-04-05', '2026-04-05',
    1775405700000, 1775409300000, 0,
    'Google Meet', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f9cd22e3-9938-469e-8803-5f6a32fb054d', 'Meeting #7528', '2026-04-05', '2026-04-05',
    1775372400000, NULL, 1,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fc764a91-95db-47dd-b226-5af3465643ad', 'Meeting #4574', '2026-04-05', '2026-04-05',
    1775422800000, 1775437200000, 0,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2d56cc55-d9e7-4611-b77c-effef93935e3', 'Meeting #9174', '2026-04-06', '2026-04-06',
    1775458800000, NULL, 1,
    NULL, 'Notes for Meeting #9174', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '18c724e1-8a86-4cfe-b14c-eece3ffdd62a', 'Meeting #4224', '2026-04-06', '2026-04-06',
    1775496600000, 1775500200000, 0,
    'Main Office', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '94c2b8f5-5462-479d-afea-22d9e8f4de32', 'Meeting #6657', '2026-04-06', '2026-04-06',
    1775488500000, 1775490300000, 0,
    NULL, 'Notes for Meeting #6657', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ce173810-62aa-4c2d-8c24-988c00d98c14', 'Meeting #4857', '2026-04-06', '2026-04-06',
    1775490300000, 1775504700000, 0,
    'Main Office', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '174d557a-3442-4028-9a05-df8e0fa351b7', 'Meeting #9800', '2026-04-06', '2026-04-06',
    1775458800000, NULL, 1,
    NULL, 'Notes for Meeting #9800', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd6672e37-7f5c-427f-b370-89c0c37af0bc', 'Meeting #9913', '2026-04-06', '2026-04-06',
    1775510100000, 1775511900000, 0,
    NULL, 'Notes for Meeting #9913', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b381018a-d4f6-40ee-a708-813eab7badf0', 'Meeting #9340', '2026-04-06', '2026-04-06',
    1775498400000, 1775512800000, 0,
    'Downtown Office', 'Notes for Meeting #9340', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '156c546e-9ab4-4bf3-b060-cf2e24facbee', 'Meeting #6248', '2026-04-06', '2026-04-06',
    1775512800000, 1775520000000, 0,
    'Downtown Office', 'Notes for Meeting #6248', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9fddecd1-e58d-48b8-8fd2-320a83cb56b0', 'Meeting #6981', '2026-04-06', '2026-04-06',
    1775511000000, 1775512800000, 0,
    'Conference Room A', 'Notes for Meeting #6981', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c41fb837-4b26-4e31-a5fb-710ada9a6814', 'Meeting #3131', '2026-04-06', '2026-04-06',
    1775508300000, 1775510100000, 0,
    'Main Office', 'Notes for Meeting #3131', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c83c63fe-30e5-477c-8244-0df5f23e2959', 'Meeting #5460', '2026-04-06', '2026-04-06',
    1775458800000, NULL, 1,
    NULL, 'Notes for Meeting #5460', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ef7ec5a2-c53c-4ca9-8665-b6d15431d508', 'Meeting #8719', '2026-04-07', '2026-04-07',
    1775581200000, 1775588400000, 0,
    'Coffee Shop', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '22b672cf-fc0c-4756-80d8-5a0526073c67', 'Meeting #5311', '2026-04-07', '2026-04-07',
    1775585700000, 1775592900000, 0,
    'Coffee Shop', 'Notes for Meeting #5311', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '01c9f1e2-fca7-4f6e-bc7a-586c4e62525e', 'Meeting #6660', '2026-04-07', '2026-04-07',
    1775578500000, 1775582100000, 0,
    'Microsoft Teams', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c4bc2781-660d-4f9b-8b39-f8ca1f4d5806', 'Meeting #5259', '2026-04-07', '2026-04-07',
    1775545200000, NULL, 1,
    NULL, 'Notes for Meeting #5259', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5b8617db-e969-41ff-8fe2-c121add163ee', 'Meeting #9029', '2026-04-08', '2026-04-08',
    1775665800000, 1775667600000, 0,
    'Zoom', 'Notes for Meeting #9029', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '897a076b-0cb6-4db4-ae52-ac7ef371c39e', 'Meeting #5501', '2026-04-08', '2026-04-08',
    1775631600000, NULL, 1,
    NULL, 'Notes for Meeting #5501', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '6fcacefc-efe2-47e3-b9e8-acbe5c5ef013', 'Meeting #1910', '2026-04-08', '2026-04-08',
    1775631600000, NULL, 1,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2d19a6be-cb99-4b01-84fb-40c4f005fe3a', 'Meeting #6667', '2026-04-09', '2026-04-09',
    1775781900000, 1775796300000, 0,
    NULL, 'Notes for Meeting #6667', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd3169c58-df79-41df-a43b-6639672aceec', 'Meeting #5932', '2026-04-09', '2026-04-09',
    1775718000000, NULL, 1,
    NULL, 'Notes for Meeting #5932', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9743eac0-0ec0-465b-8e2e-9f0cc0b929db', 'Meeting #3411', '2026-04-09', '2026-04-09',
    1775770200000, 1775773800000, 0,
    'Google Meet', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e62ac7a8-1afe-4879-9658-b3187d3add7f', 'Meeting #3723', '2026-04-10', '2026-04-10',
    1775804400000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '4083807e-7978-4e75-a320-6efeae7cb1c1', 'Meeting #3537', '2026-04-10', '2026-04-10',
    1775804400000, NULL, 1,
    NULL, 'Notes for Meeting #3537', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '09163908-1929-4f88-8e3b-11e0d95b759d', 'Meeting #2422', '2026-04-10', '2026-04-10',
    1775846700000, 1775850300000, 0,
    'Google Meet', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b8ed9f02-1c37-49e3-9e86-796d54f4e7c0', 'Meeting #7095', '2026-04-10', '2026-04-10',
    1775845800000, 1775849400000, 0,
    'Google Meet', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd0f28999-62cb-4784-a0a0-2392c385a854', 'Meeting #5013', '2026-04-10', '2026-04-10',
    1775835900000, 1775843100000, 0,
    '123 Main Street', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '1a561e66-fe0e-4e93-8a7f-8d44100dc708', 'Meeting #8680', '2026-04-10', '2026-04-10',
    1775804400000, NULL, 1,
    NULL, NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e7c91d67-ba93-4f36-82ae-1752c158b610', 'Meeting #8698', '2026-04-10', '2026-04-10',
    1775835900000, 1775850300000, 0,
    'Microsoft Teams', 'Notes for Meeting #8698', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964972, 1773109964972,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8dfcf04c-2a45-4ee8-a7c5-16044258fafa', 'Meeting #7619', '2026-04-10', '2026-04-10',
    1775856600000, 1775871000000, 0,
    'Main Office', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7647b342-0b32-4fae-80a1-d97f29cf7fe7', 'Meeting #8335', '2026-04-11', '2026-04-11',
    1775942100000, 1775956500000, 0,
    NULL, NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '554f608f-f7dd-466d-8213-0ee732a6216e', 'Meeting #1204', '2026-04-11', '2026-04-11',
    1775939400000, 1775941200000, 0,
    NULL, 'Notes for Meeting #1204', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e8fd3685-6b0b-492e-9ee7-ae55eae0b708', 'Meeting #7572', '2026-04-11', '2026-04-11',
    1775925000000, 1775932200000, 0,
    'Conference Room A', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2015bf34-a7a5-4e33-8df0-960eaa770797', 'Meeting #3510', '2026-04-11', '2026-04-11',
    1775922300000, 1775929500000, 0,
    'Building 2, Floor 3', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '13bbbed7-b9b9-453a-8251-8a7ec2669839', 'Meeting #9438', '2026-04-12', '2026-04-12',
    1776031200000, 1776038400000, 0,
    'Downtown Office', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a3c79054-0e0a-4376-a960-3ea53bfb0cac', 'Meeting #1933', '2026-04-12', '2026-04-12',
    1776015000000, 1776018600000, 0,
    'Conference Room A', 'Notes for Meeting #1933', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e1e17803-63e7-4bed-babc-67f46681ea4e', 'Meeting #4614', '2026-04-12', '2026-04-12',
    1775977200000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '2eaa17cf-81d9-4a11-bf9f-152eeb6e2e16', 'Meeting #1084', '2026-04-12', '2026-04-12',
    1776033900000, 1776037500000, 0,
    'Google Meet', 'Notes for Meeting #1084', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '08518bf6-cb61-4a7c-bb64-75c694da0bd0', 'Meeting #9596', '2026-04-13', '2026-04-13',
    1776063600000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '52d8ed35-2064-4069-b7f1-4e49a7dda852', 'Meeting #3703', '2026-04-13', '2026-04-13',
    1776063600000, NULL, 1,
    NULL, 'Notes for Meeting #3703', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9dbb6f75-450c-41c0-af70-9a985df5e22c', 'Meeting #4017', '2026-04-13', '2026-04-13',
    1776127500000, 1776131100000, 0,
    '123 Main Street', 'Notes for Meeting #4017', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7dd7ef05-5561-4215-b8ee-e3f68e136611', 'Meeting #6284', '2026-04-14', '2026-04-14',
    1776184200000, 1776191400000, 0,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '469c4a47-7d25-487f-9695-bd151c921ac1', 'Meeting #6113', '2026-04-14', '2026-04-14',
    1776202200000, 1776205800000, 0,
    'Main Office', 'Notes for Meeting #6113', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c3f73182-24c1-45e2-9be7-7508bd6e9f82', 'Meeting #8881', '2026-04-14', '2026-04-14',
    1776204000000, 1776207600000, 0,
    'Microsoft Teams', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '880b8f0a-869d-4704-b49d-2082af0e0ea9', 'Meeting #8552', '2026-04-14', '2026-04-14',
    1776188700000, 1776192300000, 0,
    'Google Meet', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a44b330d-418d-4436-b7fb-e7364b351351', 'Meeting #2893', '2026-04-14', '2026-04-14',
    1776200400000, 1776204000000, 0,
    'Phone Call', NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0e0508d8-845b-4bf2-977b-da58bd253530', 'Meeting #5313', '2026-04-14', '2026-04-14',
    1776210300000, 1776212100000, 0,
    'Conference Room A', 'Notes for Meeting #5313', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '45f45437-0bd5-4a30-9a3d-21b0da774bbd', 'Meeting #2287', '2026-04-14', '2026-04-14',
    1776200400000, 1776202200000, 0,
    'Client Office', 'Notes for Meeting #2287', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a0a147e1-86d3-4602-8527-ed514483024b', 'Meeting #7763', '2026-04-14', '2026-04-14',
    1776150000000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd3d63dca-3adc-4c95-ba4a-c0e3aa750b3b', 'Meeting #4297', '2026-04-14', '2026-04-14',
    1776182400000, 1776186000000, 0,
    'Building 2, Floor 3', 'Notes for Meeting #4297', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'a0495cfe-77e9-4aff-8d06-9176122e41cc', 'Meeting #3596', '2026-04-14', '2026-04-14',
    1776193200000, 1776200400000, 0,
    'Building 2, Floor 3', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd36f4f83-b92a-4ed1-824f-72cad8d6187d', 'Meeting #7505', '2026-04-14', '2026-04-14',
    1776150000000, NULL, 1,
    NULL, 'Notes for Meeting #7505', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'aa2b39db-aec8-4c40-8ba8-c4f7fa4e84f9', 'Meeting #9691', '2026-04-14', '2026-04-14',
    1776199500000, 1776201300000, 0,
    'Microsoft Teams', NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ea3db4c5-066d-4a68-b26f-52ab005147ec', 'Meeting #5128', '2026-04-15', '2026-04-15',
    1776236400000, NULL, 1,
    NULL, NULL, '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '01d3ef24-b363-4630-91cf-73781349317f', 'Meeting #2983', '2026-04-15', '2026-04-15',
    1776236400000, NULL, 1,
    NULL, 'Notes for Meeting #2983', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8feb78eb-1a63-49c6-9cfe-c7d5bdc55b2f', 'Meeting #6373', '2026-04-15', '2026-04-15',
    1776236400000, NULL, 1,
    NULL, 'Notes for Meeting #6373', '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'd9b72ee5-1117-41e4-9df7-dbd97a6db2c9', 'Meeting #1248', '2026-04-15', '2026-04-15',
    1776236400000, NULL, 1,
    NULL, 'Notes for Meeting #1248', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f20c07e6-d8ee-4d5d-9a69-a6ee1cd87566', 'Meeting #3469', '2026-04-15', '2026-04-15',
    1776286800000, 1776301200000, 0,
    'Main Office', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9b90b09a-1234-4dda-b3db-604fcfd4b5ed', 'Meeting #8308', '2026-04-15', '2026-04-15',
    1776236400000, NULL, 1,
    NULL, 'Notes for Meeting #8308', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '73ab0985-38c7-4041-b486-13ca72666749', 'Meeting #9463', '2026-04-15', '2026-04-15',
    1776282300000, 1776296700000, 0,
    'Google Meet', 'Notes for Meeting #9463', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3b343ec5-1e3f-4142-82ce-4d94f8fb35da', 'Meeting #7734', '2026-04-15', '2026-04-15',
    1776297600000, 1776299400000, 0,
    NULL, 'Notes for Meeting #7734', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964973, 1773109964973,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8767cecc-9530-42e6-91f4-643a6a738d8d', 'Meeting #2084', '2026-04-15', '2026-04-15',
    1776275100000, 1776282300000, 0,
    'Building 2, Floor 3', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '3f2cc015-f39e-410c-885e-15cd8ead3abf', 'Meeting #7873', '2026-04-15', '2026-04-15',
    1776294000000, 1776295800000, 0,
    'Microsoft Teams', NULL, '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '397c4074-028c-486c-85da-276181b1d01a', 'Meeting #9280', '2026-04-16', '2026-04-16',
    1776369600000, 1776384000000, 0,
    'Building 2, Floor 3', 'Notes for Meeting #9280', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c2a82857-c02e-4fe1-9a84-50ed58819019', 'Meeting #1924', '2026-04-16', '2026-04-16',
    1776369600000, 1776384000000, 0,
    'Downtown Office', 'Notes for Meeting #1924', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bb3faf3e-856d-4008-9372-21c6a51943ad', 'Meeting #4885', '2026-04-16', '2026-04-16',
    1776369600000, 1776384000000, 0,
    '123 Main Street', 'Notes for Meeting #4885', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0d215688-014c-4c07-8329-b4087e883f8e', 'Meeting #7305', '2026-04-16', '2026-04-16',
    1776322800000, NULL, 1,
    NULL, NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'ef72bec9-1173-4bd9-a2e1-a244b4c2a283', 'Meeting #3011', '2026-04-16', '2026-04-16',
    1776384900000, 1776388500000, 0,
    NULL, NULL, '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '554d0326-d452-4e99-a4c4-9a9d5eee02a3', 'Meeting #9836', '2026-04-16', '2026-04-16',
    1776365100000, 1776372300000, 0,
    'Google Meet', NULL, '#46BDC6', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0547aa22-fe33-452a-aa39-60920f3effd0', 'Meeting #3079', '2026-04-16', '2026-04-16',
    1776322800000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '9827c272-dd7f-4836-93fe-1c7ceea118bb', 'Meeting #5678', '2026-04-16', '2026-04-16',
    1776322800000, NULL, 1,
    NULL, 'Notes for Meeting #5678', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '075fa8f9-b4b0-4e31-b91e-15c84c11a30d', 'Meeting #8687', '2026-04-17', '2026-04-17',
    1776409200000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '127e6af0-acbe-4c8d-9efd-1129cd3b5fed', 'Meeting #7584', '2026-04-17', '2026-04-17',
    1776440700000, 1776442500000, 0,
    NULL, 'Notes for Meeting #7584', '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '839e5be4-5290-4cdd-9044-227b0e3c26ab', 'Meeting #5664', '2026-04-17', '2026-04-17',
    1776447900000, 1776451500000, 0,
    'Google Meet', NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '41e5e7e3-689c-4dcb-90a4-14dcfabeb2f6', 'Meeting #2036', '2026-04-17', '2026-04-17',
    1776442500000, 1776456900000, 0,
    'Microsoft Teams', 'Notes for Meeting #2036', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c20f21da-b6c7-40c7-921a-526eabe14907', 'Meeting #4920', '2026-04-17', '2026-04-17',
    1776447000000, 1776461400000, 0,
    'Microsoft Teams', 'Notes for Meeting #4920', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c8045b48-647b-4c1f-8723-caa331debcf7', 'Meeting #6681', '2026-04-17', '2026-04-17',
    1776457800000, 1776461400000, 0,
    'Zoom', 'Notes for Meeting #6681', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '0ac032d1-c472-4d20-81d5-63c837aebb42', 'Meeting #5744', '2026-04-17', '2026-04-17',
    1776465000000, 1776479400000, 0,
    'Microsoft Teams', 'Notes for Meeting #5744', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f0954e61-13cd-4cf3-87a9-b601e97f2a8e', 'Meeting #4414', '2026-04-17', '2026-04-17',
    1776409200000, NULL, 1,
    NULL, 'Notes for Meeting #4414', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'fb6f9f62-c98d-485c-b69f-acfe1c283b58', 'Meeting #6852', '2026-04-17', '2026-04-17',
    1776464100000, 1776465900000, 0,
    'Conference Room B', 'Notes for Meeting #6852', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'bd914e1d-708f-4606-a96f-dca56c6b8ba8', 'Meeting #4215', '2026-04-17', '2026-04-17',
    1776471300000, 1776478500000, 0,
    'Coffee Shop', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'b2a853f2-fa3f-45c9-85a3-74b4c836e4d2', 'Meeting #2936', '2026-04-17', '2026-04-17',
    1776440700000, 1776455100000, 0,
    NULL, 'Notes for Meeting #2936', '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e88ee1e0-019f-430d-8099-03d880da770e', 'Meeting #7321', '2026-04-18', '2026-04-18',
    1776527100000, 1776541500000, 0,
    'Downtown Office', NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '500dbe62-f523-4c7a-8cdb-d5ef66a0bbb7', 'Meeting #4483', '2026-04-18', '2026-04-18',
    1776495600000, NULL, 1,
    NULL, NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7e90ea4e-d4fe-4f35-b072-5d546f00bda2', 'Meeting #7471', '2026-04-18', '2026-04-18',
    1776495600000, NULL, 1,
    NULL, 'Notes for Meeting #7471', '#E67C73', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '7c37248a-59a6-47e8-b90a-4d9d577b5d17', 'Meeting #2032', '2026-04-18', '2026-04-18',
    1776495600000, NULL, 1,
    NULL, NULL, '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e5b338c5-8e31-440a-8aa7-6147c13ae858', 'Meeting #3355', '2026-04-18', '2026-04-18',
    1776495600000, NULL, 1,
    NULL, NULL, '#4285F4', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8ce76405-094e-4d44-9514-b3f6a725e518', 'Meeting #9138', '2026-04-18', '2026-04-18',
    1776546000000, 1776549600000, 0,
    'Phone Call', 'Notes for Meeting #9138', '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '82623e8b-1910-4d2b-8c76-cc037f2aec9f', 'Meeting #2502', '2026-04-18', '2026-04-18',
    1776550500000, 1776552300000, 0,
    'Conference Room A', 'Notes for Meeting #2502', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5330bae2-2f46-4f4c-9851-4eb505d20973', 'Meeting #7289', '2026-04-18', '2026-04-18',
    1776528900000, 1776530700000, 0,
    '123 Main Street', 'Notes for Meeting #7289', '#F6BF26', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'c8b8ab79-9189-4df6-8c11-9e4d8451bb5c', 'Meeting #4140', '2026-04-19', '2026-04-19',
    1776636000000, 1776650400000, 0,
    'Downtown Office', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '30ffe8bf-0433-4bd0-8e9b-b27789f5faa3', 'Meeting #9399', '2026-04-19', '2026-04-19',
    1776625200000, 1776628800000, 0,
    'Client Office', 'Notes for Meeting #9399', '#8E24AA', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '5fc51045-e4bf-4184-8db8-4c5785b6af00', 'Meeting #2849', '2026-04-19', '2026-04-19',
    1776622500000, 1776624300000, 0,
    'Zoom', NULL, '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964974, 1773109964974,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'e71a510b-ecdd-4e2c-89aa-5940762b4f73', 'Meeting #8306', '2026-04-20', '2026-04-20',
    1776717900000, 1776725100000, 0,
    'Coffee Shop', 'Notes for Meeting #8306', '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964975, 1773109964975,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '41da855e-5865-48ba-b07e-18fe58790b1c', 'Meeting #7041', '2026-04-20', '2026-04-20',
    1776704400000, 1776718800000, 0,
    'Google Meet', NULL, '#34A853', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964975, 1773109964975,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '26ddf0f9-81da-49ff-a57c-0b62254a7be9', 'Meeting #6904', '2026-04-20', '2026-04-20',
    1776706200000, 1776713400000, 0,
    'Conference Room A', NULL, '#7986CB', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964975, 1773109964975,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    'f61787db-6f57-43e6-b65c-5c8077218ade', 'Meeting #8203', '2026-04-20', '2026-04-20',
    1776707100000, 1776710700000, 0,
    NULL, 'Notes for Meeting #8203', '#FF6D01', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964975, 1773109964975,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '8b99c458-90d3-4d03-83a4-0fb0c9c5fd94', 'Meeting #6313', '2026-04-20', '2026-04-20',
    1776668400000, NULL, 1,
    NULL, 'Notes for Meeting #6313', '#EA4335', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964975, 1773109964975,
    NULL, NULL, 'LOCAL'
);
INSERT INTO local_events (
    id, title, dayId, endDayId, startTime, endTime, isAllDay,
    location, description, colorHex, attendees,
    syncEnabled, syncStatus, googleEventId, googleCalendarId, googleCalendarName,
    lastSyncedAt, serverEtag, isReadOnly, createdAt, updatedAt,
    linkedMemoPageId, localCalendarId, providerId
) VALUES (
    '47279e87-b837-4a94-87cb-efcee2fd8d58', 'Meeting #9931', '2026-04-20', '2026-04-20',
    1776712500000, 1776716100000, 0,
    'Building 2, Floor 3', NULL, '#FBBC04', NULL,
    0, 'LOCAL_ONLY', NULL, NULL, NULL,
    NULL, NULL, 0, 1773109964975, 1773109964975,
    NULL, NULL, 'LOCAL'
);

INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f34e7ded-5fa0-4def-9972-c0ff1a2630cf', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'YMmQZXbkBrL0hAyX4As1E', '2026-02-14', 0, 0, 'f34e7ded-5fa0-4def-9972-c0ff1a2630cf',
    0, '', 'Write Annual Report', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b8b2e567-c27e-4c60-a3d9-9f7cd1c16bda', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TN8JxloGJcMGekOC2OSeB', '2026-02-14', 0, 1, 'b8b2e567-c27e-4c60-a3d9-9f7cd1c16bda',
    0, '', 'Gather data', NULL,
    'YMmQZXbkBrL0hAyX4As1E', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b9be87e4-9894-4d9e-aa47-35f81fee7b97', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'sN_nDzgJXEuH6gD5-a9Z5', '2026-02-14', 0, 2, 'b9be87e4-9894-4d9e-aa47-35f81fee7b97',
    0, '', 'Create charts', NULL,
    'YMmQZXbkBrL0hAyX4As1E', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c0247b66-2095-48b6-a095-0ba9f2b10a68', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'EikNBm1dX6F3uNAMs10sH', '2026-02-14', 0, 3, 'c0247b66-2095-48b6-a095-0ba9f2b10a68',
    0, '', 'Publish report', NULL,
    'YMmQZXbkBrL0hAyX4As1E', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a12370b2-b2a5-4cb6-a9a8-3b42e2e86804', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'B9XDjiI_pweFsmTEIHldC', '2026-02-14', 0, 4, 'a12370b2-b2a5-4cb6-a9a8-3b42e2e86804',
    0, '', 'Draft narrative', NULL,
    'YMmQZXbkBrL0hAyX4As1E', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c7cbe283-f86f-4743-9e9b-7418577d1242', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tKAeCidvBsh8s3UzOEt-q', '2026-02-19', 0, 0, 'c7cbe283-f86f-4743-9e9b-7418577d1242',
    0, '', 'Build New Website', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1fe9efe0-2cbe-4c51-9ae4-37be2d11d916', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-7j8HQwc9ktbl4lDG-mat', '2026-02-19', 0, 1, '1fe9efe0-2cbe-4c51-9ae4-37be2d11d916',
    0, '', 'Create sitemap', NULL,
    'tKAeCidvBsh8s3UzOEt-q', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '23c1c505-ae0b-4d9e-99db-00a69894e367', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'bMoP4WqA9iaaFC3czn_y8', '2026-02-19', 0, 2, '23c1c505-ae0b-4d9e-99db-00a69894e367',
    0, '', 'Set up analytics', NULL,
    'tKAeCidvBsh8s3UzOEt-q', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a63ff669-9f1d-49a0-a4bb-671407da8fd7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'iq_RSJhnww4d6DtNecm_q', '2026-02-19', 0, 3, 'a63ff669-9f1d-49a0-a4bb-671407da8fd7',
    0, '', 'Implement pages', NULL,
    'tKAeCidvBsh8s3UzOEt-q', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4d8533ec-6496-48da-a797-3ee4ed886074', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'mGD58pSu2LrBIPXgon3e7', '2026-02-19', 0, 4, '4d8533ec-6496-48da-a797-3ee4ed886074',
    0, '', 'Gather requirements', NULL,
    'tKAeCidvBsh8s3UzOEt-q', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9c9cd05b-5bc8-46b7-b9bf-652f1d029f10', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'AJJHvLGwMJNvDnhr9u8BN', '2026-02-19', 0, 5, '9c9cd05b-5bc8-46b7-b9bf-652f1d029f10',
    0, '', 'Design mockups', NULL,
    'tKAeCidvBsh8s3UzOEt-q', 0, NULL, NULL, NULL,
    NULL, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c01ad274-3081-4e9f-9c77-2ddabc68dc04', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964976, 1773109964976
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'hWWVy6_ItkzTa2-VmXZYN', '2026-04-19', 0, 0, 'c01ad274-3081-4e9f-9c77-2ddabc68dc04',
    0, '', 'Plan Garden', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964976, 1773109964976
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '797f595d-2edc-456e-810c-952f2ae038c4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'BpphlWTujFqudx1i95Vot', '2026-04-19', 0, 1, '797f595d-2edc-456e-810c-952f2ae038c4',
    0, '', 'Design layout', NULL,
    'hWWVy6_ItkzTa2-VmXZYN', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '01f3dd5f-4187-481c-8ea5-45bf87f2ba4f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'RBWorJ6HEJq_QWWgE3_An', '2026-04-19', 0, 2, '01f3dd5f-4187-481c-8ea5-45bf87f2ba4f',
    0, '', 'Prepare soil', NULL,
    'hWWVy6_ItkzTa2-VmXZYN', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9591b33b-bff6-462f-86c7-babc6a89e682', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'XmvhvTAdLKGS3EBMdYWMO', '2026-04-19', 0, 3, '9591b33b-bff6-462f-86c7-babc6a89e682',
    0, '', 'Set watering schedule', NULL,
    'hWWVy6_ItkzTa2-VmXZYN', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'accc3c37-f09a-430c-8534-5e4adb74fe62', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'dliiaqx64EadifnFjiA4P', '2026-04-19', 0, 4, 'accc3c37-f09a-430c-8534-5e4adb74fe62',
    0, '', 'Plant seeds', NULL,
    'hWWVy6_ItkzTa2-VmXZYN', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bf5dcabf-3015-4e2c-aaee-547d98ceb89c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jhsU6I20DwfG-Qy0LNI5O', '2026-03-02', 0, 0, 'bf5dcabf-3015-4e2c-aaee-547d98ceb89c',
    0, 'B', 'Improve Fitness Routine', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4b22b615-8aa2-4f04-a4fe-c9f6b1d948f3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'PR3T8dT0VPo_nSiY26xdG', '2026-03-02', 0, 1, '4b22b615-8aa2-4f04-a4fe-c9f6b1d948f3',
    0, '', 'Adjust plan', NULL,
    'jhsU6I20DwfG-Qy0LNI5O', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '86c5cce9-4caf-42a5-9815-c5492376890e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '5KFUbpSooAZq1Uz8BZ1B7', '2026-03-02', 0, 2, '86c5cce9-4caf-42a5-9815-c5492376890e',
    0, '', 'Set goals', NULL,
    'jhsU6I20DwfG-Qy0LNI5O', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c668f42f-32ba-4ff1-afce-93bf8d6861ea', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'dsWZAySRqme6VyQZ8XvuI', '2026-03-02', 0, 3, 'c668f42f-32ba-4ff1-afce-93bf8d6861ea',
    0, '', 'Track progress', NULL,
    'jhsU6I20DwfG-Qy0LNI5O', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f27cced8-fa3c-40b3-9538-0bfd39ef2783', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Zt0wbf7kBz1sZi9EEMTZR', '2026-03-02', 0, 4, 'f27cced8-fa3c-40b3-9538-0bfd39ef2783',
    0, '', 'Choose program', NULL,
    'jhsU6I20DwfG-Qy0LNI5O', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '865b4a30-336b-4e64-bca0-237874a35803', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9VIOH3_rC9_IZs4NysNlx', '2026-03-02', 0, 5, '865b4a30-336b-4e64-bca0-237874a35803',
    0, '', 'Schedule workouts', NULL,
    'jhsU6I20DwfG-Qy0LNI5O', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c75bda63-7b92-451d-b13e-f8c40f24140c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'H8L_eR-zMbzWLRpy81wox', '2026-02-13', 0, 0, 'c75bda63-7b92-451d-b13e-f8c40f24140c',
    0, 'B', 'Plan Fundraiser', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1e2b6829-3cdc-45e3-b4c2-9e8a1b300cad', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'W5BfErRWPqQvVsAAAvO_z', '2026-02-13', 0, 1, '1e2b6829-3cdc-45e3-b4c2-9e8a1b300cad',
    0, '', 'Manage registrations', NULL,
    'H8L_eR-zMbzWLRpy81wox', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5e383604-228d-4dc1-9045-fcff910d2fbe', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ypywiapMDU5aeTCqXyXBq', '2026-02-13', 0, 2, '5e383604-228d-4dc1-9045-fcff910d2fbe',
    0, '', 'Set fundraising goal', NULL,
    'H8L_eR-zMbzWLRpy81wox', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '237455ac-6f8f-44a9-980f-f4da9c0f306a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'liS590vTvgFCL0GnsHKNE', '2026-02-13', 0, 3, '237455ac-6f8f-44a9-980f-f4da9c0f306a',
    0, '', 'Recruit volunteers', NULL,
    'H8L_eR-zMbzWLRpy81wox', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd2c23b83-0593-4238-bb40-46bdb5ebef06', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '2j1Q2ui3ZBFijznb9a538', '2026-02-13', 0, 4, 'd2c23b83-0593-4238-bb40-46bdb5ebef06',
    0, '', 'Promote event', NULL,
    'H8L_eR-zMbzWLRpy81wox', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fa78e3b3-06e7-4fda-b2f6-ddf9df8948c5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    's8ZgsKbroBzz0bngyBTaS', '2026-02-13', 0, 5, 'fa78e3b3-06e7-4fda-b2f6-ddf9df8948c5',
    0, '', 'Secure venue', NULL,
    'H8L_eR-zMbzWLRpy81wox', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0275a520-d8a1-41c6-80b1-ba34c3289655', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'C5aLt5I5IzPUyaObvFlA1', '2026-02-15', 0, 0, '0275a520-d8a1-41c6-80b1-ba34c3289655',
    0, 'A', 'Design Mobile App', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9409def2-4f63-487f-b1c2-758b4187dc22', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '1UOk6oR1VuA-9wgu0hm0S', '2026-02-15', 0, 1, '9409def2-4f63-487f-b1c2-758b4187dc22',
    0, '', 'Define user flows', NULL,
    'C5aLt5I5IzPUyaObvFlA1', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b31dee11-f889-4bca-8b17-b764da0400ce', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_p5NzGeid1sLEQ4nd-Bif', '2026-02-15', 0, 2, 'b31dee11-f889-4bca-8b17-b764da0400ce',
    0, '', 'Create wireframes', NULL,
    'C5aLt5I5IzPUyaObvFlA1', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c0227f70-9be6-4f02-b57d-ff03539bcb85', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'fK9t1et_nsKLoiiDtqXLJ', '2026-02-15', 0, 3, 'c0227f70-9be6-4f02-b57d-ff03539bcb85',
    0, '', 'Iterate UI', NULL,
    'C5aLt5I5IzPUyaObvFlA1', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '71a24510-e523-4b7a-b316-5ba97e91c359', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'gPud_WkIYMGVHbXvU4Sh7', '2026-02-15', 0, 4, '71a24510-e523-4b7a-b316-5ba97e91c359',
    0, '', 'Build prototype', NULL,
    'C5aLt5I5IzPUyaObvFlA1', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '34fe695e-9e39-4bb4-abdd-c4b75511c044', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '2dJH7RbvG9T91TDPW74kd', '2026-02-15', 0, 5, '34fe695e-9e39-4bb4-abdd-c4b75511c044',
    0, '', 'Handoff specs', NULL,
    'C5aLt5I5IzPUyaObvFlA1', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a9605b58-f33f-4512-86eb-30d9c267273f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'RBnmwD2_-VqTWja23ucEo', '2026-03-30', 0, 0, 'a9605b58-f33f-4512-86eb-30d9c267273f',
    0, 'B', 'Quarterly Budget Review', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773644400000, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7a680b92-3b5c-41ee-b523-e6158b288ca2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wKNEWp5h7ncltmfBYr-OH', '2026-03-30', 0, 1, '7a680b92-3b5c-41ee-b523-e6158b288ca2',
    0, '', 'Identify cuts', NULL,
    'RBnmwD2_-VqTWja23ucEo', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1862b454-d481-460e-8508-0444cb6b84a0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'qon0v3g6lnGjnqCnJhRx9', '2026-03-30', 0, 2, '1862b454-d481-460e-8508-0444cb6b84a0',
    0, '', 'Forecast revenue', NULL,
    'RBnmwD2_-VqTWja23ucEo', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6501afe2-5a21-407b-83e9-942bbccfc0b9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'psZZ6r218f-HVZPIKujrU', '2026-03-30', 0, 3, '6501afe2-5a21-407b-83e9-942bbccfc0b9',
    0, '', 'Collect expense reports', NULL,
    'RBnmwD2_-VqTWja23ucEo', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e90495d0-d704-4192-b73a-b52a092e77d8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'dbyCaLjg2dgwaZL7-bvMI', '2026-03-30', 0, 4, 'e90495d0-d704-4192-b73a-b52a092e77d8',
    0, '', 'Present to leadership', NULL,
    'RBnmwD2_-VqTWja23ucEo', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '478f94d2-8848-4883-8c8b-035421cf0490', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'r75Jjcd2DZYy_Yf0Uz8L9', '2026-04-11', 0, 0, '478f94d2-8848-4883-8c8b-035421cf0490',
    0, 'A', 'Customer Survey', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767600000000, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '304d6c57-3489-4d00-9030-fddf0c88ea36', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'uXBn0zO8zlahQ15EN0rq3', '2026-04-11', 0, 1, '304d6c57-3489-4d00-9030-fddf0c88ea36',
    0, '', 'Analyze results', NULL,
    'r75Jjcd2DZYy_Yf0Uz8L9', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '24e02f1b-ec60-464e-9c82-6a6260f5f58a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'zMGAV_yx_Cq7QdUebTKde', '2026-04-11', 0, 2, '24e02f1b-ec60-464e-9c82-6a6260f5f58a',
    0, '', 'Launch survey', NULL,
    'r75Jjcd2DZYy_Yf0Uz8L9', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c3ca562f-49f9-4bcd-a873-d412d03ed434', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'bD10bbXTUFmko88zzJEGS', '2026-04-11', 0, 3, 'c3ca562f-49f9-4bcd-a873-d412d03ed434',
    0, '', 'Draft questions', NULL,
    'r75Jjcd2DZYy_Yf0Uz8L9', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'adb0e9ca-e5c3-443b-9ad5-c3059ef6481b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'JRM8SC1H3jVWVwn1wBnkH', '2026-04-11', 0, 4, 'adb0e9ca-e5c3-443b-9ad5-c3059ef6481b',
    0, '', 'Set audience', NULL,
    'r75Jjcd2DZYy_Yf0Uz8L9', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9a5f2c3c-6649-495a-bddd-41763e80fb0c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'YNzGeRLT1ynGK1ftA5UEq', '2026-04-11', 0, 5, '9a5f2c3c-6649-495a-bddd-41763e80fb0c',
    0, '', 'Define goals', NULL,
    'r75Jjcd2DZYy_Yf0Uz8L9', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ee1cc1a9-2d94-41b6-be95-7e2b8c79a263', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'XyzN2DF79gveZhqDHJgTC', '2026-03-07', 0, 0, 'ee1cc1a9-2d94-41b6-be95-7e2b8c79a263',
    0, 'B', 'Prepare Conference Talk', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ed521637-2493-43a6-a19d-209bb67d159e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9mddrPNFM58uhqOthbIf2', '2026-03-07', 0, 1, 'ed521637-2493-43a6-a19d-209bb67d159e',
    0, '', 'Practice talk', NULL,
    'XyzN2DF79gveZhqDHJgTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3660e97a-adaa-415c-a7c2-0c02a48a5a62', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'WhhWzz0TS_A3aPh-335K9', '2026-03-07', 0, 2, '3660e97a-adaa-415c-a7c2-0c02a48a5a62',
    0, '', 'Build slides', NULL,
    'XyzN2DF79gveZhqDHJgTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'de20ebe4-75d7-4bee-9e0a-2ab58234de2e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'a6RUYXEQ5hrYJpEZ7G4i3', '2026-03-07', 0, 3, 'de20ebe4-75d7-4bee-9e0a-2ab58234de2e',
    0, '', 'Rehearse timing', NULL,
    'XyzN2DF79gveZhqDHJgTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd6571c36-231c-420f-a25c-383024711f52', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Z3xGbGwaLlObw3p4ydLVY', '2026-03-07', 0, 4, 'd6571c36-231c-420f-a25c-383024711f52',
    0, '', 'Final polish', NULL,
    'XyzN2DF79gveZhqDHJgTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f8399a68-ea0f-402c-a504-0316a2e00dda', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '2R6rsRraDNpKCvdU36VAK', '2026-03-31', 0, 0, 'f8399a68-ea0f-402c-a504-0316a2e00dda',
    0, 'B', 'Migrate Database', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768204800000, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '42b38f03-43b5-4149-9561-643ffea80692', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'PkaxIsshk_iSnIWu-wPxw', '2026-03-31', 0, 1, '42b38f03-43b5-4149-9561-643ffea80692',
    0, '', 'Execute migration', NULL,
    '2R6rsRraDNpKCvdU36VAK', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '137fe0b4-bc75-4f40-8cbc-377e7f199f2b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'w5c5K__CSTE2pD2H1yMB0', '2026-03-31', 0, 2, '137fe0b4-bc75-4f40-8cbc-377e7f199f2b',
    0, '', 'Inventory tables', NULL,
    '2R6rsRraDNpKCvdU36VAK', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'de98074b-614f-4e71-ad14-eed64e5d3980', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'AkJFnGGWtIwCHTk2ArE7e', '2026-03-31', 0, 3, 'de98074b-614f-4e71-ad14-eed64e5d3980',
    0, '', 'Plan migration', NULL,
    '2R6rsRraDNpKCvdU36VAK', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bbb14042-b6dc-4468-9fd9-30e45c1c9eb7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'k_VAgh5VU1Ih0csPLka8H', '2026-03-31', 0, 4, 'bbb14042-b6dc-4468-9fd9-30e45c1c9eb7',
    0, '', 'Monitor performance', NULL,
    '2R6rsRraDNpKCvdU36VAK', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f05fe6c4-d960-4e90-b3e7-50897424bbd4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'WIp3bPjjsTmvkSV-bknTC', '2026-03-21', 0, 0, 'f05fe6c4-d960-4e90-b3e7-50897424bbd4',
    0, '', 'Plan a Trip', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773039600000, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '001b1301-c912-46c6-b38b-67def2b88135', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wGJydXPS5M0vGk2aO4s2Q', '2026-03-21', 0, 1, '001b1301-c912-46c6-b38b-67def2b88135',
    0, '', 'Book flights', NULL,
    'WIp3bPjjsTmvkSV-bknTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '28eaef63-be2a-4a04-891c-697b6bae75d0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'G_J3_KdhZ1lifTQtTbuh3', '2026-03-21', 0, 2, '28eaef63-be2a-4a04-891c-697b6bae75d0',
    0, '', 'Reserve lodging', NULL,
    'WIp3bPjjsTmvkSV-bknTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1a56e908-90df-4252-90bb-65ed4660d73c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'YIFx35-jr1U6OuUn-Roig', '2026-03-21', 0, 3, '1a56e908-90df-4252-90bb-65ed4660d73c',
    0, '', 'Pick destination', NULL,
    'WIp3bPjjsTmvkSV-bknTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '61ac5d46-317b-440a-9c8d-9458ed2d2aa4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'djadkyoKH8i5r8wcE6c5I', '2026-03-21', 0, 4, '61ac5d46-317b-440a-9c8d-9458ed2d2aa4',
    0, '', 'Arrange transportation', NULL,
    'WIp3bPjjsTmvkSV-bknTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'df28b992-538c-4275-9ec8-ebaaf69bd4d9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9Zn5zrnMTQvlCEYcuwEFZ', '2026-03-21', 0, 5, 'df28b992-538c-4275-9ec8-ebaaf69bd4d9',
    0, '', 'Create itinerary', NULL,
    'WIp3bPjjsTmvkSV-bknTC', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4879b406-301f-4ce1-8e92-511e1313b35e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'anTwaN-g53t15Gwf1BQw-', '2026-03-06', 0, 0, '4879b406-301f-4ce1-8e92-511e1313b35e',
    0, 'C', 'Write Book Draft', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dc891f29-0803-442d-842f-3523d9652f97', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jJJr38RjTyxS3T7JLonVS', '2026-03-06', 0, 1, 'dc891f29-0803-442d-842f-3523d9652f97',
    0, '', 'Outline chapters', NULL,
    'anTwaN-g53t15Gwf1BQw-', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '57206eaa-5772-4d73-aa26-552b1dbbde85', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '4GHEA9mBlhJ0Ky_uDAj2a', '2026-03-06', 0, 2, '57206eaa-5772-4d73-aa26-552b1dbbde85',
    0, '', 'Revise draft', NULL,
    'anTwaN-g53t15Gwf1BQw-', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b0e20212-2644-4f10-842d-1deb3e90a334', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'J8QnL3hjhSKkCzLkDBSqp', '2026-03-06', 0, 3, 'b0e20212-2644-4f10-842d-1deb3e90a334',
    0, '', 'Draft chapters', NULL,
    'anTwaN-g53t15Gwf1BQw-', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5a334fec-b41e-4ca9-87a9-4ca337fe249d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964977, 1773109964977
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'o93AKcsBX77mEqEnkQ29T', '2026-03-06', 0, 4, '5a334fec-b41e-4ca9-87a9-4ca337fe249d',
    0, '', 'Final edits', NULL,
    'anTwaN-g53t15Gwf1BQw-', 0, NULL, NULL, NULL,
    NULL, 1773109964977, 1773109964977
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e60f0494-77f6-4048-925b-46dc0e085e8e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'BPxG85pe1PZu357X1lvxX', '2026-03-06', 0, 5, 'e60f0494-77f6-4048-925b-46dc0e085e8e',
    0, '', 'Get beta readers', NULL,
    'anTwaN-g53t15Gwf1BQw-', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '46ed8bd8-8ebb-4b89-aec6-98be86db2cb8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'IAHV74KQ4jPOh6P4q25nt', '2026-04-18', 0, 0, '46ed8bd8-8ebb-4b89-aec6-98be86db2cb8',
    0, '', 'Host Team Offsite', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '199a4b3f-a1d8-46dd-8045-654d7917b275', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'aHFbPvTeF-WFUhiEl210M', '2026-04-18', 0, 1, '199a4b3f-a1d8-46dd-8045-654d7917b275',
    0, '', 'Send invites', NULL,
    'IAHV74KQ4jPOh6P4q25nt', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3a45db6e-280a-42c8-8dcf-df34a4918019', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ZAqTZFpI4TZXqVRXv5jo_', '2026-04-18', 0, 2, '3a45db6e-280a-42c8-8dcf-df34a4918019',
    0, '', 'Book venue', NULL,
    'IAHV74KQ4jPOh6P4q25nt', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c874fadd-2840-4cd6-8717-de264c5820c1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'pK86CQZ6J7PM1pVo1BFlM', '2026-04-18', 0, 3, 'c874fadd-2840-4cd6-8717-de264c5820c1',
    0, '', 'Arrange travel', NULL,
    'IAHV74KQ4jPOh6P4q25nt', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '57da51a2-269f-49d5-8eb1-fd3c43297720', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'V4pFtTZsILaN9Ybavm4K9', '2026-04-18', 0, 4, '57da51a2-269f-49d5-8eb1-fd3c43297720',
    0, '', 'Cater meals', NULL,
    'IAHV74KQ4jPOh6P4q25nt', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4ad3861d-cb08-4a6e-8294-100006fe219e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'l6Z_PtKme3u_7vHZ9oKI5', '2026-03-09', 0, 0, '4ad3861d-cb08-4a6e-8294-100006fe219e',
    0, 'C', 'Plan Wedding', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766131200000, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a546987c-aed5-4331-ba23-261671c4770b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '5NGR-XS5r4g3HDf8BiUSI', '2026-03-09', 0, 1, 'a546987c-aed5-4331-ba23-261671c4770b',
    0, '', 'Plan ceremony', NULL,
    'l6Z_PtKme3u_7vHZ9oKI5', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '402d6361-a81f-47e6-a2e6-2f639cae2197', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ga-z5ersWmBjZXOy1JHjW', '2026-03-09', 0, 2, '402d6361-a81f-47e6-a2e6-2f639cae2197',
    0, '', 'Finalize timeline', NULL,
    'l6Z_PtKme3u_7vHZ9oKI5', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '11e5f55d-df14-49bf-a0e1-6565277c1f03', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'AVyHZooCzU6-44gSxSCsQ', '2026-03-09', 0, 3, '11e5f55d-df14-49bf-a0e1-6565277c1f03',
    0, '', 'Send invites', NULL,
    'l6Z_PtKme3u_7vHZ9oKI5', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a7fcc431-5386-4f3e-8ad2-1eeb13f10947', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'siwIVD1-okNXw3LwT1ZPG', '2026-03-09', 0, 4, 'a7fcc431-5386-4f3e-8ad2-1eeb13f10947',
    0, '', 'Book venue', NULL,
    'l6Z_PtKme3u_7vHZ9oKI5', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bfd633d4-fa5a-4fd8-b2a6-b363b6954888', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '2wOinU0-hiitM8mJ5Kx-n', '2026-03-20', 0, 0, 'bfd633d4-fa5a-4fd8-b2a6-b363b6954888',
    0, 'C', 'Organize Office Move', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'df38a51e-19d4-48e4-bfee-5dcba2c11eb0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '2sz6DhsK4dsMIDWKPWHjm', '2026-03-20', 0, 1, 'df38a51e-19d4-48e4-bfee-5dcba2c11eb0',
    0, '', 'Coordinate movers', NULL,
    '2wOinU0-hiitM8mJ5Kx-n', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c9a73fbb-f11f-4824-bf9d-fd38eafa6042', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'oGndMjKpPmrJsb2PIgX-N', '2026-03-20', 0, 2, 'c9a73fbb-f11f-4824-bf9d-fd38eafa6042',
    0, '', 'Label boxes', NULL,
    '2wOinU0-hiitM8mJ5Kx-n', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4c3251ef-09de-4626-bf37-71296e742487', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ePXY3VbySFrTPhc7CTuJW', '2026-03-20', 0, 3, '4c3251ef-09de-4626-bf37-71296e742487',
    0, '', 'Unpack essentials', NULL,
    '2wOinU0-hiitM8mJ5Kx-n', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e0060bbe-6a07-40c8-8d11-58037998bfd2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'CHxyjfKyAV_YbEIFR-oZk', '2026-03-20', 0, 4, 'e0060bbe-6a07-40c8-8d11-58037998bfd2',
    0, '', 'Inventory equipment', NULL,
    '2wOinU0-hiitM8mJ5Kx-n', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ab26bd13-6b66-4e2a-81d5-8bede5e88500', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Dxd8XchmB1jBuMckKUbkP', '2026-03-20', 0, 5, 'ab26bd13-6b66-4e2a-81d5-8bede5e88500',
    0, '', 'Update address', NULL,
    '2wOinU0-hiitM8mJ5Kx-n', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '91fab0da-5648-4df1-b779-012e53ca9f78', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tnnTS2dYJNq9CRFjEE6rC', '2026-02-11', 0, 0, '91fab0da-5648-4df1-b779-012e53ca9f78',
    0, '', 'Renovate Kitchen', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768896000000, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b4fdb657-99b4-4d43-8577-9e831ea88558', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'e3Wz9fMYPYRAVGNcEm9WQ', '2026-02-11', 0, 1, 'b4fdb657-99b4-4d43-8577-9e831ea88558',
    0, '', 'Schedule work', NULL,
    'tnnTS2dYJNq9CRFjEE6rC', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '47e6b442-41b1-403f-b830-82e7dad5aa43', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'x2q-QqN88nC28-MlRLE1t', '2026-02-11', 0, 2, '47e6b442-41b1-403f-b830-82e7dad5aa43',
    0, '', 'Select materials', NULL,
    'tnnTS2dYJNq9CRFjEE6rC', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1b4894b8-b5bb-4aa5-97f4-6d3244d9b9c4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'CpwesnAh9DR3OC6SdpLMo', '2026-02-11', 0, 3, '1b4894b8-b5bb-4aa5-97f4-6d3244d9b9c4',
    0, '', 'Choose contractor', NULL,
    'tnnTS2dYJNq9CRFjEE6rC', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '063b319b-ada4-43af-9bed-79700945822a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '72nQwnXvECG37Jevxf5d1', '2026-02-11', 0, 4, '063b319b-ada4-43af-9bed-79700945822a',
    0, '', 'Inspect progress', NULL,
    'tnnTS2dYJNq9CRFjEE6rC', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '178a410d-07e7-4eba-9b14-cf25ee99968c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '1STy5KGrL-sih-lm5jOrO', '2026-03-19', 0, 0, '178a410d-07e7-4eba-9b14-cf25ee99968c',
    0, '', 'Create Podcast', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7fa374e3-c2ac-4207-a4a4-d23e8b495091', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '5IEOi9nk1OdxP28iiBq-c', '2026-03-19', 0, 1, '7fa374e3-c2ac-4207-a4a4-d23e8b495091',
    0, '', 'Buy equipment', NULL,
    '1STy5KGrL-sih-lm5jOrO', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9431c616-ac0e-4e36-a1fa-4fa52f553815', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'K2osnCtNDh7mEoiSr4WH6', '2026-03-19', 0, 2, '9431c616-ac0e-4e36-a1fa-4fa52f553815',
    0, '', 'Edit audio', NULL,
    '1STy5KGrL-sih-lm5jOrO', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '53b90693-d9cb-4db0-ac7c-4ac4ab9de3bc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'H5d8k6Qw5-oPwIVufVGjt', '2026-03-19', 0, 3, '53b90693-d9cb-4db0-ac7c-4ac4ab9de3bc',
    0, '', 'Publish episode', NULL,
    '1STy5KGrL-sih-lm5jOrO', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ab762664-6c30-465d-8664-d5f9ee8b56c8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Pi2brIqr7hoKqMg7DV6cD', '2026-03-19', 0, 4, 'ab762664-6c30-465d-8664-d5f9ee8b56c8',
    0, '', 'Choose format', NULL,
    '1STy5KGrL-sih-lm5jOrO', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '61a78818-bfda-43d8-be38-c30a4a2200bc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'eFrGiHisSXwCCbYnig7QG', '2026-02-21', 0, 0, '61a78818-bfda-43d8-be38-c30a4a2200bc',
    0, 'A', 'Create Investment Plan', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '11421f94-845b-4bb6-95de-53fbe4228b3e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'R5z6mqzGDzA1BI2hD3J9k', '2026-02-21', 0, 1, '11421f94-845b-4bb6-95de-53fbe4228b3e',
    0, '', 'Set auto-invest', NULL,
    'eFrGiHisSXwCCbYnig7QG', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '28e51e24-39f5-4237-a777-aac3b5127aaa', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'g1uDklhApN9-Fa0om6uPg', '2026-02-21', 0, 2, '28e51e24-39f5-4237-a777-aac3b5127aaa',
    0, '', 'Review quarterly', NULL,
    'eFrGiHisSXwCCbYnig7QG', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3e213ea1-85c6-4124-a49f-969874d87d12', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ju1AZrp3O29gFnpwJosea', '2026-02-21', 0, 3, '3e213ea1-85c6-4124-a49f-969874d87d12',
    0, '', 'Research options', NULL,
    'eFrGiHisSXwCCbYnig7QG', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9495d6fc-bf03-4a64-b9ac-5d791c12699b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'cvbODcu-YR2aEOU4dpr28', '2026-02-21', 0, 4, '9495d6fc-bf03-4a64-b9ac-5d791c12699b',
    0, '', 'Define risk level', NULL,
    'eFrGiHisSXwCCbYnig7QG', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ea0dc99f-2540-4bf3-bb35-34c6f53f6c11', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'knoVWb5RZFqse4vlhh5B9', '2026-02-21', 0, 5, 'ea0dc99f-2540-4bf3-bb35-34c6f53f6c11',
    0, '', 'Allocate funds', NULL,
    'eFrGiHisSXwCCbYnig7QG', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dc85d791-a7c4-4f22-8438-3c6fbf0aa4bb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'S2X_bJLblGssuzqf4NDeJ', '2026-04-13', 0, 0, 'dc85d791-a7c4-4f22-8438-3c6fbf0aa4bb',
    0, 'B', 'Start Personal Budget', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '078d5b05-f65d-4703-b455-e69fc07b39ec', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'DWPqkRYAY7rxg4e18JUsf', '2026-04-13', 0, 1, '078d5b05-f65d-4703-b455-e69fc07b39ec',
    0, '', 'Set monthly limits', NULL,
    'S2X_bJLblGssuzqf4NDeJ', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0de48909-2ac5-44da-a36e-a9a6edf1fce0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'NH6N9c9LJ9Zf77I6PAR9x', '2026-04-13', 0, 2, '0de48909-2ac5-44da-a36e-a9a6edf1fce0',
    0, '', 'Track expenses', NULL,
    'S2X_bJLblGssuzqf4NDeJ', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd25db546-3e1f-4fd3-9563-63dbb889d878', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'STFkb1oChxjpIGmX2LTOo', '2026-04-13', 0, 3, 'd25db546-3e1f-4fd3-9563-63dbb889d878',
    0, '', 'Automate savings', NULL,
    'S2X_bJLblGssuzqf4NDeJ', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '70f9b3e1-12ec-41a3-9d50-c3e63b4b850a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wzhva3q9AbHn68qH9RVKo', '2026-04-13', 0, 4, '70f9b3e1-12ec-41a3-9d50-c3e63b4b850a',
    0, '', 'Review weekly', NULL,
    'S2X_bJLblGssuzqf4NDeJ', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b8e1a68d-1566-48da-9740-5d7f97f403cb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'xjINtJF9IO4Lui0K9efSY', '2026-02-27', 0, 0, 'b8e1a68d-1566-48da-9740-5d7f97f403cb',
    0, 'C', 'Audit Security', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'df234f82-4cd5-46f0-a6e2-206976406e7a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'dGjAE6wPwW4AULM4Q5Ryr', '2026-02-27', 0, 1, 'df234f82-4cd5-46f0-a6e2-206976406e7a',
    0, '', 'Update policies', NULL,
    'xjINtJF9IO4Lui0K9efSY', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '59aa18ba-8e7c-42f9-9831-7693012841bd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'vywN7KkFY_LxoiEt1_gRv', '2026-02-27', 0, 2, '59aa18ba-8e7c-42f9-9831-7693012841bd',
    0, '', 'Check permissions', NULL,
    'xjINtJF9IO4Lui0K9efSY', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7683c58f-26bd-4399-890b-f9b066222734', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7rA16wzqdehsYnDco5yXI', '2026-02-27', 0, 3, '7683c58f-26bd-4399-890b-f9b066222734',
    0, '', 'Document findings', NULL,
    'xjINtJF9IO4Lui0K9efSY', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a23dd6bd-4cab-491e-9fa4-7dd4131478f8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'JZKnq1E7ad6SMb4bUafe4', '2026-02-27', 0, 4, 'a23dd6bd-4cab-491e-9fa4-7dd4131478f8',
    0, '', 'Run vulnerability scan', NULL,
    'xjINtJF9IO4Lui0K9efSY', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd70ff80a-fb26-4947-9329-63b092819d61', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jr4mmuqrL2GIkvJ3YY-yg', '2026-03-08', 0, 0, 'd70ff80a-fb26-4947-9329-63b092819d61',
    0, 'B', 'Implement New Feature', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e53daf90-c136-4964-ad62-a5ff523cbdca', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Y4635t527HibaaHWWC6VB', '2026-03-08', 0, 1, 'e53daf90-c136-4964-ad62-a5ff523cbdca',
    0, '', 'QA review', NULL,
    'jr4mmuqrL2GIkvJ3YY-yg', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e6745c56-2e40-4876-9348-e23fa4f65400', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'O8xehfoT12CKHNpjGoy-u', '2026-03-08', 0, 2, 'e6745c56-2e40-4876-9348-e23fa4f65400',
    0, '', 'Break into tasks', NULL,
    'jr4mmuqrL2GIkvJ3YY-yg', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'beb9361a-6d4a-43bd-9824-dcb4e64773bd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '76_eDe_I5fquA88ccrmEb', '2026-03-08', 0, 3, 'beb9361a-6d4a-43bd-9824-dcb4e64773bd',
    0, '', 'Implement core logic', NULL,
    'jr4mmuqrL2GIkvJ3YY-yg', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '835732e7-10ca-45b4-b397-22cc20427f93', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9ClC2TQ16aq7G4_Hac-IT', '2026-03-08', 0, 4, '835732e7-10ca-45b4-b397-22cc20427f93',
    0, '', 'Add tests', NULL,
    'jr4mmuqrL2GIkvJ3YY-yg', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4be6caa4-61cb-407e-97a7-7d2f3826ab3d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'q86JI_kXviKpO90LOM71X', '2026-03-08', 0, 5, '4be6caa4-61cb-407e-97a7-7d2f3826ab3d',
    0, '', 'Release', NULL,
    'jr4mmuqrL2GIkvJ3YY-yg', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5ec48194-6893-4e64-a648-46651e0e5ac4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'fEj94q8YTdecagxqTIMab', '2026-02-16', 0, 0, '5ec48194-6893-4e64-a648-46651e0e5ac4',
    0, 'B', 'Launch Newsletter', NULL,
    NULL, 0, NULL, NULL, NULL,
    1771228800000, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd1b53679-ff10-4001-ab04-5c4103457197', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '3LY4C7PXnpUda8cLyB1XR', '2026-02-16', 0, 1, 'd1b53679-ff10-4001-ab04-5c4103457197',
    0, '', 'Draft template', NULL,
    'fEj94q8YTdecagxqTIMab', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1ac70921-ca6e-479b-8240-5abf2cf4cad8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'QYmVzUl9s1V4M12uXnXd_', '2026-02-16', 0, 2, '1ac70921-ca6e-479b-8240-5abf2cf4cad8',
    0, '', 'Choose platform', NULL,
    'fEj94q8YTdecagxqTIMab', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '358b9690-27fe-44de-bebc-2f1ab0cca265', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'nmMwENNnaH_-NAgPOBLID', '2026-02-16', 0, 3, '358b9690-27fe-44de-bebc-2f1ab0cca265',
    0, '', 'Schedule send', NULL,
    'fEj94q8YTdecagxqTIMab', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '21afb2ab-522e-40d8-b275-991333ee6ddb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'JBkIGygc_qE1xcYmS6Pz9', '2026-02-16', 0, 4, '21afb2ab-522e-40d8-b275-991333ee6ddb',
    0, '', 'Write first issue', NULL,
    'fEj94q8YTdecagxqTIMab', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dbb0bd3c-0e9a-453e-97bd-d2fc94443197', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964978, 1773109964978
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'gKW8qpAgSJ7f8M1hBMa6U', '2026-02-16', 0, 5, 'dbb0bd3c-0e9a-453e-97bd-d2fc94443197',
    0, '', 'Review analytics', NULL,
    'fEj94q8YTdecagxqTIMab', 0, NULL, NULL, NULL,
    NULL, 1773109964978, 1773109964978
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cc9e864b-980b-4c7a-ac3b-9034234cee95', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'CCZpxovKYS2Io-8a2-1fD', '2026-03-03', 0, 0, 'cc9e864b-980b-4c7a-ac3b-9034234cee95',
    0, 'A', 'Improve Onboarding', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'aca5ca04-9804-419a-8867-47f8a76f2e74', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'uiBG3bOwoGlkFeOkRORRB', '2026-03-03', 0, 1, 'aca5ca04-9804-419a-8867-47f8a76f2e74',
    0, '', 'Collect feedback', NULL,
    'CCZpxovKYS2Io-8a2-1fD', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c8646275-eb05-4908-80f6-3cce66115706', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'duwLKWrGVQe9peiFPfkkx', '2026-03-03', 0, 2, 'c8646275-eb05-4908-80f6-3cce66115706',
    0, '', 'Update checklist', NULL,
    'CCZpxovKYS2Io-8a2-1fD', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bb90440b-14d7-4c3b-8a93-a7aa344abf7d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-dxex3MR6JxfHiCSDMOgf', '2026-03-03', 0, 3, 'bb90440b-14d7-4c3b-8a93-a7aa344abf7d',
    0, '', 'Record walkthrough', NULL,
    'CCZpxovKYS2Io-8a2-1fD', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8863c614-fbc4-43bf-b7a0-d861b517c424', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Ga8XNR5iuKzF5aV7dhSfM', '2026-03-03', 0, 4, '8863c614-fbc4-43bf-b7a0-d861b517c424',
    0, '', 'Map current steps', NULL,
    'CCZpxovKYS2Io-8a2-1fD', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'becdee19-d9ea-42f0-8cb1-ade49536e318', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'mGDlwY4I3y4pF6M1dh4ei', '2026-02-12', 0, 0, 'becdee19-d9ea-42f0-8cb1-ade49536e318',
    0, 'B', 'Launch Marketing Campaign', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769328000000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '604d47ed-706f-4595-8fc2-4dc7dfd60ad4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7Cj-qIdthaA5Ds3paB9TO', '2026-02-12', 0, 1, '604d47ed-706f-4595-8fc2-4dc7dfd60ad4',
    0, '', 'Set budget', NULL,
    'mGDlwY4I3y4pF6M1dh4ei', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4ad38ff8-4906-41a2-9df8-400d8b6920cb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'XXNDLAi36PfKU0jQtxlJ0', '2026-02-12', 0, 2, '4ad38ff8-4906-41a2-9df8-400d8b6920cb',
    0, '', 'Draft messaging', NULL,
    'mGDlwY4I3y4pF6M1dh4ei', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b10d9f28-8e94-4d79-9a00-987784130fd7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'eo8DEL0GD4ykHXzyg4U5J', '2026-02-12', 0, 3, 'b10d9f28-8e94-4d79-9a00-987784130fd7',
    0, '', 'Review metrics', NULL,
    'mGDlwY4I3y4pF6M1dh4ei', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1a980865-edce-4ea7-9400-535d198a4b13', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'h_TQJFjMwc1PdsNXvoW5P', '2026-02-12', 0, 4, '1a980865-edce-4ea7-9400-535d198a4b13',
    0, '', 'Schedule channels', NULL,
    'mGDlwY4I3y4pF6M1dh4ei', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '13adc81e-cce3-42f0-a3b5-226ef81e5c55', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'L3rlspB9msFkE01jQFDxI', '2026-02-12', 0, 5, '13adc81e-cce3-42f0-a3b5-226ef81e5c55',
    0, '', 'Define target audience', NULL,
    'mGDlwY4I3y4pF6M1dh4ei', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f2195979-1fe1-44b7-834d-17f8bc536f00', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'QoZnn9e4Kqa281DzxhOIs', '2026-04-05', 0, 0, 'f2195979-1fe1-44b7-834d-17f8bc536f00',
    0, '', 'Create Training Program', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766908800000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e64ff1f2-082f-4291-b629-dda708d294cf', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'A6GNP1GmklM-INIP9Knlq', '2026-04-05', 0, 1, 'e64ff1f2-082f-4291-b629-dda708d294cf',
    0, '', 'Build curriculum', NULL,
    'QoZnn9e4Kqa281DzxhOIs', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '36ce066a-3df0-4ff5-8a26-6183d07e9e50', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'n51FEpHeHRkuiWnJ8vU4F', '2026-04-05', 0, 2, '36ce066a-3df0-4ff5-8a26-6183d07e9e50',
    0, '', 'Gather feedback', NULL,
    'QoZnn9e4Kqa281DzxhOIs', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '65e3ccd9-b52d-4edf-aea8-0c5554242c93', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'h-KDMjhypNfNpGI3ipsYq', '2026-04-05', 0, 3, '65e3ccd9-b52d-4edf-aea8-0c5554242c93',
    0, '', 'Create materials', NULL,
    'QoZnn9e4Kqa281DzxhOIs', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '27669850-bf21-46f2-b5f7-24b9d17fc100', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'dG3qUQEyDWVcff4NKpTTp', '2026-04-05', 0, 4, '27669850-bf21-46f2-b5f7-24b9d17fc100',
    0, '', 'Schedule sessions', NULL,
    'QoZnn9e4Kqa281DzxhOIs', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0d50ac14-aa8c-4e0d-8658-c3c47642e4a6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'yWWN5P1wei7VG0nHtL8O7', '2026-04-05', 0, 5, '0d50ac14-aa8c-4e0d-8658-c3c47642e4a6',
    0, '', 'Define objectives', NULL,
    'QoZnn9e4Kqa281DzxhOIs', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cb8a0c2d-d6e3-44b7-9a14-529b28dea728', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'iyxgiG8uTtu9VyYAv9rIX', '2026-04-07', 0, 0, 'cb8a0c2d-d6e3-44b7-9a14-529b28dea728',
    0, 'B', 'Upgrade Home Network', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766822400000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8d33090a-e1b4-4a7c-8ad9-37238a3ac9a8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'aLt_0azlrRe4Tj_aKnINP', '2026-04-07', 0, 1, '8d33090a-e1b4-4a7c-8ad9-37238a3ac9a8',
    0, '', 'Audit devices', NULL,
    'iyxgiG8uTtu9VyYAv9rIX', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4989daa1-8942-49bf-92ec-dc1aa73c66b9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'w3lR2h2aPtP-uijV_AuwS', '2026-04-07', 0, 2, '4989daa1-8942-49bf-92ec-dc1aa73c66b9',
    0, '', 'Optimize placement', NULL,
    'iyxgiG8uTtu9VyYAv9rIX', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f6e36b48-d98d-4a5c-8f35-cff00cc83523', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'CxTkOfYh-q2RQhi98aODO', '2026-04-07', 0, 3, 'f6e36b48-d98d-4a5c-8f35-cff00cc83523',
    0, '', 'Choose router', NULL,
    'iyxgiG8uTtu9VyYAv9rIX', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '904d644a-43e0-489b-bec5-68f257e44293', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'uQa9T_20TJFMt30Ry4EJB', '2026-04-07', 0, 4, '904d644a-43e0-489b-bec5-68f257e44293',
    0, '', 'Test coverage', NULL,
    'iyxgiG8uTtu9VyYAv9rIX', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4b8d53dd-6dc9-4859-a99c-70684691cd06', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '8Qo1h3ybTpvaSqWlaBS6g', '2026-04-07', 0, 5, '4b8d53dd-6dc9-4859-a99c-70684691cd06',
    0, '', 'Update firmware', NULL,
    'iyxgiG8uTtu9VyYAv9rIX', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c44e5c08-ef65-4c94-832f-d9a73acce709', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7t3VH7xOgJFx0toQZsrk6', '2026-04-14', 0, 0, 'c44e5c08-ef65-4c94-832f-d9a73acce709',
    0, 'A', 'Prepare Product Launch', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '67e74ada-c2d8-45df-b250-fc1f682f9b53', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'J6uWjHRot_R98kxETymwh', '2026-04-14', 0, 1, '67e74ada-c2d8-45df-b250-fc1f682f9b53',
    0, '', 'Update documentation', NULL,
    '7t3VH7xOgJFx0toQZsrk6', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '81f4bd35-eabc-4cbb-9360-a258d4ed05e4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-JXW61G4E2CIcP1XFdcGK', '2026-04-14', 0, 2, '81f4bd35-eabc-4cbb-9360-a258d4ed05e4',
    0, '', 'Go-live checklist', NULL,
    '7t3VH7xOgJFx0toQZsrk6', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3ef0dd64-1811-40d2-943b-01f23f556c60', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'KDI3tsDLRrtePolAGXQgk', '2026-04-14', 0, 3, '3ef0dd64-1811-40d2-943b-01f23f556c60',
    0, '', 'Finalize features', NULL,
    '7t3VH7xOgJFx0toQZsrk6', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cbd7d97b-2871-4314-b85a-dc2d005f9729', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'CMK92GecuIV7WrEct-4vs', '2026-04-14', 0, 4, 'cbd7d97b-2871-4314-b85a-dc2d005f9729',
    0, '', 'Train support', NULL,
    '7t3VH7xOgJFx0toQZsrk6', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '595ed5e6-fd7d-4e72-a9f6-fa8a5c15675e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'iX7IbR3SrmNn0cuGl0XfR', '2026-03-12', 0, 0, '595ed5e6-fd7d-4e72-a9f6-fa8a5c15675e',
    0, '', 'Hire New Engineer', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9beb29e0-e561-4f0c-8fac-1d12f0ef7bf0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_4Lv74PBuaxD_OL9OM91Q', '2026-03-12', 0, 1, '9beb29e0-e561-4f0c-8fac-1d12f0ef7bf0',
    0, '', 'Screen resumes', NULL,
    'iX7IbR3SrmNn0cuGl0XfR', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0a20a7f8-f1cd-43e9-b805-8e571b246176', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '8cZMUO04MY9bWSEEmofzU', '2026-03-12', 0, 2, '0a20a7f8-f1cd-43e9-b805-8e571b246176',
    0, '', 'Collect feedback', NULL,
    'iX7IbR3SrmNn0cuGl0XfR', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4ea1baa5-616f-4825-8d19-a03ab3de54e8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'G9dkmQ-zcgotuQKS2NSSI', '2026-03-12', 0, 3, '4ea1baa5-616f-4825-8d19-a03ab3de54e8',
    0, '', 'Post listings', NULL,
    'iX7IbR3SrmNn0cuGl0XfR', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '52d4a060-861a-44c5-ab65-9b80e2481861', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'EDLn2cU8KSoAI8EdiPoGR', '2026-03-12', 0, 4, '52d4a060-861a-44c5-ab65-9b80e2481861',
    0, '', 'Schedule interviews', NULL,
    'iX7IbR3SrmNn0cuGl0XfR', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2603cf87-e9bf-464a-a702-00ae05307e97', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'IVzrZeaHiIg0U0LIHu-NQ', '2026-03-12', 0, 5, '2603cf87-e9bf-464a-a702-00ae05307e97',
    0, '', 'Write job description', NULL,
    'iX7IbR3SrmNn0cuGl0XfR', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '41e7d893-3e8d-444a-97d4-751ef39b5fb7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'bjn5myr9NsfQdg5YLMba1', '2026-02-22', 0, 0, '41e7d893-3e8d-444a-97d4-751ef39b5fb7',
    0, 'A', 'Clean and Organize Home', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766995200000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8f67d101-b23e-4eb2-80db-c5e850f1c63f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ZOl8bMcItO7sbharGluLS', '2026-02-22', 0, 1, '8f67d101-b23e-4eb2-80db-c5e850f1c63f',
    0, '', 'Deep clean', NULL,
    'bjn5myr9NsfQdg5YLMba1', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0706ac95-d414-46dc-b1e7-7c35fafaddc1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'My9Tw3fa64jlWHe8BG6fR', '2026-02-22', 0, 2, '0706ac95-d414-46dc-b1e7-7c35fafaddc1',
    0, '', 'Donate items', NULL,
    'bjn5myr9NsfQdg5YLMba1', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1005f9c2-ce12-4c77-b75c-fef6fbd862bd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jVSqx5suP82gvEbvt1DwT', '2026-02-22', 0, 3, '1005f9c2-ce12-4c77-b75c-fef6fbd862bd',
    0, '', 'Set maintenance routine', NULL,
    'bjn5myr9NsfQdg5YLMba1', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '008187a8-c2e4-41e4-ba29-0e49863881df', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'a_C1-R70KcSeKUzb09HO0', '2026-02-22', 0, 4, '008187a8-c2e4-41e4-ba29-0e49863881df',
    0, '', 'Declutter rooms', NULL,
    'bjn5myr9NsfQdg5YLMba1', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dade620e-d7b9-4aa6-b234-65cf74d2c38c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Sw5KKcXzak4gjA93lwoJN', '2026-02-20', 0, 0, 'dade620e-d7b9-4aa6-b234-65cf74d2c38c',
    0, 'A', 'Launch Online Course', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772784000000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '36bd4c0a-60b7-46cb-a398-7fb0b2190612', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'OsxqpXbS_77Bi9x_tP65m', '2026-02-20', 0, 1, '36bd4c0a-60b7-46cb-a398-7fb0b2190612',
    0, '', 'Create lesson plan', NULL,
    'Sw5KKcXzak4gjA93lwoJN', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '819cd32c-221e-4a4f-9b35-bfd9ded4bcbc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jG5yqkTOw8dspbWjUxZ3Y', '2026-02-20', 0, 2, '819cd32c-221e-4a4f-9b35-bfd9ded4bcbc',
    0, '', 'Market course', NULL,
    'Sw5KKcXzak4gjA93lwoJN', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bf62a6f7-49e8-43f4-9ff5-2d3cb265b943', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'pPdViUlB_FL8bT2EZ6Yds', '2026-02-20', 0, 3, 'bf62a6f7-49e8-43f4-9ff5-2d3cb265b943',
    0, '', 'Build exercises', NULL,
    'Sw5KKcXzak4gjA93lwoJN', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '221216b0-11b0-4292-8d51-60949ba85e81', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'G_Vq8hD4mSp1devo-tWNx', '2026-02-20', 0, 4, '221216b0-11b0-4292-8d51-60949ba85e81',
    0, '', 'Record videos', NULL,
    'Sw5KKcXzak4gjA93lwoJN', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '79d4e80c-6899-4472-aa67-f1905a890939', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Xr4uao8LFu9zzDDGOAicf', '2026-02-20', 0, 5, '79d4e80c-6899-4472-aa67-f1905a890939',
    0, '', 'Define outcomes', NULL,
    'Sw5KKcXzak4gjA93lwoJN', 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7ccf52b4-04ac-4661-ace2-de68d3e358e5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '6NPorw1BzTeA65bvwk3wc', '2026-02-11', 0, 5, '7ccf52b4-04ac-4661-ace2-de68d3e358e5',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768550400000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '68f5cde6-dbd3-452a-bc7a-447b7d00f083', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7xsygiMmUPsGsjTrecC9M', '2026-02-12', 0, 6, '68f5cde6-dbd3-452a-bc7a-447b7d00f083',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766822400000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a3014344-2063-4612-adb6-9988e282c772', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Ih49k69R-v7MnSLuv7vsA', '2026-02-12', 0, 7, 'a3014344-2063-4612-adb6-9988e282c772',
    0, 'B', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    1771228800000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5fb0cef6-de59-44db-849c-093d8805f83d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9XIHRid0sZdzzwTOGL1EF', '2026-02-12', 0, 8, '5fb0cef6-de59-44db-849c-093d8805f83d',
    0, '', 'Buy groceries', NULL,
    NULL, 0, NULL, NULL, NULL,
    1774854000000, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '15c4d2cf-5deb-4b4f-a172-127460186af3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964979, 1773109964979
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'nON74oOb5Xx_T-lNpKwis', '2026-02-12', 0, 9, '15c4d2cf-5deb-4b4f-a172-127460186af3',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964979, 1773109964979
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '408907e6-a09a-4186-8882-782cc5bccdc0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '3rhuapNPvyrfVje2dcxCJ', '2026-02-12', 1, 0, '408907e6-a09a-4186-8882-782cc5bccdc0',
    0, '', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e81f40f0-cf2d-49ec-b46d-e64cf722995c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'uKXQHovtUnGTaXI1Hz5kN', '2026-02-14', 0, 5, 'e81f40f0-cf2d-49ec-b46d-e64cf722995c',
    1, '', 'Mail package to mom', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9f9218af-b90b-41b7-8518-9042ad34ae2a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'K1IQPIjYJHwaB8YwK1uUx', '2026-02-14', 0, 6, '9f9218af-b90b-41b7-8518-9042ad34ae2a',
    0, '', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f6f60986-0f19-427a-a642-043f1f5d9bbe', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'h6oyYNgLMXg5ovShxxp0c', '2026-02-14', 0, 7, 'f6f60986-0f19-427a-a642-043f1f5d9bbe',
    0, '', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '172d22bf-0b6b-4a55-97ae-041019b2e3e6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'nuQ-LkfUP2y-ZTNFp8DkO', '2026-02-14', 0, 8, '172d22bf-0b6b-4a55-97ae-041019b2e3e6',
    0, 'B', 'Setup CI/CD pipeline', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bc540181-c3c4-4985-a706-6d5cd2c6594f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'cSZZS1k6XZR3NjVzg1ojY', '2026-02-14', 0, 9, 'bc540181-c3c4-4985-a706-6d5cd2c6594f',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1765958400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '729bc678-0bd7-4f4c-9214-0e4e33e72ec9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Xmf7ZYZMA-lG3HINJTX6R', '2026-02-14', 1, 0, '729bc678-0bd7-4f4c-9214-0e4e33e72ec9',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1769932800000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b7fe1792-cac4-4f28-8370-e01bd64b9788', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '5lXS6mQX7o1VZqHv9yehR', '2026-02-14', 1, 1, 'b7fe1792-cac4-4f28-8370-e01bd64b9788',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1775372400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ccfa2ac0-ea3d-4aba-8137-6e9a56ddd731', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'mccwNXVT-QSCFdO7Shs1e', '2026-02-14', 1, 2, 'ccfa2ac0-ea3d-4aba-8137-6e9a56ddd731',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4807b35b-542c-4a49-b998-99ada619960f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'xKuY_GYyM6lnz5Kv8H-78', '2026-02-14', 1, 3, '4807b35b-542c-4a49-b998-99ada619960f',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770883200000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '80fb4c29-8f3c-4bd5-812d-7fde6d3c00b6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'kwOUcyy7LHbW_NFs7GQZx', '2026-02-14', 1, 4, '80fb4c29-8f3c-4bd5-812d-7fde6d3c00b6',
    1, 'A', 'Write blog post on React', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '72e43648-a40b-4a15-bb45-f2f56a025b82', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'MEFKrkw3GfwKM-Q7hx-l3', '2026-02-15', 0, 6, '72e43648-a40b-4a15-bb45-f2f56a025b82',
    0, '', 'Set up 401k contribution', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775890800000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3f4f7e95-dd3f-4413-a109-b3cc9b782364', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '6U5EmxM305gqmcP057Yom', '2026-02-15', 0, 7, '3f4f7e95-dd3f-4413-a109-b3cc9b782364',
    0, '', 'Prepare mentoring materials', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '86da24b1-0491-49ba-b3f9-77229b3de15c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'k4VrcqtVlmcde4GCDjWRp', '2026-02-15', 0, 8, '86da24b1-0491-49ba-b3f9-77229b3de15c',
    0, 'B', 'Draft RFC document', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f5f956a3-c25d-41db-9a83-72f23ef70723', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lfhokAsza6YjDYsOMl3WV', '2026-02-15', 0, 9, 'f5f956a3-c25d-41db-9a83-72f23ef70723',
    0, '', 'Change HVAC filter', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1e559051-09fd-40be-b507-8e17d463c45e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'i-XnfTnwodWA3Y-5KLKaM', '2026-02-15', 1, 0, '1e559051-09fd-40be-b507-8e17d463c45e',
    1, 'B', 'Archive old branches', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776495600000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0a47970a-e21a-43fc-a608-30785a264050', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tYK1oQo2x8a-RbAI_6Dz2', '2026-02-15', 1, 1, '0a47970a-e21a-43fc-a608-30785a264050',
    0, '', 'Review credit card statements', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ae8a6d89-9270-49e0-8405-620af82deb29', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'uT_uZd0Cr0f6GdadL1DUi', '2026-02-16', 0, 6, 'ae8a6d89-9270-49e0-8405-620af82deb29',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '35c6426c-a979-425e-a76f-b20908be70ab', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-0y0CebVYHyHZObnPrHUP', '2026-02-16', 0, 7, '35c6426c-a979-425e-a76f-b20908be70ab',
    0, '', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fbe38729-7069-43b1-ac7d-7cdfeda055e3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '75BcpCxbZyq_lIKPTm1at', '2026-02-16', 0, 8, 'fbe38729-7069-43b1-ac7d-7cdfeda055e3',
    0, 'B', 'Renew car registration', NULL,
    NULL, 0, NULL, NULL, NULL,
    1765958400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'db391abe-9b01-423f-acfe-8b2fe61f6649', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TjO_tlSDiIxai3TrhgnJ9', '2026-02-16', 0, 9, 'db391abe-9b01-423f-acfe-8b2fe61f6649',
    0, '', 'Fix API rate limiting', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766044800000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ac811668-877d-439d-a707-3ebaec02e897', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7gmnzkr4YPODCXa1gGKmt', '2026-02-16', 1, 0, 'ac811668-877d-439d-a707-3ebaec02e897',
    0, 'C', 'Pay utility bills', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769328000000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '87d4328b-7793-42b9-8689-c95426b76b08', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '00yUZbdOn0fXfzEm13BjW', '2026-02-16', 1, 1, '87d4328b-7793-42b9-8689-c95426b76b08',
    0, 'A', 'Update API documentation', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3d29963a-b817-4e2d-aaef-21a24c544ff5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7NOkweWvMJ0KNflsX-UCz', '2026-02-16', 1, 2, '3d29963a-b817-4e2d-aaef-21a24c544ff5',
    0, 'A', 'Buy anniversary gift', NULL,
    NULL, 0, NULL, NULL, NULL,
    1771056000000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5587416c-85e9-45e4-9616-24bd1eeb994d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-o-JYGSwEsXZAdcswyK4r', '2026-02-19', 0, 6, '5587416c-85e9-45e4-9616-24bd1eeb994d',
    0, '', 'Create budget spreadsheet', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768291200000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a0ac747b-a085-43da-937f-941e608da79d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'qCGzf6S5JPgAZ9nRaLCvy', '2026-02-19', 0, 7, 'a0ac747b-a085-43da-937f-941e608da79d',
    0, 'A', 'Review PR #2348', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773558000000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cad7d030-2e91-4d96-86e1-011b08c0f115', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lFqMI5bUiyMltsyTdOPzX', '2026-02-19', 0, 8, 'cad7d030-2e91-4d96-86e1-011b08c0f115',
    1, 'C', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2d4951b2-9d05-4081-bb10-7e7ec02077ac', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'N3krK5aO2qcfTQs1F5JNi', '2026-02-19', 0, 9, '2d4951b2-9d05-4081-bb10-7e7ec02077ac',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770192000000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7258ea8e-8cb5-469f-8980-760a97b0a92a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'X-JYxnuDRw9_NnitLgft-', '2026-02-19', 1, 0, '7258ea8e-8cb5-469f-8980-760a97b0a92a',
    0, 'B', 'Debug performance issue', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '764316be-6616-431b-a3c6-3b9b2a2271fe', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'E8Zbrq1rvW4apn-0_VnfQ', '2026-02-19', 1, 1, '764316be-6616-431b-a3c6-3b9b2a2271fe',
    0, '', 'Schedule mentee check-in', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767600000000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5ea8619e-606c-40a2-8966-7d3c11bada97', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wBh2HiSnpxIWzifT0OX8e', '2026-02-19', 1, 2, '5ea8619e-606c-40a2-8966-7d3c11bada97',
    0, 'C', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766390400000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c9e9abdf-6b96-4221-bcb1-a778f16063d5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TFbdBF5ZD_PL1O3aDZm1w', '2026-02-19', 1, 3, 'c9e9abdf-6b96-4221-bcb1-a778f16063d5',
    0, '', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769587200000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd29a8e2c-d92d-4074-85a1-cb67f37dbe5e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'OENNEv4vm8aeMfSPjU60V', '2026-02-19', 1, 4, 'd29a8e2c-d92d-4074-85a1-cb67f37dbe5e',
    0, 'C', 'Buy groceries', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772265600000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '907466f6-3a1f-4a86-ba0b-417d9abafe76', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964980, 1773109964980
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '6sX1XrWC3Z7ZPLFKJy87F', '2026-02-21', 0, 6, '907466f6-3a1f-4a86-ba0b-417d9abafe76',
    0, '', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775026800000, 1773109964980, 1773109964980
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e8036bb2-fb86-4268-b4ce-82ab8bf5f7ed', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lIJzCbMUKHJgbr4cO7cHf', '2026-02-21', 0, 7, 'e8036bb2-fb86-4268-b4ce-82ab8bf5f7ed',
    0, 'C', 'Update volunteer hours log', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772438400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0e233b8d-1585-4a60-82b4-1ce29e6db8ca', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-Dbl_ZWFDGtK1I8QMh0Yq', '2026-02-21', 0, 8, '0e233b8d-1585-4a60-82b4-1ce29e6db8ca',
    0, '', 'Get spare keys made', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769846400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f671d150-60a3-4910-8a07-f28b4d26c7e8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '1RzxM2W4ap2IMejMDT2CP', '2026-02-22', 0, 5, 'f671d150-60a3-4910-8a07-f28b4d26c7e8',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dc103135-10be-4f92-9fc2-f87e0b3f06c4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'p13RRq3KVoYHPm0JTYqhA', '2026-02-22', 0, 6, 'dc103135-10be-4f92-9fc2-f87e0b3f06c4',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776322800000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '03940500-876d-4adc-8cda-1380a1277b00', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ZVrDKRrXtlAU88NnC9lLo', '2026-02-22', 0, 7, '03940500-876d-4adc-8cda-1380a1277b00',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766131200000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '37907a46-45a6-4c6c-9b50-3ed7e247df43', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'urtATtXwCF8IkuZLAli4M', '2026-02-22', 0, 8, '37907a46-45a6-4c6c-9b50-3ed7e247df43',
    1, 'B', 'Fix login redirect bug', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0b9bf5d2-b0c7-49ef-80db-94b2a10dd735', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-LyHWdLjHHMISFodqPidx', '2026-02-22', 0, 9, '0b9bf5d2-b0c7-49ef-80db-94b2a10dd735',
    1, '', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2db87bc8-91f0-4807-9671-1157b4b84a58', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'u2qrCHbbx-J6oGH4tZtiF', '2026-02-22', 1, 0, '2db87bc8-91f0-4807-9671-1157b4b84a58',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1772265600000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ff364e0f-bfa5-4f63-9d06-590338fe4459', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'QZhm2FqMmkQR0yvQjvPlN', '2026-02-22', 1, 1, 'ff364e0f-bfa5-4f63-9d06-590338fe4459',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '82f24ae6-ea1c-49c2-a48a-8da40e0af09e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TmNEA__bT-CpDxj1cwnhT', '2026-02-22', 1, 2, '82f24ae6-ea1c-49c2-a48a-8da40e0af09e',
    1, 'A', 'Values alignment check', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '22399ec9-bd21-485b-a24f-ff4dc09db680', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'EWnVHaEisLQvHb2VrjBlF', '2026-02-22', 1, 3, '22399ec9-bd21-485b-a24f-ff4dc09db680',
    1, '', 'Fix session timeout issue', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3da10c60-8f4f-48c2-b0c0-b8e031d6adf8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'cMQ2A0pqolVb3CEm9Grxj', '2026-02-22', 1, 4, '3da10c60-8f4f-48c2-b0c0-b8e031d6adf8',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cba381b7-6d96-4419-a56a-d8cdee7f4a1d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'w3czziCT6QrKCxujM7Hdv', '2026-02-27', 0, 5, 'cba381b7-6d96-4419-a56a-d8cdee7f4a1d',
    0, 'A', 'Fill up gas tank', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '54f1d91a-e4e6-49ca-8208-ad14344cdfe4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'CUMTAjjw-hNpIiOcMr65f', '2026-02-27', 0, 6, '54f1d91a-e4e6-49ca-8208-ad14344cdfe4',
    1, '', 'Plan team offsite', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '038ce2ed-e5bc-42ce-85db-c0ec2fe2a3da', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'LQ9Y_sjoUqgbmjkTgo4Lz', '2026-02-27', 0, 7, '038ce2ed-e5bc-42ce-85db-c0ec2fe2a3da',
    0, '', 'Review PR #2342', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769328000000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0e65bf43-c22c-4ee5-a282-3a916af57bf5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'KQxymr70bkddIIwSjs1WB', '2026-02-27', 0, 8, '0e65bf43-c22c-4ee5-a282-3a916af57bf5',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2036f22f-058a-46dc-ad56-ca38445f6664', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'XyKqxFR8ja4MuQMP_KnXP', '2026-03-02', 0, 6, '2036f22f-058a-46dc-ad56-ca38445f6664',
    0, '', 'Get car oil changed', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767859200000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '063e672c-a69f-4325-9d1f-16c8fa9910a9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '3Oyem-39J0Okb3Fp_Xxp0', '2026-03-02', 0, 7, '063e672c-a69f-4325-9d1f-16c8fa9910a9',
    0, '', 'Update npm dependencies', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767427200000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '899cb55b-5162-4895-be62-9f9819b3d006', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '0P0ubg3TF5YSNUKODHLaW', '2026-03-02', 0, 8, '899cb55b-5162-4895-be62-9f9819b3d006',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770451200000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '25fa4dec-5a24-4763-ad6a-77db8beb4aba', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'RJ0sH0p3XauOTCgUvbgXI', '2026-03-02', 0, 9, '25fa4dec-5a24-4763-ad6a-77db8beb4aba',
    0, 'B', 'Buy office supplies', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769760000000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '81739c03-ce2a-459f-9cb3-ed5fbbcb3021', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '0NlL0CcVbFkk1yVJnREfU', '2026-03-02', 1, 0, '81739c03-ce2a-459f-9cb3-ed5fbbcb3021',
    0, '', 'Schedule mentee check-in', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775372400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4d6dcd15-2a68-4cf3-af65-66c764e3cdb2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lFI9EvocNJ0g6-a2Wl8wA', '2026-03-02', 1, 1, '4d6dcd15-2a68-4cf3-af65-66c764e3cdb2',
    0, 'C', 'Write blog post on K8s', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b1115b78-36cd-415d-adb4-188a84c34820', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'C9Euas9f4A4RugvjjYV8F', '2026-03-02', 1, 2, 'b1115b78-36cd-415d-adb4-188a84c34820',
    1, '', 'Design logo concepts', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773990000000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '597fd0e9-fdbb-4933-8f1f-e16f79679b7a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '1heCHOiM62hAuFLYiJD6f', '2026-03-03', 0, 5, '597fd0e9-fdbb-4933-8f1f-e16f79679b7a',
    0, '', 'Pick up package from locker', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772870400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0e97852f-630b-4673-ae61-205e26efd8a7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'gST5ik8mxAnRnvww6-DhZ', '2026-03-03', 0, 6, '0e97852f-630b-4673-ae61-205e26efd8a7',
    0, 'C', 'Organize conference talk', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '61cd8c6b-f4ba-4151-9ba5-afe36f427ba0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'kM0C3WqKJbIjFWtNFRJzq', '2026-03-03', 0, 7, '61cd8c6b-f4ba-4151-9ba5-afe36f427ba0',
    1, '', 'Update resume', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ed3812bb-b992-46e3-b3e5-a222e128916f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '0J5R0HJTswgkTNaafYwpO', '2026-03-03', 0, 8, 'ed3812bb-b992-46e3-b3e5-a222e128916f',
    0, 'C', 'Buy groceries', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769587200000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1ab51980-b6e8-4a0d-9c0d-8e9901be24a7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'qz0QaT3DIJqhTvq0G3v0u', '2026-03-03', 0, 9, '1ab51980-b6e8-4a0d-9c0d-8e9901be24a7',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'acfa7143-fda0-422a-8b84-dc5647296a88', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'j5h9vf2DWTI3XutDtbo8h', '2026-03-03', 1, 0, 'acfa7143-fda0-422a-8b84-dc5647296a88',
    0, 'A', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '273db51b-c15c-4c8b-843f-94cc9d1574f9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'R0q9t25ReiCR8aQi52byW', '2026-03-03', 1, 1, '273db51b-c15c-4c8b-843f-94cc9d1574f9',
    1, 'A', 'Call insurance company', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0c9a3325-3c57-4b2b-8f28-aa91c6d6f4b1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7A-61bawEtaKaswJI0dzG', '2026-03-03', 1, 2, '0c9a3325-3c57-4b2b-8f28-aa91c6d6f4b1',
    0, 'B', 'Update Python dependencies', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769932800000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '002b2c9c-5086-4001-8055-6a291fde0d17', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'o3RpoEscUnT_XssCF5Fnq', '2026-03-03', 1, 3, '002b2c9c-5086-4001-8055-6a291fde0d17',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1773471600000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '507685c3-28ef-4dfe-b5d8-8edbb5f5b29a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ZMrJ7d--t_WDSzALBRbWA', '2026-03-03', 1, 4, '507685c3-28ef-4dfe-b5d8-8edbb5f5b29a',
    0, '', 'Research new laptop', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775113200000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '34d5b00e-2001-4c84-b83b-0a77f186b3d4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ILGyLBp42Ma5QGtTypmoL', '2026-03-05', 0, 0, '34d5b00e-2001-4c84-b83b-0a77f186b3d4',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768118400000, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '98a2d094-be37-4b17-a248-3274c7872248', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964981, 1773109964981
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'i6zqJyEXuVdOzlPD-x4xD', '2026-03-05', 0, 1, '98a2d094-be37-4b17-a248-3274c7872248',
    0, '', 'Clean up email inbox', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964981, 1773109964981
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c79d2cfa-66ee-40bc-959c-f248e81621a7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_owKMGDgq0u2458EwiDAb', '2026-03-05', 0, 2, 'c79d2cfa-66ee-40bc-959c-f248e81621a7',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1769932800000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd181b0b8-1b98-4600-b1d4-eb49c69fabff', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Tou0MnlpMRyDKq8rTvyiP', '2026-03-05', 0, 3, 'd181b0b8-1b98-4600-b1d4-eb49c69fabff',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770883200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'badc6d0c-5844-4b17-80e9-cc8eea7c480f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '6DTR8kyS9UXrljffxo5Xa', '2026-03-06', 0, 6, 'badc6d0c-5844-4b17-80e9-cc8eea7c480f',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766304000000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '20bb3559-0a10-48ef-a74a-042094413b46', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'vYMsa8eT6H0RsWpncFfOt', '2026-03-06', 0, 7, '20bb3559-0a10-48ef-a74a-042094413b46',
    0, 'A', 'Schedule dentist appointment', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0d267ab2-4a4a-4c4e-979e-7f5f325e2850', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'VwEYWtNXM1uuMWHbD76g8', '2026-03-06', 0, 8, '0d267ab2-4a4a-4c4e-979e-7f5f325e2850',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ba3bf23c-dd38-4972-bf33-4bbf052767bf', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'VqKDI-0gFoHhUAV_mK9vQ', '2026-03-06', 0, 9, 'ba3bf23c-dd38-4972-bf33-4bbf052767bf',
    0, '', 'Fill up gas tank', NULL,
    NULL, 0, NULL, NULL, NULL,
    1770019200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7d655302-d89e-4cb4-b55c-ea4ce2d8cdc0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'oY0s8KB60VQ-mAtOqNUZM', '2026-03-06', 1, 0, '7d655302-d89e-4cb4-b55c-ea4ce2d8cdc0',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1773817200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c17b9575-207e-4169-86b9-7ef0ed38c709', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9h8E_92x939cAuPO8Jcj_', '2026-03-07', 0, 5, 'c17b9575-207e-4169-86b9-7ef0ed38c709',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1773644400000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9e5cf165-b2d6-4633-a121-1d4a79339cff', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'xhW_UbvMLhYSnDSt31UHN', '2026-03-07', 0, 6, '9e5cf165-b2d6-4633-a121-1d4a79339cff',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770883200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '00c25d79-eb5d-43b5-9a03-d705774ad504', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'muCGj94LnKiwrjcOrjHEk', '2026-03-07', 0, 7, '00c25d79-eb5d-43b5-9a03-d705774ad504',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '03412965-8725-4e32-8444-17022fa594cc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Rpy1ImVHtWq4LwAZ0Y9Vv', '2026-03-07', 0, 8, '03412965-8725-4e32-8444-17022fa594cc',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1773126000000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b915976c-f20c-4739-b626-770a0ef8be5b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'm6qR-if9rlLBPMejUnqju', '2026-03-07', 0, 9, 'b915976c-f20c-4739-b626-770a0ef8be5b',
    0, '', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766131200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'aeb6bdc0-837b-47f6-bd6f-8d8be13aa6da', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'nritJMc7Ol3zHqv3ItxEa', '2026-03-07', 1, 0, 'aeb6bdc0-837b-47f6-bd6f-8d8be13aa6da',
    0, '', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766736000000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '81049ec8-32aa-44a9-8b12-f989724c60b1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Lz3khWVAuo_CM0b07Jk1J', '2026-03-07', 1, 1, '81049ec8-32aa-44a9-8b12-f989724c60b1',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1767600000000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f9cae310-3b7a-488a-bed1-ff684f7fe4d3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ep5WkZtE7wPNfgKSHJhGR', '2026-03-07', 1, 2, 'f9cae310-3b7a-488a-bed1-ff684f7fe4d3',
    0, 'B', 'Send invoice to client', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775458800000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7909c8f0-5540-4595-b1a7-2658accdacab', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'J8RUvnQW092zTFHqNWAIv', '2026-03-07', 1, 3, '7909c8f0-5540-4595-b1a7-2658accdacab',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '67bee68f-2d3b-4ce2-b001-35e9234bda31', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Q4yD077bHkEhWURR4JqPd', '2026-03-08', 0, 6, '67bee68f-2d3b-4ce2-b001-35e9234bda31',
    0, '', 'Plan workshop curriculum', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '57fa1480-3501-4653-9ecf-557deff52ec3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '25i70Q84y98MwSoYcudi3', '2026-03-08', 0, 7, '57fa1480-3501-4653-9ecf-557deff52ec3',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7c00a474-072f-43b4-bf47-fc1868f40cde', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'L85jzf0LR7NRKC406JMMf', '2026-03-09', 0, 5, '7c00a474-072f-43b4-bf47-fc1868f40cde',
    0, 'B', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '014aa513-4972-4b4e-92f4-b16e99c8f7a8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'To6WzKx9G2yYxOLkdMSC-', '2026-03-09', 0, 6, '014aa513-4972-4b4e-92f4-b16e99c8f7a8',
    0, '', 'Create expense report', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '150d9ae4-2a8f-4c1e-b6f4-6ace7e46f1e1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wWNY0XFAB0qMGhvBFcnZU', '2026-03-09', 0, 7, '150d9ae4-2a8f-4c1e-b6f4-6ace7e46f1e1',
    0, '', 'Create onboarding guide', NULL,
    NULL, 0, NULL, NULL, NULL,
    1771228800000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2d1bfb21-ac0c-4fa4-9cfa-3b76fdb39779', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'O4lSHgq7MrxFYV89IQF-L', '2026-03-12', 0, 6, '2d1bfb21-ac0c-4fa4-9cfa-3b76fdb39779',
    0, '', 'Setup monitoring alerts', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775718000000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5510ba8b-f73f-4d6e-a4a8-be68d048bc25', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'xMzCLNp9iKhL9SKo26Qeo', '2026-03-12', 0, 7, '5510ba8b-f73f-4d6e-a4a8-be68d048bc25',
    0, '', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769500800000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '37b159d3-c64e-49cd-9d0f-42cd2c19fd91', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ej-x8FjkG7kbs0FaxkfvG', '2026-03-12', 0, 8, '37b159d3-c64e-49cd-9d0f-42cd2c19fd91',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9dc64de7-ff1d-4afa-8999-1eee8c35acc7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'C7tdhCT54U63YOYSu1giz', '2026-03-12', 0, 9, '9dc64de7-ff1d-4afa-8999-1eee8c35acc7',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766649600000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9129dabd-cc7a-4661-93b8-227161bf7f25', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Q6PRn2MxCiMiA7t3nJNpU', '2026-03-12', 1, 0, '9129dabd-cc7a-4661-93b8-227161bf7f25',
    1, '', 'Record tutorial video', NULL,
    NULL, 0, NULL, NULL, NULL,
    1774249200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c7d9ecee-02dd-484e-a79a-123fc70a67f7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'E9QKw0SXGhz3yYwKx5T98', '2026-03-12', 1, 1, 'c7d9ecee-02dd-484e-a79a-123fc70a67f7',
    0, '', 'Review PR #2351', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bcd354b5-5a49-474f-a774-8d35dd292d51', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'JMETVIjFFXawrJonMzROK', '2026-03-12', 1, 2, 'bcd354b5-5a49-474f-a774-8d35dd292d51',
    0, 'B', 'Review credit card statements', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767340800000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6637b25d-5d4f-448b-8311-fc493eccbc22', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'a19DvMM3D572IUFIaoCs3', '2026-03-12', 1, 3, '6637b25d-5d4f-448b-8311-fc493eccbc22',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '870c729a-ab47-45b0-8c70-d062b7772bd4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TtRTzb49IR8clg2Lgnw7s', '2026-03-12', 1, 4, '870c729a-ab47-45b0-8c70-d062b7772bd4',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1774681200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4ec9a591-b35b-4be1-ab4d-555aa514c8ed', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Sq-3Fvae-ByPDF7GZqFfJ', '2026-03-13', 0, 0, '4ec9a591-b35b-4be1-ab4d-555aa514c8ed',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768291200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fcb21492-a0e5-4c1a-922e-ac464062f44f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'AfrDrjtm-q8GVR88VeesB', '2026-03-13', 0, 1, 'fcb21492-a0e5-4c1a-922e-ac464062f44f',
    0, '', 'Cancel streaming subscription', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775545200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '186270b6-d399-4cd1-b327-e94fab01d1c6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '56YoykeJKJocCzFNOYoZ0', '2026-03-13', 0, 2, '186270b6-d399-4cd1-b327-e94fab01d1c6',
    0, 'C', 'Create expense report', NULL,
    NULL, 0, NULL, NULL, NULL,
    1765785600000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b556cbef-612e-4f54-8d41-78ace1273605', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Q7-qnzakfvB3YI8OZZoMJ', '2026-03-13', 0, 3, 'b556cbef-612e-4f54-8d41-78ace1273605',
    0, '', 'Update API documentation', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769846400000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0f472ee2-9404-42ec-aec7-1cfc2b4fe078', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TJfX3ccV2Kl6NNcDcBynh', '2026-03-19', 0, 5, '0f472ee2-9404-42ec-aec7-1cfc2b4fe078',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776409200000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd9414c64-c2b5-435c-9e0d-e9ab7a00df37', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'gaRqSCj7RZhfpswpvKZRn', '2026-03-19', 0, 6, 'd9414c64-c2b5-435c-9e0d-e9ab7a00df37',
    0, '', 'Prepare mentoring materials', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4343214e-2a54-40a7-bea3-6d29ac22046c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964982, 1773109964982
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'eXbV8vdbBLy38KN7PFRJE', '2026-03-19', 0, 7, '4343214e-2a54-40a7-bea3-6d29ac22046c',
    0, '', 'Deposit check', NULL,
    NULL, 0, NULL, NULL, NULL,
    1771142400000, 1773109964982, 1773109964982
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '98986f7f-ec47-45b3-821d-559ea3c6a01a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'N9xI4lw5yvC1HO2KUhFC4', '2026-03-19', 0, 8, '98986f7f-ec47-45b3-821d-559ea3c6a01a',
    0, '', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1c149b4c-f60d-4e83-aa52-1ff4143bc031', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'fna8h25vNfi1Ha4HkbHvf', '2026-03-20', 0, 6, '1c149b4c-f60d-4e83-aa52-1ff4143bc031',
    0, 'B', 'Prepare mentoring materials', NULL,
    NULL, 0, NULL, NULL, NULL,
    1765785600000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '31b827c9-5e1e-4205-a319-04be108989c5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9yNrVg96KYI_NZY_2iQUM', '2026-03-20', 0, 7, '31b827c9-5e1e-4205-a319-04be108989c5',
    0, '', 'Review OKR progress', NULL,
    NULL, 0, NULL, NULL, NULL,
    1774422000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b9a051e7-9eb8-4071-8a11-87b56ac1470d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'gJ57P8xUrgHgqccKrBBuP', '2026-03-20', 0, 8, 'b9a051e7-9eb8-4071-8a11-87b56ac1470d',
    0, '', 'Review quarterly goals', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769760000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1f2e9fc5-be07-44ed-af2a-f60ebca112a4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'pBKe4nXKqSXU8CCZRldVA', '2026-03-21', 0, 6, '1f2e9fc5-be07-44ed-af2a-f60ebca112a4',
    0, '', 'Return library books', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '82d9c8a6-13cd-48e2-912e-a174f23868a2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'UoXJ17lyjLhqAXMTz6kR8', '2026-03-21', 0, 7, '82d9c8a6-13cd-48e2-912e-a174f23868a2',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8ca8b5a5-d730-4394-8a37-bc537127db90', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'XLpvyNaWjk0tmK-UDgmQI', '2026-03-21', 0, 8, '8ca8b5a5-d730-4394-8a37-bc537127db90',
    0, '', 'Review security patches', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766995200000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'edb7339c-57e7-43f1-8ec2-a26550dd5743', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7ns4UzbmMMOraAQLoKUbo', '2026-03-21', 0, 9, 'edb7339c-57e7-43f1-8ec2-a26550dd5743',
    0, 'B', 'Update volunteer hours log', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767600000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4fcf736d-7175-4e13-a382-c0c14c8a036b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '8ibwjQXxx-xmiH7BRp-wK', '2026-03-23', 0, 0, '4fcf736d-7175-4e13-a382-c0c14c8a036b',
    0, '', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776409200000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f52f42cf-218d-4d3c-a479-ab0a12ce701b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'au31WdyO2AnX9VfPfKWIA', '2026-03-23', 0, 1, 'f52f42cf-218d-4d3c-a479-ab0a12ce701b',
    0, '', 'Drop off recycling', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775286000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2d7e7b67-c96e-4905-9dbb-dde2fea475d9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '0SCEnsuvdxuw0Lz-chZYc', '2026-03-23', 0, 2, '2d7e7b67-c96e-4905-9dbb-dde2fea475d9',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5785b502-be9d-432d-baa4-6d4ae9f8429a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'j9Dl8MHpay0pO8M9CCUpq', '2026-03-24', 0, 0, '5785b502-be9d-432d-baa4-6d4ae9f8429a',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768636800000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '12b1efa1-834d-40bb-8820-018667a0bc57', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '0l3fYUeEH3ujixTHObyz0', '2026-03-24', 0, 1, '12b1efa1-834d-40bb-8820-018667a0bc57',
    0, 'B', 'Renew passport', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '519024cc-f25f-4f1b-be2f-a9a75d86e6f9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Fz6IQVPM4YWYJYCkj2WmG', '2026-03-24', 0, 2, '519024cc-f25f-4f1b-be2f-a9a75d86e6f9',
    0, '', 'Clean up email inbox', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766044800000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '622a7923-0d26-4fce-b7af-33a7134a2b65', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'j6W156psEWwEDA96fo4Wt', '2026-03-24', 0, 3, '622a7923-0d26-4fce-b7af-33a7134a2b65',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'def2ccb8-ffc8-49d5-b12e-9b1226a04ac4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'U9KI8LmGVVg2tLHS5PQvS', '2026-03-24', 0, 4, 'def2ccb8-ffc8-49d5-b12e-9b1226a04ac4',
    0, 'A', 'Values alignment check', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fb7801e4-94ef-4140-ad3a-1d258265e3e7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'SmhFX2mdfFpT2QbsJveyV', '2026-03-24', 0, 5, 'fb7801e4-94ef-4140-ad3a-1d258265e3e7',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '34288d65-bda8-42d9-b134-afba3c9e9348', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '8lt1TL1UauZtf-o9_Asc1', '2026-03-25', 0, 0, '34288d65-bda8-42d9-b134-afba3c9e9348',
    0, '', 'Pay utility bills', NULL,
    NULL, 0, NULL, NULL, NULL,
    1774854000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6c58d83b-aca5-4c8f-9f42-bf323aeb1876', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'y8k5vI0fYLDDJ758awoS0', '2026-03-25', 0, 1, '6c58d83b-aca5-4c8f-9f42-bf323aeb1876',
    0, '', 'Code review for team', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769328000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c43ccf7e-5c87-4176-8e3f-f10c423ce0a1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7DSD0wiZjOCxuF1o0P_L3', '2026-03-25', 0, 2, 'c43ccf7e-5c87-4176-8e3f-f10c423ce0a1',
    0, 'A', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5bb2c83b-f6db-44a4-8d31-fa862337e112', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tB50nz7ppoJI2Q38dPlZz', '2026-03-25', 0, 3, '5bb2c83b-f6db-44a4-8d31-fa862337e112',
    0, 'B', 'Draft project proposal', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8cf1f2a8-1232-4bd4-8379-461040104e24', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'B3px1zQHFhX2ZMNebTt5g', '2026-03-25', 0, 4, '8cf1f2a8-1232-4bd4-8379-461040104e24',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1774335600000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6798b0c9-83df-4e2a-8817-cd290c094dcc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tIJZq-EOfbmDMxlthxdp3', '2026-03-25', 0, 5, '6798b0c9-83df-4e2a-8817-cd290c094dcc',
    0, 'A', 'Backup phone photos', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1f04671c-0ce0-483e-a74a-faa6ff2f188f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'TKLLCEPUilPzqez5zOz5-', '2026-03-25', 0, 6, '1f04671c-0ce0-483e-a74a-faa6ff2f188f',
    0, 'B', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '263eea9c-3966-437c-96c4-f61f57382044', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'y7q7fGOmUGh6sQP0XQY4L', '2026-03-28', 0, 0, '263eea9c-3966-437c-96c4-f61f57382044',
    0, 'C', 'Pick up package from locker', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768982400000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b671eb05-8470-4aa1-9bbd-8bc6336ed5f6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'LoIAbkUPQJ6H3HTBbkYTT', '2026-03-28', 0, 1, 'b671eb05-8470-4aa1-9bbd-8bc6336ed5f6',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770883200000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a6494cdb-eb80-4ec5-8e95-62ed717a2108', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '4nws4KPs4hZS0CKiO3WYR', '2026-03-28', 0, 2, 'a6494cdb-eb80-4ec5-8e95-62ed717a2108',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768896000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '52fcf48f-1452-4b08-bfd6-813a993d2fd2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'pQoXVGqTRwyZ-5O86Z2Ku', '2026-03-29', 0, 0, '52fcf48f-1452-4b08-bfd6-813a993d2fd2',
    0, '', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '25e6e2c3-3ab3-4663-86f4-452036c56c9c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'hH6iKqotPo-Ju1DFqvDc7', '2026-03-29', 0, 1, '25e6e2c3-3ab3-4663-86f4-452036c56c9c',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8de163df-3fb5-4008-a3ae-6448184a2ba5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_OZ2dSFujn8SyrC9y8IbR', '2026-03-29', 0, 2, '8de163df-3fb5-4008-a3ae-6448184a2ba5',
    0, 'A', 'Buy groceries', NULL,
    NULL, 0, NULL, NULL, NULL,
    1770969600000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '779230ac-ca51-4b28-979a-0037009702f3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'GM_4Ws_mmCj9Q6rOBpIdV', '2026-03-29', 0, 3, '779230ac-ca51-4b28-979a-0037009702f3',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1769846400000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8fcd558f-ec98-4469-973f-08642dc97340', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'n0lziI8pDFZ5yNH6Cu8T9', '2026-03-30', 0, 5, '8fcd558f-ec98-4469-973f-08642dc97340',
    0, 'B', 'Plan workshop curriculum', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766736000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '263555ef-008b-426b-8c7e-8743a7d387e7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'BQcAyMQTbinLgHa0bwtKz', '2026-03-30', 0, 6, '263555ef-008b-426b-8c7e-8743a7d387e7',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768896000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '88f97365-62f9-4297-80aa-044be497b289', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'xHyv7DeGG_2rUIfpG4Swt', '2026-03-30', 0, 7, '88f97365-62f9-4297-80aa-044be497b289',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770364800000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '86a76b76-fc9d-4ee4-9d7f-129c0dabe762', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '1f07udT3-SWGRrfDfXBNo', '2026-03-30', 0, 8, '86a76b76-fc9d-4ee4-9d7f-129c0dabe762',
    0, '', 'Send invoice to client', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '99db6a8d-53be-4de3-b684-e0ffe80f48e0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '4GP7ppzfdcKsQe-65T-DC', '2026-03-30', 0, 9, '99db6a8d-53be-4de3-b684-e0ffe80f48e0',
    1, '', 'Update life vision board', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5f8b6e06-3504-4d1d-95b6-3252e37fcb33', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'w30qFWmTVzI0KVFw9eAaY', '2026-03-30', 1, 0, '5f8b6e06-3504-4d1d-95b6-3252e37fcb33',
    0, 'C', 'Review quarterly goals', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768032000000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6d9f9549-3d9c-4afc-b380-01478146eb0b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964983, 1773109964983
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'kh7LOE-HapG_pho10aqjb', '2026-03-30', 1, 1, '6d9f9549-3d9c-4afc-b380-01478146eb0b',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964983, 1773109964983
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'aa3f0840-c99e-4e92-a9dc-b6db5b656d6c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'hMHsH4KncS2-P2PBnCCco', '2026-03-30', 1, 2, 'aa3f0840-c99e-4e92-a9dc-b6db5b656d6c',
    0, 'B', 'Configure staging environment', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c95cd96b-f736-4224-a74b-55f9b321f294', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'w2BHx__7_v0jZnSmeW0LX', '2026-03-30', 1, 3, 'c95cd96b-f736-4224-a74b-55f9b321f294',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fe43255c-95fd-4d8f-b41a-a1cf758c2ed9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Ef-jdD7SQz2_e9azl_hW1', '2026-03-30', 1, 4, 'fe43255c-95fd-4d8f-b41a-a1cf758c2ed9',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '348e427a-280e-4f4f-9e3d-0a65fd0eb8bd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'XE7u5W592IepdH9Oj_YxN', '2026-03-31', 0, 5, '348e427a-280e-4f4f-9e3d-0a65fd0eb8bd',
    1, '', 'File tax documents', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '45c62755-8c78-4e87-95b0-291b535ebcbe', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Y0vmSRzmTAgXW8w1nXpop', '2026-03-31', 0, 6, '45c62755-8c78-4e87-95b0-291b535ebcbe',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768982400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dc842e5c-45a9-43ce-95c0-29fb7f6f6e3f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'rpfcN_AORPWHTIXNfN1in', '2026-03-31', 0, 7, 'dc842e5c-45a9-43ce-95c0-29fb7f6f6e3f',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1775286000000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9632ed49-7aab-41d3-9800-69f30429c372', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'fQ8x8LepiqCiDLoyL72C1', '2026-03-31', 0, 8, '9632ed49-7aab-41d3-9800-69f30429c372',
    0, '', 'Review OKR progress', NULL,
    NULL, 0, NULL, NULL, NULL,
    1770624000000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e6e22a03-eef0-4ee5-bc6c-81ef16d92d75', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'nzmxVe7jv0dpFkjVyZO_q', '2026-03-31', 0, 9, 'e6e22a03-eef0-4ee5-bc6c-81ef16d92d75',
    0, '', 'Research competitors', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776150000000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1cbb5c2b-32f5-4b43-83e9-647df39e3ffe', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'dayBKuTEOdUPIXNpG7USP', '2026-03-31', 1, 0, '1cbb5c2b-32f5-4b43-83e9-647df39e3ffe',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a34e3a67-f69f-4843-8dba-b6eb60906605', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'owKcPl-dE2MJQ6ZGD00dW', '2026-04-02', 0, 0, 'a34e3a67-f69f-4843-8dba-b6eb60906605',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c79606a4-a508-4973-99f6-ee4b4c93acf4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_mK7i9X5tUYmqsNClqdhy', '2026-04-02', 0, 1, 'c79606a4-a508-4973-99f6-ee4b4c93acf4',
    0, '', 'Plan product roadmap', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772179200000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '921e6417-194d-4dec-bc01-90539548fa2f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '5J998CsCW9vyrzGSITNhu', '2026-04-02', 0, 2, '921e6417-194d-4dec-bc01-90539548fa2f',
    1, 'B', 'Deploy to staging', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773385200000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '55d2df56-0fcd-435d-b546-b530b76ddce6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'T7Z8z7UeFmzovK7bm5DO9', '2026-04-02', 0, 3, '55d2df56-0fcd-435d-b546-b530b76ddce6',
    0, 'B', 'Get haircut', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767945600000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b4f7e515-e994-4a2c-97d5-accf1351a59b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'uBReupU7S9-Zv1m2AB79V', '2026-04-02', 0, 4, 'b4f7e515-e994-4a2c-97d5-accf1351a59b',
    1, 'B', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769155200000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '44cd490a-1d71-4a64-9e8f-c3544a9d4904', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'mdN4UFDy3bBCwpSHChc3P', '2026-04-03', 0, 0, '44cd490a-1d71-4a64-9e8f-c3544a9d4904',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1772006400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '99adb1f6-238a-4c51-953f-8f0fb9f2349d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'xr9FpAkvq-K9q-REfV9mQ', '2026-04-03', 0, 1, '99adb1f6-238a-4c51-953f-8f0fb9f2349d',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c9775ffa-4b1d-450b-8f4e-591204ea46b4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '8dUUwnmH62-t86Lvs_1BF', '2026-04-03', 0, 2, 'c9775ffa-4b1d-450b-8f4e-591204ea46b4',
    0, '', 'Book flight tickets', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766476800000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2dee7625-cdc7-4d09-b627-5dde33fe005b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'n2acGu0nfGS6Qpq3I9zLf', '2026-04-03', 0, 3, '2dee7625-cdc7-4d09-b627-5dde33fe005b',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c50a744f-8f9f-417e-b0e0-7d2536d526a1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Cw-7HjPuTnqdCL-qElDIj', '2026-04-03', 0, 4, 'c50a744f-8f9f-417e-b0e0-7d2536d526a1',
    0, '', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768809600000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0ba26cf3-26b7-48b6-b090-e5276a90359d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'OJGsax8fYR4tcr52ZkXYB', '2026-04-05', 0, 6, '0ba26cf3-26b7-48b6-b090-e5276a90359d',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '583b1702-2e39-46da-91c4-779ed746fa9d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '-T98iKe7pC09Ba_t8E190', '2026-04-05', 0, 7, '583b1702-2e39-46da-91c4-779ed746fa9d',
    0, '', 'Schedule mentee check-in', NULL,
    NULL, 0, NULL, NULL, NULL,
    1774940400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7a1c0842-94d4-40f1-ba00-76a725acffe7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'M1zfSkq5RG5vil08bYp5e', '2026-04-05', 0, 8, '7a1c0842-94d4-40f1-ba00-76a725acffe7',
    1, 'C', 'Update LinkedIn profile', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775804400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5d887c8a-361e-4502-a8d7-1abc095f95f3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '3KwpTMQitIOAiyJhjxwnM', '2026-04-05', 0, 9, '5d887c8a-361e-4502-a8d7-1abc095f95f3',
    1, 'A', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2bce2012-2d55-4206-b33f-68729dca2e82', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'GfSDG9o9Zgmv0tcLZ6xvQ', '2026-04-05', 1, 0, '2bce2012-2d55-4206-b33f-68729dca2e82',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766563200000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3cc2305c-4365-499f-8e75-bee54309e8e8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'k22HIoU1IBn9Yxwu5H7zL', '2026-04-05', 1, 1, '3cc2305c-4365-499f-8e75-bee54309e8e8',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7236c12c-1a15-4918-a15f-31a28e794152', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'zOKeRj2ywARCiycFZYmp5', '2026-04-06', 0, 0, '7236c12c-1a15-4918-a15f-31a28e794152',
    0, 'A', 'Prepare mentoring materials', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775804400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '72cd83c9-158b-4df9-805a-4115718089de', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'JToRzZXYpgrgggbAokbZP', '2026-04-06', 0, 1, '72cd83c9-158b-4df9-805a-4115718089de',
    1, 'A', 'Review Alex''s code submission', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '421abe15-49ae-48a4-adfc-e84e19a5d739', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jIaJG1Vz2lhIeWUxoRPb9', '2026-04-06', 0, 2, '421abe15-49ae-48a4-adfc-e84e19a5d739',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '30c8aa36-843b-497c-97f1-cb919b8bcfeb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9U8YrqyZ8zttGdP-FIMAa', '2026-04-06', 0, 3, '30c8aa36-843b-497c-97f1-cb919b8bcfeb',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766131200000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5eb8a9d6-4e59-4015-b0bd-33da07d8500c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lyiwI5X3cDa0cc6-4bYIM', '2026-04-06', 0, 4, '5eb8a9d6-4e59-4015-b0bd-33da07d8500c',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '54afd399-67ef-41ba-8fbf-c1a1365a3cd4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Ambjydkt22C_4wpACicoE', '2026-04-06', 0, 5, '54afd399-67ef-41ba-8fbf-c1a1365a3cd4',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dec96477-68c8-42e2-a997-2023485f0168', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wtFiStVGryUs0rc3qrzsu', '2026-04-06', 0, 6, 'dec96477-68c8-42e2-a997-2023485f0168',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '57ea7955-e26a-4f90-b3d3-50fbecb2372d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jQUwLk2C9TuGKisUXm8N5', '2026-04-07', 0, 6, '57ea7955-e26a-4f90-b3d3-50fbecb2372d',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1769760000000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '39148b10-5ccf-4d09-820c-fa0696ce0a03', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'D9BxUeN1LkMmewXWOlusn', '2026-04-07', 0, 7, '39148b10-5ccf-4d09-820c-fa0696ce0a03',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1775372400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '78b8ff03-c892-4394-b2d6-7782972ebfd9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tkS4DZ2cl1rNQmr6K-lXD', '2026-04-07', 0, 8, '78b8ff03-c892-4394-b2d6-7782972ebfd9',
    0, 'B', 'Fill up gas tank', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768982400000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fae30460-dcc6-4e71-9529-9cc1251d3e19', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lVjARpWPQ-tgvyOocjM2l', '2026-04-07', 0, 9, 'fae30460-dcc6-4e71-9529-9cc1251d3e19',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1772697600000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '700e6b63-978e-4571-a6bb-f44f864f60d7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964984, 1773109964984
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'BiYIAJmBr8rlGNoBB7m3m', '2026-04-07', 1, 0, '700e6b63-978e-4571-a6bb-f44f864f60d7',
    0, 'A', 'Prepare sprint demo', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775026800000, 1773109964984, 1773109964984
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0bb664b6-4556-455a-a2bb-8bc95ea5ab6f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'UVsfkZTKHTPYWRi0MB9gY', '2026-04-08', 0, 0, '0bb664b6-4556-455a-a2bb-8bc95ea5ab6f',
    0, '', 'Update portfolio website', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6b7cca5e-3555-46e5-b724-591953f59d4b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'bMlHQhb9pmf2agIkcBnp1', '2026-04-08', 0, 1, '6b7cca5e-3555-46e5-b724-591953f59d4b',
    1, '', 'Schedule mentee check-in', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '15f51bd6-c007-43e9-9c47-3eb881c3e8b3', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'hc7Ljp5aYvYpQ22mM7W5R', '2026-04-08', 0, 2, '15f51bd6-c007-43e9-9c47-3eb881c3e8b3',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '339ed4bb-7f5d-4341-a344-cf2643154e33', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'kiJ5R2wQF8FBHkRlfixef', '2026-04-08', 0, 3, '339ed4bb-7f5d-4341-a344-cf2643154e33',
    0, '', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    1771315200000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4d861d79-5638-432b-8ecf-6c16c380477f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ofO0TVFKfbbHNSK5A4soG', '2026-04-08', 0, 4, '4d861d79-5638-432b-8ecf-6c16c380477f',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ef2c05c8-e678-4910-8c2f-03ecf902a0ec', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Fl9Ij2pET5qhons1SLP0u', '2026-04-09', 0, 0, 'ef2c05c8-e678-4910-8c2f-03ecf902a0ec',
    0, '', 'Prepare Q1 presentation', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f0c17cad-5840-4e0b-aa11-fde8de7d9527', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'klQr8MuDlbhry2l-edexx', '2026-04-09', 0, 1, 'f0c17cad-5840-4e0b-aa11-fde8de7d9527',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1767859200000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8248916e-d445-4cf8-aaf3-1d00361c988d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '3qOkjrZKX5Hc29pnqi9iS', '2026-04-09', 0, 2, '8248916e-d445-4cf8-aaf3-1d00361c988d',
    0, 'A', 'Prepare sprint demo', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775977200000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b4e90fa2-4c27-4f2b-bb02-b93dfc7bb780', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'EVCEVujdXSFUqOIOuFnIA', '2026-04-09', 0, 3, 'b4e90fa2-4c27-4f2b-bb02-b93dfc7bb780',
    0, '', 'Buy birthday gift for Sarah', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766217600000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4cb8979f-cb83-4de3-a241-11b6e45d115c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'k4lMheseG5MYKKUaSUlou', '2026-04-09', 0, 4, '4cb8979f-cb83-4de3-a241-11b6e45d115c',
    0, 'C', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    1770278400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8306d613-6cae-4a8c-bdd4-31eb0f12ba12', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Nb4JEdYuctlhpP_R6mJrI', '2026-04-10', 0, 0, '8306d613-6cae-4a8c-bdd4-31eb0f12ba12',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9b8b3675-76f6-4fbe-a563-446f21a88218', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'mKfGi0RuZP0AKtI2BKwhT', '2026-04-10', 0, 1, '9b8b3675-76f6-4fbe-a563-446f21a88218',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '94078ff8-8253-4c01-965b-ed2b6ce88d95', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tMsDD3jMZ9Kk3qRC2TBOI', '2026-04-10', 0, 2, '94078ff8-8253-4c01-965b-ed2b6ce88d95',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1771315200000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd7795860-0f2f-4f5d-8797-44516f1037d5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'MxCfqTXhy-HOCHBgePc17', '2026-04-10', 0, 3, 'd7795860-0f2f-4f5d-8797-44516f1037d5',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776063600000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'db70f8f5-c121-4bad-91a0-c6dcf28339b5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'l6hbV7O2V1RA71uvNhQik', '2026-04-11', 0, 6, 'db70f8f5-c121-4bad-91a0-c6dcf28339b5',
    0, 'B', 'Buy groceries', NULL,
    NULL, 0, NULL, NULL, NULL,
    1768032000000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0a9fe8f8-c814-4933-bf9a-d49b80f69ea9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_G4qdsLgl59K2l3G3FkIb', '2026-04-11', 0, 7, '0a9fe8f8-c814-4933-bf9a-d49b80f69ea9',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1775113200000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a9f84c2d-3ef8-4215-818f-f59cae684b44', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'APQvi4MYLA0WaE0rVVGJ7', '2026-04-11', 0, 8, 'a9f84c2d-3ef8-4215-818f-f59cae684b44',
    1, '', 'Drop off donations', NULL,
    NULL, 0, NULL, NULL, NULL,
    1766304000000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'beef57c0-d843-4e34-9256-b6cbcad132a8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'J9zvu6waZBnZo4mTim_T7', '2026-04-11', 0, 9, 'beef57c0-d843-4e34-9256-b6cbcad132a8',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770278400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c79743fc-39e7-408e-8896-df67e2a3d060', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'PxNK0FA13LvCfSOuf5v9Z', '2026-04-11', 1, 0, 'c79743fc-39e7-408e-8896-df67e2a3d060',
    0, 'B', 'Write recommendation for Alex', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '08ae347f-4601-449b-bddf-2ab95db2bfd9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '7S0e95HyzoEQ2b8uNWja6', '2026-04-11', 1, 1, '08ae347f-4601-449b-bddf-2ab95db2bfd9',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1774335600000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'caa0b969-a118-4b8c-8f95-4544de7c72e8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ZCK1jUqoYj3dAiK2kfuq5', '2026-04-11', 1, 2, 'caa0b969-a118-4b8c-8f95-4544de7c72e8',
    0, 'A', 'Morning journal entry', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bc5e5315-d741-4160-a64e-b773f9972812', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Ecy0CJU8sB6vDdBWeHB_6', '2026-04-11', 1, 3, 'bc5e5315-d741-4160-a64e-b773f9972812',
    0, 'A', 'Schedule annual physical', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1554565d-9efc-4de9-a6d5-cc0b55abafb9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'rOFfUk8nVTiP0sp8Z39vM', '2026-04-11', 1, 4, '1554565d-9efc-4de9-a6d5-cc0b55abafb9',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1775804400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8d7ef2c8-f856-4092-9cda-b1882a896d38', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'i8XK_TNDqaVKLQP1uAow0', '2026-04-13', 0, 5, '8d7ef2c8-f856-4092-9cda-b1882a896d38',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1775458800000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '72f0d605-401a-4a73-9efa-028f1e654109', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'HoLOMnbncAY1scqLqrrnN', '2026-04-13', 0, 6, '72f0d605-401a-4a73-9efa-028f1e654109',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e695d7e9-a78f-4fdc-8e0e-c07b74016bfb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '9VO6WIn3aeiEXsNYgk4ll', '2026-04-13', 0, 7, 'e695d7e9-a78f-4fdc-8e0e-c07b74016bfb',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2211a1ff-6a17-4355-9f02-c06c65f43864', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'I3vTR-DAbaXyqvxqNmM7h', '2026-04-13', 0, 8, '2211a1ff-6a17-4355-9f02-c06c65f43864',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1774076400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e210ee0b-6bdf-4c29-8e8a-577a255dd3f9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'bjU2x0uq7w_767EA-gPzR', '2026-04-14', 0, 5, 'e210ee0b-6bdf-4c29-8e8a-577a255dd3f9',
    1, '', 'Clean up tech debt', NULL,
    NULL, 0, NULL, NULL, NULL,
    1769068800000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '448dfe06-7213-4973-8552-d3f3c8d612f5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'gjRk21oXf4pSkkLxVfXdD', '2026-04-14', 0, 6, '448dfe06-7213-4973-8552-d3f3c8d612f5',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776322800000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5e1c99b9-980b-45b0-add0-c69ed315ab56', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'YTtiVg_mjo7X6cDmvXz52', '2026-04-14', 0, 7, '5e1c99b9-980b-45b0-add0-c69ed315ab56',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1769760000000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e39c7f47-ed00-43c0-bb38-cc848299a8e1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lLMWrCeIbiO2swGN51O00', '2026-04-14', 0, 8, 'e39c7f47-ed00-43c0-bb38-cc848299a8e1',
    0, '', 'Review health insurance', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '928d9196-d29b-44f2-b571-9797e0308a8b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'rxL3FN64ifGTVUqeRB-tS', '2026-04-14', 0, 9, '928d9196-d29b-44f2-b571-9797e0308a8b',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768118400000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e238bf0b-c13d-4115-b408-56b8232de55a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jCWSRDKk9C5ouQc6ZBWiX', '2026-04-14', 1, 0, 'e238bf0b-c13d-4115-b408-56b8232de55a',
    0, '', 'Return Amazon package', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767427200000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ed3cf383-fa67-419d-8134-307693933b91', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '6UU1pkANeA-uQGwbMQ6HI', '2026-04-15', 0, 0, 'ed3cf383-fa67-419d-8134-307693933b91',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770969600000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '745b165c-c849-4c5d-998e-b352f4ddbdb8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964985, 1773109964985
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'c4yBdjQdggvKEPe3-Vdvp', '2026-04-15', 0, 1, '745b165c-c849-4c5d-998e-b352f4ddbdb8',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770624000000, 1773109964985, 1773109964985
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '83d830eb-5a97-48b5-af5c-52ccd2e197e5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'LYBNfE4wctQTYgdh2pIDB', '2026-04-15', 0, 2, '83d830eb-5a97-48b5-af5c-52ccd2e197e5',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6a37db11-f529-40f4-b79a-575fe0835c91', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '8AvpbB2MTrB0N89h1QdcL', '2026-04-15', 0, 3, '6a37db11-f529-40f4-b79a-575fe0835c91',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1774249200000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '065aa1d2-2998-4b27-87e2-17c0c4af21bd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'W71zVnuVTkxh0lkjfBoMP', '2026-04-15', 0, 4, '065aa1d2-2998-4b27-87e2-17c0c4af21bd',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1767859200000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '402cffe9-e9b8-4be0-93ff-b93f9baa9df2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'rmMCxF_73yJjoJXw6rOPH', '2026-04-15', 0, 5, '402cffe9-e9b8-4be0-93ff-b93f9baa9df2',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1771833600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '39613531-88c1-4a2d-b6fa-169ec40bb450', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '_5zUWCV09P3YXXpnXFBgp', '2026-04-17', 0, 0, '39613531-88c1-4a2d-b6fa-169ec40bb450',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '28ae1a0f-1057-4a8a-9003-dd29b6025d55', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Wa0XQKAYhgSC81wWuPlYD', '2026-04-17', 0, 1, '28ae1a0f-1057-4a8a-9003-dd29b6025d55',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1766131200000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '582da6ec-9bc0-44e4-a566-fade26624788', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Z6n8dRTn3Qy1O1xehPCdk', '2026-04-17', 0, 2, '582da6ec-9bc0-44e4-a566-fade26624788',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1765612800000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3813e8ff-b1f9-4f91-b2d0-1756c1f46dcc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wWrqmAXvkOza7c-1dIbrp', '2026-04-17', 0, 3, '3813e8ff-b1f9-4f91-b2d0-1756c1f46dcc',
    0, 'B', 'Buy birthday gift for Mike', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775458800000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '477bb733-e5b9-4154-a0b5-f88f0e9ba055', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Q5WZAgQm7utGgPCuQU922', '2026-04-17', 0, 4, '477bb733-e5b9-4154-a0b5-f88f0e9ba055',
    0, '', 'Gratitude list', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a92feae5-39d8-4d70-ad0d-fac1002ac1eb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'FFyEM_q1OyxejeCH-4-B0', '2026-04-17', 0, 5, 'a92feae5-39d8-4d70-ad0d-fac1002ac1eb',
    0, '', 'Return shoes', NULL,
    NULL, 0, NULL, NULL, NULL,
    1765785600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e423a487-b316-410f-91cd-4c35bd2d306e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'GDw6SnYMcWsDfoVGL8tc9', '2026-04-17', 0, 6, 'e423a487-b316-410f-91cd-4c35bd2d306e',
    1, '', 'Refactor payment service', NULL,
    NULL, 0, NULL, NULL, NULL,
    1767081600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5df66737-54e8-4a4f-b16d-da1ac6f32219', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '76MFlaopYUe69Uedk41d9', '2026-04-18', 0, 5, '5df66737-54e8-4a4f-b16d-da1ac6f32219',
    0, '', 'Review PR #2345', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775372400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7dc01bd4-f513-4c25-aac0-74682dd54869', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'iINA5-wdPlII5PsN0jOlY', '2026-04-18', 0, 6, '7dc01bd4-f513-4c25-aac0-74682dd54869',
    0, 'C', 'Deploy to production', NULL,
    NULL, 0, NULL, NULL, NULL,
    1770278400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '00d38021-9346-4b00-9b00-2988ce887f6d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'tdiVN9NyLnQM1pmTmX2ZM', '2026-04-18', 0, 7, '00d38021-9346-4b00-9b00-2988ce887f6d',
    0, '', 'Get prescription refilled', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '754abfe4-6fa1-4e2c-a912-bf9684de67bf', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'rLyDAUn1y8Aqx8mU2cPpU', '2026-04-18', 0, 8, '754abfe4-6fa1-4e2c-a912-bf9684de67bf',
    0, 'A', 'Buy new running shoes', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772265600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cffe3250-bf61-40f5-9c40-75f3762e1c77', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'Umy5g0sUoaKXPY3INK4ZA', '2026-04-18', 0, 9, 'cffe3250-bf61-40f5-9c40-75f3762e1c77',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0fa206f2-4545-474e-94c7-76131e9a9c46', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'yqrV-6JzDkqGQMO8FYABA', '2026-04-18', 1, 0, '0fa206f2-4545-474e-94c7-76131e9a9c46',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768464000000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'be2c249a-46b0-46a3-8d10-a73117cfbf15', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'YEAqKu1tFSEqw0F8T-o-a', '2026-04-18', 1, 1, 'be2c249a-46b0-46a3-8d10-a73117cfbf15',
    0, '', 'Review PR #2356', NULL,
    NULL, 0, NULL, NULL, NULL,
    1775199600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c9bfb028-9c26-42d9-923b-e0899b2ecc6c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'huEvT3iF75DwV4qRNK-uL', '2026-04-18', 1, 2, 'c9bfb028-9c26-42d9-923b-e0899b2ecc6c',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776236400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7ed91c7e-0799-4696-ac30-3b6bb4bd2d5a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'YqFi4-7u-lXFgnrjh_MgR', '2026-04-18', 1, 3, '7ed91c7e-0799-4696-ac30-3b6bb4bd2d5a',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1773298800000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8113bf01-6c59-4f62-a2b3-5ecbf5dbd8d7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'ZLT-j2hhrMi4HolU-bXgw', '2026-04-18', 1, 4, '8113bf01-6c59-4f62-a2b3-5ecbf5dbd8d7',
    0, 'C', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1770537600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e383d5cf-e6c5-47de-aff5-16fb0cfd0c3f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'hAlFI6QnDSNIbVbnu1155', '2026-04-19', 0, 5, 'e383d5cf-e6c5-47de-aff5-16fb0cfd0c3f',
    1, 'C', 'Return library books', NULL,
    NULL, 0, NULL, NULL, NULL,
    1772438400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '64f6eaee-6c4f-46e6-bdbd-9ca4a6412608', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    '4-qwi8g0-L1dJ9z26KoYV', '2026-04-19', 0, 6, '64f6eaee-6c4f-46e6-bdbd-9ca4a6412608',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4cd0de99-4f03-45f3-b122-1a811c74f9d7', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'bC8V5TFy51EGHHSINtmru', '2026-04-19', 0, 7, '4cd0de99-4f03-45f3-b122-1a811c74f9d7',
    0, 'A', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9dcb16f0-e5ee-4fa9-a132-8d1d59957be6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'JVI5azEg6Kbp-53B7GCW7', '2026-04-19', 0, 8, '9dcb16f0-e5ee-4fa9-a132-8d1d59957be6',
    1, '', 'Write technical spec', NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a25e8510-227d-4757-a4c0-e54b6031b8ea', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'iQtWFAsLLtJ9oIiS9_CNu', '2026-04-19', 0, 9, 'a25e8510-227d-4757-a4c0-e54b6031b8ea',
    0, '', 'Buy groceries', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773212400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd9bfb62b-232a-49f5-ac75-2bd6093b5380', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'wmZM_vs3d5JvvmuCTNJkC', '2026-04-19', 1, 0, 'd9bfb62b-232a-49f5-ac75-2bd6093b5380',
    0, 'B', 'Update README', NULL,
    NULL, 0, NULL, NULL, NULL,
    1776668400000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cce14a96-40b9-4210-aba5-28e31ce22f19', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'IZwoJ5krVGeOVrcuFicNF', '2026-04-19', 1, 1, 'cce14a96-40b9-4210-aba5-28e31ce22f19',
    0, 'B', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1771920000000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c4d99a99-7650-402e-8707-02dcf20c1604', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'nA7GjjeplFgV77VCkvkmi', '2026-04-19', 1, 2, 'c4d99a99-7650-402e-8707-02dcf20c1604',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    NULL, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '830eba35-9d97-458b-b2c9-fbf3d8b9cb7b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'jQG9pRMgzOqOIvZ-uhAoa', '2026-04-19', 1, 3, '830eba35-9d97-458b-b2c9-fbf3d8b9cb7b',
    0, '', NULL, NULL,
    NULL, 0, NULL, NULL, NULL,
    1768809600000, 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'efc1ade4-61a3-4267-adab-12db715f5600', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO task (
    id, dayDate, taskPageIndex, rowIndex, pageId,
    isCompleted, priority, typedText, recognizedText,
    parentTaskId, migrationCount, originalDate, lastMigratedAt, estimatedMinutes,
    dueDate, createdAt, updatedAt
) VALUES (
    'lrA3HPXx4FpKvug8wpz1g', '2026-04-19', 1, 4, 'efc1ade4-61a3-4267-adab-12db715f5600',
    0, 'A', 'Update emergency contacts', NULL,
    NULL, 0, NULL, NULL, NULL,
    1773039600000, 1773109964986, 1773109964986
);

INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e6e7995e-d855-419b-b235-3ff7c345d2bd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '9d0b43f0-7ef7-4ddd-9b58-80b42c318c36', '## Code Review Session
Date: February 10, 2026

### PRs Reviewed

**PR #2341: Auth Service Refactor** (by Mike)
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #2342: API Rate Limiting** (by me)
- Alex caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #2343: Dashboard Components** (by Taylor)
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: 4m 32s (down from last week)
- Test coverage: 94.2%
- Lint errors: 0
- Bundle size: 2.05MB

### Patterns to Adopt
- Error boundary pattern from #2341
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components', 61.61759497016707, 100.73662053606395, 1300.0, 310.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'e6e7995e-d855-419b-b235-3ff7c345d2bd', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd0f39761-4a38-4ccb-aff5-11ff45f05d44', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '33f82bdf-fc52-4b0d-9593-bedf2e380547', '# Side Project: TaskFlow
Phase: Brainstorming
Date: February 10, 2026

## Concept Exploration

### The Idea: TaskFlow
A minimalist task manager that learns your patterns and suggests optimal scheduling.

### Why This?
- Frustrated with complex tools (Notion, Asana)
- Want something that "just works"
- Opportunity to learn React Native

### Core Features (MVP)
1. Quick task capture (< 2 seconds)
2. Smart due date suggestions
3. Daily focus mode
4. Simple recurring tasks

### Tech Stack Decisions
- **Frontend**: React Native (cross-platform)
- **Backend**: Supabase (fast to set up)
- **State**: Zustand (simple, performant)

### Competition Research
- Todoist: Too many features
- Things 3: Mac only, expensive
- TickTick: Closest to my vision

### Next Steps
- [ ] Sketch initial wireframes
- [ ] Set up React Native environment
- [ ] Create GitHub repo', 52.688278049579786, 89.8653775752156, 1300.0, 286.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd0f39761-4a38-4ccb-aff5-11ff45f05d44', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-10', '["e6e7995e-d855-419b-b235-3ff7c345d2bd", "d0f39761-4a38-4ccb-aff5-11ff45f05d44"]', 'e6e7995e-d855-419b-b235-3ff7c345d2bd', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5d7fae99-161f-4cdd-84b9-5b433bb40bd9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1f861c1f-dd6d-45b4-a976-88f52b8846f9', '## 1:1 with Chris
Date: February 11, 2026

### Topics Discussed

**Career Growth**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Promotion Timeline**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: February 25
- Topic: Review proposal draft', 69.74039594835698, 83.90890125664498, 1300.0, 311.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5d7fae99-161f-4cdd-84b9-5b433bb40bd9', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd5f06cd5-173f-4f3f-ba7e-e6944af89319', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'c1bbc382-c6a1-4e63-9a10-c1ad38c71605', '# Daily Journal - Wednesday
Date: February 11, 2026

## Mood
Steady

## Today''s Highlights
- Had API Review - good discussion on priorities
- Mentorship Session went well - got good career advice
- Took care of gym session - checked off the list
- Got outside for a 20 min walk

## What I Learned
Sometimes the simplest solution really is best

## Tomorrow''s Calendar
- Skip Level
- Visit Grandparents
- Code2Community Mentoring

Need to prepare for these.

## Life Update
Still apartment hunting. Viewed 0 places so far.

## Gratitude
Grateful for the supportive team at work', 52.264099678064355, 102.15705616211805, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd5f06cd5-173f-4f3f-ba7e-e6944af89319', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '915d6962-97a1-4878-8923-114b4402bd41', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '56ee0f66-6de8-4e71-bba5-eb16a19b7cc5', '# Mentorship Session - Week 1
Date: February 11, 2026
Mentee: Alex
Organization: Code2Community

## Today''s Topic
**Development environment**: VS Code, terminal basics

## Session Notes
- Started with review of last week''s homework
- Alex asked great questions about best practices
- Alex explained the concept back to me correctly
- Assigned practice exercises for the week

## Alex''s Progress
- **Confidence**: Building foundation
- **Problem-solving**: Learning to break down problems
- **Questions**: Good curiosity

## Homework Assigned
- [ ] Complete 3 practice problems on development environment
- [ ] Read one chapter of "Automate the Boring Stuff"
- [ ] Try building a small script using today''s concepts

## My Reflections
It''s rewarding to see Alex''s growth. Teaching helps me understand concepts more deeply too.
Learning to explain things simply is a skill.

## Next Session
- Continue with next topic
- Review homework together
- Hours logged: 3', 52.366625767483555, 107.06350005662527, 1300.0, 320.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '915d6962-97a1-4878-8923-114b4402bd41', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-11', '["5d7fae99-161f-4cdd-84b9-5b433bb40bd9", "d5f06cd5-173f-4f3f-ba7e-e6944af89319", "915d6962-97a1-4878-8923-114b4402bd41"]', '5d7fae99-161f-4cdd-84b9-5b433bb40bd9', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c122d4ab-0c2c-4729-a0d2-dc1d51ba34e5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '89a36186-6c1d-4217-8346-c0b2f68a6747', '## Architecture Review: Phoenix API Migration
Date: February 12, 2026

### Attendees
- Mike (Lead), Jordan, Jamie, Me

### Current Architecture
```
Client → API Gateway → Auth Service → Core API → PostgreSQL
                    ↓
              Redis Cache
```

### Proposed Changes

**1. Microservices Migration**
- Extract payment processing to separate service
- Event-driven communication via Kafka
- Independent deployment and scaling

**2. Performance Improvements**
- Add CDN for static assets
- Implement GraphQL for mobile clients
- Database read replicas for reporting

### Discussion Points
- Jamie: Concern about operational complexity
- Jordan: Need to upgrade K8s cluster first
- Consensus: Start with payment service extraction

### Decisions Made
- Proceed with payment service POC
- Keep monolith for core API (for now)
- Invest in observability before splitting

### Next Steps
- [ ] Me: Draft RFC for payment service
- [ ] Jordan: K8s upgrade plan
- [ ] Mike: Define service boundaries

### Follow-up
- Review RFC in 2 weeks
- POC demo end of Sprint 2', 60.67441102747934, 93.99326853449358, 1300.0, 356.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c122d4ab-0c2c-4729-a0d2-dc1d51ba34e5', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '013355e5-f09c-4e55-abf3-519a3165ed8a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'd7a4b3c4-1719-4715-a176-dcb18afd9fea', '# Learning Log
Date: February 12, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 0/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 0
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 0
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 58.91476997862346, 80.32281081499919, 1300.0, 374.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '013355e5-f09c-4e55-abf3-519a3165ed8a', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-12', '["c122d4ab-0c2c-4729-a0d2-dc1d51ba34e5", "013355e5-f09c-4e55-abf3-519a3165ed8a"]', 'c122d4ab-0c2c-4729-a0d2-dc1d51ba34e5', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c7282b2a-e86b-4f81-a389-41d636d15157', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a16532c2-1774-4a26-b2d5-ad77bd45fae7', '## Daily Standup - Friday
Date: February 13, 2026

### My Updates

**Yesterday:**
- Completed PR #2351 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1210
- Address code review feedback on #2352
- Sync with Jordan on deployment pipeline

**Blockers:**
- Need Jordan to review infra changes

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 52.31435996766001, 81.86593008969648, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c7282b2a-e86b-4f81-a389-41d636d15157', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '26855632-6899-4ab0-a245-9cd1d254f85b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b9255cf4-b74f-4ea0-8927-79877d002086', '# Weekly Review - Week 1
Date: February 13, 2026

## This Week''s Wins
- Got positive feedback on code review
- Shipped the auth migration on time
- Maintained gym streak all week

## Challenges Faced
- Struggled with focus during afternoon meetings
- Difficult conversation with stakeholder

## Key Learnings
- Asking for help earlier saves time overall

## Time Audit
- Deep work: 22 hours
- Meetings: 11 hours
- Admin/email: 6 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟡 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟡 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Complete sprint stories
2. Continue K8s learning
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Batch email checking to 2x/day', 56.79523798832753, 83.93510731597128, 1300.0, 284.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '26855632-6899-4ab0-a245-9cd1d254f85b', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-13', '["c7282b2a-e86b-4f81-a389-41d636d15157", "26855632-6899-4ab0-a245-9cd1d254f85b"]', 'c7282b2a-e86b-4f81-a389-41d636d15157', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '14d1944b-fb58-4fc9-97ab-7c00a42871fa', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '05b36c89-ff35-4414-9a18-fc5550a8cb96', '# Side Project: TaskFlow
Phase: Brainstorming
Date: February 14, 2026

## Concept Exploration

### The Idea: TaskFlow
A minimalist task manager that learns your patterns and suggests optimal scheduling.

### Why This?
- Frustrated with complex tools (Notion, Asana)
- Want something that "just works"
- Opportunity to learn React Native

### Core Features (MVP)
1. Quick task capture (< 2 seconds)
2. Smart due date suggestions
3. Daily focus mode
4. Simple recurring tasks

### Tech Stack Decisions
- **Frontend**: React Native (cross-platform)
- **Backend**: Supabase (fast to set up)
- **State**: Zustand (simple, performant)

### Competition Research
- Todoist: Too many features
- Things 3: Mac only, expensive
- TickTick: Closest to my vision

### Next Steps
- [ ] Sketch initial wireframes
- [ ] Set up React Native environment
- [ ] Create GitHub repo', 69.82954826449364, 86.3299527660106, 1300.0, 286.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '14d1944b-fb58-4fc9-97ab-7c00a42871fa', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '33b148f1-2b06-4334-9a6c-fbb900cd185d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '856e8c58-174e-4cca-9a1c-692310065554', '# Fitness Log - Week 1
Date: February 14, 2026

## Weekly Summary
- **Workouts completed**: 3/5 planned
- **Gym streak**: 4 days
- **Sleep avg**: 7.0 hours

## Workouts This Week
- Monday: 5K run (24:30)
- Wednesday: Strength training (Deadlift: 185lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 24:30 (was 24:57) 
- Deadlift: 185lbs (+2lbs)
- Body weight: 174.7lbs

## How I''m Feeling
Building the habit. Some days are harder than others.


## Goals for Week 2
- Maintain 4+ workouts
- Add 5lbs to deadlift
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 8 glasses/day avg', 69.86567304585927, 100.03481316351719, 1300.0, 243.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '33b148f1-2b06-4334-9a6c-fbb900cd185d', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-14', '["14d1944b-fb58-4fc9-97ab-7c00a42871fa", "33b148f1-2b06-4334-9a6c-fbb900cd185d"]', '14d1944b-fb58-4fc9-97ab-7c00a42871fa', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '94bba2cd-529f-4d22-9caa-b31ffec09593', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b71c57be-0d7e-4267-b405-4db69aec3c72', '# Weekly Review - Week 1
Date: February 15, 2026

## This Week''s Wins
- Hit a 5K PR: 24:30
- Great 1:1 with manager - clear path to promotion
- Finally understood distributed consensus

## Challenges Faced
- Felt imposter syndrome during architecture review
- Sleep schedule got off track

## Key Learnings
- Writing things down beats trying to remember

## Time Audit
- Deep work: 19 hours
- Meetings: 14 hours
- Admin/email: 4 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟡 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Continue K8s learning
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Drink more water during the day', 69.68061061754601, 99.15449718519662, 1300.0, 282.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '94bba2cd-529f-4d22-9caa-b31ffec09593', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0476146c-48fa-423f-b053-9df7bf9295f2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'fb755581-ff5a-45db-99fe-ce417d59391a', '# Daily Journal - Sunday
Date: February 15, 2026

## Mood
Steady

## Today''s Highlights
- Had OKR Review - good discussion on priorities
- Mentorship Session went well - got good career advice
- Squeezed in a quick workout

## What I Learned
A new SQL optimization technique - EXISTS vs IN

## Tomorrow''s Calendar
- Retrospective
- Lunch with Friends
- Family Trip to Beach

Need to prepare for these.

## Life Update
Still apartment hunting. Viewed 0 places so far.

## Gratitude
Lucky to have good health', 52.20313687797437, 87.77569652331857, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '0476146c-48fa-423f-b053-9df7bf9295f2', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0f5ce019-7255-4677-b4ad-723ac01d471b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '7502d5c7-4b13-411c-aeee-29ad3f52f4ae', '# Community Service Reflection
Date: February 15, 2026
Organization: Code2Community

## Impact Summary
- **Total volunteer hours**: 2
- **Mentorship weeks**: 1
- **Mentee**: Alex

## Meaningful Moments
Seeing Alex grow from nervous beginner to confident coder

## What I''m Learning
Through mentoring, I''m developing:
- Leadership - taking responsibility for someone''s learning
- Patience - learning to let Alex figure things out

## Why This Matters
Tech changed my life trajectory. Code2Community helps others access the same opportunity.
Giving back feels like paying forward what my mentors gave me.

## Connection to Values
- **Contribution**: Making a real difference in someone''s future
- **Growth**: Teaching deepens my own understanding
- **Connection**: Building a relationship across generations

## Gratitude
Thankful for:
- The Code2Community program coordinators
- Alex''s dedication and curiosity
- The chance to use my skills for something meaningful

## Looking Forward
Can''t wait to see what Alex accomplishes next', 64.11901143298219, 80.3678687570296, 1300.0, 343.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '0f5ce019-7255-4677-b4ad-723ac01d471b', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-15', '["94bba2cd-529f-4d22-9caa-b31ffec09593", "0476146c-48fa-423f-b053-9df7bf9295f2", "0f5ce019-7255-4677-b4ad-723ac01d471b"]', '94bba2cd-529f-4d22-9caa-b31ffec09593', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd240794d-5b6d-4a83-9b4d-528c232d3413', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'fbc57679-47d3-4911-bc6e-15c2b3f21154', '# Daily Planning - Monday
Date: February 16, 2026

## Today''s Calendar
- Retrospective
- Lunch with Friends
- Family Trip to Beach
- Morning Journaling
- UX Review → Bring notes

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1210 implementation
3. Update documentation

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Weekly Sync
- Sales Demo

## Key Events This Week
- Tuesday: Sales Demo
- Wednesday: Performance Review
- Thursday: Sales Demo
- Thursday: Architecture Review

## Personal
- Evening: Check new apartment listings on Zillow

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 51.358925325659605, 90.4426989966129, 1300.0, 280.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd240794d-5b6d-4a83-9b4d-528c232d3413', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b73f34b5-2959-4185-84e0-b069edb82d5b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b4d9b5dd-f1a2-411f-a0d8-69affa219415', '# Learning Log
Date: February 16, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 0/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 1
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 3
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Feeling motivated. The K8s knowledge is already helping at work.', 51.50999638404721, 82.58004238867203, 1300.0, 374.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'b73f34b5-2959-4185-84e0-b069edb82d5b', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-16', '["d240794d-5b6d-4a83-9b4d-528c232d3413", "b73f34b5-2959-4185-84e0-b069edb82d5b"]', 'd240794d-5b6d-4a83-9b4d-528c232d3413', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1431b221-58e2-4d5a-9114-8d4dcb5c9276', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e0406f3c-c72b-412b-995f-bcfe72fd38c4', '# Meeting Prep: Sales Demo
Date: February 17, 2026

## Agenda
1. Project status update
2. Demo new features
3. Q&A and feedback
4. Next steps

## Key Points to Cover
- Auth migration complete - 40% faster login
- Dashboard redesign in progress (Sprint 1)
- Mobile app timeline question

## Demo Flow
1. Show new login experience
2. Walk through dashboard improvements
3. Preview upcoming features

## Potential Questions
- Timeline for mobile? → Q2 tentative
- API rate limits? → 1000 req/min, can discuss enterprise
- Data export? → Coming in Sprint 2

## Materials
- [ ] Slides updated
- [ ] Demo environment ready
- [ ] Backup screenshots in case of issues', 55.08823886232429, 109.5533329760464, 1300.0, 219.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1431b221-58e2-4d5a-9114-8d4dcb5c9276', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-17', '["1431b221-58e2-4d5a-9114-8d4dcb5c9276"]', '1431b221-58e2-4d5a-9114-8d4dcb5c9276', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9c1a56a5-04b0-4873-bf19-87fbcbe1b5d5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '82401fab-b83b-496e-a814-b2c8aa72cfdd', '## 1:1 with Chris
Date: February 18, 2026

### Topics Discussed

**Project Challenges**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Team Dynamics**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: March 04
- Topic: Review proposal draft', 53.51787031283128, 89.58321251281939, 1300.0, 310.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '9c1a56a5-04b0-4873-bf19-87fbcbe1b5d5', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f7387027-5fd9-451c-a61b-670acc02ffb6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'c727cff7-e1d1-416b-a52c-fac7a8241297', '# Daily Journal - Wednesday
Date: February 18, 2026

## Mood
Contemplative

## Today''s Highlights
- Had Performance Review - good discussion on priorities
- 1:1 with Mike went well - got good career advice
- Took a proper lunch break

## What I Learned
Sometimes the simplest solution really is best

## Tomorrow''s Calendar
- Vendor Discussion
- 1:1 with Jordan
- Conference Day

Need to prepare for these.

## Life Update
Still apartment hunting. Viewed 2 places so far.
Guitar week 1: Still working on basic chords. Fingers hurt but improving!

## Gratitude
Lucky to have good health', 63.77924615522352, 101.7101689970851, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'f7387027-5fd9-451c-a61b-670acc02ffb6', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-18', '["9c1a56a5-04b0-4873-bf19-87fbcbe1b5d5", "f7387027-5fd9-451c-a61b-670acc02ffb6"]', '9c1a56a5-04b0-4873-bf19-87fbcbe1b5d5', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '937322c6-68c3-4cd4-9c64-0204c0ea36f8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '037028d8-1755-420c-9738-0ff1ea159953', '# Meeting Prep: Sales Demo
Date: February 19, 2026

## Agenda
1. Project status update
2. Demo new features
3. Q&A and feedback
4. Next steps

## Key Points to Cover
- Auth migration complete - 40% faster login
- Dashboard redesign in progress (Sprint 1)
- Mobile app timeline question

## Demo Flow
1. Show new login experience
2. Walk through dashboard improvements
3. Preview upcoming features

## Potential Questions
- Timeline for mobile? → Q2 tentative
- API rate limits? → 1000 req/min, can discuss enterprise
- Data export? → Coming in Sprint 2

## Materials
- [ ] Slides updated
- [ ] Demo environment ready
- [ ] Backup screenshots in case of issues', 55.502113109370136, 92.88086687107929, 1300.0, 219.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '937322c6-68c3-4cd4-9c64-0204c0ea36f8', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '883f0bd1-aab4-48f0-a63e-30a1639fad3f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f8ab453f-7a51-4eb7-bd27-848a339f4739', '# Learning Log
Date: February 19, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 2/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 2
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 5
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Excited about the conference opportunity - great motivation to learn.', 64.26226381340253, 81.17613248139261, 1300.0, 376.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '883f0bd1-aab4-48f0-a63e-30a1639fad3f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-19', '["937322c6-68c3-4cd4-9c64-0204c0ea36f8", "883f0bd1-aab4-48f0-a63e-30a1639fad3f"]', '937322c6-68c3-4cd4-9c64-0204c0ea36f8', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9b22ca05-b833-4d98-b239-f24d033080a1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0cddc49b-f7ee-478a-833b-f05a676da2a5', '## Daily Standup - Friday
Date: February 20, 2026

### My Updates

**Yesterday:**
- Completed PR #2345 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1210
- Address code review feedback on #2346
- Sync with Jordan on deployment pipeline

**Blockers:**
- Dependency update broke tests - fixing

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 68.13425471751341, 81.58578198422704, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '9b22ca05-b833-4d98-b239-f24d033080a1', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cb16649c-217a-41e6-bc3e-7dbe878b8f26', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'cc5ec149-238c-474a-b74f-01620f654654', '# Weekly Review - Week 2
Date: February 20, 2026

## This Week''s Wins
- Finally understood distributed consensus
- Shipped the auth migration on time
- Helped onboard the new team member

## Challenges Faced
- Felt imposter syndrome during architecture review
- Difficult conversation with stakeholder

## Key Learnings
- Saying no to meetings protects deep work time

## Time Audit
- Deep work: 15 hours
- Meetings: 13 hours
- Admin/email: 5 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟡 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Work on side project
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Drink more water during the day', 61.91464656876677, 93.29596808574793, 1300.0, 286.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'cb16649c-217a-41e6-bc3e-7dbe878b8f26', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-20', '["9b22ca05-b833-4d98-b239-f24d033080a1", "cb16649c-217a-41e6-bc3e-7dbe878b8f26"]', '9b22ca05-b833-4d98-b239-f24d033080a1', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2d9ecf2d-3580-4c14-bb7f-a82322d0f3db', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b7cb4d97-bd12-4aec-a50d-1ea8bc9d5119', '# Side Project: TaskFlow
Phase: Brainstorming
Date: February 21, 2026

## Concept Exploration

### The Idea: TaskFlow
A minimalist task manager that learns your patterns and suggests optimal scheduling.

### Why This?
- Frustrated with complex tools (Notion, Asana)
- Want something that "just works"
- Opportunity to learn React Native

### Core Features (MVP)
1. Quick task capture (< 2 seconds)
2. Smart due date suggestions
3. Daily focus mode
4. Simple recurring tasks

### Tech Stack Decisions
- **Frontend**: React Native (cross-platform)
- **Backend**: Supabase (fast to set up)
- **State**: Zustand (simple, performant)

### Competition Research
- Todoist: Too many features
- Things 3: Mac only, expensive
- TickTick: Closest to my vision

### Next Steps
- [ ] Sketch initial wireframes
- [ ] Set up React Native environment
- [ ] Create GitHub repo', 56.79330958957226, 99.9751436758675, 1300.0, 286.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '2d9ecf2d-3580-4c14-bb7f-a82322d0f3db', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '80d6e244-e521-4e5f-a6a7-2f741a68a72e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a704534f-ca3d-4bb8-a36d-4722f0d2c77c', '# Fitness Log - Week 2
Date: February 21, 2026

## Weekly Summary
- **Workouts completed**: 4/5 planned
- **Gym streak**: 8 days
- **Sleep avg**: 6.9 hours

## Workouts This Week
- Monday: 5K run (24:11)
- Wednesday: Strength training (Deadlift: 187lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 24:11 (was 24:26) 
- Deadlift: 187lbs (+2lbs)
- Body weight: 174.4lbs

## How I''m Feeling
Building the habit. Some days are harder than others.


## Goals for Week 3
- Maintain 4+ workouts
- Try the new HIIT class
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 7 glasses/day avg', 55.30444888711512, 104.30320174448563, 1300.0, 244.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '80d6e244-e521-4e5f-a6a7-2f741a68a72e', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4fa58f48-a57b-461a-9ffb-d51a5c8c807a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8af90a41-f2de-4b97-ad3f-6ace3f545a05', '# Quarterly Life Review - Q1
Date: February 21, 2026

## Core Values Check
Am I living aligned with what matters most?

### My Values
1. **Growth** - Continuously learning and improving
2. **Balance** - Sustainable pace, not burnout
3. **Contribution** - Making a positive difference
4. **Connection** - Meaningful relationships

## Life Areas Review

### Career
**Goals**: Get promoted to senior engineer, Launch side project
**Progress**: Making progress

### Health
**Goals**: Run sub-23 5K, Consistent gym habit
**Progress**: Maintaining

### Relationships
**Goals**: Deepen relationship with Morgan, Stay connected with family
**Progress**: Feeling connected

### Growth
**Goals**: Read 12 books this year, Learn Kubernetes
**Progress**: Ahead of schedule

### Contribution
**Goals**: Mentor Alex successfully, Volunteer 50 hours
**Progress**: Going well


## Wins This Quarter
- Promoted to mid-level engineer
- Better work-life boundaries
- Improved sleep habits

## Areas for Growth
- More present during family time
- Ask for help sooner

## Intention for Next Quarter
Protect energy by saying no to things that don''t align

## Gratitude Reflection
Taking a moment to appreciate:
- Health and energy to pursue my goals
- Supportive relationships (Morgan, family, friends)
- Meaningful work and growth opportunities
- The ability to give back through mentoring

## One Word for Next Quarter
**Presence**', 65.34388622802693, 104.1953037650098, 1300.0, 470.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '4fa58f48-a57b-461a-9ffb-d51a5c8c807a', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-21', '["2d9ecf2d-3580-4c14-bb7f-a82322d0f3db", "80d6e244-e521-4e5f-a6a7-2f741a68a72e", "4fa58f48-a57b-461a-9ffb-d51a5c8c807a"]', '2d9ecf2d-3580-4c14-bb7f-a82322d0f3db', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e731aa70-3000-4345-a2fa-25a049eaac6d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8fef05d8-fdc4-4e42-a398-deea667978e7', '# Weekly Review - Week 2
Date: February 22, 2026

## This Week''s Wins
- Helped onboard the new team member
- Side project reached Design & Architecture phase
- Finally understood distributed consensus

## Challenges Faced
- Difficult conversation with stakeholder
- Struggled with focus during afternoon meetings

## Key Learnings
- Small consistent progress beats sporadic big efforts

## Time Audit
- Deep work: 18 hours
- Meetings: 10 hours
- Admin/email: 6 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟡 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟡 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Work on side project
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Batch email checking to 2x/day', 66.24109810343265, 109.33349151685816, 1300.0, 294.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'e731aa70-3000-4345-a2fa-25a049eaac6d', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '16581d63-1fe7-4b49-8cc1-3c3b9613a81d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '7cf3d490-2754-40fc-a93e-c06fc96814b5', '# Daily Journal - Sunday
Date: February 22, 2026

## Mood
Contemplative

## Today''s Highlights
- Had Design Review - good discussion on priorities
- Took a proper lunch break

## What I Learned
How to use React DevTools profiler effectively

## Tomorrow''s Calendar
- Client Call - TechStart
- Documentation Review
- Code2Community Mentoring

Need to prepare for these.

## Life Update
Found a great apartment in the Mission! 1BR with good natural light.
Dating Morgan - things are going well. Date #2 coming up.
Guitar week 1: Still working on basic chords. Fingers hurt but improving!
Health: Physical done - everything looks good, cholesterol slightly high

## Gratitude
Lucky to have good health', 69.40966047979667, 91.9449732704113, 1300.0, 232.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '16581d63-1fe7-4b49-8cc1-3c3b9613a81d', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-22', '["e731aa70-3000-4345-a2fa-25a049eaac6d", "16581d63-1fe7-4b49-8cc1-3c3b9613a81d"]', 'e731aa70-3000-4345-a2fa-25a049eaac6d', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c019e3e3-3bc0-4966-b552-d7da9774d305', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '9ef7b04e-d0e8-4074-8934-257a863a2744', '# Daily Planning - Monday
Date: February 23, 2026

## Today''s Calendar
- Client Call - TechStart
- Documentation Review → Bring notes
- Code2Community Mentoring
- Help Emma with Science Fair
- Dinner with Family

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1210 implementation
3. Address tech debt item

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Career Discussion
- 1:1 with Mike
- Interview - PM

## Key Events This Week
- Tuesday: Interview - PM
- Wednesday: Sales Demo
- Wednesday: Demo Day
- Wednesday: API Review



## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 69.21572859989419, 109.37144780803013, 1300.0, 276.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c019e3e3-3bc0-4966-b552-d7da9774d305', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ab14b369-29c8-44b2-bae2-505f388d36ca', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '9f667213-7294-42f9-a323-00439f936dfa', '# Learning Log
Date: February 23, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 3/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 3
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 8
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Feeling motivated. The K8s knowledge is already helping at work.', 57.56193404902551, 85.86928209852103, 1300.0, 374.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'ab14b369-29c8-44b2-bae2-505f388d36ca', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-23', '["c019e3e3-3bc0-4966-b552-d7da9774d305", "ab14b369-29c8-44b2-bae2-505f388d36ca"]', 'c019e3e3-3bc0-4966-b552-d7da9774d305', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ec20225f-427c-424b-915b-01876a478b75', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e12f0bca-7691-4857-a1c0-fcaeb55edc4c', '# Interview Prep: Interview - PM
Date: February 24, 2026

## My Questions
1. Technical background and experience
2. System design: "Design a URL shortener"
3. Coding: Implement LRU cache
4. Behavioral: Tell me about a difficult project

## Evaluation Criteria
- Technical depth
- Problem-solving approach
- Communication clarity
- Cultural fit

## Notes Space
[Space for interview notes]

## Follow-up
- [ ] Submit feedback within 24 hours
- [ ] Discuss with Mike if strong candidate', 55.50696868298612, 89.00586907212491, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'ec20225f-427c-424b-915b-01876a478b75', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-24', '["ec20225f-427c-424b-915b-01876a478b75"]', 'ec20225f-427c-424b-915b-01876a478b75', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '85fce8b4-81e7-4f24-850d-f44a3e938e40', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b8f7391b-f8aa-4c15-afcb-45b980f83e29', '## 1:1 with Chris
Date: February 25, 2026

### Topics Discussed

**Skill Development**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Conference Attendance**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: March 11
- Topic: Review proposal draft', 50.05414058176316, 88.0977324750661, 1300.0, 312.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '85fce8b4-81e7-4f24-850d-f44a3e938e40', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1cde4f5d-65b7-4c36-8677-d622bf34b071', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8108dddb-10ed-4062-9381-9a97bec84651', '# Daily Journal - Wednesday
Date: February 25, 2026

## Mood
Focused

## Today''s Highlights
- Had Sales Demo - good discussion on priorities
- 1:1 with Mike went well - got good career advice
- Took a proper lunch break

## What I Learned
The importance of clear commit messages (got confused by old code)

## Tomorrow''s Calendar
- Project Review
- Weekly Sync
- Morning Journaling

Need to prepare for these.

## Life Update
Found a great apartment in the Mission! 1BR with good natural light.
Dating Morgan - things are going well. Date #3 coming up.
Guitar week 2: 7 practice sessions. Learning: Wonderwall (intro)

## Gratitude
Thankful for the little things - good coffee, sunny weather', 67.7467574778202, 85.77809975787576, 1300.0, 230.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1cde4f5d-65b7-4c36-8677-d622bf34b071', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f7c28353-91b4-4457-8d58-2c3277f55501', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0a413d2a-7c3d-4986-82a6-8e0b4a88221a', '# Mentorship Session - Week 3
Date: February 25, 2026
Mentee: Alex
Organization: Code2Community

## Today''s Topic
**Object-oriented programming**: classes, inheritance

## Session Notes
- Started with review of last week''s homework
- Alex got confused about loop logic
- Alex explained the concept back to me correctly
- Assigned practice exercises for the week

## Alex''s Progress
- **Confidence**: Growing
- **Problem-solving**: Learning to break down problems
- **Questions**: Good curiosity

## Homework Assigned
- [ ] Complete 3 practice problems on object-oriented programming
- [ ] Read one chapter of "Automate the Boring Stuff"
- [ ] Try building a small script using today''s concepts

## My Reflections
It''s rewarding to see Alex''s growth. Teaching helps me understand concepts more deeply too.
Learning to explain things simply is a skill.

## Next Session
- Continue with project work
- Review homework together
- Hours logged: 2', 52.95073389470473, 90.0139967678733, 1300.0, 313.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'f7c28353-91b4-4457-8d58-2c3277f55501', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-25', '["85fce8b4-81e7-4f24-850d-f44a3e938e40", "1cde4f5d-65b7-4c36-8677-d622bf34b071", "f7c28353-91b4-4457-8d58-2c3277f55501"]', '85fce8b4-81e7-4f24-850d-f44a3e938e40', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '99fdf2ef-dd5c-48f0-9795-6e22a43437c2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f0028676-55c1-48dd-8678-5f5305337501', '# Meeting Prep: Project Review
Date: February 26, 2026

## My Updates
- Sprint 2 progress: on track
- PR #2357 merged
- Blocker: waiting on Taylor for mockups

## Discussion Points
1. Timeline for phase 2
2. Resource allocation
3. Tech debt priorities

## Questions to Raise
- Clarity on Q2 roadmap
- Budget for new tooling

## Action Items from Last Time
- [x] Complete API documentation
- [ ] Schedule follow-up with Jordan', 67.4868553025502, 80.36755301162928, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '99fdf2ef-dd5c-48f0-9795-6e22a43437c2', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '490c908e-c9fc-4665-91cb-b1aeb1d95f37', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'eb8226c1-82fa-40c9-b68a-9023588e71a9', '# Learning Log
Date: February 26, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 3/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 3
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 8
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
The book is dense but worth it. Taking good notes.', 59.82155862115056, 90.4870169938907, 1300.0, 370.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '490c908e-c9fc-4665-91cb-b1aeb1d95f37', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-26', '["99fdf2ef-dd5c-48f0-9795-6e22a43437c2", "490c908e-c9fc-4665-91cb-b1aeb1d95f37"]', '99fdf2ef-dd5c-48f0-9795-6e22a43437c2', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5112c25c-b2d4-4d9b-969a-d39876ab2aa1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'd54f732c-cdc3-4829-a055-e3575e90fc55', '## Daily Standup - Friday
Date: February 27, 2026

### My Updates

**Yesterday:**
- Completed PR #2358 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1220
- Address code review feedback on #2359
- Sync with Jordan on deployment pipeline

**Blockers:**
- CI pipeline flaky - investigating

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 66.32733693311651, 96.16982181429063, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5112c25c-b2d4-4d9b-969a-d39876ab2aa1', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2a3e841f-c04b-4e81-afcf-2b8a465e5b54', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '17f0d596-d709-4ade-bc03-bc2708a7a5b7', '# Weekly Review - Week 3
Date: February 27, 2026

## This Week''s Wins
- Hit a 5K PR: 23:58
- Finally understood distributed consensus
- Helped onboard the new team member

## Challenges Faced
- Side project progress slower than hoped
- Struggled with focus during afternoon meetings

## Key Learnings
- Writing things down beats trying to remember

## Time Audit
- Deep work: 15 hours
- Meetings: 11 hours
- Admin/email: 4 hours
- Learning: 4 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🔴 Mid-week slump
- Thu: 🟡 Recovered
- Fri: 🟡 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Continue K8s learning
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Drink more water during the day', 50.395000849619855, 94.318132461588, 1300.0, 280.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '2a3e841f-c04b-4e81-afcf-2b8a465e5b54', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-27', '["5112c25c-b2d4-4d9b-969a-d39876ab2aa1", "2a3e841f-c04b-4e81-afcf-2b8a465e5b54"]', '5112c25c-b2d4-4d9b-969a-d39876ab2aa1', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '33ae3f35-26b4-4f54-b4f2-f68937f187b4', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e64d4618-4fef-4269-9a36-96160cddee37', '# Side Project: TaskFlow
Phase: Design & Architecture
Date: February 28, 2026

## TaskFlow - Design Phase

### Wireframes Complete ✓
- Home screen with today''s tasks
- Quick add modal
- Settings page
- Weekly review screen

### Technical Architecture
```
React Native App
    ↓
Zustand Store → AsyncStorage (offline)
    ↓
Supabase (sync when online)
```

### Database Schema
- users (id, email, preferences)
- tasks (id, title, due_date, completed, user_id)
- patterns (id, user_id, time_preferences)

### Design System
- Colors: Neutral grays + accent blue
- Typography: Inter for UI, monospace for times
- Spacing: 8px grid

### Commits This Week: 19

### Blockers
- Deciding on navigation library (React Navigation vs Expo Router)
- Need to research offline-first patterns

### This Weekend
- [ ] Finalize component library
- [ ] Set up Supabase project
- [ ] Create initial screens', 50.906558999305155, 89.74907881765333, 1300.0, 295.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '33ae3f35-26b4-4f54-b4f2-f68937f187b4', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '9db89ec9-06f9-4bb0-b586-e6a7a27581fc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '15b253cd-2abc-48e6-a263-a7b59ef2513a', '# Fitness Log - Week 3
Date: February 28, 2026

## Weekly Summary
- **Workouts completed**: 4/5 planned
- **Gym streak**: 12 days
- **Sleep avg**: 6.7 hours

## Workouts This Week
- Monday: 5K run (23:58)
- Wednesday: Strength training (Deadlift: 192lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 23:58 (was 24:28) 
- Deadlift: 192lbs (+2lbs)
- Body weight: 174.1lbs

## How I''m Feeling
Building the habit. Some days are harder than others.


## Goals for Week 4
- Maintain 4+ workouts
- Add 5lbs to deadlift
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 9 glasses/day avg', 57.09962719388163, 90.60344593421421, 1300.0, 243.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '9db89ec9-06f9-4bb0-b586-e6a7a27581fc', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-02-28', '["33ae3f35-26b4-4f54-b4f2-f68937f187b4", "9db89ec9-06f9-4bb0-b586-e6a7a27581fc"]', '33ae3f35-26b4-4f54-b4f2-f68937f187b4', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c0285f9a-050c-4651-adfd-c9162c30033d', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '02190b40-625e-4775-b4dc-2e8456487598', '# Weekly Review - Week 3
Date: March 01, 2026

## This Week''s Wins
- Great 1:1 with manager - clear path to promotion
- Finally understood distributed consensus
- Helped onboard the new team member

## Challenges Faced
- Difficult conversation with stakeholder
- Struggled with focus during afternoon meetings

## Key Learnings
- Writing things down beats trying to remember

## Time Audit
- Deep work: 21 hours
- Meetings: 13 hours
- Admin/email: 5 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Prepare presentation
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 53.3086350139371, 109.17312709898121, 1300.0, 294.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c0285f9a-050c-4651-adfd-c9162c30033d', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f0e68cc1-cade-43b6-91ea-2e2da4a03119', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '7ab25f0f-e0ba-4802-9cbf-156405516883', '# Daily Journal - Sunday
Date: March 01, 2026

## Mood
Productive

## Today''s Highlights
- Had Project Review - good discussion on priorities
- Got outside for a 20 min walk

## What I Learned
Sometimes the simplest solution really is best

## Tomorrow''s Calendar
- School Holiday - MLK Day
- Morning Journaling
- All-Hands Meeting

Need to prepare for these.

## Life Update
Found a great apartment in the Mission! 1BR with good natural light.
Dating Morgan - things are going well. Date #3 coming up.
Guitar week 2: 10 practice sessions. Learning: Wonderwall (intro)

## Gratitude
Thankful for the little things - good coffee, sunny weather', 65.40487842441428, 97.14091192560056, 1300.0, 214.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'f0e68cc1-cade-43b6-91ea-2e2da4a03119', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-01', '["c0285f9a-050c-4651-adfd-c9162c30033d", "f0e68cc1-cade-43b6-91ea-2e2da4a03119"]', 'c0285f9a-050c-4651-adfd-c9162c30033d', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '65db9349-1c45-4e8f-8455-9e774ead6e4f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8dea74e2-97b2-42fd-9cbb-80743d5a33d5', '# Daily Planning - Monday
Date: March 02, 2026

## Today''s Calendar
- School Holiday - MLK Day
- Morning Journaling
- All-Hands Meeting
- Work From Home
- Vacation Day

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1220 implementation
3. Write unit tests

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Work From Home
- 1:1 with Sarah
- 1:1 with Jordan

## Key Events This Week
- Wednesday: Client Call - Acme Corp
- Wednesday: Code Review Session
- Thursday: Client Call - TechStart
- Thursday: Client Call - Acme Corp



## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 69.21991810215934, 93.30958699536522, 1300.0, 275.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '65db9349-1c45-4e8f-8455-9e774ead6e4f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bc15808d-76f7-43f1-8a38-255c9df20af6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3ad0f498-3f69-40a2-94c8-e5ce3f542740', '# Learning Log
Date: March 02, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 3/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 3
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 8
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Feeling motivated. The K8s knowledge is already helping at work.', 62.66645538113994, 99.71685058670568, 1300.0, 373.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'bc15808d-76f7-43f1-8a38-255c9df20af6', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-02', '["65db9349-1c45-4e8f-8455-9e774ead6e4f", "bc15808d-76f7-43f1-8a38-255c9df20af6"]', '65db9349-1c45-4e8f-8455-9e774ead6e4f', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6c3ff826-19ca-4c5e-917d-8b0edb72e480', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0df91a1a-bb5c-4b44-8f19-f91853906b50', '## Code Review Session
Date: March 03, 2026

### PRs Reviewed

**PR #2362: Auth Service Refactor** (by Mike)
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #2363: API Rate Limiting** (by me)
- Alex caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #2364: Dashboard Components** (by Taylor)
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: 4m 23s (down from last week)
- Test coverage: 94.8%
- Lint errors: 0
- Bundle size: 2.00MB

### Patterns to Adopt
- Error boundary pattern from #2362
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components', 55.22615554122001, 104.2327273666222, 1300.0, 309.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '6c3ff826-19ca-4c5e-917d-8b0edb72e480', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5ec9efdf-4913-4801-b9be-497de99eaa13', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '37e11a48-aa93-4698-a7c0-18914b7196aa', '# Side Project: TaskFlow
Phase: Design & Architecture
Date: March 03, 2026

## TaskFlow - Design Phase

### Wireframes Complete ✓
- Home screen with today''s tasks
- Quick add modal
- Settings page
- Weekly review screen

### Technical Architecture
```
React Native App
    ↓
Zustand Store → AsyncStorage (offline)
    ↓
Supabase (sync when online)
```

### Database Schema
- users (id, email, preferences)
- tasks (id, title, due_date, completed, user_id)
- patterns (id, user_id, time_preferences)

### Design System
- Colors: Neutral grays + accent blue
- Typography: Inter for UI, monospace for times
- Spacing: 8px grid

### Commits This Week: 22

### Blockers
- Deciding on navigation library (React Navigation vs Expo Router)
- Need to research offline-first patterns

### This Weekend
- [ ] Finalize component library
- [ ] Set up Supabase project
- [ ] Create initial screens', 56.74414213935012, 107.31944217972378, 1300.0, 294.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5ec9efdf-4913-4801-b9be-497de99eaa13', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-03', '["6c3ff826-19ca-4c5e-917d-8b0edb72e480", "5ec9efdf-4913-4801-b9be-497de99eaa13"]', '6c3ff826-19ca-4c5e-917d-8b0edb72e480', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '45893a40-6c60-40a3-8d3c-5d61ddc349d2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '2915be13-ae9c-4901-a190-10f08091a5a1', '## 1:1 with Chris
Date: March 04, 2026

### Topics Discussed

**Work-Life Balance**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Pto Planning**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: March 18
- Topic: Review proposal draft', 57.83712821970319, 97.2960765607187, 1300.0, 308.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '45893a40-6c60-40a3-8d3c-5d61ddc349d2', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '45559a1c-4b7d-4a4d-8a09-693abb6e73fd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0c6e7168-8d33-49c9-9b5b-29a371fa7f32', '# Daily Journal - Wednesday
Date: March 04, 2026

## Mood
Contemplative

## Today''s Highlights
- Had Code Review Session - good discussion on priorities
- 1:1 with Taylor went well - got good career advice
- Took a proper lunch break

## What I Learned
A new SQL optimization technique - EXISTS vs IN

## Tomorrow''s Calendar
- Client Call - TechStart
- Book Club Meeting
- Meditation Session

Need to prepare for these.

## Life Update
Lease signed! Moving in on March 24.
Dating Morgan - things are going well. Date #4 coming up.
Guitar week 3: 11 practice sessions. Learning: Wonderwall (intro)

## Gratitude
Appreciate having time for my hobbies', 55.991809108838396, 107.82755353249783, 1300.0, 216.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '45559a1c-4b7d-4a4d-8a09-693abb6e73fd', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-04', '["45893a40-6c60-40a3-8d3c-5d61ddc349d2", "45559a1c-4b7d-4a4d-8a09-693abb6e73fd"]', '45893a40-6c60-40a3-8d3c-5d61ddc349d2', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c2a26f73-7336-4991-bffe-06ebb5184ef6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '55e6c3c5-74be-40d7-80b6-a40aaa45f87b', '# Meeting Prep: Client Call - TechStart
Date: March 05, 2026

## Agenda
1. Project status update
2. Demo new features
3. Q&A and feedback
4. Next steps

## Key Points to Cover
- Auth migration complete - 40% faster login
- Dashboard redesign in progress (Sprint 2)
- Mobile app timeline question

## Demo Flow
1. Show new login experience
2. Walk through dashboard improvements
3. Preview upcoming features

## Potential Questions
- Timeline for mobile? → Q2 tentative
- API rate limits? → 1000 req/min, can discuss enterprise
- Data export? → Coming in Sprint 3

## Materials
- [ ] Slides updated
- [ ] Demo environment ready
- [ ] Backup screenshots in case of issues', 62.37692696339225, 103.75958462539278, 1300.0, 223.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c2a26f73-7336-4991-bffe-06ebb5184ef6', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'eeb7c5eb-1348-44bc-8204-04ab84d91390', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8114fda3-f2ec-4501-aa93-9e38c20764ce', '# Learning Log
Date: March 05, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 5/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 3
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 10
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 64.29597131413117, 90.5190534297892, 1300.0, 373.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'eeb7c5eb-1348-44bc-8204-04ab84d91390', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-05', '["c2a26f73-7336-4991-bffe-06ebb5184ef6", "eeb7c5eb-1348-44bc-8204-04ab84d91390"]', 'c2a26f73-7336-4991-bffe-06ebb5184ef6', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fd490d3b-ce2a-43ed-8754-2fdec57ebee5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3e71fb71-bc0e-464f-9bd2-e65c6dceb8a9', '## Daily Standup - Friday
Date: March 06, 2026

### My Updates

**Yesterday:**
- Completed PR #2356 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1220
- Address code review feedback on #2357
- Sync with Jordan on deployment pipeline

**Blockers:**
- Dependency update broke tests - fixing

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 69.93726937418278, 93.8144679976718, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'fd490d3b-ce2a-43ed-8754-2fdec57ebee5', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '08b028ad-49a5-46d9-8616-af92dd277b05', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '87c78d70-ad57-4f7c-bf73-b1670599ec42', '# Weekly Review - Week 4
Date: March 06, 2026

## This Week''s Wins
- Helped onboard the new team member
- Side project reached MVP Development phase
- Shipped the auth migration on time

## Challenges Faced
- Sleep schedule got off track
- Difficult conversation with stakeholder

## Key Learnings
- Small consistent progress beats sporadic big efforts

## Time Audit
- Deep work: 17 hours
- Meetings: 12 hours
- Admin/email: 5 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟡 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Prepare presentation
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 61.01459530639146, 87.21845482906983, 1300.0, 288.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '08b028ad-49a5-46d9-8616-af92dd277b05', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-06', '["fd490d3b-ce2a-43ed-8754-2fdec57ebee5", "08b028ad-49a5-46d9-8616-af92dd277b05"]', 'fd490d3b-ce2a-43ed-8754-2fdec57ebee5', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c6c10e0a-1e2d-4c25-827c-af8e344f6b03', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '86a13cf0-3fa0-4e48-994a-bbe1935ae399', '# Side Project: TaskFlow
Phase: MVP Development
Date: March 07, 2026

## TaskFlow - MVP Sprint

### Progress Update
Building out the core functionality. 26 commits so far.

### Completed This Week
- [x] Basic task CRUD operations
- [x] Local storage with AsyncStorage
- [x] Home screen UI
- [x] Quick add functionality

### In Progress
- [ ] Due date picker component
- [ ] Supabase auth integration
- [ ] Push notifications setup

### Code Snippet - Quick Add
```typescript
const addTask = async (title: string) => {
  const task = {
    id: nanoid(),
    title,
    createdAt: Date.now(),
    completed: false,
  };
  await store.addTask(task);
};
```

### Challenges Faced
- React Native debugging is painful
- Expo EAS build queue times slow
- Keyboard handling on Android different from iOS

### Metrics
- Bundle size: 12MB (need to optimize)
- Cold start: 1.2s
- Test coverage: 45%

### Next Week Goals
- Complete Supabase sync
- Add onboarding flow
- Internal alpha release', 51.06636393949688, 106.3051158228611, 1300.0, 326.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c6c10e0a-1e2d-4c25-827c-af8e344f6b03', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f90625dc-9b72-4548-8d39-9531f1f3b9c9', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e8c2598b-524f-4bf5-99ab-4dd8f42bdea6', '# Fitness Log - Week 4
Date: March 07, 2026

## Weekly Summary
- **Workouts completed**: 3/5 planned
- **Gym streak**: 16 days
- **Sleep avg**: 7.1 hours

## Workouts This Week
- Monday: 5K run (23:48)
- Wednesday: Strength training (Deadlift: 192lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 23:48 (was 24:14) 
- Deadlift: 192lbs (+2lbs)
- Body weight: 173.8lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.


## Goals for Week 5
- Push for 5 workouts
- Try the new HIIT class
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 9 glasses/day avg', 66.66042821954719, 106.21649851862652, 1300.0, 242.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'f90625dc-9b72-4548-8d39-9531f1f3b9c9', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7e901c34-5c6d-42a8-90f1-5e681062c201', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '38274787-5f11-461a-a07c-501f7f15ae73', '# Quarterly Life Review - Q1
Date: March 07, 2026

## Core Values Check
Am I living aligned with what matters most?

### My Values
1. **Growth** - Continuously learning and improving
2. **Balance** - Sustainable pace, not burnout
3. **Contribution** - Making a positive difference
4. **Connection** - Meaningful relationships

## Life Areas Review

### Career
**Goals**: Get promoted to senior engineer, Launch side project
**Progress**: Need more focus

### Health
**Goals**: Run sub-23 5K, Consistent gym habit
**Progress**: Maintaining

### Relationships
**Goals**: Deepen relationship with Morgan, Stay connected with family
**Progress**: Could be better

### Growth
**Goals**: Read 12 books this year, Learn Kubernetes
**Progress**: Ahead of schedule

### Contribution
**Goals**: Mentor Alex successfully, Volunteer 50 hours
**Progress**: Most fulfilling area


## Wins This Quarter
- Promoted to mid-level engineer
- Strong mentorship relationship
- Improved sleep habits

## Areas for Growth
- Reduce screen time
- Ask for help sooner

## Intention for Next Quarter
Protect energy by saying no to things that don''t align

## Gratitude Reflection
Taking a moment to appreciate:
- Health and energy to pursue my goals
- Supportive relationships (Morgan, family, friends)
- Meaningful work and growth opportunities
- The ability to give back through mentoring

## One Word for Next Quarter
**Focus**', 66.6898000030896, 98.8213326654035, 1300.0, 467.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7e901c34-5c6d-42a8-90f1-5e681062c201', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-07', '["c6c10e0a-1e2d-4c25-827c-af8e344f6b03", "f90625dc-9b72-4548-8d39-9531f1f3b9c9", "7e901c34-5c6d-42a8-90f1-5e681062c201"]', 'c6c10e0a-1e2d-4c25-827c-af8e344f6b03', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4c6601db-de55-4f3d-8794-24b741f60ffa', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f55e5c53-3378-40c5-a0d7-6c827616ede8', '# Weekly Review - Week 4
Date: March 08, 2026

## This Week''s Wins
- Side project reached MVP Development phase
- Hit a 5K PR: 23:48
- Shipped the auth migration on time

## Challenges Faced
- Sleep schedule got off track
- Felt imposter syndrome during architecture review

## Key Learnings
- Saying no to meetings protects deep work time

## Time Audit
- Deep work: 22 hours
- Meetings: 8 hours
- Admin/email: 4 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟡 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟡 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Prepare presentation
3. Maintain fitness routine

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 52.34595649876033, 90.84279846407047, 1300.0, 283.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '4c6601db-de55-4f3d-8794-24b741f60ffa', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'abfb1a10-ed0e-4b77-bf40-542f8e48ca8c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '4983251e-4440-4340-a608-348a40a4b9bd', '# Daily Journal - Sunday
Date: March 08, 2026

## Mood
Focused

## Today''s Highlights
- Had Team Standup - good discussion on priorities
- Took a proper lunch break

## What I Learned
The importance of clear commit messages (got confused by old code)

## Tomorrow''s Calendar
- 1:1 with Manager
- Customer Success Check-in
- Gym Session

Need to prepare for these.

## Life Update
Lease signed! Moving in on March 24.
Made it official with Morgan! Feeling happy.
Guitar week 3: 14 practice sessions. Learning: Wonderwall (intro)

## Gratitude
Grateful for the supportive team at work', 60.860795432699376, 91.42355005623608, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'abfb1a10-ed0e-4b77-bf40-542f8e48ca8c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-08', '["4c6601db-de55-4f3d-8794-24b741f60ffa", "abfb1a10-ed0e-4b77-bf40-542f8e48ca8c"]', '4c6601db-de55-4f3d-8794-24b741f60ffa', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8b20dcc4-f27b-4b37-9a4b-def14e7c4757', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a5269dde-da2e-461d-87ff-e355dfa5575e', '# Daily Planning - Monday
Date: March 09, 2026

## Today''s Calendar
- 1:1 with Manager → Prepare talking points
- Customer Success Check-in
- Gym Session
- Dinner with Family
- Mentorship Session

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1220 implementation
3. Update documentation

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Team Lunch
- Vendor Discussion
- Food Bank Volunteering

## Key Events This Week
- Wednesday: OKR Review
- Wednesday: Sprint Planning

## Personal
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 58.82868596786796, 81.55970986258598, 1300.0, 273.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '8b20dcc4-f27b-4b37-9a4b-def14e7c4757', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0efa7b37-6694-44fb-aa90-853545d11699', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b9f4f132-8352-4361-a7b1-7f37d364fea0', '# Learning Log
Date: March 09, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 7/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 3
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 12
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Excited about the conference opportunity - great motivation to learn.', 52.21354563116072, 85.23120438616688, 1300.0, 375.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '0efa7b37-6694-44fb-aa90-853545d11699', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-09', '["8b20dcc4-f27b-4b37-9a4b-def14e7c4757", "0efa7b37-6694-44fb-aa90-853545d11699"]', '8b20dcc4-f27b-4b37-9a4b-def14e7c4757', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a7d1d4bc-aea6-4423-8cf0-88bbd6c9b613', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '6335c9f8-8371-4559-9a56-0d69c57916ca', '## Daily Standup - Tuesday
Date: March 10, 2026

### My Updates

**Yesterday:**
- Completed PR #2355 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1230
- Address code review feedback on #2356
- Sync with Jordan on deployment pipeline

**Blockers:**
- Dependency update broke tests - fixing

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 68.15458497364419, 97.24407010488633, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'a7d1d4bc-aea6-4423-8cf0-88bbd6c9b613', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-10', '["a7d1d4bc-aea6-4423-8cf0-88bbd6c9b613"]', 'a7d1d4bc-aea6-4423-8cf0-88bbd6c9b613', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '820005dc-dd42-492f-9e71-57769d23ad59', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'ea6d19b9-fadc-4d2e-b28e-5855a82a8235', '## 1:1 with Chris
Date: March 11, 2026

### Topics Discussed

**Feedback Session**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Performance Review Prep**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: March 25
- Topic: Review proposal draft', 51.112216899550646, 98.54287068238233, 1300.0, 312.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '820005dc-dd42-492f-9e71-57769d23ad59', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd7bfb292-dd04-4abd-ab6d-f9aa70faca70', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b73616eb-c8f5-41e0-ac27-96d5f1a9e1fd', '# Daily Journal - Wednesday
Date: March 11, 2026

## Mood
Steady

## Today''s Highlights
- Had OKR Review - good discussion on priorities
- 1:1 with Taylor went well - got good career advice
- Took a proper lunch break

## What I Learned
Sometimes the simplest solution really is best

## Tomorrow''s Calendar
- Physical Therapy
- 1:1 with Sarah
- Parent-Teacher Conference

Need to prepare for these.

## Life Update
Lease signed! Moving in on March 24.
Made it official with Morgan! Feeling happy.
Guitar week 4: 16 practice sessions. Learning: Wonderwall (intro), Horse With No Name

## Gratitude
Thankful for the little things - good coffee, sunny weather', 58.756338999452694, 99.78997292114279, 1300.0, 219.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd7bfb292-dd04-4abd-ab6d-f9aa70faca70', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5c25cb75-c28f-4a75-9c19-a65468d1be80', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '9f7b904e-6bd5-49f6-bc88-fa1d981da0b1', '# Mentorship Session - Week 5
Date: March 11, 2026
Mentee: Alex
Organization: Code2Community

## Today''s Topic
**Code organization**: modules, imports

## Session Notes
- Started with review of last week''s homework
- Took extra time to explain the concept with real-world analogy
- Alex explained the concept back to me correctly
- Assigned practice exercises for the week

## Alex''s Progress
- **Confidence**: Growing
- **Problem-solving**: Improving each week
- **Questions**: Asking deeper questions now

## Homework Assigned
- [ ] Complete 3 practice problems on code organization
- [ ] Read one chapter of "Automate the Boring Stuff"
- [ ] Try building a small script using today''s concepts

## My Reflections
It''s rewarding to see Alex''s growth. Teaching helps me understand concepts more deeply too.
This mentoring journey reminds me why I got into tech.

## Next Session
- Continue with project work
- Review homework together
- Hours logged: 3', 67.209644912499, 100.95605274715848, 1300.0, 317.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5c25cb75-c28f-4a75-9c19-a65468d1be80', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-11', '["820005dc-dd42-492f-9e71-57769d23ad59", "d7bfb292-dd04-4abd-ab6d-f9aa70faca70", "5c25cb75-c28f-4a75-9c19-a65468d1be80"]', '820005dc-dd42-492f-9e71-57769d23ad59', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5e89552f-a366-493e-9c8c-c94c390a793c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'c8f1f1d8-bc84-4182-a563-86b2138e8735', '## Architecture Review: Phoenix API Migration
Date: March 12, 2026

### Attendees
- Mike (Lead), Jordan, Jamie, Me

### Current Architecture
```
Client → API Gateway → Auth Service → Core API → PostgreSQL
                    ↓
              Redis Cache
```

### Proposed Changes

**1. Microservices Migration**
- Extract payment processing to separate service
- Event-driven communication via Kafka
- Independent deployment and scaling

**2. Performance Improvements**
- Add CDN for static assets
- Implement GraphQL for mobile clients
- Database read replicas for reporting

### Discussion Points
- Jamie: Concern about operational complexity
- Jordan: Need to upgrade K8s cluster first
- Consensus: Start with payment service extraction

### Decisions Made
- Proceed with payment service POC
- Keep monolith for core API (for now)
- Invest in observability before splitting

### Next Steps
- [ ] Me: Draft RFC for payment service
- [ ] Jordan: K8s upgrade plan
- [ ] Mike: Define service boundaries

### Follow-up
- Review RFC in 2 weeks
- POC demo end of Sprint 4', 61.11923343993236, 109.38518792386353, 1300.0, 355.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5e89552f-a366-493e-9c8c-c94c390a793c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7e6c4edd-7ea2-4fb6-8260-45c56a158575', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1c2b25ad-e93f-4f1d-a39b-526082c53e65', '# Learning Log
Date: March 12, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 8/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 5
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 15
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Struggled with the optimization step

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Feeling motivated. The K8s knowledge is already helping at work.', 69.0371228567301, 101.74510391432234, 1300.0, 379.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7e6c4edd-7ea2-4fb6-8260-45c56a158575', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-12', '["5e89552f-a366-493e-9c8c-c94c390a793c", "7e6c4edd-7ea2-4fb6-8260-45c56a158575"]', '5e89552f-a366-493e-9c8c-c94c390a793c', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8fd6e4fe-0108-4999-9986-52eed146c377', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '49186700-39d9-4f46-97c1-2f5a6001c41c', '## Daily Standup - Friday
Date: March 13, 2026

### My Updates

**Yesterday:**
- Completed PR #2347 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1230
- Address code review feedback on #2348
- Sync with Jordan on deployment pipeline

**Blockers:**
- Waiting on Taylor for updated designs

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 51.515366059438826, 86.69699939639375, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '8fd6e4fe-0108-4999-9986-52eed146c377', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '78db5909-df70-4c65-9877-1339eade9aa8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '9f5c52b4-8d7c-49b0-9e44-d1475b525cf7', '# Weekly Review - Week 5
Date: March 13, 2026

## This Week''s Wins
- Great 1:1 with manager - clear path to promotion
- Side project reached MVP Development phase
- Helped onboard the new team member

## Challenges Faced
- Struggled with focus during afternoon meetings
- Difficult conversation with stakeholder

## Key Learnings
- Asking for help earlier saves time overall

## Time Audit
- Deep work: 18 hours
- Meetings: 10 hours
- Admin/email: 4 hours
- Learning: 4 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟡 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Prepare presentation
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Batch email checking to 2x/day', 53.65959800831554, 81.77242383036543, 1300.0, 290.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '78db5909-df70-4c65-9877-1339eade9aa8', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-13', '["8fd6e4fe-0108-4999-9986-52eed146c377", "78db5909-df70-4c65-9877-1339eade9aa8"]', '8fd6e4fe-0108-4999-9986-52eed146c377', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a4e69798-1fb0-40f3-85ec-0bb29e62e816', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'ae253545-c9e3-4ab3-b6dc-4295ca7c3429', '# Side Project: TaskFlow
Phase: MVP Development
Date: March 14, 2026

## TaskFlow - MVP Sprint

### Progress Update
Building out the core functionality. 39 commits so far.

### Completed This Week
- [x] Basic task CRUD operations
- [x] Local storage with AsyncStorage
- [x] Home screen UI
- [x] Quick add functionality

### In Progress
- [ ] Due date picker component
- [ ] Supabase auth integration
- [ ] Push notifications setup

### Code Snippet - Quick Add
```typescript
const addTask = async (title: string) => {
  const task = {
    id: nanoid(),
    title,
    createdAt: Date.now(),
    completed: false,
  };
  await store.addTask(task);
};
```

### Challenges Faced
- React Native debugging is painful
- Expo EAS build queue times slow
- Keyboard handling on Android different from iOS

### Metrics
- Bundle size: 12MB (need to optimize)
- Cold start: 1.2s
- Test coverage: 45%

### Next Week Goals
- Complete Supabase sync
- Add onboarding flow
- Internal alpha release', 52.05863593136223, 89.40993112467439, 1300.0, 326.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'a4e69798-1fb0-40f3-85ec-0bb29e62e816', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '17bf0383-029a-401a-b2e0-6800e674b4ac', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1965b561-9bd8-496e-8110-0a9b9a28c42e', '# Fitness Log - Week 5
Date: March 14, 2026

## Weekly Summary
- **Workouts completed**: 3/5 planned
- **Gym streak**: 20 days
- **Sleep avg**: 6.7 hours

## Workouts This Week
- Monday: 5K run (23:33)
- Wednesday: Strength training (Deadlift: 194lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 23:33 (was 23:57) 🎉 PR!
- Deadlift: 194lbs (+1lbs)
- Body weight: 173.5lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.


## Goals for Week 6
- Push for 5 workouts
- Add 5lbs to deadlift
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 9 glasses/day avg', 69.26942283465972, 89.00616888617832, 1300.0, 243.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '17bf0383-029a-401a-b2e0-6800e674b4ac', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-14', '["a4e69798-1fb0-40f3-85ec-0bb29e62e816", "17bf0383-029a-401a-b2e0-6800e674b4ac"]', 'a4e69798-1fb0-40f3-85ec-0bb29e62e816', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '2fe5d2e8-1c1c-4b95-abe1-d9b8342fbecb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '03ce169e-5b69-4067-8669-3fd6ff681ddb', '# Weekly Review - Week 5
Date: March 15, 2026

## This Week''s Wins
- Helped onboard the new team member
- Finally understood distributed consensus
- Maintained gym streak all week

## Challenges Faced
- Side project progress slower than hoped
- Felt imposter syndrome during architecture review

## Key Learnings
- Small consistent progress beats sporadic big efforts

## Time Audit
- Deep work: 22 hours
- Meetings: 11 hours
- Admin/email: 4 hours
- Learning: 5 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Prepare presentation
3. Catch up on sleep

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Batch email checking to 2x/day', 67.30189462159412, 85.76067892272955, 1300.0, 287.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '2fe5d2e8-1c1c-4b95-abe1-d9b8342fbecb', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f3cf6eaa-cead-460d-a1d4-5c36fc6d3cd6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'fc414f37-07bf-40dc-8229-167325fe0257', '# Daily Journal - Sunday
Date: March 15, 2026

## Mood
Productive

## Today''s Highlights
- Had Team Standup - good discussion on priorities
- Took care of gym session - checked off the list
- Cooked a nice dinner

## What I Learned
The importance of clear commit messages (got confused by old code)

## Tomorrow''s Calendar
- Client Call - Acme Corp
- Team Standup
- Physical Therapy

Need to prepare for these.

## Life Update
Lease signed! Moving in on March 24.
Made it official with Morgan! Feeling happy.
Guitar week 4: 17 practice sessions. Learning: Wonderwall (intro), Horse With No Name

## Gratitude
Appreciate having time for my hobbies', 57.80999726535116, 106.56355601211168, 1300.0, 215.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'f3cf6eaa-cead-460d-a1d4-5c36fc6d3cd6', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd54535f2-063d-4af2-8913-b3d9cc67c030', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0c9978fd-5f07-40bd-bf14-a8c969a43d2f', '# Community Service Reflection
Date: March 15, 2026
Organization: Code2Community

## Impact Summary
- **Total volunteer hours**: 18
- **Mentorship weeks**: 5
- **Mentee**: Alex

## Meaningful Moments
Alex helped another student debug their code!

## What I''m Learning
Through mentoring, I''m developing:
- Communication - explaining complex things simply
- Leadership - taking responsibility for someone''s learning

## Why This Matters
Tech changed my life trajectory. Code2Community helps others access the same opportunity.
Giving back feels like paying forward what my mentors gave me.

## Connection to Values
- **Contribution**: Making a real difference in someone''s future
- **Growth**: Teaching deepens my own understanding
- **Connection**: Building a relationship across generations

## Gratitude
Thankful for:
- The Code2Community program coordinators
- Alex''s dedication and curiosity
- The chance to use my skills for something meaningful

## Looking Forward
Excited to help Alex with their portfolio project', 57.13053553036329, 98.81263754399666, 1300.0, 339.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd54535f2-063d-4af2-8913-b3d9cc67c030', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-15', '["2fe5d2e8-1c1c-4b95-abe1-d9b8342fbecb", "f3cf6eaa-cead-460d-a1d4-5c36fc6d3cd6", "d54535f2-063d-4af2-8913-b3d9cc67c030"]', '2fe5d2e8-1c1c-4b95-abe1-d9b8342fbecb', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c8e62bfd-e997-486b-b23f-8f38e1041b00', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '914606af-5ef7-40e5-b288-8c76c6b53c0b', '# Daily Planning - Monday
Date: March 16, 2026

## Today''s Calendar
- Client Call - Acme Corp
- Team Standup → Update on PHOE-1230
- Physical Therapy
- Coffee Chat

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1230 implementation
3. Address tech debt item

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Vet Appointment
- Weekly Sync
- Weekly Sync

## Key Events This Week
- Wednesday: Interview - Designer
- Wednesday: Sprint Planning
- Wednesday: Budget Review
- Thursday: Project Review

## Personal
- 8 days until move - start packing!
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 51.230803328551076, 96.29566760508277, 1300.0, 293.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c8e62bfd-e997-486b-b23f-8f38e1041b00', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd8981a6b-fcdd-4d60-b6e0-256376fcad49', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '47ce1456-fe06-4efd-9978-083effbc8c7c', '# Learning Log
Date: March 16, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 9/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 6
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 15
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 66.83764946461766, 94.11174124316489, 1300.0, 373.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd8981a6b-fcdd-4d60-b6e0-256376fcad49', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-16', '["c8e62bfd-e997-486b-b23f-8f38e1041b00", "d8981a6b-fcdd-4d60-b6e0-256376fcad49"]', 'c8e62bfd-e997-486b-b23f-8f38e1041b00', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '238c2625-0ba4-424f-8e5a-ef0a00413b9f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a2aa6318-f03a-447d-badc-a8c479d36194', '## Daily Standup - Tuesday
Date: March 17, 2026

### My Updates

**Yesterday:**
- Completed PR #2352 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1230
- Address code review feedback on #2353
- Sync with Jordan on deployment pipeline

**Blockers:**
- Dependency update broke tests - fixing

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 56.68223839051275, 95.4275339272221, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '238c2625-0ba4-424f-8e5a-ef0a00413b9f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-17', '["238c2625-0ba4-424f-8e5a-ef0a00413b9f"]', '238c2625-0ba4-424f-8e5a-ef0a00413b9f', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '091d03df-c8a5-42a6-a1fd-6a0b9883277f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'cfe9ff0c-ec31-46eb-addb-bd1730e8be6b', '## Daily Standup - Wednesday
Date: March 18, 2026

### My Updates

**Yesterday:**
- Completed PR #2342 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1230
- Address code review feedback on #2343
- Sync with Jordan on deployment pipeline

**Blockers:**
- Dependency update broke tests - fixing

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 65.62416726155585, 98.64845281380315, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '091d03df-c8a5-42a6-a1fd-6a0b9883277f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c2abac14-d89a-4c31-a141-6b03a4455477', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '69264882-1daa-4cc5-bc71-95132f242baa', '# Daily Journal - Wednesday
Date: March 18, 2026

## Mood
Focused

## Today''s Highlights
- Had Sprint Planning - good discussion on priorities
- Took care of dentist appointment - checked off the list
- Got outside for a 20 min walk

## What I Learned
The importance of clear commit messages (got confused by old code)

## Tomorrow''s Calendar
- Department Meeting
- Project Review
- Coffee Chat

Need to prepare for these.

## Life Update
Lease signed! Moving in on March 24.
Made it official with Morgan! Feeling happy.
Guitar week 5: 18 practice sessions. Learning: Wonderwall (intro), Horse With No Name

## Gratitude
Lucky to have good health', 65.48786923799848, 85.88156707810032, 1300.0, 215.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c2abac14-d89a-4c31-a141-6b03a4455477', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-18', '["091d03df-c8a5-42a6-a1fd-6a0b9883277f", "c2abac14-d89a-4c31-a141-6b03a4455477"]', '091d03df-c8a5-42a6-a1fd-6a0b9883277f', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4776eaf6-1351-4c22-add4-245c92508115', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '20026584-708c-43a9-a335-f78467e21656', '# Meeting Prep: Project Review
Date: March 19, 2026

## My Updates
- Sprint 3 progress: on track
- PR #2378 merged
- Blocker: waiting on Taylor for mockups

## Discussion Points
1. Timeline for phase 2
2. Resource allocation
3. Tech debt priorities

## Questions to Raise
- Clarity on Q2 roadmap
- Budget for new tooling

## Action Items from Last Time
- [x] Complete API documentation
- [ ] Schedule follow-up with Jordan', 64.71457695072677, 88.84086190245006, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '4776eaf6-1351-4c22-add4-245c92508115', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4b42be0e-e083-4aee-87dc-17c07b7b4726', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e4937867-55a2-4ec1-a9c7-4bea6d0664a2', '# Learning Log
Date: March 19, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 9/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 6
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 15
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 66.1560548255675, 108.98105137586396, 1300.0, 373.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '4b42be0e-e083-4aee-87dc-17c07b7b4726', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-19', '["4776eaf6-1351-4c22-add4-245c92508115", "4b42be0e-e083-4aee-87dc-17c07b7b4726"]', '4776eaf6-1351-4c22-add4-245c92508115', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1bd487c3-440b-4bdb-a6fe-0830cf8cf57a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '301339e7-effe-4262-a44f-52877b7b5be0', '## Daily Standup - Friday
Date: March 20, 2026

### My Updates

**Yesterday:**
- Completed PR #2344 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1230
- Address code review feedback on #2345
- Sync with Jordan on deployment pipeline

**Blockers:**
- No blockers today

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 60.7979309933107, 103.15424008784711, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1bd487c3-440b-4bdb-a6fe-0830cf8cf57a', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'cd4af40f-9339-4e5c-b150-730dcc619d72', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'eeab0a5a-5ad6-4100-a09b-809492a4d24d', '# Weekly Review - Week 6
Date: March 20, 2026

## This Week''s Wins
- Maintained gym streak all week
- Great 1:1 with manager - clear path to promotion
- Got positive feedback on code review

## Challenges Faced
- Side project progress slower than hoped
- Struggled with focus during afternoon meetings

## Key Learnings
- Saying no to meetings protects deep work time

## Time Audit
- Deep work: 20 hours
- Meetings: 11 hours
- Admin/email: 6 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟡 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Continue K8s learning
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Take more breaks between meetings', 54.33661968502434, 90.8057368918612, 1300.0, 287.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'cd4af40f-9339-4e5c-b150-730dcc619d72', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-20', '["1bd487c3-440b-4bdb-a6fe-0830cf8cf57a", "cd4af40f-9339-4e5c-b150-730dcc619d72"]', '1bd487c3-440b-4bdb-a6fe-0830cf8cf57a', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'fd663cff-e654-4f7c-b23f-c28eef4f39d2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'bca4b1ba-a827-4ad1-ba21-5c6871c659bc', '# Side Project: TaskFlow
Phase: Beta Testing
Date: March 21, 2026

## TaskFlow - Beta Phase

### Beta Launch! 🚀
Released to 15 beta testers (friends and colleagues).

### Feedback Summary (Week 1)
- **Positive**: "Love the quick add", "Finally something simple"
- **Issues**: Sync conflicts, crash on Android 12
- **Requests**: Dark mode, widgets, calendar integration

### Bug Fixes This Week
- Fixed sync conflict resolution
- Resolved Android 12 crash (notification permissions)
- Memory leak in task list

### Metrics
- Daily active users: 12/15
- Tasks created: 340
- Crash-free rate: 98.2%

### Commits: 42

### Priority Fixes
1. ~~Sync conflicts~~ ✓
2. ~~Android crash~~ ✓
3. Dark mode (in progress)
4. Performance optimization

### User Quotes
> "I''ve tried so many task apps. This is the first one I actually use daily." - Alex

> "The quick add is genius. Captures my thoughts before I forget." - Taylor

### Launch Prep
- [ ] App Store screenshots
- [ ] Write launch blog post
- [ ] Set up analytics (PostHog)', 54.4113394405553, 107.93258854120691, 1300.0, 340.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'fd663cff-e654-4f7c-b23f-c28eef4f39d2', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3164ddaa-8471-4e4b-bc20-494b30d8b200', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '12493ec0-816a-4d89-82c3-5f4efa23fabb', '# Fitness Log - Week 6
Date: March 21, 2026

## Weekly Summary
- **Workouts completed**: 5/5 planned
- **Gym streak**: 24 days
- **Sleep avg**: 7.0 hours

## Workouts This Week
- Monday: 5K run (23:17)
- Wednesday: Strength training (Deadlift: 194lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 23:17 (was 23:36) 🎉 PR!
- Deadlift: 194lbs (+5lbs)
- Body weight: 173.2lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.
Hit a new PR on the 5K - sub-23 feels achievable now.

## Goals for Week 7
- Push for 5 workouts
- Try the new HIIT class
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 9 glasses/day avg', 60.05840406727846, 90.15217613130328, 1300.0, 262.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '3164ddaa-8471-4e4b-bc20-494b30d8b200', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b95369b3-a992-43a1-9403-265cca95c17f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '49ddc53c-e999-4068-8d4d-28a2918f0c72', '# Quarterly Life Review - Q1
Date: March 21, 2026

## Core Values Check
Am I living aligned with what matters most?

### My Values
1. **Growth** - Continuously learning and improving
2. **Balance** - Sustainable pace, not burnout
3. **Contribution** - Making a positive difference
4. **Connection** - Meaningful relationships

## Life Areas Review

### Career
**Goals**: Get promoted to senior engineer, Launch side project
**Progress**: Making progress

### Health
**Goals**: Run sub-23 5K, Consistent gym habit
**Progress**: Strong progress

### Relationships
**Goals**: Deepen relationship with Morgan, Stay connected with family
**Progress**: Could be better

### Growth
**Goals**: Read 12 books this year, Learn Kubernetes
**Progress**: On pace

### Contribution
**Goals**: Mentor Alex successfully, Volunteer 50 hours
**Progress**: Most fulfilling area


## Wins This Quarter
- Promoted to mid-level engineer
- Strong mentorship relationship
- Read 4 books

## Areas for Growth
- More consistent meditation
- Ask for help sooner

## Intention for Next Quarter
Create more, consume less

## Gratitude Reflection
Taking a moment to appreciate:
- Health and energy to pursue my goals
- Supportive relationships (Morgan, family, friends)
- Meaningful work and growth opportunities
- The ability to give back through mentoring

## One Word for Next Quarter
**Presence**', 57.98784424117815, 88.18538723182179, 1300.0, 456.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'b95369b3-a992-43a1-9403-265cca95c17f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-21', '["fd663cff-e654-4f7c-b23f-c28eef4f39d2", "3164ddaa-8471-4e4b-bc20-494b30d8b200", "b95369b3-a992-43a1-9403-265cca95c17f"]', 'fd663cff-e654-4f7c-b23f-c28eef4f39d2', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c9638052-74ae-4fe0-91c6-d474519fe26a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'bddde0e6-3433-44b7-ba15-b4968ecdd2f0', '# Weekly Review - Week 6
Date: March 22, 2026

## This Week''s Wins
- Hit a 5K PR: 23:17
- Shipped the auth migration on time
- Maintained gym streak all week

## Challenges Faced
- Difficult conversation with stakeholder
- Sleep schedule got off track

## Key Learnings
- Small consistent progress beats sporadic big efforts

## Time Audit
- Deep work: 22 hours
- Meetings: 9 hours
- Admin/email: 3 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟡 Started strong
- Tue: 🟡 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Continue K8s learning
3. Maintain fitness routine

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 53.53710947931602, 86.92832157489107, 1300.0, 278.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c9638052-74ae-4fe0-91c6-d474519fe26a', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a073dde0-4855-4341-ad07-3be45b583cd5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0caefd2a-cf98-424f-a3db-e9a420918442', '# Daily Journal - Sunday
Date: March 22, 2026

## Mood
Productive

## Today''s Highlights
- Had Quarterly Planning - good discussion on priorities
- Took care of lunch with friends - checked off the list
- Cooked a nice dinner

## What I Learned
Taking breaks actually improves productivity

## Tomorrow''s Calendar
- Doctor Visit
- Take Jake to Scout Camp
- Grocery Run

Need to prepare for these.

## Life Update
Moving week chaos. Boxes everywhere but making progress.
Made it official with Morgan! Feeling happy.
Guitar week 5: 20 practice sessions. Learning: Wonderwall (intro), Horse With No Name

## Gratitude
Grateful for the supportive team at work', 64.67409874534204, 81.14270426750132, 1300.0, 218.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'a073dde0-4855-4341-ad07-3be45b583cd5', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-22', '["c9638052-74ae-4fe0-91c6-d474519fe26a", "a073dde0-4855-4341-ad07-3be45b583cd5"]', 'c9638052-74ae-4fe0-91c6-d474519fe26a', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c2d6d77b-aff8-40c2-b591-d73c0b04c507', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3f1cecaa-1111-4ebc-afc1-bd814c9e7da7', '# Daily Planning - Monday
Date: March 23, 2026

## Today''s Calendar
- Doctor Visit
- Take Jake to Scout Camp
- Grocery Run
- Family Bike Ride
- Family Bike Ride

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1230 implementation
3. Update documentation

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #8136
- Meeting #8170
- Meeting #9896





## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 58.643840701214856, 90.77189777399495, 1300.0, 217.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c2d6d77b-aff8-40c2-b591-d73c0b04c507', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '83d39566-28e8-4161-a3f9-a4b382da6bff', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f8e0afa7-7c64-4c81-ab4a-61d1db3e30d6', '# Learning Log
Date: March 23, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 11/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 7
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 21
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Struggled with the optimization step

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 67.98467936356522, 93.12796902448225, 1300.0, 379.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '83d39566-28e8-4161-a3f9-a4b382da6bff', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-23', '["c2d6d77b-aff8-40c2-b591-d73c0b04c507", "83d39566-28e8-4161-a3f9-a4b382da6bff"]', 'c2d6d77b-aff8-40c2-b591-d73c0b04c507', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1b18fba2-c7d7-45b3-87ea-c5c82e294e90', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '01a51820-b5b0-44f0-85f7-c2b67980610c', '## Code Review Session
Date: March 24, 2026

### PRs Reviewed

**PR #2383: Auth Service Refactor** (by Mike)
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #2384: API Rate Limiting** (by me)
- Alex caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #2385: Dashboard Components** (by Taylor)
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: 4m 2s (down from last week)
- Test coverage: 95.4%
- Lint errors: 0
- Bundle size: 1.90MB

### Patterns to Adopt
- Error boundary pattern from #2383
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components', 50.80968935002786, 98.08150575532818, 1300.0, 309.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1b18fba2-c7d7-45b3-87ea-c5c82e294e90', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3098a707-05fa-4f59-b17b-9149233f833e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f8007d38-f0f6-4f4f-97a4-22fa9f08a297', '# Side Project: TaskFlow
Phase: Beta Testing
Date: March 24, 2026

## TaskFlow - Beta Phase

### Beta Launch! 🚀
Released to 15 beta testers (friends and colleagues).

### Feedback Summary (Week 1)
- **Positive**: "Love the quick add", "Finally something simple"
- **Issues**: Sync conflicts, crash on Android 12
- **Requests**: Dark mode, widgets, calendar integration

### Bug Fixes This Week
- Fixed sync conflict resolution
- Resolved Android 12 crash (notification permissions)
- Memory leak in task list

### Metrics
- Daily active users: 12/15
- Tasks created: 340
- Crash-free rate: 98.2%

### Commits: 43

### Priority Fixes
1. ~~Sync conflicts~~ ✓
2. ~~Android crash~~ ✓
3. Dark mode (in progress)
4. Performance optimization

### User Quotes
> "I''ve tried so many task apps. This is the first one I actually use daily." - Alex

> "The quick add is genius. Captures my thoughts before I forget." - Taylor

### Launch Prep
- [ ] App Store screenshots
- [ ] Write launch blog post
- [ ] Set up analytics (PostHog)', 61.31021588078737, 90.22605803117663, 1300.0, 340.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '3098a707-05fa-4f59-b17b-9149233f833e', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-24', '["1b18fba2-c7d7-45b3-87ea-c5c82e294e90", "3098a707-05fa-4f59-b17b-9149233f833e"]', '1b18fba2-c7d7-45b3-87ea-c5c82e294e90', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1ee96534-f782-4d29-9e4e-85452e18342c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '60abed53-fa3b-4ca2-8bf2-1ad6259702eb', '## 1:1 with Chris
Date: March 25, 2026

### Topics Discussed

**Career Growth**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Promotion Timeline**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: April 08
- Topic: Review proposal draft', 60.27090387944084, 97.5819213451772, 1300.0, 309.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1ee96534-f782-4d29-9e4e-85452e18342c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '307ac119-e5a7-4ae1-bceb-dbc696aa926c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e1f3d210-4ad4-4f29-a16e-92c6a350430e', '# Daily Journal - Wednesday
Date: March 25, 2026

## Mood
Steady

## Today''s Highlights
- Knocked out 3 small tasks
- Squeezed in a quick workout

## What I Learned
How to use React DevTools profiler effectively

## Tomorrow''s Calendar
- Meeting #1100
- Meeting #5725
- Meeting #7967

Need to prepare for these.

## Life Update
Moving week chaos. Boxes everywhere but making progress.
Made it official with Morgan! Feeling happy.
Guitar week 6: 22 practice sessions. Learning: Horse With No Name, Wish You Were Here

## Gratitude
Lucky to have good health', 66.15843454814906, 89.9527749758659, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '307ac119-e5a7-4ae1-bceb-dbc696aa926c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '46fb06a6-ee18-4c55-8fb4-6af91d7ec7fb', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'ac87adb4-2f73-4357-aebe-13ab6301ff7d', '# Mentorship Session - Week 7
Date: March 25, 2026
Mentee: Alex
Organization: Code2Community

## Today''s Topic
**Testing basics**: unittest, pytest intro

## Session Notes
- Started with review of last week''s homework
- Took extra time to explain the concept with real-world analogy
- Saw real improvement in problem-solving approach
- Assigned practice exercises for the week

## Alex''s Progress
- **Confidence**: Growing
- **Problem-solving**: Improving each week
- **Questions**: Asking deeper questions now

## Homework Assigned
- [ ] Complete 3 practice problems on testing basics
- [ ] Read one chapter of "Automate the Boring Stuff"
- [ ] Try building a small script using today''s concepts

## My Reflections
It''s rewarding to see Alex''s growth. Teaching helps me understand concepts more deeply too.
This mentoring journey reminds me why I got into tech.

## Next Session
- Continue with debugging practice
- Review homework together
- Hours logged: 3', 57.70462358025605, 109.74326365695084, 1300.0, 319.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '46fb06a6-ee18-4c55-8fb4-6af91d7ec7fb', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-25', '["1ee96534-f782-4d29-9e4e-85452e18342c", "307ac119-e5a7-4ae1-bceb-dbc696aa926c", "46fb06a6-ee18-4c55-8fb4-6af91d7ec7fb"]', '1ee96534-f782-4d29-9e4e-85452e18342c', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7f829031-eb71-496c-a11c-65e10c7032de', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1318954e-b6e9-4c52-af3a-cea4d174c6cb', '# Daily Planning - Thursday
Date: March 26, 2026

## Today''s Calendar
- Meeting #1100
- Meeting #5725
- Meeting #7967
- Meeting #9005

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1240 implementation
3. Update documentation

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #3885
- Meeting #9687
- Meeting #3765



## Personal
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 61.88000643769507, 89.8314448881414, 1300.0, 224.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7f829031-eb71-496c-a11c-65e10c7032de', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '447bd898-c337-4bf6-99a3-260d41dda7f6', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '71b6b087-7c7f-4733-b83c-d68396e829ea', '# Learning Log
Date: March 26, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 11/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 8
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 21
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
The book is dense but worth it. Taking good notes.', 57.277492200028995, 95.33573279738872, 1300.0, 369.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '447bd898-c337-4bf6-99a3-260d41dda7f6', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-26', '["7f829031-eb71-496c-a11c-65e10c7032de", "447bd898-c337-4bf6-99a3-260d41dda7f6"]', '7f829031-eb71-496c-a11c-65e10c7032de', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3b66f1cf-2928-4e22-b258-0fb5621e6f61', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3022bc9e-d080-4ace-9705-616b39448c82', '## Daily Standup - Friday
Date: March 27, 2026

### My Updates

**Yesterday:**
- Completed PR #2350 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1240
- Address code review feedback on #2351
- Sync with Jordan on deployment pipeline

**Blockers:**
- Waiting on Taylor for updated designs

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 57.94955171731843, 94.71868196664519, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '3b66f1cf-2928-4e22-b258-0fb5621e6f61', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b676e7ca-3c42-498c-af9a-c7cf7da6a55f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1c255977-9390-4909-bcf7-a2737ce481cb', '# Weekly Review - Week 7
Date: March 27, 2026

## This Week''s Wins
- Helped onboard the new team member
- Shipped the auth migration on time
- Maintained gym streak all week

## Challenges Faced
- Difficult conversation with stakeholder
- Struggled with focus during afternoon meetings

## Key Learnings
- Asking for help earlier saves time overall

## Time Audit
- Deep work: 17 hours
- Meetings: 11 hours
- Admin/email: 4 hours
- Learning: 4 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🔴 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Complete sprint stories
2. Work on side project
3. Catch up on sleep

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 50.84370736955223, 94.1558208933475, 1300.0, 287.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'b676e7ca-3c42-498c-af9a-c7cf7da6a55f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-27', '["3b66f1cf-2928-4e22-b258-0fb5621e6f61", "b676e7ca-3c42-498c-af9a-c7cf7da6a55f"]', '3b66f1cf-2928-4e22-b258-0fb5621e6f61', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'de0c4738-dcd5-469d-ad70-e44d1542afea', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'b6d82046-6f1b-439d-b37f-0ee57cd04778', '# Side Project: TaskFlow
Phase: Beta Testing
Date: March 28, 2026

## TaskFlow - Beta Phase

### Beta Launch! 🚀
Released to 15 beta testers (friends and colleagues).

### Feedback Summary (Week 1)
- **Positive**: "Love the quick add", "Finally something simple"
- **Issues**: Sync conflicts, crash on Android 12
- **Requests**: Dark mode, widgets, calendar integration

### Bug Fixes This Week
- Fixed sync conflict resolution
- Resolved Android 12 crash (notification permissions)
- Memory leak in task list

### Metrics
- Daily active users: 12/15
- Tasks created: 340
- Crash-free rate: 98.2%

### Commits: 48

### Priority Fixes
1. ~~Sync conflicts~~ ✓
2. ~~Android crash~~ ✓
3. Dark mode (in progress)
4. Performance optimization

### User Quotes
> "I''ve tried so many task apps. This is the first one I actually use daily." - Alex

> "The quick add is genius. Captures my thoughts before I forget." - Taylor

### Launch Prep
- [ ] App Store screenshots
- [ ] Write launch blog post
- [ ] Set up analytics (PostHog)', 65.88000737651376, 99.80014003619151, 1300.0, 340.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'de0c4738-dcd5-469d-ad70-e44d1542afea', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7bbb8267-10a7-4134-a8d9-b799f8e5c444', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '41664710-f033-490d-8b4a-e818597fe310', '# Fitness Log - Week 7
Date: March 28, 2026

## Weekly Summary
- **Workouts completed**: 4/5 planned
- **Gym streak**: 28 days
- **Sleep avg**: 6.9 hours

## Workouts This Week
- Monday: 5K run (23:00)
- Wednesday: Strength training (Deadlift: 199lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 23:00 (was 23:16) 🎉 PR!
- Deadlift: 199lbs (+1lbs)
- Body weight: 172.9lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.
Hit a new PR on the 5K - sub-23 feels achievable now.

## Goals for Week 8
- Push for 5 workouts
- Add 5lbs to deadlift
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 8 glasses/day avg', 54.32035034425966, 102.32708537983214, 1300.0, 261.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7bbb8267-10a7-4134-a8d9-b799f8e5c444', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-28', '["de0c4738-dcd5-469d-ad70-e44d1542afea", "7bbb8267-10a7-4134-a8d9-b799f8e5c444"]', 'de0c4738-dcd5-469d-ad70-e44d1542afea', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '3f04c623-6546-4902-805c-6a5b6350327c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1cdf4b1d-259e-43b7-8caf-c53a65136347', '# Weekly Review - Week 7
Date: March 29, 2026

## This Week''s Wins
- Maintained gym streak all week
- Got positive feedback on code review
- Finally understood distributed consensus

## Challenges Faced
- Side project progress slower than hoped
- Felt imposter syndrome during architecture review

## Key Learnings
- Writing things down beats trying to remember

## Time Audit
- Deep work: 21 hours
- Meetings: 12 hours
- Admin/email: 6 hours
- Learning: 4 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟡 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Review team PRs
2. Continue K8s learning
3. Catch up on sleep

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Take more breaks between meetings', 52.23175830492622, 86.58268762513322, 1300.0, 284.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '3f04c623-6546-4902-805c-6a5b6350327c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'e16572ac-f257-4162-9bf8-8b9cc3e08548', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '10ff0e3e-9d94-4664-a349-84f8ef1626b5', '# Daily Journal - Sunday
Date: March 29, 2026

## Mood
Contemplative

## Today''s Highlights
- Made progress on the tricky bug
- Squeezed in a quick workout

## What I Learned
The importance of clear commit messages (got confused by old code)

## Tomorrow''s Calendar
- Meeting #6428
- Meeting #7660
- Meeting #4924

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 6: 24 practice sessions. Learning: Horse With No Name, Wish You Were Here

## Gratitude
Thankful for the little things - good coffee, sunny weather', 55.37498703024257, 107.02013407646945, 1300.0, 212.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'e16572ac-f257-4162-9bf8-8b9cc3e08548', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-29', '["3f04c623-6546-4902-805c-6a5b6350327c", "e16572ac-f257-4162-9bf8-8b9cc3e08548"]', '3f04c623-6546-4902-805c-6a5b6350327c', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ef38f03a-eb2b-4a31-b5f7-c25b8b97a6d1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '389403db-a5e5-4d0e-be6a-6e0974665f5c', '# Daily Planning - Monday
Date: March 30, 2026

## Today''s Calendar
- Meeting #6428
- Meeting #7660
- Meeting #4924
- Meeting #2261

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1240 implementation
3. Write unit tests

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #1981
- Meeting #3377
- Meeting #3739



## Personal
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 68.9894964745578, 88.07372371778759, 1300.0, 222.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'ef38f03a-eb2b-4a31-b5f7-c25b8b97a6d1', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '820d08fe-4d00-4af5-9de8-7c89c052c995', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1ab46613-75e4-4565-b10c-27ddfa24e3a5', '# Learning Log
Date: March 30, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 11/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 9
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 21
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Feeling motivated. The K8s knowledge is already helping at work.', 52.31003756467126, 82.6040034066732, 1300.0, 374.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '820d08fe-4d00-4af5-9de8-7c89c052c995', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-30', '["ef38f03a-eb2b-4a31-b5f7-c25b8b97a6d1", "820d08fe-4d00-4af5-9de8-7c89c052c995"]', 'ef38f03a-eb2b-4a31-b5f7-c25b8b97a6d1', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ff5381c0-b829-45c0-8471-a2ca6d0137b1', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e00df321-d4ae-4dd0-b638-c94d91f73f30', '## Code Review Session
Date: March 31, 2026

### PRs Reviewed

**PR #2390: Auth Service Refactor** (by Mike)
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #2391: API Rate Limiting** (by me)
- Alex caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #2392: Dashboard Components** (by Taylor)
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: 4m 2s (down from last week)
- Test coverage: 95.4%
- Lint errors: 0
- Bundle size: 1.90MB

### Patterns to Adopt
- Error boundary pattern from #2390
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components', 55.42078261840808, 89.41275512020718, 1300.0, 309.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'ff5381c0-b829-45c0-8471-a2ca6d0137b1', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-03-31', '["ff5381c0-b829-45c0-8471-a2ca6d0137b1"]', 'ff5381c0-b829-45c0-8471-a2ca6d0137b1', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '8cef138e-a5ff-4462-968e-76d60bec5b62', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '137de8c7-0bfa-481d-8347-f4570ef85099', '## Daily Standup - Wednesday
Date: April 01, 2026

### My Updates

**Yesterday:**
- Completed PR #2350 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1240
- Address code review feedback on #2351
- Sync with Jordan on deployment pipeline

**Blockers:**
- CI pipeline flaky - investigating

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 51.87580926969396, 86.1836998818798, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '8cef138e-a5ff-4462-968e-76d60bec5b62', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7e97f9e6-b954-4776-b55f-392ede9a7835', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f691f54c-fb67-4a50-95df-f7612f602229', '# Daily Journal - Wednesday
Date: April 01, 2026

## Mood
Productive

## Today''s Highlights
- Made progress on the tricky bug
- Got outside for a 20 min walk

## What I Learned
Sometimes the simplest solution really is best

## Tomorrow''s Calendar
- Meeting #3182
- Meeting #2987
- Meeting #9213

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 7: 27 practice sessions. Learning: Horse With No Name, Wish You Were Here
Family: Dad''s retirement party planning

## Gratitude
Thankful for the little things - good coffee, sunny weather', 57.52723393574483, 105.0098182434359, 1300.0, 220.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7e97f9e6-b954-4776-b55f-392ede9a7835', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-01', '["8cef138e-a5ff-4462-968e-76d60bec5b62", "7e97f9e6-b954-4776-b55f-392ede9a7835"]', '8cef138e-a5ff-4462-968e-76d60bec5b62', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5ceab486-5510-4780-9b74-aa07506c2d9a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1dac1cd8-5ff7-4352-8ada-029364c03bc9', '# Daily Planning - Thursday
Date: April 02, 2026

## Today''s Calendar
- Meeting #3182
- Meeting #2987
- Meeting #9213
- Meeting #4914
- Meeting #7032

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1240 implementation
3. Update documentation

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #8617
- Meeting #7342
- Meeting #2585





## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 66.3111340852467, 103.61228610125974, 1300.0, 214.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5ceab486-5510-4780-9b74-aa07506c2d9a', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a5d87eae-30b3-4ba9-8b1a-8268fa67e5cd', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '021af8e8-cfd4-4184-a2dd-d5e6fae37477', '# Learning Log
Date: April 02, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 12/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 9
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 21
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Struggled with the optimization step

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 59.285433784806585, 98.27553494981889, 1300.0, 379.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'a5d87eae-30b3-4ba9-8b1a-8268fa67e5cd', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-02', '["5ceab486-5510-4780-9b74-aa07506c2d9a", "a5d87eae-30b3-4ba9-8b1a-8268fa67e5cd"]', '5ceab486-5510-4780-9b74-aa07506c2d9a', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5cd7d0a0-6a15-47c3-aec8-90c6a0da917f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '9c8ddbae-f049-4160-afe0-99f33c8905a0', '## Daily Standup - Friday
Date: April 03, 2026

### My Updates

**Yesterday:**
- Completed PR #2349 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1240
- Address code review feedback on #2350
- Sync with Jordan on deployment pipeline

**Blockers:**
- No blockers today

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 51.44653343578348, 108.51075839826456, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5cd7d0a0-6a15-47c3-aec8-90c6a0da917f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '5b255059-69d3-4747-9478-9a8823a2c23c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3fafbc07-5c78-42d2-81fd-5f348a85eb6c', '# Weekly Review - Week 8
Date: April 03, 2026

## This Week''s Wins
- Hit a 5K PR: 22:38
- Shipped the auth migration on time
- Maintained gym streak all week

## Challenges Faced
- Sleep schedule got off track
- Struggled with focus during afternoon meetings

## Key Learnings
- Writing things down beats trying to remember

## Time Audit
- Deep work: 22 hours
- Meetings: 12 hours
- Admin/email: 3 hours
- Learning: 5 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Complete sprint stories
2. Continue K8s learning
3. Catch up on sleep

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Take more breaks between meetings', 63.002189145258015, 108.8522829475362, 1300.0, 274.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '5b255059-69d3-4747-9478-9a8823a2c23c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-03', '["5cd7d0a0-6a15-47c3-aec8-90c6a0da917f", "5b255059-69d3-4747-9478-9a8823a2c23c"]', '5cd7d0a0-6a15-47c3-aec8-90c6a0da917f', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '44ee8ce0-0893-4128-b7fe-c025cc7e3c65', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'c72d3136-eace-4db4-a17c-b34791c9ae6c', '# Side Project: TaskFlow
Phase: Launch Prep
Date: April 04, 2026

## TaskFlow - Launch Prep 🎯

### Launch Checklist
- [x] App Store listing complete
- [x] Privacy policy published
- [x] Marketing site live (taskflow.app)
- [ ] ProductHunt draft ready
- [ ] Launch day social posts scheduled

### Final Polish
- Dark mode complete
- Onboarding flow refined
- Performance: 60fps scrolling achieved

### Marketing Plan
- Day 1: ProductHunt launch
- Day 2-3: Twitter/X thread
- Week 1: HN "Show HN" post
- Week 2: Reach out to reviewers

### Pricing Decision
- Free tier: 50 tasks, local only
- Pro: $4/mo - unlimited + sync + widgets

### Launch Day Tasks
- [ ] Submit to ProductHunt (midnight)
- [ ] Tweet announcement
- [ ] Monitor crash reports
- [ ] Respond to early feedback

### Feeling
Nervous but excited! Months of work coming together. Whatever happens, I learned a ton about React Native and shipping a product.', 66.22805088940294, 105.51153666763386, 1300.0, 306.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '44ee8ce0-0893-4128-b7fe-c025cc7e3c65', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '49b98b8e-b941-4074-a962-3cc43e5d7a55', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '7c0ac14b-ec01-4a69-8851-9d42ec9f83bc', '# Fitness Log - Week 8
Date: April 04, 2026

## Weekly Summary
- **Workouts completed**: 4/5 planned
- **Gym streak**: 32 days
- **Sleep avg**: 7.4 hours

## Workouts This Week
- Monday: 5K run (22:38)
- Wednesday: Strength training (Deadlift: 204lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 22:38 (was 22:53) 🎉 PR!
- Deadlift: 204lbs (+5lbs)
- Body weight: 172.6lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.
Hit a new PR on the 5K - sub-23 feels achievable now.

## Goals for Week 9
- Push for 5 workouts
- Try the new HIIT class
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 8 glasses/day avg', 55.924220592375946, 95.12369113063825, 1300.0, 262.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '49b98b8e-b941-4074-a962-3cc43e5d7a55', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6d9851e7-33b4-459f-b081-e784100bc057', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a0dc8f75-b00c-43dc-b370-44d551a79a79', '# Quarterly Life Review - Q2
Date: April 04, 2026

## Core Values Check
Am I living aligned with what matters most?

### My Values
1. **Growth** - Continuously learning and improving
2. **Balance** - Sustainable pace, not burnout
3. **Contribution** - Making a positive difference
4. **Connection** - Meaningful relationships

## Life Areas Review

### Career
**Goals**: Get promoted to senior engineer, Launch side project
**Progress**: On track

### Health
**Goals**: Run sub-23 5K, Consistent gym habit
**Progress**: Maintaining

### Relationships
**Goals**: Deepen relationship with Morgan, Stay connected with family
**Progress**: Feeling connected

### Growth
**Goals**: Read 12 books this year, Learn Kubernetes
**Progress**: Ahead of schedule

### Contribution
**Goals**: Mentor Alex successfully, Volunteer 50 hours
**Progress**: Making impact


## Wins This Quarter
- Side project getting traction
- Reconnected with old friends
- Learned new technical skill

## Areas for Growth
- More present during family time
- Delegate more at work

## Intention for Next Quarter
Focus on depth over breadth - fewer goals, more impact

## Gratitude Reflection
Taking a moment to appreciate:
- Health and energy to pursue my goals
- Supportive relationships (Morgan, family, friends)
- Meaningful work and growth opportunities
- The ability to give back through mentoring

## One Word for Next Quarter
**Courage**', 68.45183075892042, 80.22404035216225, 1300.0, 470.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '6d9851e7-33b4-459f-b081-e784100bc057', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-04', '["44ee8ce0-0893-4128-b7fe-c025cc7e3c65", "49b98b8e-b941-4074-a962-3cc43e5d7a55", "6d9851e7-33b4-459f-b081-e784100bc057"]', '44ee8ce0-0893-4128-b7fe-c025cc7e3c65', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bce69b29-094f-450e-adb3-1ed609024121', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '00a5c1be-dbde-4c5a-8831-6497716e848d', '# Weekly Review - Week 8
Date: April 05, 2026

## This Week''s Wins
- Great 1:1 with manager - clear path to promotion
- Hit a 5K PR: 22:38
- Helped onboard the new team member

## Challenges Faced
- Side project progress slower than hoped
- Difficult conversation with stakeholder

## Key Learnings
- Small consistent progress beats sporadic big efforts

## Time Audit
- Deep work: 17 hours
- Meetings: 11 hours
- Admin/email: 3 hours
- Learning: 4 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Continue K8s learning
3. Maintain fitness routine

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Take more breaks between meetings', 68.29580156034262, 83.01957785006759, 1300.0, 286.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'bce69b29-094f-450e-adb3-1ed609024121', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '6dd6d28f-dffd-44a3-8a30-9a29e02b0887', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '508da3a8-c995-4d59-bbb8-f5fe05618e2c', '# Daily Journal - Sunday
Date: April 05, 2026

## Mood
Steady

## Today''s Highlights
- Helpful pair programming session
- Cooked a nice dinner

## What I Learned
How to use React DevTools profiler effectively

## Tomorrow''s Calendar
- Meeting #9174
- Meeting #4224
- Meeting #6657

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 7: 29 practice sessions. Learning: Horse With No Name, Wish You Were Here

## Gratitude
Grateful for the supportive team at work', 60.52130076956617, 109.74340427805264, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '6dd6d28f-dffd-44a3-8a30-9a29e02b0887', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-05', '["bce69b29-094f-450e-adb3-1ed609024121", "6dd6d28f-dffd-44a3-8a30-9a29e02b0887"]', 'bce69b29-094f-450e-adb3-1ed609024121', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '14164bf6-db3e-4787-9a32-8fe0caabbc1c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'cde0dec8-2c2a-4e94-b95a-819b1b706004', '# Daily Planning - Monday
Date: April 06, 2026

## Today''s Calendar
- Meeting #9174
- Meeting #4224
- Meeting #6657
- Meeting #4857
- Meeting #9800

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1240 implementation
3. Address tech debt item

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #8719
- Meeting #5311
- Meeting #6660



## Personal
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 53.04878701290034, 98.80787388597093, 1300.0, 229.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '14164bf6-db3e-4787-9a32-8fe0caabbc1c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0fe1831e-3f62-48a5-83ce-1d4135aea981', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e7b9c186-678e-4cb6-bcc6-7adcf0ba3397', '# Learning Log
Date: April 06, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 12/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 9
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 21
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Struggled with the optimization step

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
The book is dense but worth it. Taking good notes.', 58.959935445051144, 84.66477484045237, 1300.0, 375.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '0fe1831e-3f62-48a5-83ce-1d4135aea981', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-06', '["14164bf6-db3e-4787-9a32-8fe0caabbc1c", "0fe1831e-3f62-48a5-83ce-1d4135aea981"]', '14164bf6-db3e-4787-9a32-8fe0caabbc1c', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '4d344322-b913-44f7-a88e-4a8c9cfef3b0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '884c11aa-515f-4fc8-a15e-c213d44579db', '## Code Review Session
Date: April 07, 2026

### PRs Reviewed

**PR #2397: Auth Service Refactor** (by Mike)
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #2398: API Rate Limiting** (by me)
- Alex caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #2399: Dashboard Components** (by Taylor)
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: 3m 55s (down from last week)
- Test coverage: 96.2%
- Lint errors: 0
- Bundle size: 1.85MB

### Patterns to Adopt
- Error boundary pattern from #2397
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components', 56.1646947223365, 103.2576019620411, 1300.0, 309.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '4d344322-b913-44f7-a88e-4a8c9cfef3b0', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-07', '["4d344322-b913-44f7-a88e-4a8c9cfef3b0"]', '4d344322-b913-44f7-a88e-4a8c9cfef3b0', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'a753622c-8a41-4cb8-8897-9e4e6f25b1fc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'acfa62fa-4541-4cae-8c92-c4bf2462bb56', '## 1:1 with Chris
Date: April 08, 2026

### Topics Discussed

**Project Challenges**
- Discussed path to senior engineer role
- Chris suggested leading the next project initiative
- Goal: Present architecture proposal by end of Q1

**Team Dynamics**
- Team morale is good after successful launch
- Mike transitioning some responsibilities
- Opportunity to mentor new hire starting next month

### Feedback Received
- Positive: Code quality has improved significantly
- Growth area: Could be more vocal in design discussions
- Chris noticed my K8s deep-dive - keep it up

### My Questions
- Timeline for performance review cycle?
- Budget for conference attendance (KubeCon)?
- Thoughts on 20% time for side projects?

### Action Items
- [ ] Draft proposal for new monitoring system
- [ ] Schedule skip-level with Jamie
- [ ] Complete self-review by end of month

### Next 1:1
- Two weeks: April 22
- Topic: Review proposal draft', 68.2726763908718, 87.48731343808933, 1300.0, 309.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'a753622c-8a41-4cb8-8897-9e4e6f25b1fc', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '53a1ba65-e4c1-4cb8-a5df-bed33d7da6cc', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '40ff2d9a-767a-4b42-a7f1-5e01a6306e6a', '# Daily Journal - Wednesday
Date: April 08, 2026

## Mood
Steady

## Today''s Highlights
- Helpful pair programming session
- Got outside for a 20 min walk

## What I Learned
Sometimes the simplest solution really is best

## Tomorrow''s Calendar
- Meeting #6667
- Meeting #5932
- Meeting #3411

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 8: 29 practice sessions. Learning: Wish You Were Here, Blackbird (working on it)

## Gratitude
Lucky to have good health', 61.452362004146494, 95.19183144537318, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '53a1ba65-e4c1-4cb8-a5df-bed33d7da6cc', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'dce896ff-e59d-4b5b-891f-e22fc8f9b69b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '0957875b-a7b4-4187-bb8f-b5b7a34dc2cc', '# Mentorship Session - Week 9
Date: April 08, 2026
Mentee: Alex
Organization: Code2Community

## Today''s Topic
**Contributing to open source**: finding good first issues

## Session Notes
- Started with review of last week''s homework
- Took extra time to explain the concept with real-world analogy
- First time Alex spotted a bug on their own
- Assigned practice exercises for the week

## Alex''s Progress
- **Confidence**: Growing
- **Problem-solving**: Improving each week
- **Questions**: Asking deeper questions now

## Homework Assigned
- [ ] Complete 3 practice problems on contributing to open source
- [ ] Read one chapter of "Automate the Boring Stuff"
- [ ] Try building a small script using today''s concepts

## My Reflections
It''s rewarding to see Alex''s growth. Teaching helps me understand concepts more deeply too.
This mentoring journey reminds me why I got into tech.

## Next Session
- Continue with next topic
- Review homework together
- Hours logged: 3', 53.11934458761216, 102.22136308606181, 1300.0, 324.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'dce896ff-e59d-4b5b-891f-e22fc8f9b69b', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-08', '["a753622c-8a41-4cb8-8897-9e4e6f25b1fc", "53a1ba65-e4c1-4cb8-a5df-bed33d7da6cc", "dce896ff-e59d-4b5b-891f-e22fc8f9b69b"]', 'a753622c-8a41-4cb8-8897-9e4e6f25b1fc', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'efe43f4b-68b2-40f2-9a9e-83bf622fe99f', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1c70e728-b3b7-454c-91f2-7f92d5c1ee14', '## Architecture Review: Phoenix API Migration
Date: April 09, 2026

### Attendees
- Mike (Lead), Jordan, Jamie, Me

### Current Architecture
```
Client → API Gateway → Auth Service → Core API → PostgreSQL
                    ↓
              Redis Cache
```

### Proposed Changes

**1. Microservices Migration**
- Extract payment processing to separate service
- Event-driven communication via Kafka
- Independent deployment and scaling

**2. Performance Improvements**
- Add CDN for static assets
- Implement GraphQL for mobile clients
- Database read replicas for reporting

### Discussion Points
- Jamie: Concern about operational complexity
- Jordan: Need to upgrade K8s cluster first
- Consensus: Start with payment service extraction

### Decisions Made
- Proceed with payment service POC
- Keep monolith for core API (for now)
- Invest in observability before splitting

### Next Steps
- [ ] Me: Draft RFC for payment service
- [ ] Jordan: K8s upgrade plan
- [ ] Mike: Define service boundaries

### Follow-up
- Review RFC in 2 weeks
- POC demo end of Sprint 6', 52.50591254067024, 89.32241476154488, 1300.0, 355.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'efe43f4b-68b2-40f2-9a9e-83bf622fe99f', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1abb0fe6-c4c5-424c-b308-6e97bd498a86', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a5401d9f-7abf-464f-9a1d-f7fa9e11fe8c', '# Learning Log
Date: April 09, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 12/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 10
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 21
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Some days learning feels slow, but looking back I''ve come far.', 64.26801787174112, 85.20390090721024, 1300.0, 374.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1abb0fe6-c4c5-424c-b308-6e97bd498a86', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-09', '["efe43f4b-68b2-40f2-9a9e-83bf622fe99f", "1abb0fe6-c4c5-424c-b308-6e97bd498a86"]', 'efe43f4b-68b2-40f2-9a9e-83bf622fe99f', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '292d6d02-8950-40d1-ad60-27f2919b40b0', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '85652593-28ff-479c-916c-225386317230', '## Daily Standup - Friday
Date: April 10, 2026

### My Updates

**Yesterday:**
- Completed PR #2346 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1250
- Address code review feedback on #2347
- Sync with Jordan on deployment pipeline

**Blockers:**
- CI pipeline flaky - investigating

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 53.62095320846114, 109.39568756761341, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '292d6d02-8950-40d1-ad60-27f2919b40b0', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '681e52e5-c9f6-4c0e-8dad-d859853d9a9c', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '2824a9f0-6dab-4eee-95ef-bd74edf3a169', '# Weekly Review - Week 9
Date: April 10, 2026

## This Week''s Wins
- Hit a 5K PR: 22:25
- Maintained gym streak all week
- Helped onboard the new team member

## Challenges Faced
- Side project progress slower than hoped
- Felt imposter syndrome during architecture review

## Key Learnings
- Asking for help earlier saves time overall

## Time Audit
- Deep work: 18 hours
- Meetings: 10 hours
- Admin/email: 3 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟡 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Complete sprint stories
2. Prepare presentation
3. Maintain fitness routine

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 68.60400721275259, 93.62099650798739, 1300.0, 285.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '681e52e5-c9f6-4c0e-8dad-d859853d9a9c', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-10', '["292d6d02-8950-40d1-ad60-27f2919b40b0", "681e52e5-c9f6-4c0e-8dad-d859853d9a9c"]', '292d6d02-8950-40d1-ad60-27f2919b40b0', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '1ad16cf4-f6ff-4c6c-9588-98cc9f54ee18', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3647807c-c7e6-4b21-9b99-054d3289458a', '# Side Project: TaskFlow
Phase: Post-Launch
Date: April 11, 2026

## TaskFlow - Week 1 Post-Launch

### Launch Results! 🎉
- **ProductHunt**: #4 of the day
- **Downloads**: 2,400
- **Pro conversions**: 89 (3.7%)
- **Revenue**: $356

### User Feedback Themes
1. Love the simplicity
2. Want: Widgets (coming soon)
3. Want: Team features (considering)
4. Bug: Rare sync issues on slow networks

### Press Coverage
- Featured in "Top 10 Productivity Apps This Week"
- Mentioned in iOS dev newsletter

### What I Learned
- Launching is just the beginning
- Support takes more time than expected
- Users find bugs I never imagined

### Next Steps
- Widgets for iOS 17
- Android widget parity
- Explore team/collaboration features

### Commits total: 66

### Reflection
Building TaskFlow from idea to launch in ~2 months while working full-time was intense but rewarding. The best part: people actually use something I made!', 65.95871313258714, 92.2430003379137, 1300.0, 305.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '1ad16cf4-f6ff-4c6c-9588-98cc9f54ee18', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b924fbbe-f453-4ef7-83ec-04c160850ee5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '692ebcf5-affd-4544-93fb-e5df9836b8e7', '# Fitness Log - Week 9
Date: April 11, 2026

## Weekly Summary
- **Workouts completed**: 3/5 planned
- **Gym streak**: 36 days
- **Sleep avg**: 7.1 hours

## Workouts This Week
- Monday: 5K run (22:25)
- Wednesday: Strength training (Deadlift: 209lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 22:25 (was 22:52) 🎉 PR!
- Deadlift: 209lbs (+4lbs)
- Body weight: 172.3lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.
Hit a new PR on the 5K - sub-23 feels achievable now.

## Goals for Week 10
- Push for 5 workouts
- Add 5lbs to deadlift
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 6 glasses/day avg', 55.14675490139553, 80.01466648284004, 1300.0, 261.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'b924fbbe-f453-4ef7-83ec-04c160850ee5', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-11', '["1ad16cf4-f6ff-4c6c-9588-98cc9f54ee18", "b924fbbe-f453-4ef7-83ec-04c160850ee5"]', '1ad16cf4-f6ff-4c6c-9588-98cc9f54ee18', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '99c22f15-4bd2-4e18-aa84-b0c3fbe0094b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8e6718db-220b-4f5d-8bb2-bc3ba13a3871', '# Weekly Review - Week 9
Date: April 12, 2026

## This Week''s Wins
- Hit a 5K PR: 22:25
- Shipped the auth migration on time
- Great 1:1 with manager - clear path to promotion

## Challenges Faced
- Struggled with focus during afternoon meetings
- Felt imposter syndrome during architecture review

## Key Learnings
- Saying no to meetings protects deep work time

## Time Audit
- Deep work: 20 hours
- Meetings: 13 hours
- Admin/email: 4 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Complete sprint stories
2. Continue K8s learning
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Batch email checking to 2x/day', 65.48093260404647, 107.6038065104657, 1300.0, 288.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '99c22f15-4bd2-4e18-aa84-b0c3fbe0094b', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd808bb07-08e9-40a5-ab9b-c20247b96d8a', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'c8f5f273-1bc4-4063-8f8c-28b150f0a52a', '# Daily Journal - Sunday
Date: April 12, 2026

## Mood
Focused

## Today''s Highlights
- Knocked out 3 small tasks
- Got outside for a 20 min walk

## What I Learned
How to use React DevTools profiler effectively

## Tomorrow''s Calendar
- Meeting #9596
- Meeting #3703
- Meeting #4017

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 8: 31 practice sessions. Learning: Wish You Were Here, Blackbird (working on it)

## Gratitude
Grateful for the supportive team at work', 67.81409837930237, 99.6509480657786, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd808bb07-08e9-40a5-ab9b-c20247b96d8a', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7a0e01a4-c0e5-4abb-9bf8-9feaef161567', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'cd348b4b-5203-4eed-a030-d950b72a98de', '# Community Service Reflection
Date: April 12, 2026
Organization: Code2Community

## Impact Summary
- **Total volunteer hours**: 34
- **Mentorship weeks**: 9
- **Mentee**: Alex

## Meaningful Moments
Other volunteers commented on Alex''s improvement

## What I''m Learning
Through mentoring, I''m developing:
- Communication - explaining complex things simply
- Patience - learning to let Alex figure things out

## Why This Matters
Tech changed my life trajectory. Code2Community helps others access the same opportunity.
Giving back feels like paying forward what my mentors gave me.

## Connection to Values
- **Contribution**: Making a real difference in someone''s future
- **Growth**: Teaching deepens my own understanding
- **Connection**: Building a relationship across generations

## Gratitude
Thankful for:
- The Code2Community program coordinators
- Alex''s dedication and curiosity
- The chance to use my skills for something meaningful

## Looking Forward
Excited to help Alex with their portfolio project', 60.198613330495625, 84.85814923463528, 1300.0, 338.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7a0e01a4-c0e5-4abb-9bf8-9feaef161567', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-12', '["99c22f15-4bd2-4e18-aa84-b0c3fbe0094b", "d808bb07-08e9-40a5-ab9b-c20247b96d8a", "7a0e01a4-c0e5-4abb-9bf8-9feaef161567"]', '99c22f15-4bd2-4e18-aa84-b0c3fbe0094b', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '7ba6154b-23c2-4d8b-8947-4e371976b428', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'f21d22c1-63a8-4286-a564-4efbcfb51dd9', '# Daily Planning - Monday
Date: April 13, 2026

## Today''s Calendar
- Meeting #9596
- Meeting #3703
- Meeting #4017

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1250 implementation
3. Write unit tests

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #6284
- Meeting #6113
- Meeting #8881



## Personal
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 68.56137596866536, 104.01390414615214, 1300.0, 217.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '7ba6154b-23c2-4d8b-8947-4e371976b428', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '11b59fab-7cc7-4153-8799-98f02c762a75', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '1727ec6a-4633-4816-b0db-fe7847717b96', '# Learning Log
Date: April 13, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 12/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 10
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 23
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Excited about the conference opportunity - great motivation to learn.', 63.47587428238811, 86.10862440211497, 1300.0, 376.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '11b59fab-7cc7-4153-8799-98f02c762a75', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-13', '["7ba6154b-23c2-4d8b-8947-4e371976b428", "11b59fab-7cc7-4153-8799-98f02c762a75"]', '7ba6154b-23c2-4d8b-8947-4e371976b428', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b2383719-8854-408a-bd28-48d37f376e2b', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'a737cb4c-9d54-48f4-aef5-2a5f7a06eb58', '## Code Review Session
Date: April 14, 2026

### PRs Reviewed

**PR #2404: Auth Service Refactor** (by Mike)
- Good separation of concerns
- Suggested extracting token validation to util
- Approved with minor changes

**PR #2405: API Rate Limiting** (by me)
- Alex caught edge case with burst limits
- Added unit tests for Redis failover
- Merged after addressing feedback

**PR #2406: Dashboard Components** (by Taylor)
- Accessibility improvements needed
- Performance concern with large data sets
- Requested changes, follow-up Monday

### Code Quality Metrics
- Build time: 3m 55s (down from last week)
- Test coverage: 96.2%
- Lint errors: 0
- Bundle size: 1.85MB

### Patterns to Adopt
- Error boundary pattern from #2404
- React Query caching strategy
- Zod schema validation

### Tech Debt Noted
- Legacy auth code needs migration (PHOE-999)
- Duplicate API utilities in 3 places
- Consider monorepo for shared components', 53.91551559193186, 106.35541841879405, 1300.0, 309.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'b2383719-8854-408a-bd28-48d37f376e2b', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '53d84c02-9b0a-461e-bec6-cc9d021d5fa8', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '91b77a3a-1cb5-4674-8278-09684b4e660d', '# Side Project: TaskFlow
Phase: Post-Launch
Date: April 14, 2026

## TaskFlow - Week 1 Post-Launch

### Launch Results! 🎉
- **ProductHunt**: #4 of the day
- **Downloads**: 2,400
- **Pro conversions**: 89 (3.7%)
- **Revenue**: $356

### User Feedback Themes
1. Love the simplicity
2. Want: Widgets (coming soon)
3. Want: Team features (considering)
4. Bug: Rare sync issues on slow networks

### Press Coverage
- Featured in "Top 10 Productivity Apps This Week"
- Mentioned in iOS dev newsletter

### What I Learned
- Launching is just the beginning
- Support takes more time than expected
- Users find bugs I never imagined

### Next Steps
- Widgets for iOS 17
- Android widget parity
- Explore team/collaboration features

### Commits total: 70

### Reflection
Building TaskFlow from idea to launch in ~2 months while working full-time was intense but rewarding. The best part: people actually use something I made!', 59.00159904150351, 96.58083362299519, 1300.0, 305.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '53d84c02-9b0a-461e-bec6-cc9d021d5fa8', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-14', '["b2383719-8854-408a-bd28-48d37f376e2b", "53d84c02-9b0a-461e-bec6-cc9d021d5fa8"]', 'b2383719-8854-408a-bd28-48d37f376e2b', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'd198390f-52b9-4e5a-be3e-5c41fd91d498', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '2b5314d9-3b0a-41ae-8566-f01b4053a2f0', '## Daily Standup - Wednesday
Date: April 15, 2026

### My Updates

**Yesterday:**
- Completed PR #2346 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1250
- Address code review feedback on #2347
- Sync with Jordan on deployment pipeline

**Blockers:**
- No blockers today

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 66.73601977300382, 87.50361239231253, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'd198390f-52b9-4e5a-be3e-5c41fd91d498', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '438ba8ef-9e6f-4c10-bfc8-3091baf455f5', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'e78eab41-cf8c-43a8-aaf1-652dc1d61e7b', '# Daily Journal - Wednesday
Date: April 15, 2026

## Mood
Contemplative

## Today''s Highlights
- Good standup - Mike praised my PR
- Squeezed in a quick workout

## What I Learned
The importance of clear commit messages (got confused by old code)

## Tomorrow''s Calendar
- Meeting #9280
- Meeting #1924
- Meeting #4885

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 9: 32 practice sessions. Learning: Wish You Were Here, Blackbird (working on it)

## Gratitude
Appreciate having time for my hobbies', 54.42702351269723, 89.5297494417278, 1300.0, 209.33333333333334,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '438ba8ef-9e6f-4c10-bfc8-3091baf455f5', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-15', '["d198390f-52b9-4e5a-be3e-5c41fd91d498", "438ba8ef-9e6f-4c10-bfc8-3091baf455f5"]', 'd198390f-52b9-4e5a-be3e-5c41fd91d498', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '81d1c5f2-1059-44ea-9350-8d302a85d615', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    'adac3af8-972d-4e31-835c-91b6ec8f60fa', '# Daily Planning - Thursday
Date: April 16, 2026

## Today''s Calendar
- Meeting #9280
- Meeting #1924
- Meeting #4885
- Meeting #7305
- Meeting #3011

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1250 implementation
3. Update documentation

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async

## Tomorrow Preview
- Meeting #8687
- Meeting #7584
- Meeting #5664



## Personal
- 30 min guitar practice after work

## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 64.25604792925581, 99.87821208254599, 1300.0, 229.66666666666666,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '81d1c5f2-1059-44ea-9350-8d302a85d615', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'c5d2fc1d-d9fd-4ee8-8aac-a110005f0386', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '487925ac-b285-469f-8067-fb56d59b5265', '# Learning Log
Date: April 16, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 12/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 10
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 25
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Solved in 25 minutes

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
The book is dense but worth it. Taking good notes.', 62.71967019206601, 101.95016735449518, 1300.0, 370.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'c5d2fc1d-d9fd-4ee8-8aac-a110005f0386', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-16', '["81d1c5f2-1059-44ea-9350-8d302a85d615", "c5d2fc1d-d9fd-4ee8-8aac-a110005f0386"]', '81d1c5f2-1059-44ea-9350-8d302a85d615', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'af4346c2-e78f-49a1-99c7-9ddc6403de47', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '3dca05c2-c693-485e-8af4-b652f8750e78', '## Daily Standup - Friday
Date: April 17, 2026

### My Updates

**Yesterday:**
- Completed PR #2346 for API endpoint refactoring
- Reviewed Alex''s test automation changes
- Pair programmed with Mike on caching strategy

**Today:**
- Continue work on PHOE-1250
- Address code review feedback on #2347
- Sync with Jordan on deployment pipeline

**Blockers:**
- Dependency update broke tests - fixing

### Team Notes
- Sarah: Demo prep for stakeholders Friday
- Alex: Found edge case in auth flow - creating ticket
- Mike: Architecture review scheduled for 2pm', 63.22474237455205, 109.70916312926023, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'af4346c2-e78f-49a1-99c7-9ddc6403de47', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'f22bf32e-b95e-4727-bb0b-439379148762', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '69cae717-9990-4d69-b4a8-6da5f532c086', '# Weekly Review - Week 10
Date: April 17, 2026

## This Week''s Wins
- Maintained gym streak all week
- Shipped the auth migration on time
- Side project reached Post-Launch phase

## Challenges Faced
- Sleep schedule got off track
- Felt imposter syndrome during architecture review

## Key Learnings
- Asking for help earlier saves time overall

## Time Audit
- Deep work: 18 hours
- Meetings: 11 hours
- Admin/email: 5 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟡 Good momentum
- Wed: 🟡 Mid-week slump
- Thu: 🟢 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Work on side project
3. Maintain fitness routine

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Prepare tomorrow''s priorities the night before', 58.20207497431607, 80.60388537551393, 1300.0, 287.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'f22bf32e-b95e-4727-bb0b-439379148762', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-17', '["af4346c2-e78f-49a1-99c7-9ddc6403de47", "f22bf32e-b95e-4727-bb0b-439379148762"]', 'af4346c2-e78f-49a1-99c7-9ddc6403de47', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '48afce39-45cc-4471-8775-f707845e03f2', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '5c4036eb-c1a7-4867-9e26-a8462afb72ef', '# Side Project: TaskFlow
Phase: Post-Launch
Date: April 18, 2026

## TaskFlow - Week 1 Post-Launch

### Launch Results! 🎉
- **ProductHunt**: #4 of the day
- **Downloads**: 2,400
- **Pro conversions**: 89 (3.7%)
- **Revenue**: $356

### User Feedback Themes
1. Love the simplicity
2. Want: Widgets (coming soon)
3. Want: Team features (considering)
4. Bug: Rare sync issues on slow networks

### Press Coverage
- Featured in "Top 10 Productivity Apps This Week"
- Mentioned in iOS dev newsletter

### What I Learned
- Launching is just the beginning
- Support takes more time than expected
- Users find bugs I never imagined

### Next Steps
- Widgets for iOS 17
- Android widget parity
- Explore team/collaboration features

### Commits total: 73

### Reflection
Building TaskFlow from idea to launch in ~2 months while working full-time was intense but rewarding. The best part: people actually use something I made!', 51.19580754224721, 107.42348787834692, 1300.0, 305.3333333333333,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '48afce39-45cc-4471-8775-f707845e03f2', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '050eb838-68e6-47cb-b96d-dde745595662', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '22c05634-0089-4c82-b576-61486777f662', '# Fitness Log - Week 9
Date: April 18, 2026

## Weekly Summary
- **Workouts completed**: 4/5 planned
- **Gym streak**: 36 days
- **Sleep avg**: 6.7 hours

## Workouts This Week
- Monday: 5K run (22:02)
- Wednesday: Strength training (Deadlift: 209lbs)
- Friday: HIIT session (30 min)
- Saturday: Recovery run (3K easy pace)

## Progress vs Last Week
- 5K time: 22:02 (was 22:24) 🎉 PR!
- Deadlift: 209lbs (+4lbs)
- Body weight: 172.3lbs

## How I''m Feeling
Strong and motivated! The consistency is paying off.
Hit a new PR on the 5K - sub-23 feels achievable now.

## Goals for Week 10
- Push for 5 workouts
- Add 5lbs to deadlift
- Get to bed by 11pm consistently

## Nutrition Notes
- Meal prep on Sunday working well
- Need more protein on workout days
- Hydration: 6 glasses/day avg', 63.04858723371086, 86.74160913042314, 1300.0, 261.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '050eb838-68e6-47cb-b96d-dde745595662', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '0b8dbb22-f6dd-479b-87bf-dbad13e43e46', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '153d6bed-d7bf-4ddc-bfce-9bff0e270e9d', '# Quarterly Life Review - Q2
Date: April 18, 2026

## Core Values Check
Am I living aligned with what matters most?

### My Values
1. **Growth** - Continuously learning and improving
2. **Balance** - Sustainable pace, not burnout
3. **Contribution** - Making a positive difference
4. **Connection** - Meaningful relationships

## Life Areas Review

### Career
**Goals**: Get promoted to senior engineer, Launch side project
**Progress**: On track

### Health
**Goals**: Run sub-23 5K, Consistent gym habit
**Progress**: Maintaining

### Relationships
**Goals**: Deepen relationship with Morgan, Stay connected with family
**Progress**: Could be better

### Growth
**Goals**: Read 12 books this year, Learn Kubernetes
**Progress**: On pace

### Contribution
**Goals**: Mentor Alex successfully, Volunteer 50 hours
**Progress**: Going well


## Wins This Quarter
- Hit fitness milestone
- Reconnected with old friends
- Learned new technical skill

## Areas for Growth
- More present during family time
- Delegate more at work

## Intention for Next Quarter
Focus on depth over breadth - fewer goals, more impact

## Gratitude Reflection
Taking a moment to appreciate:
- Health and energy to pursue my goals
- Supportive relationships (Morgan, family, friends)
- Meaningful work and growth opportunities
- The ability to give back through mentoring

## One Word for Next Quarter
**Focus**', 69.8072111303284, 106.80872132145663, 1300.0, 462.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '0b8dbb22-f6dd-479b-87bf-dbad13e43e46', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-18', '["48afce39-45cc-4471-8775-f707845e03f2", "050eb838-68e6-47cb-b96d-dde745595662", "0b8dbb22-f6dd-479b-87bf-dbad13e43e46"]', '48afce39-45cc-4471-8775-f707845e03f2', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'ff9e438b-a109-46dc-b6f8-dec8d6eeec6e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '8cd57c11-7e39-4bf2-aa80-4f90532096e4', '# Weekly Review - Week 10
Date: April 19, 2026

## This Week''s Wins
- Got positive feedback on code review
- Side project reached Post-Launch phase
- Maintained gym streak all week

## Challenges Faced
- Felt imposter syndrome during architecture review
- Side project progress slower than hoped

## Key Learnings
- Asking for help earlier saves time overall

## Time Audit
- Deep work: 15 hours
- Meetings: 12 hours
- Admin/email: 4 hours
- Learning: 3 hours

## Energy Levels
- Mon: 🟢 Started strong
- Tue: 🟢 Good momentum
- Wed: 🔴 Mid-week slump
- Thu: 🟡 Recovered
- Fri: 🟢 Finished strong

## Next Week''s Priorities
1. Finish feature branch
2. Continue K8s learning
3. Schedule social time

## Gratitude
- Great team that supports each other
- Flexible work arrangement
- Health to pursue my goals

## One Thing to Improve
Take more breaks between meetings', 53.31928224356122, 87.6790049361092, 1300.0, 286.6666666666667,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'ff9e438b-a109-46dc-b6f8-dec8d6eeec6e', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    '113da0d3-ec98-42f1-a801-0018c7833a74', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '88df02fd-cda1-4d91-9f21-c15fa96c4da2', '# Daily Journal - Sunday
Date: April 19, 2026

## Mood
Focused

## Today''s Highlights
- Made progress on the tricky bug
- Squeezed in a quick workout

## What I Learned
How to use React DevTools profiler effectively

## Tomorrow''s Calendar
- Meeting #8306
- Meeting #7041
- Meeting #6904

Need to prepare for these.

## Life Update
Finally settled into the new place. Love the neighborhood.
Morgan met my friends last weekend. Everyone got along great.
Guitar week 9: 34 practice sessions. Learning: Wish You Were Here, Blackbird (working on it)

## Gratitude
Appreciate having time for my hobbies', 58.61794908362013, 103.33794167964615, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', '113da0d3-ec98-42f1-a801-0018c7833a74', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-19', '["ff9e438b-a109-46dc-b6f8-dec8d6eeec6e", "113da0d3-ec98-42f1-a801-0018c7833a74"]', 'ff9e438b-a109-46dc-b6f8-dec8d6eeec6e', 1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'bcfb6fdb-2604-4003-8cb7-0b36fbc06d7e', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '34181158-9c7b-4519-9613-23ff310b42d0', '# Daily Planning - Monday
Date: April 20, 2026

## Today''s Calendar
- Meeting #8306
- Meeting #7041
- Meeting #6904
- Meeting #8203
- Meeting #6313

## Priority Tasks
1. Complete PR review for Mike
2. Continue PHOE-1250 implementation
3. Write unit tests

## Time Blocks
- 9:00-10:00: Deep work on sprint task
- 10:00-12:00: Meetings (see calendar)
- 12:00-13:00: Lunch + walk
- 13:00-16:00: Implementation time
- 16:00-17:00: Code reviews & async







## End of Day Check
- [ ] Update JIRA tickets
- [ ] Push code & create PR if ready
- [ ] Respond to Slack messages', 65.65216699219371, 87.77202726838708, 1300.0, 200.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'bcfb6fdb-2604-4003-8cb7-0b36fbc06d7e', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO page (
    id, scroll, notebookId, background, backgroundType,
    parentFolderId, linkedEventId, createdAt, updatedAt
) VALUES (
    'b08f0312-cb4e-4a8d-9356-6fa41b6aebae', 0, NULL, 'blank', 'native',
    NULL, NULL, 1773109964986, 1773109964986
);
INSERT INTO text_annotations (
    id, content, x, y, width, height,
    fontSize, fontFamily, fontWeight, fontStyle, textColor, backgroundColor, textAlign, padding,
    spans, sourceFormat, pageId, sourceType, sourceStrokeIds, recognitionLanguage, recognitionConfidence,
    createdAt, updatedAt
) VALUES (
    '80b6afdc-1eff-402e-8781-6fadd4fb0d33', '# Learning Log
Date: April 20, 2026

## Currently Reading
**Designing Data-Intensive Applications**
- Progress: Chapter 12/12
- Key insight: The importance of idempotent operations in distributed systems
- Will apply: To our event processing pipeline

## Kubernetes Deep Dive
- Chapters completed: 10
- This week: Learned about StatefulSets and persistent volumes
- Practice: Set up local minikube cluster
- Goal: CKA certification by Q2

## LeetCode Progress
- Problems solved: 25
- Focus areas: Dynamic programming, graphs
- Today''s problem: "Longest Increasing Subsequence"
- Struggled with the optimization step

## Conference Prep: KubeCon
- Working on talk proposal about our migration story



## Team Knowledge Sharing
- Presented: "Intro to GraphQL" at team lunch
- Feedback: Team wants follow-up on subscriptions
- Next topic: "React Server Components"

## Resources Queue
- [ ] Finish DDIA chapter on replication
- [ ] Watch: "Scaling Instagram" talk
- [ ] Read: Stripe''s API design guidelines
- [ ] Course: Complete AWS Solutions Architect module 4

## Reflection
Feeling motivated. The K8s knowledge is already helping at work.', 56.5255221185337, 90.22342052057934, 1300.0, 380.0,
    48.0, 'sans-serif', 400, 'normal', -16777216, 0, 'left', 8.0,
    NULL, 'markdown', 'b08f0312-cb4e-4a8d-9356-6fa41b6aebae', 'typed', NULL, 'en', 0.95,
    1773109964986, 1773109964986
);
INSERT INTO daypage (
    date, pageIds, openPageId, createdAt, updatedAt
) VALUES (
    '2026-04-20', '["bcfb6fdb-2604-4003-8cb7-0b36fbc06d7e", "b08f0312-cb4e-4a8d-9356-6fa41b6aebae"]', 'bcfb6fdb-2604-4003-8cb7-0b36fbc06d7e', 1773109964986, 1773109964986
);

DELETE FROM kv WHERE `key` = 'AI_PREFERENCES';
DELETE FROM kv WHERE `key` = 'AI_PREFERENCES_PROMPT_STATE';
INSERT OR REPLACE INTO kv (`key`, value) VALUES ('AI_PREFERENCES', '{"workPriority":4,"educationPriority":3,"familyPriority":2,"healthPriority":3,"socialPriority":4,"hobbyPriority":5,"financePriority":3,"homePriority":3,"growthPriority":3,"lifeSituations":["FREELANCER"],"energyPattern":"AFTERNOON_PEAK","livingEnvironment":"URBAN","industry":"NOT_SET","profession":"NOT_SET","isActiveLifestyle":false,"isWeekendWarrior":false,"isHomebody":false,"isSocialButterfly":true,"isQuietIntrovert":false,"isOutdoorsLover":true,"isFoodie":true,"isDigitalNative":false,"hasKids":false,"hasPets":true,"isCareGiver":false,"livesAlone":false,"interests":["ART","MUSIC","PHOTOGRAPHY","WRITING","TRAVEL","COOKING"],"suggestionCount":"COMPREHENSIVE","suggestionStyle":"HOLISTIC","includeHealthReminders":true,"includeSocialReminders":true,"includeDeadlineWarnings":true,"vacationMode":false,"crunchMode":false,"holidayMode":false,"planningHorizon":"DAY_BY_DAY","focusStyle":"TASK_SWITCHING","sleepSchedule":"NIGHT_OWL","commuteStyle":"REMOTE","routinePreference":"SPONTANEOUS","stressResponse":"DECOMPRESS","setupComplete":true,"lastUpdated":1773109964996}');
INSERT OR REPLACE INTO kv (`key`, value) VALUES ('AI_PREFERENCES_PROMPT_STATE', '{"phase":"DEACTIVATED","totalCloses":0,"closesInCurrentPhase":0,"lastPromptTimestamp":1773109964996,"isDeactivated":true}');