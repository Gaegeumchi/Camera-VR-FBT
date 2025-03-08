import socket
import threading
import tkinter as tk
from PIL import Image, ImageTk
import io

def handle_client(client_socket, client_address):
    """클라이언트 연결 처리"""
    print(f"클라이언트 연결됨: {client_address}")
    try:
        data = client_socket.recv(1024)
        if not data:
            return
        message = data.decode().strip()
        print(f"받은 메시지: {message}")  # 받은 메시지 출력
        if message == "connect_signal":
            response = "fbt from gaegeumchi\n"
            client_socket.send(response.encode())
        else:
            client_socket.send("Unknown signal\n".encode())
    except Exception as e:
        print(f"오류 발생: {e}")
    finally:
        client_socket.close()
        print(f"클라이언트 연결 종료: {client_address}")

def start_socket_server(host, port):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((host, port))
    server_socket.listen(5)
    print(f"소켓 서버 시작: {host}:{port}")
    try:
        while True:
            client_socket, client_address = server_socket.accept()
            client_thread = threading.Thread(target=handle_client, args=(client_socket, client_address))
            client_thread.start()
    except KeyboardInterrupt:
        print("소켓 서버 종료")
    finally:
        server_socket.close()

if __name__ == "__main__":
    HOST = "0.0.0.0"
    PORT = 1818

    window = tk.Tk()
    label = tk.Label(window)
    label.pack()

    threading.Thread(target=start_socket_server, args=(HOST, PORT)).start()
    window.mainloop()