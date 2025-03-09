import socket

VRCHAT_OSC_IP = "127.0.0.1"
VRCHAT_OSC_PORT = 9000
osc_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

def send_pose_to_vrchat(pose_data):
    """ VRChat OSC 프로토콜을 활용하여 포즈 데이터를 VRChat에 전달 """
    for idx, (x, y, z) in enumerate(pose_data):
        osc_message = f"/avatar/parameters/bone_{idx} {x:.3f} {y:.3f} {z:.3f}"
        osc_socket.sendto(osc_message.encode(), (VRCHAT_OSC_IP, VRCHAT_OSC_PORT))
