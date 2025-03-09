const express = require("express");
const http = require("http");
const WebSocket = require("ws");
const cors = require("cors");

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

app.use(cors());
app.use(express.json());

let poseData = null;

wss.on("connection", (ws) => {
    console.log("New client connected");
    if (poseData) {
        ws.send(JSON.stringify(poseData));
    }
    ws.on("message", (message) => {
        poseData = JSON.parse(message);
        wss.clients.forEach(client => {
            if (client.readyState === WebSocket.OPEN) {
                client.send(JSON.stringify(poseData));
            }
        });
    });
    ws.on("close", () => console.log("Client disconnected"));
});

app.get("/pose", (req, res) => {
    res.json(poseData || {});
});

server.listen(3000, () => {
    console.log("Server is running on port 3000");
});
