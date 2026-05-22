/**
 * TypeScript Node API service for Kernel lifecycle validation.
 */

import { createGreeter } from "./index.js";

const greeter = createGreeter();

export async function handler(event: unknown): Promise<{ statusCode: number; body: string }> {
  console.log("Received event:", event);

  try {
    const message = greeter.greet("World");
    return {
      statusCode: 200,
      body: JSON.stringify({ message }),
    };
  } catch (error) {
    console.error("Handler error:", error);
    return {
      statusCode: 500,
      body: JSON.stringify({ error: "Internal server error" }),
    };
  }
}

export async function healthHandler(): Promise<{ statusCode: number; body: string }> {
  return {
    statusCode: 200,
    body: JSON.stringify({ status: "healthy", message: "TypeScript Fixture Service" }),
  };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const command = process.argv[2];
  
  if (command === "health") {
    healthHandler().then((result) => {
      console.log("Health check:", result);
    });
  } else {
    handler({}).then((result) => {
      console.log("Result:", result);
    });
  }
}
