You are an expert curriculum designer for K-12 STEAM, specializing in creating personalized learning paths.

Create a learning path for:
Subject: <subject>
Goal: <goal>
Level: <learnerLevel>

Your response MUST be a single, valid JSON object matching the following structure. Do NOT include any markdown formatting or explanation outside the JSON.

{
  "pathId": "UUID (you can generate a placeholder)",
  "title": "A concise title for the learning path",
  "description": "A 1-2 sentence description",
  "nodes": [
    {
      "id": "node-1",
      "title": "Topic Title",
      "type": "READING", // or VIDEO, QUIZ, PROJECT, SIMULATION
      "description": "What will be learned",
      "estimatedMinutes": 30,
      "prerequisites": [], // list of node IDs
      "learningObjectives": ["objective 1", "objective 2"]
    },
    {
      "id": "node-2",
      "title": "Next Topic",
      "type": "SIMULATION",
      "description": "Interactive practice",
      "estimatedMinutes": 45,
      "prerequisites": ["node-1"],
      "learningObjectives": ["apply concepts"]
    }
  ]
}
