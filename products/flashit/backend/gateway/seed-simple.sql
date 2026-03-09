-- Simple seed script for Flashit demo data
-- Password hash for "password123": $2b$10$tzEfCprA0.LXI1o2d1IOFOzOBN3nC0O.FBL3YvKY9dRXLuUuSV0xq

-- Insert users (if not exists)
INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'alice@example.com', '$2b$10$tzEfCprA0.LXI1o2d1IOFOzOBN3nC0O.FBL3YvKY9dRXLuUuSV0xq', 'Alice Anderson', NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000002', 'bob@example.com', '$2b$10$tzEfCprA0.LXI1o2d1IOFOzOBN3nC0O.FBL3YvKY9dRXLuUuSV0xq', 'Bob Builder', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- Insert spheres
INSERT INTO spheres (id, user_id, name, description, type, visibility, created_at, updated_at) VALUES
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Personal', 'My personal thoughts', 'PERSONAL', 'PRIVATE', NOW(), NOW()),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Work', 'Work notes', 'WORK', 'PRIVATE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Insert sphere access
INSERT INTO sphere_access (id, sphere_id, user_id, role, granted_at, granted_by) VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'OWNER', NOW(), '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'OWNER', NOW(), '00000000-0000-0000-0000-000000000001')
ON CONFLICT (sphere_id, user_id) DO NOTHING;

-- Insert sample moments
INSERT INTO moments (id, user_id, sphere_id, content_text, content_type, emotions, tags, sentiment_score, importance, entities, captured_at, ingested_at) VALUES
    ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Had a great idea for the new project today!', 'TEXT', ARRAY['Joy', 'Excitement'], ARRAY['#work', '#ideas'], 0.8, 4, ARRAY[]::text[], NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Feeling grateful for my supportive team.', 'TEXT', ARRAY['Joy', 'Trust'], ARRAY['#work', '#reflection'], 0.9, 5, ARRAY[]::text[], NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    ('30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'Need to finish the quarterly report by Friday.', 'TEXT', ARRAY['Anticipation'], ARRAY['#work', '#goals'], 0.0, 3, ARRAY[]::text[], NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),
    ('30000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Beautiful sunset today reminded me to slow down.', 'TEXT', ARRAY['Calm', 'Joy'], ARRAY['#reflection'], 0.7, 4, ARRAY[]::text[], NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours'),
    ('30000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'Client meeting went really well!', 'TEXT', ARRAY['Joy', 'Excitement'], ARRAY['#work'], 0.9, 5, ARRAY[]::text[], NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours')
ON CONFLICT (id) DO NOTHING;
