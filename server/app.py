import socket
import threading
import sys
import io
from PyQt5.QtWidgets import QApplication, QLabel, QVBoxLayout, QWidget
from PyQt5.QtGui import QPixmap, QImage
from PIL import Image

class CameraServer(QWidget):
    def __init__(self, host="0.0.0.0", port=1818):
        super().__init__()
        self.host = host
        self.port = port

        # GUI 설정
        self.setWindowTitle("Camera Stream Server")
        self.setGeometry(100, 100, 640, 480)
        self.layout = QVBoxLayout()
        self.label = QLabel("Waiting for frames...")
        self.layout.addWidget(self.label)
        self.setLayout(self.layout)

        # 소켓 서버 실행
        self.server_thread = threading.Thread(target=self.start_socket_server, daemon=True)
        self.server_thread.start()

    def start_socket_server(self):
        """카메라 영상을 수신하는 소켓 서버"""
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.bind((self.host, self.port))
        server_socket.listen(5)
        print(f"소켓 서버 시작: {self.host}:{self.port}")

        try:
            while True:
                client_socket, client_address = server_socket.accept()
                print(f"클라이언트 연결됨: {client_address}")
                threading.Thread(target=self.handle_client, args=(client_socket, client_address), daemon=True).start()
        except Exception as e:
            print(f"서버 오류 발생: {e}")
        finally:
            server_socket.close()

    def handle_client(self, client_socket, client_address):
        """클라이언트의 카메라 스트림을 수신하여 GUI에 표시"""
        print(f"클라이언트 연결됨: {client_address}")
        try:
            client_socket.sendall(b"fbt from gaegeumchi\n")

            while True:
                # 데이터 크기 먼저 수신
                data_size_bytes = client_socket.recv(4)
                if not data_size_bytes:
                    break
                data_size = int.from_bytes(data_size_bytes, byteorder="big")
                print(f"수신할 데이터 크기: {data_size} bytes")  # ✅ 디버깅 추가

                # 이미지 데이터 수신
                image_data = b""
                while len(image_data) < data_size:
                    chunk = client_socket.recv(data_size - len(image_data))
                    if not chunk:
                        break
                    image_data += chunk

                print(f"이미지 데이터 수신 완료! 크기: {len(image_data)} bytes")  # ✅ 디버깅 추가

                if not image_data:
                    break

                # 이미지 변환 및 표시
                self.display_image(image_data)

        except Exception as e:
            print(f"클라이언트 처리 중 오류 발생: {e}")
        finally:
            print(f"클라이언트 연결 종료: {client_address}")
            client_socket.close()




    def display_image(self, image_data):
        """수신된 이미지 데이터를 QLabel에 표시"""
        try:
            image = Image.open(io.BytesIO(image_data))
            image = image.convert("RGB")

            # PIL 이미지를 PyQt QImage로 변환
            qimage = QImage(image.tobytes(), image.width, image.height, QImage.Format_RGB888)
            pixmap = QPixmap.fromImage(qimage)

            # QLabel에 업데이트
            self.label.setPixmap(pixmap)
            self.label.setScaledContents(True)
        except Exception as e:
            print(f"이미지 표시 중 오류 발생: {e}")

# GUI 실행
if __name__ == "__main__":
    app = QApplication(sys.argv)
    server = CameraServer()
    server.show()
    sys.exit(app.exec_())
