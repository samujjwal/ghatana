-- Seed demo workspace and project for Canvas-First UX testing

-- Create demo project
INSERT INTO "public"."Project" (
  id, 
  "ownerWorkspaceId", 
  "createdById",
  name,
  description,
  type,
  status,
  "lifecyclePhase",
  "isDefault",
  "aiSummary",
  "aiNextActions",
  "aiHealthScore",
  "createdAt",
  "updatedAt"
)
SELECT
  'project_canvas_demo_1',
  'workspace_canvas_demo',
  id,
  'Canvas Demo Project',
  'Interactive demo project showcasing the Canvas-First UX with all 7 lifecycle phases (INTENT → SHAPE → VALIDATE → GENERATE → RUN → OBSERVE → IMPROVE)',
  'FULL_STACK',
  'ACTIVE',
  'SHAPE',
  true,
  'Demo project at SHAPE phase with canvas workspace ready for testing',
  ARRAY['Design canvas layout', 'Create demo nodes', 'Test phase transitions'],
  72,
  NOW(),
  NOW()
FROM "public"."User" WHERE email = 'admin@yappc.com'
ON CONFLICT DO NOTHING;

-- Create workspace membership for admin
INSERT INTO "public"."WorkspaceMember" (
  id,
  "workspaceId",
  "userId",
  role,
  "createdAt"
)
SELECT
  'member_canvas_demo',
  'workspace_canvas_demo',
  id,
  'OWNER',
  NOW()
FROM "public"."User" WHERE email = 'admin@yappc.com'
ON CONFLICT DO NOTHING;

-- Verify seeding
SELECT 'Seeding complete!' as status;
SELECT COUNT(*) as workspace_count FROM "public"."Workspace" WHERE name = 'Canvas Demo Workspace';
SELECT COUNT(*) as project_count FROM "public"."Project" WHERE name = 'Canvas Demo Project';
SELECT 'Access at: http://localhost:7002/app/projects to view projects' as next_step;
