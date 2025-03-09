from fastapi import WebSocket, WebSocketDisconnect

class WebSocketServer:
    def __init__(self):
        self.clients = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.clients.append(websocket)

    async def disconnect(self, websocket: WebSocket):
        self.clients.remove(websocket)

    async def broadcast(self, message: str):
        for client in self.clients:
            await client.send_text(message)