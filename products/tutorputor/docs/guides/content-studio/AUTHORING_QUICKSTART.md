# Simulation Authoring Quick Start Guide

## 🚀 Getting Started

This guide will help you create your first interactive simulation in TutorPutor in just 5 steps.

---

## Step 1: Access the Authoring Tool

### Via Web Interface
1. Navigate to `/simulations/create` in your browser
2. Or click **"Create Simulation"** from the simulations dashboard

### Via API
```bash
# Start authoring session
POST /api/v1/simulations/authoring/session
```

---

## Step 2: Define Learning Objectives (Planning Phase)

### What You'll Do
- Select your domain (Physics, Chemistry, CS, etc.)
- Define the topic you want to teach
- Set grade level and duration
- List learning objectives
- Specify prerequisites

### Example
```typescript
{
  domain: "PHYSICS",
  topic: "Newton's Second Law",
  gradeLevel: "high_school",
  duration: "45_minutes",
  objectives: [
    "Understand the relationship between force, mass, and acceleration",
    "Apply F=ma to real-world scenarios",
    "Predict motion outcomes"
  ],
  prerequisites: ["Basic algebra", "Force concepts"]
}
```

### Tips
- ✅ Use specific, measurable objectives
- ✅ Start with action verbs (understand, apply, analyze)
- ✅ Align with curriculum standards
- ❌ Avoid vague goals like "learn about physics"

---

## Step 3: Create Content (Creation Phase)

### Method 1: AI Generation (Recommended)
**Fastest way to create simulations**

1. Choose **"AI Generation"** method
2. Select AI provider (OpenAI, Anthropic, or Ollama)
3. Write a natural language prompt:

```
Create an interactive simulation showing Newton's second law with:
- A cart that can be pushed with variable force
- Adjustable mass slider
- Real-time acceleration visualization
- Force and acceleration vectors
- Step-by-step explanations
```

4. Click **"Generate Simulation"**
5. Review the generated manifest
6. Proceed to design phase

### Method 2: Template-Based
1. Choose **"Use Template"**
2. Browse template marketplace
3. Select a template that matches your needs
4. Customize parameters

### Method 3: Manual Design
1. Choose **"Manual Design"**
2. Build from scratch with full control
3. Add entities, steps, and interactions manually

---

## Step 4: Design & Animate (Design + Polish Phases)

### Design Phase: Configure Entities
1. **Add Interactive Elements**
   - Sliders for parameters
   - Buttons for actions
   - Input fields for data

2. **Set Physics Parameters**
   - Gravity: 9.8 m/s²
   - Friction: 0.1
   - Restitution: 0.3

3. **Position Entities**
   - Drag and drop on canvas
   - Set precise coordinates
   - Configure properties

### Polish Phase: Add Visual Design
1. **Choose Theme**
   - Modern Education
   - Dark Mode
   - High Contrast
   - Colorful

2. **Configure Animations**
   - Set timeline duration
   - Add keyframes
   - Choose easing functions

3. **Enable Accessibility**
   - ✅ Screen reader narration
   - ✅ Keyboard shortcuts
   - ✅ Reduced motion support
   - ✅ High contrast mode
   - ✅ Alt text for visuals

4. **Add Assessment**
   - Multiple choice questions
   - Free response
   - Interactive checks
   - ECD-based grading

---

## Step 5: Review & Publish (Review Phase)

### Validation
1. Click **"Validate"** tab
2. Review errors and warnings
3. Fix any issues

### AI Refinement (Optional)
1. Click **"AI Refinement"** tab
2. Request improvements:
   - "Add more intermediate steps"
   - "Improve accessibility"
   - "Add visual feedback"
3. Apply auto-fix suggestions

### Publication
1. Click **"Publish"** tab
2. Complete publication checklist:
   - ✅ Content accuracy verified
   - ✅ Accessibility features enabled
   - ✅ Assessment aligned with objectives
   - ✅ Simulation tested
3. Add tags and notes
4. Click **"Publish Simulation"**

---

## 🎯 Complete Example: Creating a Projectile Motion Simulation

### 1. Planning (2 minutes)
```typescript
{
  domain: "PHYSICS",
  topic: "Projectile Motion",
  gradeLevel: "high_school",
  duration: "30_minutes",
  objectives: [
    "Understand parabolic trajectory",
    "Analyze horizontal and vertical components",
    "Calculate range and maximum height"
  ],
  prerequisites: ["Kinematics", "Trigonometry"]
}
```

### 2. AI Generation (1 minute)
**Prompt:**
```
Create an interactive projectile motion simulator where students can:
- Adjust launch angle (0-90°) with a slider
- Set initial velocity (0-50 m/s) with a slider
- See the trajectory path in real-time
- View velocity vectors at different points
- Display range and max height calculations
- Include step-by-step physics explanations
```

