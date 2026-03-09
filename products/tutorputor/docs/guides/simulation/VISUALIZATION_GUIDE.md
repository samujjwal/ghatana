# How to Visualize TutorPutor's Content Generation System

## 🎯 **Overview**

This guide shows you how to visually see and interact with TutorPutor's automated content generation system. The system provides multiple visualization components to monitor, preview, and understand the AI-powered content generation in real-time.

## 📊 **1. Content Generation Visualization Dashboard**

### **Location**: `/apps/tutorputor-web/src/components/content-generation/ContentGenerationVisualizationDashboard.tsx`

### **What It Shows**:

- **Real-time Metrics**: Total requests, success rate, generation time, confidence scores
- **Performance Charts**: Hourly performance trends, response time distribution
- **Quality Analytics**: Schema validation, grade appropriateness, content completeness
- **Domain Analysis**: Request volume and success rates by subject domain
- **Interactive Views**: Switch between Overview, Performance, Quality, and Domains tabs

### **How to Use**:

```bash
# Navigate to the web app
cd /home/samujjwal/Developments/ghatana/products/tutorputor/apps/tutorputor-web
npm start

# Visit the dashboard
http://localhost:3000/content-generation-dashboard
```

### **Key Features**:

- **Live Data Updates**: Metrics refresh every 2 seconds
- **Interactive Charts**: Hover over data points for details
- **Multi-view Dashboard**: Switch between different analysis perspectives
- **Performance Alerts**: Visual indicators for threshold breaches

## 🎮 **2. Interactive Simulation Preview**

### **Location**: `/apps/tutorputor-web/src/components/content-generation/SimulationPreview.tsx`

### **What It Shows**:

- **Live Simulations**: Interactive physics, chemistry, and algorithm demonstrations
- **Step-by-Step Playback**: Control simulation progression
- **Entity State Monitoring**: Real-time entity properties and transformations
- **Multiple Domains**: Physics pendulum, chemistry titration, CS algorithms

### **Available Demos**:

1. **Pendulum Motion** (Physics)
   - Adjustable gravity and mass
   - Real-time velocity tracking
   - String tension visualization

2. **Chemical Reaction** (Chemistry)
   - Acid-base titration
   - Color change indicators
   - Volume measurements

3. **Sorting Algorithm** (CS Discrete)
   - Bubble sort visualization
   - Element swapping animation
   - Pointer movement tracking

### **How to Use**:

```bash
# Access the simulation preview
http://localhost:3000/simulation-preview
```

### **Controls**:

- **▶️ Play**: Start simulation playback
- **⏸️ Pause**: Pause at current step
- **🔄 Reset**: Return to initial state
- **Step Navigation**: Click on progress dots to jump to specific steps

## 🔍 **3. Real-Time System Monitoring**

### **API Endpoints for Visualization**:

#### **Generation Metrics**

```bash
# Get current metrics
GET /api/v1/analytics/metrics

# Get performance data
GET /api/v1/analytics/performance

# Get quality metrics
GET /api/v1/analytics/quality
```

#### **System Status**

```bash
# Check system health
GET /api/v1/health

# Get active tasks
GET /api/v1/automation/tasks

# Get queue status
GET /api/v1/automation/queue
```

### **WebSocket Connection for Live Updates**:

```javascript
// Connect to real-time updates
const ws = new WebSocket("ws://localhost:3000/ws/analytics");
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  updateDashboard(data);
};
```

## 📈 **4. Analytics and Reporting**

### **Built-in Reports**:

1. **Performance Report**: Generation time trends, success rates
2. **Quality Report**: Validation results, confidence scores
3. **Domain Report**: Subject-specific performance metrics
4. **Usage Report**: Request patterns, peak hours, popular content

### **How to Generate Reports**:

```bash
# Generate daily performance report
curl -X POST http://localhost:3000/api/v1/reports/performance \
  -H "Content-Type: application/json" \
  -d '{"period": "daily", "format": "json"}'

# Export analytics data
curl -X GET http://localhost:3000/api/v1/analytics/export \
  -H "Accept: application/csv"
```

## 🎨 **5. Content Generation Process Visualization**

### **Step-by-Step Visualization**:

1. **Concept Input** → See how concepts are processed
2. **AI Generation** → Watch AI create content in real-time
3. **Template Matching** → See template selection process
4. **Quality Validation** → Monitor validation checks
5. **Final Output** → View generated content

### **How to Visualize the Process**:

```bash
# Enable debug mode for detailed logging
DEBUG=content-generation npm start

# View generation pipeline
http://localhost:3000/pipeline-visualization
```

## 🛠️ **6. Development Tools for Visualization**

### **Chrome DevTools Integration**:

```javascript
// Open DevTools and go to Console
// Monitor real-time generation events
window.tutorputor = {
  onGenerationStart: (request) => console.log("Starting:", request),
  onGenerationComplete: (result) => console.log("Complete:", result),
  onError: (error) => console.error("Error:", error),
};
```

### **React DevTools**:

```bash
# Install React DevTools
npm install --save-dev react-devtools

# Enable in development
import ReactDevTools from 'react-devtools';
ReactDevTools.initialize();
```

## 📱 **7. Mobile Visualization**

### **Responsive Design**:

