import { createServer } from "./createServer.js";

const port = Number(process.env.PORT ?? 3200);
const host = "0.0.0.0";

async function start() {
  const app = await createServer();
  try {
    await app.listen({ port, host });
    app.log.info(`TutorPutor API Gateway listening on ${host}:${port}`);
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

void start();

