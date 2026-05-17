/*
* Copyright (c) Joan-Manuel Marques 2013. All rights reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
*
* This file is part of the practical assignment of Distributed Systems course.
*
* This code is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This code is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this code.  If not, see <http://www.gnu.org/licenses/>.
*/

package recipes_service.tsae.sessions;


import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import communication.ObjectInputStream_DS;
import communication.ObjectOutputStream_DS;
import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import recipes_service.ServerData;
import recipes_service.communication.Message;
import recipes_service.communication.MessageAErequest;
import recipes_service.communication.MessageEndTSAE;
import recipes_service.communication.MessageOperation;
import recipes_service.communication.MsgType;
import recipes_service.data.Operation;
import recipes_service.tsae.data_structures.TimestampMatrix;
import recipes_service.tsae.data_structures.TimestampVector;

import lsim.library.api.LSimLogger;

/**
 * @author Joan-Manuel Marques
 * December 2012
 *
 */
public class TSAESessionPartnerSide extends Thread{
	
	private Socket socket = null;
	private ServerData serverData = null;
	private final ReadWriteLock lock = new ReentrantReadWriteLock();


	public TSAESessionPartnerSide(Socket socket, ServerData serverData) {
		super("TSAEPartnerSideThread");
		this.socket = socket;
		this.serverData = serverData;
	}

	public void run() {

		Message msg = null;

		int current_session_number = -1;

		try {
			ObjectOutputStream_DS out = new ObjectOutputStream_DS(socket.getOutputStream());
			ObjectInputStream_DS in = new ObjectInputStream_DS(socket.getInputStream());

			TimestampVector localSummary;
			TimestampMatrix localAck;

			lock.writeLock().lock();
			try {
				localSummary = serverData.getSummary().clone();
				// Actualizamos nuestra propia entrada en la matriz ACK con nuestro progreso actual
				serverData.getAck().update(serverData.getId(), localSummary);
				localAck = serverData.getAck().clone();
			} finally {
				lock.writeLock().unlock();
			}


			// receive request from originator and update local state
			// receive originator's summary and ack
			msg = (Message) in.readObject();
			current_session_number = msg.getSessionNumber();
			LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: "+current_session_number+"] TSAE session");
			LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: "+current_session_number+"] received message: "+ msg);

			if (msg instanceof MessageAErequest originator) {

				// Enviar operaciones que el Originador no tiene
				// listNewer usa internamente un readLock para recorrer el log de forma segura
				List<Operation> missingOps = serverData.getLog().listNewer(originator.getSummary());
				for (Operation op : missingOps) {
					var opMsg = new MessageOperation(op);
					opMsg.setSessionNumber(current_session_number);
					out.writeObject(opMsg);
				}

				// Enviar nuestro snapshot (Vectores de Resumen y Confirmación)
				var responseMsg = new MessageAErequest(localSummary, localAck);
				responseMsg.setSessionNumber(current_session_number);
				out.writeObject(responseMsg);

				// Recibir operaciones enviadas por el Originador
				List<Operation> incomingOps = new ArrayList<>();
				Message messageRecib = (Message) in.readObject();
				while (messageRecib instanceof MessageOperation opMsg) {
					incomingOps.add(opMsg.getOperation());
					messageRecib = (Message) in.readObject();
				}

				// Actualización de base de datos y metadatos, si recibimos el fin de sesión correctamente, aplicamos los cambios
				if (messageRecib instanceof MessageEndTSAE) {
					// Enviamos confirmación de cierre
					var endMsg = new MessageEndTSAE();
					endMsg.setSessionNumber(current_session_number);
					out.writeObject(endMsg);

					lock.writeLock().lock();
					try {
						// Ejecutar operaciones y actualizar el conocimiento máximo (updateMax)
						for (Operation op : incomingOps) {
							serverData.execOperation(op);
						}
						serverData.getSummary().updateMax(originator.getSummary());
						serverData.getAck().updateMax(originator.getAck());

					} finally {
						lock.writeLock().unlock();
					}
				}
			}
			socket.close();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			LSimLogger.log(Level.FATAL, "[TSAESessionPartnerSide] [session: "+current_session_number+"]" + e.getMessage());
			e.printStackTrace();
            System.exit(1);
		}catch (IOException e) {
	    }
		
		LSimLogger.log(Level.TRACE, "[TSAESessionPartnerSide] [session: "+current_session_number+"] End TSAE session");
	}
}
