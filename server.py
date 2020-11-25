import socket
import json
import numpy as np
import os
import sys
from detect_objects import load_model_u,predict
from keras.preprocessing import image

input_shape = (640, 640)


server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.bind(("", 5000))
server_socket.listen(5)

while True:
		print("TCPServer Waiting for client on port 5000")
		client_socket, address = server_socket.accept()
		print("I got a connection from "+str(address))

		while True:
			print("Esperando recibir datos...")
			data = client_socket.recv(1024)
			print(data)
			string_data = data.decode('utf-8')
			string_data = string_data.rstrip()
			print(string_data)
			data = json.loads(string_data)

			option = data['option']

			print("RECIEVED: "+str(data))

			# Abrir o cargar modelo
			if (option == "L"):
				model,preprocess_input,cats=load_model_u(data['modelo'])
				print("Modelo CARGADO")
				data_response = {"cargado" : True}
				string_data=json.dumps(data_response) + "\n"
			elif (option == "C"):
				data_response = {"closed" : True}
				string_data = json.dumps(data_response)+"\n"
				data=string_data.encode()
				print("Cerrando socket..."+str(data))
				client_socket.send(data)
				client_socket.close()
				print("Cerrado")
				break

				# FASTNET
			elif (option == "F"):
				img_path = data['path_img_selected']
				area=data['area']
				print("FASTNET")
				img=preprocess_input(image.img_to_array(image.load_img(img_path,target_size=input_shape)))
				img=np.expand_dims(img,0)
				result = predict(model,img,area,cats)
				string_data = json.dumps(result)+"\n"

			data=string_data.encode()
			print("Enviando datos al cliente...")#+str(data))
			client_socket.send(data)
			print("Enviado")
