// Minimal Socket.io test
import express from 'express';
import { createServer } from 'http';
import { Server } from 'socket.io';

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer);

app.get('/test', (req, res) => {
  res.json({ message: 'Express works' });
});

io.on('connection', (socket) => {
  console.log('Client connected:', socket.id);
  socket.emit('welcome', { message: 'Welcome!' });
});

const PORT = 3002;
httpServer.listen(PORT, () => {
  console.log(`Test server running on port ${PORT}`);
  console.log(`Try: curl http://localhost:${PORT}/test`);
  console.log(`Socket.io path: /socket.io/`);
});
