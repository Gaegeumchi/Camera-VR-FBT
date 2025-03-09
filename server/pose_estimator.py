import cv2
import mediapipe as mp
import numpy as np

class PoseEstimator:
    def __init__(self):
        self.mp_pose = mp.solutions.pose
        self.pose = self.mp_pose.Pose()

    async def process_frame(self, image):
        """ Mediapipe를 활용하여 포즈 추출 """
        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        results = self.pose.process(image_rgb)
        if results.pose_landmarks:
            landmarks = [(lm.x, lm.y, lm.z) for lm in results.pose_landmarks.landmark]
            return landmarks
        return None
