export interface GoldenEvaluationFixture {
  domain: "SCIENCE" | "MATHEMATICS" | "MEDICAL";
  gradeLevel: string;
  cases: Array<{
    name: string;
    jobType:
      | "CLAIM"
      | "EXPLAINER"
      | "SIMULATION"
      | "ANIMATION"
      | "ASSESSMENT";
    outputData: Record<string, unknown>;
    minOverallScore: number;
  }>;
}

export const goldenEvaluationDatasets: GoldenEvaluationFixture[] = [
  {
    domain: "SCIENCE",
    gradeLevel: "grade_6_8",
    cases: [
      {
        name: "photosynthesis-claim",
        jobType: "CLAIM",
        minOverallScore: 0.84,
        outputData: {
          claims: [
            {
              text: "Photosynthesis stores light energy by helping plants turn carbon dioxide and water into glucose and oxygen.",
              scaffolding: {
                steps: [
                  "First identify where the plant captures sunlight.",
                  "Next trace how water and carbon dioxide enter the process.",
                  "Finally connect glucose production to stored chemical energy.",
                ],
              },
              misconceptionNote:
                "Common mistake: learners often think plants get food from soil instead of making glucose in their leaves.",
            },
          ],
          count: 1,
        },
      },
      {
        name: "heat-transfer-explainer",
        jobType: "EXPLAINER",
        minOverallScore: 0.82,
        outputData: {
          examples: [
            {
              scenario: "A metal spoon warms up in a pot of soup.",
              question: "Why does the spoon handle get hotter over time?",
              explanation:
                "Work step by step: first identify the hotter region, next trace particle collisions through the spoon, and then explain why conduction transfers thermal energy.",
              hint: "Compare the spoon to the soup and decide which material started with more thermal energy.",
              misconception:
                "Do not confuse conduction with convection; the spoon is not circulating like the soup.",
            },
          ],
          count: 1,
        },
      },
      {
        name: "pendulum-simulation",
        jobType: "SIMULATION",
        minOverallScore: 0.78,
        outputData: {
          simulations: [
            {
              id: "sim-photoscience-1",
              title: "Pendulum Energy Transfer",
              instructions: [
                "Start with a short release angle.",
                "Increase the angle and compare the motion.",
                "Record how potential and kinetic energy trade places.",
              ],
            },
          ],
        },
      },
      {
        name: "respiration-animation",
        jobType: "ANIMATION",
        minOverallScore: 0.78,
        outputData: {
          animations: [
            {
              id: "anim-science-1",
              title: "Gas Exchange Sequence",
              storyboard: [
                "Show oxygen entering the lungs.",
                "Zoom into alveoli and capillaries.",
                "End with oxygen moving into the bloodstream.",
              ],
            },
          ],
        },
      },
      {
        name: "cell-transport-assessment",
        jobType: "ASSESSMENT",
        minOverallScore: 0.8,
        outputData: {
          assessments: [
            {
              question: "Why does diffusion move particles from high concentration to low concentration?",
              correctAnswer:
                "Random particle motion causes net movement until the concentrations become more balanced.",
              explanation:
                "Step 1: identify where particles are crowded. Step 2: notice they move randomly in all directions. Step 3: explain why more particles leave the crowded side than return.",
              distractors: [
                "Particles only move when a cell uses ATP.",
                "Particles stop moving once they enter a membrane.",
              ],
              misconceptionTarget:
                "Students may think diffusion requires the cell to push particles actively.",
            },
          ],
        },
      },
    ],
  },
  {
    domain: "MATHEMATICS",
    gradeLevel: "grade_6_8",
    cases: [
      {
        name: "ratio-claim",
        jobType: "CLAIM",
        minOverallScore: 0.84,
        outputData: {
          claims: [
            {
              text: "Equivalent ratios describe the same multiplicative relationship even when both quantities are scaled by the same factor.",
              scaffolding: {
                steps: [
                  "First compare the two quantities.",
                  "Next multiply both parts by the same factor.",
                  "Finally check that the relationship stays constant.",
                ],
              },
              misconceptionNote:
                "Common mistake: adding the same amount to both terms does not preserve an equivalent ratio.",
            },
          ],
          count: 1,
        },
      },
      {
        name: "linear-pattern-explainer",
        jobType: "EXPLAINER",
        minOverallScore: 0.82,
        outputData: {
          examples: [
            {
              scenario: "A gym charges a membership fee plus a fixed monthly rate.",
              question: "How can you tell this situation is modeled by a linear relationship?",
              explanation:
                "Work through the pattern: first mark the starting fee, next compare how much the total changes each month, and then connect the constant rate of change to a linear model.",
              hint: "Look for the same increase each month.",
              misconception:
                "Do not confuse a constant difference with a constant multiplication factor.",
            },
          ],
          count: 1,
        },
      },
      {
        name: "coordinate-plane-simulation",
        jobType: "SIMULATION",
        minOverallScore: 0.78,
        outputData: {
          simulations: [
            {
              id: "sim-math-1",
              title: "Slope Explorer",
              instructions: [
                "Plot two points.",
                "Count rise and run.",
                "Compare how the graph changes when the ratio changes.",
              ],
            },
          ],
        },
      },
      {
        name: "fraction-animation",
        jobType: "ANIMATION",
        minOverallScore: 0.78,
        outputData: {
          animations: [
            {
              id: "anim-math-1",
              title: "Equivalent Fraction Zoom",
              storyboard: [
                "Split a rectangle into two equal parts.",
                "Subdivide each part again.",
                "Show why one-half matches two-fourths.",
              ],
            },
          ],
        },
      },
      {
        name: "equation-assessment",
        jobType: "ASSESSMENT",
        minOverallScore: 0.8,
        outputData: {
          assessments: [
            {
              prompt: "Solve 3x + 5 = 20. What is the first useful step?",
              correctAnswer: "Subtract 5 from both sides to isolate the term with x.",
              rationale:
                "Step 1: remove the constant term. Step 2: divide both sides by 3. Step 3: check the solution in the original equation.",
              distractors: [
                "Add 5 to both sides.",
                "Multiply both sides by 3.",
              ],
              commonMistake:
                "Students often divide by 3 first and leave the +5 attached to the variable term.",
            },
          ],
        },
      },
    ],
  },
  {
    domain: "MEDICAL",
    gradeLevel: "undergraduate",
    cases: [
      {
        name: "aseptic-technique-claim",
        jobType: "CLAIM",
        minOverallScore: 0.84,
        outputData: {
          claims: [
            {
              text: "Aseptic technique lowers infection risk by keeping sterile fields, instruments, and clinician contact surfaces free from contamination.",
              scaffolding: {
                steps: [
                  "First establish the sterile field.",
                  "Next separate sterile and non-sterile contact points.",
                  "Finally identify when the field must be reset after contamination.",
                ],
              },
              misconceptionNote:
                "Common mistake: learners assume clean gloves are always sterile gloves.",
            },
          ],
          count: 1,
        },
      },
      {
        name: "vital-signs-explainer",
        jobType: "EXPLAINER",
        minOverallScore: 0.82,
        outputData: {
          examples: [
            {
              scenario: "A patient becomes dizzy after standing up quickly.",
              question: "Which vital-sign trend would help explain the symptom?",
              explanation:
                "Start with baseline blood pressure, then compare the standing measurement, and finally connect the drop in pressure to reduced cerebral perfusion.",
              hint: "Check whether posture changed the reading.",
              misconception:
                "Do not assume a fast heart rate alone explains dizziness without checking blood pressure.",
            },
          ],
          count: 1,
        },
      },
      {
        name: "respiratory-simulation",
        jobType: "SIMULATION",
        minOverallScore: 0.78,
        outputData: {
          simulations: [
            {
              id: "sim-med-1",
              title: "Oxygen Saturation Response",
              instructions: [
                "Observe the normal reading.",
                "Change ventilation support.",
                "Explain why saturation responds over time.",
              ],
            },
          ],
        },
      },
      {
        name: "sterile-field-animation",
        jobType: "ANIMATION",
        minOverallScore: 0.78,
        outputData: {
          animations: [
            {
              id: "anim-med-1",
              title: "Sterile Field Setup",
              storyboard: [
                "Open the sterile pack without crossing the field.",
                "Place instruments in the sterile zone.",
                "Highlight the moment contamination occurs.",
              ],
            },
          ],
        },
      },
      {
        name: "triage-assessment",
        jobType: "ASSESSMENT",
        minOverallScore: 0.8,
        outputData: {
          assessments: [
            {
              question: "Which patient symptom requires the fastest escalation during respiratory triage?",
              correctAnswer:
                "Rapidly worsening work of breathing with falling oxygen saturation requires immediate escalation.",
              explanation:
                "Step 1: identify signs of airway or oxygenation failure. Step 2: compare them with non-urgent symptoms. Step 3: prioritize the highest-risk presentation first.",
              distractors: [
                "Mild sore throat with normal oxygen saturation.",
                "Stable cough that improves after rest.",
              ],
              misconceptionTarget:
                "A common misconception is treating symptom duration as more important than acute oxygenation risk.",
            },
          ],
        },
      },
    ],
  },
] as const;