### 3. Design (3 minutes)
- Add angle slider (0-90°)
- Add velocity slider (0-50 m/s)
- Configure gravity (9.8 m/s²)
- Position projectile launcher
- Add trajectory visualization

### 4. Polish (2 minutes)
- Select "Modern Education" theme
- Enable all accessibility features
- Add 3 assessment questions:
  1. "What angle gives maximum range?"
  2. "How does velocity affect trajectory?"
  3. "Calculate range for 45° at 20 m/s"

### 5. Publish (1 minute)
- Validate (all checks pass)
- Add tags: `physics, projectile, motion, interactive`
- Publish to marketplace

**Total Time: ~10 minutes** ⚡

---

## 📊 Authoring Flow Navigation

### Phase Navigation
```
Planning → Creation → Design → Polish → Review → Publish
   ↓         ↓          ↓         ↓        ↓
 Back     Back       Back      Back     Back
```

### Quick Actions
- **Save Draft**: Save progress at any phase
- **Preview**: Test simulation at any time
- **Validate**: Check for errors
- **AI Refine**: Get improvement suggestions

---

## 🎨 UI Components Reference

### Available in Each Phase

#### Planning Phase
- Domain selector
- Topic input
- Grade level selector
- Duration selector
- Objectives list (add/remove)
- Prerequisites list (add/remove)

#### Creation Phase
- Method selector (AI/Template/Manual)
- AI provider selector
- Prompt textarea
- Template browser
- Manual editor

#### Design Phase
- Entity list
- Canvas preview
- Property editor
- Physics configurator
- Interaction builder

#### Polish Phase
- Timeline editor
- Theme selector
- Color pickers
- Accessibility toggles
- Assessment builder

#### Review Phase
- Preview player
- Validation results
- AI refinement panel
- Publication form

---

## 🔗 API Integration

### Create Simulation via API
```bash
# 1. Generate with AI
POST /api/v1/simulations/generate
{
  "prompt": "Create physics simulation...",
  "domain": "PHYSICS",
  "provider": "openai-primary"
}

# 2. Validate
POST /api/v1/simulations/:id/validate
{
  "manifest": { ... }
}

# 3. Publish
POST /api/v1/simulations/:id/publish
{
  "status": "published",
  "tags": ["physics", "interactive"]
}
```

### Bulk Generation
```bash
POST /api/v1/simulations/bulk/generate
{
  "concepts": ["newton_laws", "projectile_motion"],
  "options": {
    "autoPublish": false,
    "includeAssessments": true
  }
}
```

---

## ⚡ Pro Tips

### For Faster Authoring
1. **Use AI Generation** - 10x faster than manual
2. **Start with Templates** - Customize existing work
3. **Save Drafts Often** - Don't lose progress
4. **Use Quick Actions** - Keyboard shortcuts available

### For Better Quality
1. **Validate Early** - Catch issues sooner
2. **Use AI Refinement** - Get expert suggestions
3. **Test Thoroughly** - Preview before publishing
4. **Enable Accessibility** - Reach all learners

### For Maximum Impact
1. **Align with Standards** - Map to curriculum
2. **Add Assessments** - Measure learning
3. **Use ECD Grading** - Evidence-based evaluation
4. **Iterate Based on Data** - Improve over time

---

## 🆘 Troubleshooting

### Common Issues

**AI Generation Fails**
- ✅ Check API key configuration
- ✅ Try different provider (Anthropic, Ollama)
- ✅ Simplify prompt
- ✅ Check rate limits

**Validation Errors**
- ✅ Review error messages
- ✅ Use AI auto-fix suggestions
- ✅ Check required fields
- ✅ Verify entity references

**Performance Issues**
- ✅ Reduce entity count (< 50)
- ✅ Limit steps (< 200)
- ✅ Optimize canvas size
- ✅ Enable lazy loading

---

## 📚 Next Steps

### Learn More
- [Complete Authoring Flow Guide](./SIMULATION_AUTHORING_FLOW.md)
- [AI Providers Setup](./AI_PROVIDERS_GUIDE.md)
- [Performance Optimization](./SIMULATION_PERFORMANCE_GUIDE.md)
- [Accessibility Guidelines](./SIMULATION_ACCESSIBILITY_GUIDE.md)

### Get Help
- Check documentation
- Review example simulations
- Contact support
- Join community forum

---

## 🎉 You're Ready!

You now have everything you need to create engaging, interactive simulations. Start with the AI generation method for the fastest results, then explore advanced features as you become more comfortable.

**Happy Creating! 🚀**