- All visualization components are mobile-responsive
- Touch-friendly controls for simulation playback
- Optimized charts for smaller screens

### **Mobile Access**:

```bash
# Test on mobile devices
http://localhost:3000/mobile-dashboard

# Or use browser dev tools mobile simulation
```

## 🔧 **8. Custom Visualization Components**

### **Create Custom Charts**:

```typescript
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts';

const CustomMetricChart = ({ data }: { data: any[] }) => (
  <ResponsiveContainer width="100%" height={300}>
    <LineChart data={data}>
      <CartesianGrid strokeDasharray="3 3" />
      <XAxis dataKey="time" />
      <YAxis />
      <Tooltip />
      <Line type="monotone" dataKey="value" stroke="#8884d8" />
    </LineChart>
  </ResponsiveContainer>
);
```

### **Add Custom Metrics**:

```typescript
// Extend the metrics interface
interface ExtendedMetrics {
  customMetric: number;
  anotherMetric: string;
}

// Add to dashboard
const [extendedMetrics, setExtendedMetrics] = useState<ExtendedMetrics>({});
```

## 🚀 **9. Getting Started Quick Start**

### **1. Start the Development Server**:

```bash
cd /home/samujjwal/Developments/ghatana/products/tutorputor
./run-dev.sh
```

### **2. Access Visualizations**:

- **Dashboard**: http://localhost:3201/analytics-dashboard
- **Simulation Preview**: http://localhost:3201/simulation-preview
- **Pipeline Monitor**: http://localhost:3201/pipeline-monitor

### **3. Enable Real-time Updates**:

```bash
# Start the automation engine
curl -X POST http://localhost:3000/api/v1/automation/start \
  -H "Content-Type: application/json"
```

## 📊 **10. Understanding the Visual Data**

### **Performance Metrics**:

- **Generation Time**: Time to create content (target: <30s)
- **Success Rate**: Percentage of successful generations (target: >95%)
- **Confidence Score**: AI confidence in generated content (target: >0.8)

### **Quality Metrics**:

- **Schema Validation**: JSON schema compliance (target: 99%)
- **Grade Appropriateness**: Content matches grade level (target: 96%)
- **Content Completeness**: All required fields present (target: 98%)

### **Domain Metrics**:

- **Request Volume**: Number of requests per domain
- **Success Rate**: Domain-specific success percentages
- **Popular Content**: Most requested content types

## 🎯 **11. Troubleshooting Visualization Issues**

### **Common Issues and Solutions**:

#### **Dashboard Not Loading**:

```bash
# Check if services are running
curl http://localhost:3000/health

# Restart services if needed
./run-dev.sh --restart
```

#### **Charts Not Updating**:

```javascript
// Check WebSocket connection
if (ws.readyState === WebSocket.OPEN) {
  console.log("WebSocket connected");
} else {
  console.log("WebSocket disconnected, reconnecting...");
  ws.reconnect();
}
```

#### **Simulation Not Playing**:

```javascript
// Check if simulation state is properly initialized
if (!simulationState || Object.keys(simulationState).length === 0) {
  reset();
  console.log("Simulation state reset");
}
```

## 📚 **12. Advanced Visualization Features**

### **Historical Data Analysis**:

```bash
# Get historical metrics
curl -X GET "http://localhost:3000/api/v1/analytics/history?period=7d"

# Compare performance over time
curl -X GET "http://localhost:3000/api/v1/analytics/compare?start=2024-01-01&end=2024-01-31"
```

### **Predictive Analytics**:

```bash
# Get performance predictions
curl -X GET "http://localhost:3000/api/v1/analytics/predict?metric=generation_time"

# Get quality predictions
curl -X GET "http://localhost:3000/api/v1/analytics/predict?metric=confidence_score"
```

## 🎨 **13. Customizing Visualizations**

### **Change Color Themes**:

```css
/* Custom theme for charts */
:root {
  --chart-primary: #4caf50;
  --chart-secondary: #2196f3;
  --chart-accent: #ff9800;
}
```

### **Add Custom Animations**:

```css
/* Smooth transitions for data updates */
.chart-updates {
  transition: all 0.3s ease-in-out;
}

.metric-card {
  animation: pulse 2s infinite;
}
```

## 🔮 **14. Future Visualization Enhancements**

### **Planned Features**:

- **3D Visualization**: 3D simulation previews
- **VR/AR Support**: Immersive content preview
- **AI Insights**: AI-powered visualization recommendations
- **Collaborative Viewing**: Multi-user visualization sessions

### **Integration Opportunities**:

- **Learning Management Systems**: Embed visualizations in LMS
- **Mobile Apps**: Native mobile visualization apps
- **Third-party Tools**: Export to external visualization tools

---

## 🎉 **Summary**

The TutorPutor content generation system provides comprehensive visualization capabilities through:

✅ **Real-time Dashboard** - Monitor system performance and quality  
✅ **Interactive Simulations** - See generated content in action  
✅ **Process Visualization** - Understand the generation pipeline  
✅ **Mobile Responsive** - Access visualizations on any device  
✅ **Custom Components** - Build custom visualizations  
✅ **API Integration** - Connect external tools and services

Start exploring the visualizations today to see the automated content generation system in action!
