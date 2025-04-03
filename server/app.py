from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import cv2
import mediapipe as mp
import numpy as np
import asyncio
import json
import socket
from network_handler import WebSocketServer
from pose_estimator import PoseEstimator
from steamvr_integration import send_pose_to_vrchat

app = FastAPI()
websocket_server = WebSocketServer()
pose_estimator = PoseEstimator()

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    websocket_server.clients.append(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            frame_data = json.loads(data)
            frame_array = np.array(frame_data, dtype=np.uint8).reshape((480, 640, 3))
            pose_data = await pose_estimator.process_frame(frame_array)
            if pose_data:
                send_pose_to_vrchat(pose_data)
    except WebSocketDisconnect:
        websocket_server.clients.remove(websocket)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=1818)
